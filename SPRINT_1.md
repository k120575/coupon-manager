# Sprint 1：建立骨架

**目標**：完成 Android App 的最小可用骨架——能新增、編輯、列出、使用、刪除票券，12-15 個內建分類可選，Pro 升級入口 UI 已備（功能尚未實作）。

**預估時間**：個人開發者 part-time 約 60-80 小時（2-3 週）

**Sprint 結束時的驗收**：能給朋友 alpha 測試的可運行版本。使用者可以完整跑「新增 3 張票券 → 看到列表 → 用掉 1 張 → 刪除 1 張」這條主路徑。

---

## 技術棧定案

| 層 | 選擇 | 理由 |
|---|---|---|
| 語言 | Kotlin | Android 原生標準 |
| UI | Jetpack Compose | 現代標準，比 XML 快 |
| 本機資料庫 | Room | SQLite wrapper |
| 依賴注入 | **Hilt** | Google 標準（替代方案：Koin）|
| 非同步 | Coroutines + Flow | 標準 |
| Navigation | Compose Navigation | 標準 |
| 日期 | `java.time.LocalDate` | API 26+ 內建 |
| 偏好儲存 | DataStore (Preferences) | 取代舊 SharedPreferences |
| min SDK | API 26 (Android 8.0) | 涵蓋 95%+ 裝置，可用 `java.time` |

---

## Phase 1：專案初始化（~6h）

### ISSUE-01: Android Studio 專案建立與 Gradle 配置 `[3h]`

**做什麼：**
- 用 Android Studio 建立新專案（Empty Compose Activity 範本）
- Package name: `com.kevin.coupy`（或你偏好的命名）
- 設定 `applicationId`、`versionCode = 1`、`versionName = "1.0.0"`
- 加入依賴：
  - Compose BOM 最新版
  - Room (`androidx.room:room-runtime`, `room-ktx`, `room-compiler` 用 ksp)
  - Hilt (`com.google.dagger:hilt-android`)
  - Navigation Compose
  - DataStore Preferences
  - kotlinx-coroutines-android
- 設定 `BuildConfig` 開啟
- 加入 KSP plugin（Room 編譯用）

**驗收條件：**
- [ ] 專案能用 `./gradlew assembleDebug` 編譯通過
- [ ] 模擬器或實機上跑起來看到 "Hello World"
- [ ] Hilt 的 `@HiltAndroidApp` Application 已設定

**依賴：** 無

---

### ISSUE-02: 品牌主題與配色 `[2-3h]`

**做什麼：**
- 建立 `ui/theme/Color.kt`：
  ```kotlin
  val CoupyTeal = Color(0xFF4ECDC4)
  val CoupyCoral = Color(0xFFFF6B6B)
  val CoupyBlack = Color(0xFF0F0F0F)
  val CoupyWhite = Color(0xFFFAFAFA)
  ```
- 建立 `Typography.kt`：載入 Noto Sans TC（中文）+ Inter（英文）
- 建立 `Theme.kt`：定義 `LightColorScheme` 與 `DarkColorScheme`，包到 `CoupyTheme` composable
- 預設深色模式或跟隨系統（建議跟隨系統 + 預設深色）
- 替換 `MainActivity` 用 `CoupyTheme` 包住

**驗收條件：**
- [ ] App 的主背景、按鈕、卡片顏色符合品牌色票
- [ ] 切換系統深淺模式，App 跟著切
- [ ] 中文字顯示用思源黑體（不是系統預設字體）

**依賴：** ISSUE-01

---

## Phase 2：資料層（~12h）

### ISSUE-03: Room 資料庫與 Coupon 實體 `[3h]`

**做什麼：**
- 建立 `data/entity/CouponEntity.kt`：
  ```kotlin
  @Entity(tableName = "coupons")
  data class CouponEntity(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val name: String,
      val expireDate: LocalDate,
      val category: String,
      val quantity: Int = 1,
      val status: String = "active",  // "active" | "used" | "deleted"
      val createdAt: Instant = Instant.now(),
      val usedAt: Instant? = null
  )
  ```
- 建立 `CouponDao`：插入、更新、刪除、查詢 active 票券（依到期日排序）、依 id 查詢
- 建立 `Converters.kt`：`LocalDate` ↔ String、`Instant` ↔ Long
- 建立 `AppDatabase`：`@Database` annotation、version = 1
- 用 Hilt 提供 Database 與 Dao 的 `@Module`

