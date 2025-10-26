# Auto-Fishing Technical Documentation

## Overview

The auto-fishing feature is an AI-powered system that automatically controls the bobber during the Stardew Fishing minigame. It uses predictive control algorithms to track fish movement and maintain optimal bobber position.

## Implementation Details

### Location
- **File**: `src/main/java/com/bonker/stardewfishing/client/FishingScreen.java`
- **Method**: `shouldAutoClick()` (lines 432-496)

### Key Features

#### 1. Toggle Control
- **Default State**: Enabled by default (`autoFishingEnabled = true`)
- **Toggle Key**: Press `A` during the minigame
- **Visual Feedback**:
  - Status displayed in top-left corner
  - Green text when enabled: "Auto: ON (A)"
  - Red text when disabled: "Auto: OFF (A)"
- **Audio Feedback**:
  - Success sound when enabled
  - Dwop sound when disabled

#### 2. AI Control Algorithm

The auto-fishing AI uses several strategies:

##### Predictive Control
```java
double velocityEstimate = bobberPos - lastBobberVelocity;
double predictedBobberPos = bobberPos + velocityEstimate * 2;
```
- Estimates bobber velocity based on previous position
- Predicts future bobber position (2 ticks ahead)
- Makes decisions based on predicted position rather than current position

##### Dead Zone Control
```java
double deadZone = barSize * 0.15;
```
- Implements a 15% dead zone relative to bar size
- Prevents rapid toggling/jittering
- Allows smooth control transitions

##### Distance-Based Decision Making
The AI decides whether to click based on:
1. **Predicted position relative to fish**
   - Click if fish is above predicted bobber position
   - Don't click if fish is below predicted bobber position

2. **Fine control when close**
   - Maintains fish near center of bobber bar
   - Uses dead zone to prevent oscillation

##### Enhanced Treasure Chest Strategy with Velocity Prediction (Unlimited Range)
The AI now uses an intelligent velocity-aware system for chest pursuit with NO distance limits:

**Velocity-Based Prediction**:
- Reads bobber and fish velocity from game state
- Predicts positions 2.5 ticks ahead using physics simulation
- Considers movement trajectories, not just current positions
- Evaluates if bobber can cover both targets in its path

**Trajectory Analysis** (`canCoverBothTargets` helper method):
- Simulates bobber movement over next few ticks
- Checks if predicted bobber bar will overlap both fish and chest
- Requires active velocity (bobber must be moving)
- Enables opportunistic dual-capture in single movement

**Safety Evaluation** (`isSafeToChaseChest` helper method):
- Assesses whether chasing the chest risks losing the fish
- **NEW**: Uses predicted distances instead of just current distances
- **NEW**: Detects if fish is moving away rapidly (velocity > 2-3)
- **NEW**: More conservative when fish has high escape velocity
- **NEW**: More aggressive when fish is idle or slow (velocity < 0.3-0.5)
- Considers fish progress as risk tolerance (higher progress = more aggressive)
- Dynamic risk thresholds based on game state and motion

**Priority Levels** (evaluated in order):
1. **Finish chest if nearly caught** (>70% chest progress)
   - Completes chest capture to avoid losing progress
   - **NO DISTANCE LIMIT** - goes for chest anywhere

2. **Trajectory-based dual capture** (NEW)
   - If predicted path covers both fish and chest simultaneously
   - Requires fish progress >30% for safety
   - Most efficient capture method
   - **NO DISTANCE LIMIT**

3. **Aggressive pursuit at high fish progress** (>70%)
   - **NO DISTANCE LIMIT** - chases chest anywhere if fish is safe
   - Only blocked if fish moving extremely fast away (velocity > 4.0)

4. **On-fish dual capture** (fish progress >40%)
   - When already on the fish
   - **NO DISTANCE LIMIT** - goes for chest anywhere

5. **Maintain chest progress** (>30%)
   - Prevents losing partial chest progress
   - **NO DISTANCE LIMIT**

6. **Idle fish exploitation** (fish velocity < 0.5, progress >50%)
   - **NO DISTANCE LIMIT** - chases chest anywhere when fish is idle

7. **Medium progress aggressive chase** (>50%)
   - Chases chest if predicted fish position is safe
   - Requires predicted fish distance < 1.2x bar size

