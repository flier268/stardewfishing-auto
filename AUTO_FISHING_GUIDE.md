# 完整自動釣魚系統 (Complete Auto-Fishing System)

## 概述 (Overview)

這個擴展為 Stardew Fishing 模組新增了完整的自動釣魚功能，包括自動拋竿、自動收杆、以及智能耐久度管理系統。

This extension adds a complete automatic fishing system to the Stardew Fishing mod, including auto-casting, auto-reeling, and intelligent durability management.

---

## 功能特點 (Features)

### 1. 自動拋竿 (Auto-Cast)
- ✅ 檢測釣竿處於空閒狀態時自動拋出
- ✅ 可配置的拋竿延遲（預設1秒）
- ✅ 在拋竿前檢查耐久度
- ✅ 按 **C** 鍵切換開關

### 2. 自動收杆 (Auto-Reel)
- ✅ 檢測魚咬鉤時自動收杆
- ✅ 智能檢測浮標下沉動畫
- ✅ 自動進入釣魚小遊戲
- ✅ 按 **V** 鍵切換開關

### 3. 耐久度管理 (Durability Management)
- ✅ 實時監控釣竿和浮標的耐久度
- ✅ 耐久度過低時自動從背包尋找替換品
- ✅ 自動替換低耐久度的釣竿
- ✅ 自動替換低耐久度的浮標
- ✅ 可配置最低耐久度閾值
- ✅ 可選擇在耐久度過低時停止釣魚

### 4. 狀態顯示界面 (Status Overlay)
- ✅ 實時顯示自動釣魚狀態
- ✅ 顯示釣竿和浮標耐久度
- ✅ 顯示當前釣魚狀態（空閒、等待咬鉤、收杆中等）
- ✅ 按 **B** 鍵切換顯示/隱藏

### 5. 現有功能整合 (Existing Features)
- ✅ 完全相容現有的 AI 自動釣魚小遊戲控制（按 **A** 切換）
- ✅ 完整的寶箱追蹤系統
- ✅ 速度預測和軌跡分析

---

## 按鍵綁定 (Keybindings)

| 按鍵 | 功能 | 預設 |
|------|------|------|
| **C** | 切換自動拋竿 | 關閉 |
| **V** | 切換自動收杆 | 關閉 |
| **B** | 切換狀態顯示 | 開啟 |
| **A** | 切換小遊戲 AI（已存在） | 開啟 |

*所有按鍵均可在 Minecraft 控制設定中重新綁定*

---

## 配置選項 (Configuration)

**配置文件位置**：`config/stardew_fishing-client.toml`

⚠️ **重要**: 從 v3.2.1-fix5 開始，auto-fishing 配置已移至客戶端配置。
- **舊位置**（已廢棄）：`世界資料夾/serverconfig/stardew_fishing-server.toml`
- **新位置**：`遊戲根目錄/config/stardew_fishing-client.toml`

### Auto-Fishing 區段

```toml
[autoFishing]
    # 是否預設啟用自動拋竿（可用 C 鍵切換）
    autoCastEnabled = false

    # 是否預設啟用自動收杆（可用 V 鍵切換）
    autoReelEnabled = false

    # 釣竿最低耐久度（低於此值時停止或嘗試替換）
    # 範圍：0 ~ 1000
    minRodDurability = 5

    # 浮標最低耐久度（低於此值時停止或嘗試替換）
    # 範圍：0 ~ 1000
    minBobberDurability = 3

    # 是否在耐久度過低時停止自動釣魚
    stopOnLowDurability = true

    # 是否在耐久度過低時自動從背包替換工具
    autoReplaceTool = true
```

---

## 使用指南 (Usage Guide)

### 基礎使用 (Basic Usage)

1. **手持釣竿** - 將釣竿放在主手或副手
2. **啟用自動拋竿** - 按 **C** 鍵
3. **啟用自動收杆** - 按 **V** 鍵
4. **啟用小遊戲 AI** - 按 **A** 鍵（在小遊戲中）
5. **查看狀態** - 按 **B** 鍵顯示/隱藏狀態界面

現在你可以完全放手，系統會自動：
- 拋出釣竿
- 等待魚咬鉤
- 收杆進入小遊戲
- AI 自動完成小遊戲
- 重複整個流程

### 進階使用 (Advanced Usage)

#### 半自動模式
只啟用部分功能以保持更多控制：

- **只用 AI 控制小遊戲**：只按 **A**，手動拋竿和收杆
- **自動拋竿 + 手動收杆**：只按 **C**，保留收杆時機控制
- **手動拋竿 + 自動收杆**：只按 **V**，自己決定何時拋竿