**驗收條件：**
- [ ] App 第一次啟動建立資料庫，Database Inspector 看得到 `coupons` table
- [ ] 寫一個 instrumented test：插入 1 筆、查詢回來、欄位正確
- [ ] 日期/時間欄位轉換不丟資料

**依賴：** ISSUE-01

---

### ISSUE-04: 12-15 個內建分類 + 重新命名機制 `[2-3h]`

**做什麼：**
- 建立 `data/model/Category.kt`：用 `sealed class` 或 `enum` 定義 14 個內建分類：
  ```
  電影、餐飲、購物、美容、按摩、健身、醫療、寵物、
  教育、3C、住宿、交通、咖啡、其他
  ```
- 重新命名儲存：用 DataStore Preferences，key 為原始 id（`category_movie`），value 為自訂名稱
- 建立 `CategoryRepository`：提供 `getDisplayName(id: String)` 與 `rename(id: String, newName: String)`
- 提供預設 emoji/icon mapping（之後 UI 用）

**驗收條件：**
- [ ] App 啟動可列出 14 個分類
- [ ] 改其中一個分類名稱，下次啟動仍記得
- [ ] 不允許改成空字串或超過 8 個字

**依賴：** ISSUE-01

---

### ISSUE-05: Repository 與 ViewModel 層 `[3h]`

**做什麼：**
- `CouponRepository`：包裝 Dao，提供 `Flow<List<Coupon>>`、`addCoupon()`、`updateCoupon()`、`markAsUsed(id)`、`softDelete(id)`
- `CouponListViewModel`：暴露 `couponsState: StateFlow<List<Coupon>>`，內部訂閱 repository
- `CouponEditViewModel`：管理新增/編輯表單的狀態（name, expireDate, category, quantity），提供 `save()` 與 `loadForEdit(id)`
- 兩個 ViewModel 都用 `@HiltViewModel`

**驗收條件：**
- [ ] ViewModels 用 Hilt 注入 Repository
- [ ] 寫一個 ViewModel test：呼叫 `addCoupon()` 後，`couponsState` 立刻收到新資料
- [ ] 表單 ViewModel 能正確區分「新增」與「編輯」模式

**依賴：** ISSUE-03, ISSUE-04

---

### ISSUE-06: 軟刪除策略確認與實作 `[1h]`

**做什麼：**
- 確定策略：刪除標記為 `status = 'deleted'`、不從資料庫真的刪掉
- 為什麼：使用者誤刪可以「資源回收筒」復原（v1.x+）；分析資料保留完整
- 在 Dao 加入 `getDeletedCoupons()`（給未來資源回收筒用，現在不暴露 UI）
- 確認所有查詢預設過濾掉 `status='deleted'`

**驗收條件：**
- [ ] 刪除一筆後，主列表看不到
- [ ] 直接查 DB 還在（status='deleted'）
- [ ] 使用過的票券（status='used'）也從主列表隱藏

**依賴：** ISSUE-05

---

## Phase 3：導航與列表畫面（~10h）

### ISSUE-07: 主要導航結構與 Scaffold `[3h]`

**做什麼：**
- 建立 `navigation/CoupyNavGraph.kt`，定義路由：
  - `list`（主畫面）
  - `edit/{couponId?}`（新增或編輯，id 為 null 時是新增）
  - `settings`
  - `pro_intro`（Pro 升級介紹頁）
- 主畫面 Scaffold：TopAppBar（標題「券管家」+ 設定 icon）、FloatingActionButton（+ 圖示）
- FAB 點擊 → 導航到 `edit/`（無 id）

**驗收條件：**
- [ ] 主畫面 → 點 FAB → 編輯畫面，能返回
- [ ] 主畫面 → 點 TopAppBar 設定 icon → 設定畫面，能返回
- [ ] back button 與系統返回鍵都能正確處理

**依賴：** ISSUE-02

---

### ISSUE-08: 票券列表畫面（CouponListScreen）`[3h]`

**做什麼：**
- `LazyColumn` 顯示所有 active 票券，按到期日由近到遠排序
- 訂閱 `CouponListViewModel.couponsState`
- 空狀態畫面：圖示 + 「還沒有票券，點右下 + 新增」
- 滑動下拉重整（`PullRefresh`，可選）
- 列表項點擊 → 進入編輯畫面（帶入 id）

**驗收條件：**
- [ ] 沒有資料時顯示空狀態
- [ ] 新增 3 筆票券，列表立刻刷新顯示
- [ ] 列表依到期日排序，最快過期的在上面
- [ ] 點任一張卡 → 編輯該筆