**Safety Thresholds** (Fish-Centric, NOT Distance-Limited):
- **Core Principle**: Evaluate if FISH is safe, not if CHEST is close
- **Fish close** (distance < 40% bar): Always safe - chase chest anywhere
- **Fish predicted close** (predicted < 60% bar): Always safe - chase chest anywhere
- **Fish idle** (velocity < 0.3): Always safe - chase chest anywhere
- **Fish slow** (velocity < 0.8, distance < 1.2x bar): Safe - chase chest anywhere
- **Fish progress >80%**: Chase chest anywhere
  - ONLY reject if fish velocity > 4.0 AND predicted distance > 1.5x bar
- **Fish progress >70%**: Chase chest anywhere
  - ONLY reject if fish moving away fast (velocity > 3.0) AND predicted distance > 1.2x bar
- **Fish progress >60%**: Chase chest anywhere
  - ONLY reject if fish moving away rapidly (velocity > 2.5) AND predicted distance > bar size
- **Fish progress >50%**: Chase if fish not moving away (velocity < 2.0 in wrong direction)
- **Fish progress >30%**: Need fish close or predicted close
- **Fish progress <30%**: Very conservative - need fish very close

**Key Change**: Removed all hard chest distance limits. AI now focuses purely on "will the fish escape?" rather than "is the chest too far?"

### Integration with Manual Control

The auto-fishing feature works alongside manual input:

```java
boolean effectiveMouseDown = mouseDown || shouldAutoClick();
minigame.tick(effectiveMouseDown);
```

- Manual clicks always override AI decisions
- Player can take control at any time
- AI seamlessly resumes when player releases input

### State Variables

```java
private boolean autoFishingEnabled = true;  // Feature toggle state
private double lastBobberVelocity = 0;      // For velocity calculation
```

## Algorithm Behavior

### Scenario 1: Fish Above Bobber
1. Predict bobber will be too low
2. Return `true` (click to move up)
3. Bobber accelerates upward

### Scenario 2: Fish Below Bobber
1. Predict bobber will be too high
2. Return `false` (don't click, let gravity work)
3. Bobber falls downward

### Scenario 3: Fish Near Center
1. Calculate distance from center
2. Check if within dead zone (±15% of bar size)
3. If in dead zone: maintain current state
4. If outside: adjust toward center

### Scenario 4: Chest Visible
1. Check if chest is catchable
2. If on fish already, calculate chest distance
3. Adjust control to catch both if possible
4. Maintain fish priority

## Configuration

Currently, auto-fishing has no configuration options. Future improvements could include:

- Config file to set default enabled/disabled state
- Adjustable dead zone percentage
- Prediction lookahead distance (currently 2 ticks)
- Custom key binding for toggle
- Difficulty/aggressiveness settings

## Performance Considerations

- Algorithm runs every tick during minigame
- Minimal computational overhead
- No network traffic (client-side only)
- No impact on multiplayer fairness (similar to manual play)

## Limitations

1. **Not Perfect**: AI may fail on extremely difficult fish
2. **Legendary Fish**: May struggle with rapidly moving legendary fish
3. **Predictive Accuracy**: Limited to 2-tick prediction window
4. **Bar Size Dependent**: Performance scales with bar size

## Future Improvements

Potential enhancements:
- Machine learning model trained on expert gameplay
- Adaptive difficulty adjustment
- Per-fish strategy customization
- Multi-tick prediction using fish behavior patterns
- Integration with datapack fish difficulty settings
- Statistics tracking (success rate, average accuracy, etc.)

## Testing

To test the auto-fishing feature:

1. Start a fishing minigame
2. Observe green "Auto: ON" indicator
3. Watch as AI controls the bobber automatically
4. Press `A` to toggle off and compare manual vs. auto performance
5. Try with different fish difficulties
6. Test chest catching behavior

## Code References

- Main implementation: `FishingScreen.java:432-551`
- Velocity-aware chest strategy: `FishingScreen.java:446-515`
- Trajectory analysis helper: `FishingScreen.java:553-577`
- Enhanced safety evaluation: `FishingScreen.java:579-648`
- Velocity getters: `FishingMinigame.java:267-273`
- Toggle handler: `FishingScreen.java:367-374`
- UI display: `FishingScreen.java:214-220`
- Integration point: `FishingScreen.java:253`

## Contributing

When modifying the auto-fishing algorithm:

1. Preserve the toggle functionality
2. Maintain backward compatibility
3. Test with various fish difficulties
4. Consider impact on game balance
5. Document any new parameters or strategies
