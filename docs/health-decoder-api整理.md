# Health Decoder API 文件

> 整理日期：2026-05-25  
> 版本：v1.0  
> 說明：本文件描述 `health-decoder-api` 的架構、端點與整合方式。此為 POC（概念驗證）專案，用於將 ITRI 藍牙 SDK 的解碼邏輯從裝置端抽離，改由後端伺服器統一解碼。

---

## 一、專案概覽

| 項目 | 內容 |
|------|------|
| 技術框架 | Spring Boot 4.0.2 / Java 17 |
| 建置工具 | Gradle |
| 預設 Port | `8080` |
| 核心依賴 | ITRI SDK（`com.itri.multible.itriuwbhr32hz.HealthCalculate`） |
| 資料庫 | **無**（純解碼，不寫入 DB） |

---

## 二、系統架構說明

```
App / 裝置
  │
  │ BLE Raw Data (bytes)
  ▼
health-decoder-api  (Spring Boot)
  │  POST /api/health/decode
  │  ・接收 Base64 編碼的原始藍牙封包
  │  ・呼叫 ITRI HealthCalculate SDK 解碼
  │  ・回傳結構化生理數據 (心率、呼吸率、步數...)
  ▼
呼叫方（App 或 WebAPI）
  │
  │  再將解碼後數據上傳
  ▼
WebAPI  POST /api/ipetdata/health-data
  │
  ▼
Database (USP_PetHealthAdd → PetHealthRecords)
```

**設計動機：**  
ITRI SDK 原為 Android Java Library，APP 端直接呼叫 SDK 計算。此 POC 將 SDK 包成 REST API，未來可讓 WebAPI 直接解碼，APP 只需上傳原始 bytes，不再需要內嵌 SDK。

---

## 三、API 端點

### 3.1 健康狀態確認

| 項目 | 內容 |
|------|------|
| Method | `GET` |
| Path | `/api/health/ping` |
| 認證 | 無（AllowAnonymous） |

**Response：**
```
200 OK
"Health Decoder API is running!"
```

---

### 3.2 解碼生理數據（核心端點）

| 項目 | 內容 |
|------|------|
| Method | `POST` |
| Path | `/api/health/decode` |
| Content-Type | `application/json` |
| 認證 | 無（AllowAnonymous） |

#### Request Body

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `rawData` | string | ✅ | ITRI 裝置藍牙封包的 **Base64 編碼**字串 |
| `animalType` | int | ✅ | 動物類型：`0`=人類、`1`=貓、`2`=兔子、`3`=狗（預設 3） |

**範例：**
```json
{
  "rawData": "AAECBAUGB...",
  "animalType": 3
}
```

#### Response Body

| 欄位 | 型別 | 說明 |
|------|------|------|
| `success` | boolean | 是否解碼成功 |
| `message` | string | 結果訊息 |
| `data` | HealthData | 解碼後的生理數據（成功時才有） |

#### `data` 物件欄位（HealthData）

| 欄位 | 型別 | 說明 |
|------|------|------|
| `timestamp` | long | SDK 計算時間戳 |
| `isWearing` | boolean | 裝置是否佩戴中 |
| `hrValue` | int | 心率（bpm） |
| `brValue` | int | 呼吸率（次/分） |
| `rriValue` | int | RR Interval（ms） |
| `tempValue` | float | 體感溫度（°C） |
| `humValue` | int | 環境濕度（%） |
| `stepValue` | int | 步數 |
| `pressureValue` | int | 氣壓值 |
| `powerValue` | int | 電池電量（%） |
| `gyroX` | int | 陀螺儀 X 軸 |
| `gyroY` | int | 陀螺儀 Y 軸 |
| `gyroZ` | int | 陀螺儀 Z 軸 |
| `petPose` | int | 寵物姿態（SDK 原始整數值） |

**成功範例：**
```json
{
  "success": true,
  "message": "解碼成功",
  "data": {
    "timestamp": 1716600000000,
    "isWearing": true,
    "hrValue": 85,
    "brValue": 22,
    "rriValue": 705,
    "tempValue": 38.2,
    "humValue": 60,
    "stepValue": 120,
    "pressureValue": 0,
    "powerValue": 78,
    "gyroX": 10,
    "gyroY": -5,
    "gyroZ": 3,
    "petPose": 1
  }
}
```

**失敗範例：**
```json
{
  "success": false,
  "message": "rawData 不能為空"
}
```

---

## 四、SDK 實例管理

- Service 層（`HealthDecoderService`）使用 `ConcurrentHashMap<deviceId, HealthCalculate>` 快取每台裝置的 SDK 實例
- **目前 POC 版本的 deviceId 固定為 `"default"`**，尚未從請求中傳入真實 DeviceId
- 若 `animalType` 改變，會自動呼叫 `sdk.setType()` 更新

> ⚠️ 正式版需要將 `deviceId` 加入 Request Body，以確保每台裝置的 SDK 狀態互不干擾

---

## 五、與 WebAPI 的整合關係

目前 APP 端的資料流：

```
1. BLE 裝置 → App 接收 Raw Bytes
2. App 呼叫本機 ITRI SDK 解碼
3. App POST /api/ipetdata/health-data（將解碼後數據上傳）
```

預期改造後的資料流：

```
1. BLE 裝置 → App 接收 Raw Bytes
2. App POST /api/health/decode（傳 rawData + animalType）
3. health-decoder-api 回傳解碼結果
4. App 或 WebAPI POST /api/ipetdata/health-data（儲存至 DB）
```

### WebAPI 接收端點參考（`POST /api/ipetdata/health-data`）

| 欄位 | 對應 Decoder 輸出 |
|------|------|
| `HeartRate` | `data.hrValue` |
| `BreathRate` | `data.brValue` |
| `Temperature` | `data.tempValue` |
| `Humidity` | `data.humValue` |
| `StepCount` | `data.stepValue` |
| `BatteryLevel` | `data.powerValue` |
| `IsWearing` | `data.isWearing` |
| `PetPose` | `data.petPose`（需轉為 string enum） |

---

## 六、本地啟動方式

```bash
# 在專案根目錄執行
./gradlew bootRun

# 確認服務啟動
curl http://localhost:8080/api/health/ping
```

---

## 七、已知限制與待解決問題

| 問題 | 說明 |
|------|------|
| DeviceId 未傳入 | 目前 Request 無 `deviceId` 欄位，SDK 實例統一用 `"default"` key，多裝置同時呼叫會共用同一個 SDK 實例 |
| 無認證機制 | 端點為 AllowAnonymous，正式部署前需加入 API Key 或 JWT |
| 無 DB 寫入 | 純解碼不儲存，儲存邏輯須由呼叫方處理 |
| animalType 非持久化 | 每次請求都要帶入 animalType，無法從 DeviceId 自動對應 |