**依賴：** ISSUE-05, ISSUE-07

---

### ISSUE-09: 票券卡片元件（CouponCard）`[3h]`

**做什麼：**
- 一張卡片顯示：
  - 票券名稱（粗體，最多 1 行 truncate）
  - 分類 chip / badge
  - 到期日（格式：「2026/12/31」或「3 天後到期」相對日期）
  - 張數（如果 > 1，顯示 `×2`）
- 即將過期（7 天內）的卡片顯示**珊瑚紅左邊條**強調
- 卡片背景用品牌色票
- 點擊整張卡 → 編輯

**驗收條件：**
- [ ] 名稱過長會 truncate 加 `...`
- [ ] 7 天內到期的卡片有視覺強調
- [ ] 張數 = 1 時不顯示 `×1`，> 1 時顯示
- [ ] 在深色模式下可讀

**依賴：** ISSUE-08

---

## Phase 4：新增/編輯/使用/刪除（~10h）

### ISSUE-10: 新增/編輯票券畫面（CouponEditScreen）`[5h]`

**做什麼：**
- 表單欄位：
  - **名稱** `TextField`（必填，最多 30 字）
  - **到期日** 日期選擇器（必填，預設 30 天後）
  - **分類** Dropdown，列出 14 個分類
  - **張數** Number input（預設 1，範圍 1-99）
- 上方 TopAppBar：返回 + 標題（「新增票券」或「編輯票券」）+ 「儲存」按鈕
- 編輯模式：載入既有資料、儲存按鈕呼叫 `updateCoupon()`
- 新增模式：儲存後呼叫 `addCoupon()`、自動返回列表
- 驗證：名稱空白時禁用儲存按鈕

**驗收條件：**
- [ ] 從 FAB 進入 = 新增模式（空表單）
- [ ] 從列表點擊 = 編輯模式（帶入該筆資料）
- [ ] 名稱空白時「儲存」按鈕灰色不可按
- [ ] 儲存成功後自動返回，列表立即看到變化
- [ ] 日期選擇器不允許選過去日期

**依賴：** ISSUE-05, ISSUE-07, ISSUE-04

---

### ISSUE-11: 使用 / 刪除動作 `[3h]`

**做什麼：**
- 在票券卡片支援**長按**或**滑動**呼出動作選單：
  - 「使用」→ 對話框確認「使用 1 張？剩餘 X 張」→ `quantity -= 1`，若歸零則 `status='used'`
  - 「刪除」→ 對話框確認 → `status='deleted'`
- 對話框統一用 Material AlertDialog
- 使用成功 / 刪除成功跳 Snackbar 提示，附「復原」按鈕（可選，5 秒內可按）

**驗收條件：**
- [ ] 長按卡片彈出選單
- [ ] 使用 1 張，數量從 2 變 1；再使用 1 張，卡片消失
- [ ] 刪除卡片立即從列表消失
- [ ] 誤觸有確認對話框，不會直接刪

**依賴：** ISSUE-09

---

## Phase 5：設定與 Pro 入口（~6h）

### ISSUE-12: 設定畫面（SettingsScreen）`[2-3h]`

**做什麼：**
- 區塊：
  - **目前方案**：「免費版」標籤 + 「升級 Pro」按鈕 → 導航到 `pro_intro`
  - **分類管理**：點進去 → 列出 14 個分類 + 重新命名功能
  - **資料備份**（v1 先放佔位文字「即將推出」）
  - **關於**：版本號、開發者資訊
- 每個項目用 `ListItem` composable

**驗收條件：**
- [ ] 點「升級 Pro」進到 Pro 介紹頁
- [ ] 點「分類管理」可以重新命名分類
- [ ] 重新命名後返回主畫面，分類顯示為新名稱
- [ ] 「資料備份」顯示但點不動（disabled）

**依賴：** ISSUE-04, ISSUE-07

---

### ISSUE-13: Pro 升級介紹頁（ProIntroScreen）+ 通知我上線 `[2-3h]`

**做什麼：**
- 頁面上半：標題「升級到 Pro」+ 副標「即將推出」
- 中段：列出 6 個 Pro 功能：
  - ☁️ 自動雲端同步
  - 🔄 無限 OCR
  - 🏷️ 自訂分類無上限
  - 🔍 進階篩選
  - 📍 地點觸發提醒
  - ⏰ 自訂提醒時間