#### 耐久度管理策略

**保守策略**（建議新手）：
```toml
minRodDurability = 10
minBobberDurability = 5
stopOnLowDurability = true
autoReplaceTool = true
```

**激進策略**（資源充足時）：
```toml
minRodDurability = 2
minBobberDurability = 1
stopOnLowDurability = false
autoReplaceTool = true
```

**完全手動管理**：
```toml
stopOnLowDurability = false
autoReplaceTool = false
```

---

## 釣魚狀態說明 (Fishing States)

狀態顯示界面會顯示以下狀態之一：

| 狀態 | 說明 | 顏色 |
|------|------|------|
| **Idle (Ready)** | 空閒，準備拋竿 | 白色 |
| **Waiting for Bite** | 已拋竿，等待魚咬鉤 | 青色 |
| **Fish Biting!** | 魚正在咬鉤！ | 黃色 |
| **Reeling In** | 收杆中 | 綠色 |
| **In Minigame** | 小遊戲進行中 | 洋紅色 |
| **Low Durability!** | 耐久度過低 | 紅色 |
| **No Rod** | 沒有釣竿 | 灰色 |

### 耐久度顏色指示 (Durability Color Indicators)

- 🟢 **綠色** - 耐久度良好
- 🟠 **橙色** - 耐久度警告（低於最低值的2倍）
- 🔴 **紅色** - 耐久度危險（低於最低值）

---

## 技術實現 (Technical Implementation)

### 架構概覽 (Architecture Overview)

```
AutoFishingController (核心控制器)
├── State Management (狀態管理)
│   ├── IDLE - 空閒狀態
│   ├── CAST - 已拋竿
│   ├── FISH_BITING - 魚咬鉤
│   ├── REELING - 收杆中
│   ├── IN_MINIGAME - 小遊戲中
│   ├── LOW_DURABILITY - 耐久度低
│   └── NO_ROD - 無釣竿
│
├── Auto-Cast System (自動拋竿)
│   ├── 空閒檢測
│   ├── 耐久度預檢
│   └── 延遲控制
│
├── Auto-Reel System (自動收杆)
│   ├── 咬鉤檢測
│   ├── 浮標動畫監測
│   └── 小遊戲觸發
│
├── Durability System (耐久度系統)
│   ├── 釣竿耐久度監控
│   ├── 浮標耐久度監控
│   ├── 自動替換邏輯
│   └── 背包掃描
│
└── Integration (整合)
    ├── ClientEvents (事件監聽)
    ├── AutoFishingKeys (按鍵處理)
    └── AutoFishingOverlay (UI 渲染)
```

### 核心類別 (Core Classes)

1. **AutoFishingController.java**
   - 單例模式控制器
   - 管理完整的自動釣魚生命週期
   - 狀態機實現

2. **AutoFishingKeys.java**
   - 按鍵綁定註冊
   - Forge KeyMapping 整合

3. **AutoFishingOverlay.java**
   - HUD 渲染
   - 狀態和耐久度顯示

4. **ClientEvents.java** (已修改)
   - Tick 處理
   - 按鍵事件處理
   - 配置初始化

5. **SFConfig.java** (已修改)
   - 新增 autoFishing 配置區段
   - ForgeConfigSpec 整合

---

## 工作流程 (Workflow)

### 完整自動釣魚循環 (Full Auto-Fishing Loop)

```
[1] 空閒狀態 (IDLE)
     ↓ (自動拋竿啟用 && 耐久度足夠)
[2] 執行拋竿動作
     ↓
[3] 等待咬鉤 (CAST)
     ↓ (檢測到浮標下沉)
[4] 魚咬鉤 (FISH_BITING)
     ↓ (自動收杆啟用)
[5] 執行收杆動作
     ↓
[6] 小遊戲開始 (IN_MINIGAME)
     ↓ (AI 控制 bobber，如果啟用)
[7] 小遊戲完成
     ↓ (損耗耐久度)
[8] 檢查耐久度
     ↓ (耐久度過低 && 自動替換啟用)
[9] 從背包尋找並替換工具
     ↓
回到 [1] 繼續循環
```

### 耐久度管理流程 (Durability Management Flow)

```
每個 Tick:
  ├─ 檢查釣竿耐久度
  │   ├─ < minRodDurability?
  │   │   ├─ autoReplaceTool = true → 掃描背包尋找更好的釣竿
  │   │   └─ stopOnLowDurability = true → 停止自動拋竿
  │   └─ OK → 繼續
  │
  └─ 檢查浮標耐久度
      ├─ < minBobberDurability?
      │   ├─ autoReplaceTool = true → 掃描背包尋找更好的浮標
      │   └─ stopOnLowDurability = true → 停止自動拋竿
      └─ OK → 繼續
```

