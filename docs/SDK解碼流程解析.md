# SDK 解碼流程解析

**版本：** v1.0  
**日期：** 2026-05-28  
**範疇：** `decodeMqtt()` → `splitPackage()` → `Algorithm()` 完整流程

---

## 一、整體架構概覽

```
MQTT 訊息（或 HTTP POST）
        ↓
MqttProcessingService.resolveAnimalType(mac)
        ↓ animalType (0=人/1=貓/2=兔/3=狗)
HealthDecoderService.decodeMqtt(mac, payloads, animalType)
        ↓ 每封包
HealthCalculate.splitPackage(byte[16])          ← 我們的入口
        ↓ RX_datacount % 16 == 0
        handler.post → Algorithm()              ← 非同步！
        ↓
        snapshotHealthData(sdk)                 ← 快照當前值
        ↓
MqttProcessingService.forwardToNetApi(...)      ← POST 到 .NET WebAPI
```

---

## 二、各層職責

| 層級 | 類別 / 方法 | 職責 |
|------|-------------|------|
| 入口 | `HealthController` | 接收 HTTP 請求，驗證 API Key |
| 訂閱 | `MqttSubscriberService` | 訂閱 MQTT Broker，收到訊息後送入同一流程 |
| 流程協調 | `MqttProcessingService` | 查詢 animalType、轉送 .NET |
| 解碼主邏輯 | `HealthDecoderService.decodeMqtt()` | 排序封包、逐包送 SDK、取快照 |
| SDK 解析 | `HealthCalculate.splitPackage()` | 驗證 + 解析 BLE 位元組 |
| SDK 演算法 | `HealthCalculate.Algorithm()` | 非同步執行 HR/BR 計算 |
| HR/BR 計算 | `HRBRCalculate.EKG_process()` / `BR_process()` | 切比雪夫濾波 + FFT |

---

## 三、封包格式（16 bytes，首 byte = 0xFF 或 0xFA）

| 位元組 | 欄位常數 | 說明 |
|--------|----------|------|
| [0] | — | 標頭：`0xFF`（一般）或 `0xFA`（特殊） |
| [1..2] | BLE_RAW_LOW / HIGH | HR 原始訊號（Low Byte + High Byte）|
| [3..4] | BLE_STEP_LOW / HIGH | 計步數 |
| [5..9] | BLE_TIMESTAMP_1~5 | 裝置開機 uptime（ms，5 bytes 小端序 × 10）|
| [10] | BLE_BATTERY | 電池電量（0~100%）|
| [11] | BLE_RRI | RRI / SpO2 值 |
| [12] | BLE_PET_POSE | 寵物姿勢（0~4）**僅 16 bytes 版** |
| [13] | BLE_Pressure | 氣壓值 **僅 16 bytes 版** |
| [14] | — | 未使用 |
| [15] | — | CheckSum（所有 byte 總和 % 16 == 0）|

> **19 bytes 版本**（藍牙）額外含 Gyro X/Y/Z、溫度、濕度。  
> WiFi MQTT 目前傳送 **16 bytes 版本**。

---

## 四、`HealthDecoderService.decodeMqtt()` 詳解

```
decodeMqtt(macAddress, payloads, animalType)
│
├── 1. 取得或建立 SDK 實例（以 MAC 為 key，ConcurrentHashMap 快取）
│       若 animalType 不同 → sdk.setType() 重設
│
├── 2. 對每個 DataPayload（通常 1 筆）：
│   │   記錄 lastBattery, lastWearing
│   │
│   ├── 3. 將 packets 依 n 排序（n=0, 1, 2, ... 31）
│   │
│   ├── 4. 逐包處理：
│   │   │   decodeRawData(hexData) → hex string → byte[]（16 bytes）
│   │   │   sdk.splitPackage(bytes)
│   │   │   packetIndex++
│   │   │
│   │   └── 每 16 包：Thread.sleep(50ms) → snapshotHealthData(sdk) → lastOk
│   │
│   └── 5. 非 16 倍數時：補一次 sleep(50ms) + snapshot
│
├── 6. lastOk 覆蓋 battery / wearing（用 payload 值，SDK 從封包解的不可靠）
│
└── 7. 回傳 lastOk（HealthData）
```

### 常數說明

| 常數 | 值 | 說明 |
|------|----|------|
| `ALGORITHM_WAIT_MS` | 50ms | 每 16 包後等待 Algorithm 非同步完成 |
| SDK 快取 key | MAC 位址 | 同一裝置跨 MQTT 訊息共用 SDK 狀態（buffer 連續） |

