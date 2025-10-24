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

##### Treasure Chest Priority
```java
if (minigame.isChestVisible() && !minigame.gotChest()) {
    // Special logic to catch both fish and chest
}
```
- When a treasure chest appears, AI attempts to catch it
- Only tries if already on the fish (maintains primary objective)
- Balances between fish tracking and chest collection

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

- Main implementation: `FishingScreen.java:432-496`
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