---

## 常見問題 (FAQ)

### Q: 為什麼自動拋竿沒有作用？
**A:** 檢查以下幾點：
1. 是否按了 **C** 鍵啟用？（查看聊天訊息確認）
2. 釣竿耐久度是否足夠？（查看狀態界面）
3. 是否在正確的位置（需要在水邊）？
4. 查看狀態界面確認當前狀態

### Q: 自動收杆有時候會失效？
**A:** 自動收杆依賴於浮標動畫檢測。以下情況可能影響檢測：
- 極度卡頓的環境
- 特殊的水域類型
- 某些不相容的模組

建議：
- 降低視距以提升性能
- 檢查模組相容性
- 嘗試調整 `REEL_CHECK_INTERVAL` 常數（需要修改代碼）

### Q: 自動替換工具沒有執行？
**A:** 確保：
1. `autoReplaceTool = true` 在配置中
2. 背包中有耐久度更高的相同類型釣竿/浮標
3. 當前工具耐久度已低於配置的最低值

### Q: 可以只用部分功能嗎？
**A:** 可以！所有功能都是獨立的：
- 只想要自動拋竿：只按 **C**
- 只想要自動收杆：只按 **V**
- 只想要小遊戲 AI：只按 **A**
- 任意組合都可以

### Q: 狀態界面太礙眼怎麼辦？
**A:** 按 **B** 鍵隱藏狀態界面。你仍然可以通過聊天訊息看到切換提示。

### Q: 會自動消耗多少耐久度？
**A:** 每次完成小遊戲：
- 釣竿：-1 耐久度
- 浮標：-1 耐久度

這與手動釣魚相同，沒有額外消耗。

---

## 開發資訊 (Development Info)

### 建置專案 (Building)

```bash
./gradlew clean build
```

### 程式碼結構 (Code Structure)

- `AutoFishingController.java` - 核心邏輯（455 行）
- `AutoFishingKeys.java` - 按鍵註冊（48 行）
- `AutoFishingOverlay.java` - UI 渲染（158 行）
- `ClientEvents.java` - 事件整合（修改）
- `SFConfig.java` - 配置選項（修改）

### 相依性 (Dependencies)

- Minecraft 1.20.1
- Forge 47.4.0
- Stardew Fishing 3.2.1 (base mod)

### 測試建議 (Testing Suggestions)

1. **基礎功能測試**
   - [ ] 自動拋竿是否正常執行
   - [ ] 自動收杆是否正確觸發
   - [ ] 小遊戲 AI 是否正常工作
   - [ ] 狀態界面是否正確顯示

2. **耐久度測試**
   - [ ] 低耐久度時是否正確警告
   - [ ] 自動替換是否正常執行
   - [ ] 停止功能是否正常運作

3. **邊界測試**
   - [ ] 背包沒有替換品時的行為
   - [ ] 多個相同釣竿時的選擇邏輯
   - [ ] 極低耐久度（1-2）時的行為

4. **整合測試**
   - [ ] 與其他釣魚模組的相容性
   - [ ] 高延遲環境的表現
   - [ ] 多人遊戲的同步

---

## 更新日誌 (Changelog)

### v3.2.1-autofish (2025-10-26)

**新增功能**:
- ✨ 完整的自動拋竿系統
- ✨ 完整的自動收杆系統
- ✨ 智能耐久度檢查
- ✨ 自動工具替換（從背包）
- ✨ 實時狀態顯示界面
- ✨ 可配置的耐久度閾值
- ✨ 三個新的按鍵綁定（C/V/B）

**技術改進**:
- 🔧 狀態機架構實現
- 🔧 單例模式控制器
- 🔧 ForgeConfigSpec 整合
- 🔧 完整的本地化支援

**相容性**:
- ✅ 完全向下相容現有存檔
- ✅ 不影響原有小遊戲 AI
- ✅ 可選功能，預設關閉

---

## 授權 (License)

MIT License - 與原 Stardew Fishing 模組相同

---

## 致謝 (Credits)

- **原始模組**: Stardew Fishing by bonker
- **自動釣魚擴展**: Claude Code (2025)

---

## 支援 (Support)

如有問題或建議，請在專案的 GitHub Issues 提出。

享受你的自動釣魚之旅！🎣
Enjoy your automated fishing experience! 🎣