---

## 五、`HealthCalculate.splitPackage()` 詳解

```
splitPackage(byte[] data)
│
├── 1. 驗證首 byte：data[0] == 0xFF 或 0xFA，否則回傳 ERROR_FIRST_BYTE(101)
│
├── 2. Checksum 驗證：
│       sum = data[0] + ... + data[15]
│       若 sum % 16 != 0 → 回傳 ERROR_CHECKSUM(102)
│
├── 3. 解析欄位（存入 mainInfo）：
│       Timestamp（5 bytes 小端序）
│       HR/BR 原始值（同一來源：BLE_RAW_LOW/HIGH）
│       StepValue、Battery、RRI
│       PetPose、Pressure（16 bytes 版）
│       isWearing（由 checkIsWearing() 判斷，基於 Gyro 差值和 BRValue）
│
├── 4. 存入 buffer：
│       m_HRdoubleRawData[RX_datacount] = i_HR
│       m_BRdoubleRawData[RX_datacount] = i_HR  （相同原始值，不同濾波器處理）
│       RX_datacount++
│
├── 5. 每 16 包觸發非同步演算法：
│       if (RX_datacount % 16 == 0)
│           handler.post(() -> Algorithm())       ← ScheduledExecutorService 非同步
│       RX_datacount 達 512 時歸零（環形 buffer）
│
└── 6. 回傳 RESULT_OK(-1)
```

---

## 六、`Algorithm()` 詳解（非同步執行）

每次 `splitPackage` 每 16 包觸發一次，在獨立執行緒執行：

```
Algorithm()  ← 每次處理 16 個數據點
│
├── for i = 0..15：
│   │
│   ├── BRCallIn(m_BRdoubleRawData[BRCanvasCounter])
│   │       └── BR_process() → 切比雪夫濾波 → Signal2RRI
│   │           每 32 次（secCount_BR >= 32）→ 更新 oldBR → BRValue
│   │
│   ├── HRCallIn(m_HRdoubleRawData[HRCanvasCounter])
│   │       └── EKG_process() → 切比雪夫濾波 → rawbank[]
│   │           每 32 次（secCount >= 32）→ FFT（HbProcess）→ 更新 oldHB
│   │
│   ├── c_drawValueP2P_BR++
│   │   c_drawValueP2P_HR++
│   │
│   ├── if c_drawValueP2P_BR >= 32：
│   │       c_drawValueP2P_BR = 0
│   │       BRValue = averageByPercentage(BRValue_median[], 0.6)
│   │
│   └── if c_drawValueP2P_HR >= 32：
│           c_drawValueP2P_HR = 0
│           HRValue = averageByPercentage(HRValue_median[], 0.6)
│
└── mainInfo.HRValue = HRValue
    mainInfo.BRValue = BRValue
```

---

## 七、HR/BR 計數器時序分析（32 封包批次，以狗 type=3 為例）

### 初始狀態

| 計數器 | 初始值 | 說明 |
|--------|--------|------|
| `c_drawValueP2P_HR` | **31** | 距離觸發 HRValue 更新只差 1 |
| `secCount`（HRBRCalculate） | 0 | 距離 FFT 需要 32 次 HRCallIn |
| `HRValue_median[]` | `[90,90,90,90,90]` | 狗的初始心率 |
| `HRValue` | 0 | SDK 公開的當前 HR（初始為 0）|

### 第一批 32 封包（從未使用過的 SDK 實例）

```
封包 n=0..15 → splitPackage ×16 → handler.post(Algorithm1)
┌─ Algorithm1 執行（非同步，在 sleep 50ms 後完成）：
│   16 次 HRCallIn：secCount 0→16（未到 32，FFT 不觸發，oldHB = 90）
│   c_drawValueP2P_HR：31→47（在 i=0 時觸發，32→0，更新 HRValue = 90）
│                              （在 i=16 時再觸發一次，16→32→0，更新 HRValue = 90）
└─ mainInfo.HRValue = 90
   [我們的 sleep(50ms) 快照 → HR=90]

封包 n=16..31 → splitPackage ×16 → handler.post(Algorithm2)
┌─ Algorithm2 執行：
│   16 次 HRCallIn：secCount 16→32（達到 32！FFT 執行，oldHB 更新為 FFT 結果）
│   c_drawValueP2P_HR：0→16（未到 32，HRValue 不更新）
└─ mainInfo.HRValue = 90（仍是上次的值，FFT 結果存在 oldHB 但未寫入 HRValue）
   [我們的 sleep(50ms) 快照 → HR=90]
```