- 下方：大按鈕「**通知我上線**」
- 點擊後：彈出 dialog 收 email（或直接記錄一筆「intent_signal」到本機 DB / DataStore，附時間戳）
- 已點過就顯示「✓ 已登記，上線時通知你」

**驗收條件：**
- [ ] 進入頁面可以看到所有 Pro 功能列表
- [ ] 點「通知我上線」記錄一筆訊號，下次打開頁面看到已登記狀態
- [ ] 訊號可以從 Database Inspector 看到（這是你的早期付費意願資料）

**依賴：** ISSUE-12

---

## Phase 6：收尾與品質（~6h）

### ISSUE-14: 深淺主題完整測試 `[1-2h]`

**做什麼：**
- 切換系統深淺模式，所有畫面跑一遍
- 修掉任何「在深色模式下看不清楚」的元素（特別是 disabled 狀態、輸入框）

**驗收條件：**
- [ ] 深色模式所有文字可讀
- [ ] 淺色模式所有文字可讀
- [ ] 切換不會 crash

**依賴：** 所有 UI issues 完成

---

### ISSUE-15: App icon、Manifest 與基本中繼資料 `[2-3h]`

**做什麼：**
- 從 `logo_compare.html` 的 B 版本生出 PNG icon
  - 用 [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/) 或 [Icon Kitchen](https://icon.kitchen/) 從 PNG 一鍵生 Adaptive Icon
  - 提供 foreground（兩張票券+勾勾）+ background（純黑 #0F0F0F）兩層
- `AndroidManifest.xml`：
  - `android:label="券管家"`
  - 設好 launcher Activity
- `strings.xml`：把所有 hardcoded 中文 string 移過來
- `app_name` = 「券管家」

**驗收條件：**
- [ ] 安裝後桌面 icon 是 B 版本設計
- [ ] App 名稱顯示「券管家」
- [ ] Long-press app icon 顯示正確的圓形/圓角適應 icon

**依賴：** ISSUE-01

---

## Sprint 1 完成檢查清單

跑完上述 15 個 issue 後，下列流程應該都能正常運作：

- [ ] 安裝 App，icon 是券管家設計，名稱「券管家」
- [ ] 打開 App，看到空狀態
- [ ] 點 + 新增「燒肉券」、到期日 2026/12/31、分類「餐飲」、張數 2
- [ ] 列表立刻看到剛新增的票券
- [ ] 點該卡片進入編輯，修改張數為 3，儲存返回，列表更新
- [ ] 長按卡片 → 使用 1 張，剩餘 2
- [ ] 長按 → 刪除，列表消失
- [ ] 進入設定，看到「免費版」+「升級 Pro」按鈕
- [ ] 點「升級 Pro」看到 6 個功能列表 + 通知按鈕
- [ ] 點通知 → 記錄訊號，下次再進入顯示「已登記」
- [ ] 進入分類管理，將「餐飲」改名為「美食」，列表上的票券分類顯示為「美食」
- [ ] 系統切換深/淺色，App 跟著切

達到 = Sprint 1 完成，可以給朋友 alpha 測試。

---

## 待解決問題 / 後續決策點

| 問題 | 何時決定 |
|---|---|
| 是否要做「資源回收筒」UI 還原已刪除票券 | v1.1+ |
| Hilt vs Koin（如果想用更輕量的 DI）| Sprint 1 開始前 |
| 訊號收集格式：本機 DB / 後端 endpoint | ISSUE-13 開始前（建議先本機，之後同步到 Worker）|
| 是否在 v1.0 加 Crashlytics / Firebase Analytics | Sprint 1 結束時 |
| 「使用」是否要支援「指定使用張數」（例如一次用 3 張）| v1.1+，先預留 UI |

---

## 估時總覽

| Phase | 預估時間 |
|---|---|
| Phase 1：專案初始化 | 5-6h |
| Phase 2：資料層 | 9-12h |
| Phase 3：導航與列表 | 9-10h |
| Phase 4：CRUD | 8-10h |
| Phase 5：設定與 Pro | 4-6h |
| Phase 6：收尾 | 3-5h |
| **總計** | **約 40-50h 純工作時間** |

實際 part-time 開發者，包含學習 Compose、解 bug、查文件，**現實估時 60-80 小時 = 2-3 週**。

如果連續趕，最快 1 週可完成；但建議分散到 2-3 週，留時間給 alpha 使用者回饋與調整。