### 第二批 32 封包（SDK 延續上批狀態）

```
封包 n=0..15 → Algorithm3：
    secCount 0→16（FFT 不觸發，oldHB 保持第一批 FFT 結果）
    c_drawValueP2P_HR：16→32（觸發！HRValue = averageByPercentage(HRValue_median)）
    HRValue_median[4] = 上批 FFT 的 oldHB 值
    [快照 → HR = 真實值（第一批 FFT 結果，有 1 批 lag）]

封包 n=16..31 → Algorithm4：
    secCount 16→32（FFT 再次執行，更新 oldHB）
    c_drawValueP2P_HR：0→16（不更新 HRValue）
    [快照 → HR = 同上]
```

### 關鍵結論

1. **FFT 每 32 個 `HRCallIn` 觸發一次** = 每兩次 `Algorithm()` = 每 32 封包
2. **HRValue 更新每 32 個 `HRCallIn` 觸發一次**，與 FFT 觸發時機**錯開**
3. **結果：每批 MQTT 讀到的 HR = 上一批 FFT 計算的值（1 批 lag，約 1 秒延遲）**
4. **第一批永遠是初始值（狗 = 90），第二批開始才有真實數據**

---

## 八、`snapshotHealthData()` 讀取的所有欄位

| 欄位 | SDK 方法 | 說明 |
|------|----------|------|
| `timestamp` | `getTimeStamp()` | 裝置 uptime ms（非 Unix epoch）|
| `isWearing` | `getIsWearing()` | Gyro + BRValue 判斷（16 bytes 版下 Gyro=0 可能誤判）|
| `hrValue` | `getHRValue()` | 心率 bpm（`mainInfo.HRValue`）|
| `brValue` | `getBRValue()` | 呼吸率 bpm（`mainInfo.BRValue`）|
| `rriValue` | `getRRIValue()` | RRI / SpO2（`mainInfo.RRIValue`）|
| `tempValue` | `getTempValue()` | 溫度 °C（16 bytes 版：0）|
| `stepValue` | `getStepValue()` | 計步數 |
| `humValue` | `getHumValue()` | 濕度 %（16 bytes 版：0）|
| `pressureValue` | `getPressureValue()` | 氣壓（16 bytes 版有）|
| `powerValue` | `getPowerValue()` | 電池 %（snapshotHealthData 後被 payload 值覆蓋）|
| `gyroX/Y/Z` | `getGyroValueX/Y/Z()` | 陀螺儀（16 bytes 版：0）|
| `petPose` | `getPetPoseValue()` | 姿勢整數（16 bytes 版有）|

> **注意：** `powerValue` 和 `isWearing` 在 `decodeMqtt()` 最後會被 payload 的 `battery_level` 和 `is_wearing` 覆蓋，因為 16 bytes 封包內的 Battery 和 Gyro 判斷不準確。

---

## 九、`forwardToNetApi()` 轉送 .NET 的欄位對應

| POST body 欄位 | 來源 | 說明 |
|----------------|------|------|
| `DeviceId` | macAddress | 裝置 MAC 位址 |
| `RecordTime` | `Instant.now()` UTC | **伺服器當前時間**（裝置 timestamp 是 uptime，不是 epoch）|
| `HeartRate` | `hrValue` | bpm |
| `BreathRate` | `brValue` | bpm |
| `Temperature` | `tempValue` | °C（WiFi 版為 0）|
| `Humidity` | `humValue` | %（WiFi 版為 0）|
| `StepCount` | `stepValue` | 步數 |
| `BatteryLevel` | `powerValue`（payload 覆蓋後）| % |
| `IsWearing` | `isWearing`（payload 覆蓋後）| bool |
| `PetPose` | `petPoseToString(petPose)` | walking/sniffing/trotting/galloping/staticResting |
| `DeviceTimestamp` | `payload.getTimestamp()` | 裝置 uptime ms（供偵錯用）|

---

## 十、已知限制與注意事項

### 1. 1 批 Lag（HR/BR 值有 1 秒延遲）

- **原因：** FFT 觸發時機與 `HRValue` 更新時機錯開（兩者都需 32 次 HRCallIn，但計數器起始不同）
- **影響：** 每批 MQTT（32 封包）讀到的是上一批算完的值
- **第一批：** 永遠是初始值（狗=90、貓=100、兔=150、人=80）
- **對策：** 無法消除，這是 SDK 設計特性；實際使用時幾秒後即穩定

### 2. WiFi 版 isWearing 判斷不準

- **原因：** `checkIsWearing()` 依賴 Gyro 差值，但 16 bytes 封包無 Gyro，GyroX/Y/Z 固定為 0
- **BRValue <= 2 判斷**也可能在初始時誤判
- **對策：** `decodeMqtt()` 最後覆蓋為 payload 的 `is_wearing`（由裝置韌體判斷）

### 3. 16 bytes vs 19 bytes 封包

| | 16 bytes（WiFi） | 19 bytes（BLE）|
|-|-----------------|----------------|
| Gyro | ✗（固定 0）| ✓ |
| 溫度 | ✗（固定 0）| ✓ |
| 濕度 | ✗（固定 0）| ✓ |
| PetPose | ✓ | ✗ |
| 氣壓 | ✓ | ✗ |

### 4. SDK 實例快取（ConcurrentHashMap）

- 同一 MAC 的 SDK 實例**跨 MQTT 訊息**共用，buffer 是連續的
- 這是必要的：FFT 需要累積 32 次 HRCallIn 才能計算
- **服務重啟後** SDK 狀態歸零，第一批永遠是初始值

### 5. 非同步 Race Condition（已用 sleep 緩解）

- `handler.post(Algorithm)` 使用 `ScheduledExecutorService` 的單一執行緒，Algorithm 呼叫是**序列化**的
- 但 `splitPackage()` 迴圈比 Algorithm 執行快，可能在 Algorithm 完成前就 snapshot
- **現有解法：** 每 16 包後 `Thread.sleep(50ms)`，等待 Algorithm 完成
- **50ms 是否足夠：** Algorithm 對 16 個數據點跑濾波，通常 < 5ms；50ms 有 10 倍 margin

---

## 十一、SDK 動物類型參數

| type | 動物 | HR 初始值 | HR 搜尋範圍 (surl~surh) |
|------|------|-----------|-------------------------|
| 0 | 人類 | 80 bpm | 5~35 |
| 1 | 貓 | 100 bpm | 5~50 |
| 2 | 兔子 | 150 bpm | 15~50 |
| 3 | 狗 | **90 bpm** | 5~40 |

> `surl` / `surh` 是 FFT 頻率搜尋的上下界（FFT index），狗的搜尋範圍 5~40 對應約 9~75 bpm（@32Hz 取樣）。
> HR=180 的現象可能是 FFT 抓到 2 次諧波（基頻 90bpm 的 2 倍），在第一批數據時容易發生。

---

## 十二、完整序列圖（以 32 封包單批為例）

```
MQTT Broker
    │  JSON payload (32 packets)
    ▼
MqttSubscriberService.messageArrived()
    │  解析 JSON → MqttDecodeRequest
    │  resolveAnimalType(mac) → 3（狗）
    ▼
HealthDecoderService.decodeMqtt(mac, payloads, 3)
    │
    ├─ sdk = sdkInstances.get(mac)  ← 取得/建立 HealthCalculate(3)
    │
    ├─ packets 依 n 排序：0,1,2,...,31
    │
    ├─ [n=0]  splitPackage → RX_datacount=1, 無 Algorithm
    ├─ [n=1]  splitPackage → RX_datacount=2, 無 Algorithm
    ├─ ...
    ├─ [n=15] splitPackage → RX_datacount=16, handler.post(Algorithm1) ←非同步
    │          Thread.sleep(50ms)  ← 等 Algorithm1 完成
    │          snapshotHealthData → HR=90（初始值）
    │
    ├─ [n=16] splitPackage → RX_datacount=17
    ├─ ...
    ├─ [n=31] splitPackage → RX_datacount=32, handler.post(Algorithm2) ←非同步
    │          Thread.sleep(50ms)  ← 等 Algorithm2 完成
    │          snapshotHealthData → HR=90（FFT 結果尚未寫入 HRValue）
    │
    ├─ 覆蓋 battery=55, isWearing=true（從 payload）
    │
    └─ 回傳 HealthData { HR=90, BR=20, battery=55, wearing=true }
              ↓
         forwardToNetApi() → POST .NET WebAPI
```
