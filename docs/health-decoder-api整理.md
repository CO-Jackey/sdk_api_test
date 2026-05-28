# Health Decoder API 文件

> 整理日期：2026-05-27  
> 版本：v2.0  
> 說明：本文件描述 `health-decoder-api` 的架構、端點與整合方式。

---

## ⚠️ 這個服務只做一件事

**Health Decoder API 的唯一職責：將 ITRI SDK 的解碼邏輯包成 REST API。**

| 做 ✅ | 不做 ❌ |
|-------|--------|
| 接收原始藍牙封包（Hex / Base64） | 不寫入資料庫 |
| 呼叫 ITRI HealthCalculate SDK 解碼 | 不處理使用者身份驗證 |
| 回傳結構化生理數據 | 不查詢寵物或帳號資訊 |
| （僅 decode-mqtt）轉送解碼結果到 .NET WebAPI | 不儲存任何狀態（除了 SDK buffer） |

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

### 路徑 A：APP 端解碼（現行）

```
BLE 裝置 ──藍牙── APP（內嵌 ITRI SDK）
                    │ POST 解碼後數據
                    ▼
              .NET WebAPI  /api/ipetdata/health-data
                    │
                    ▼
              DB (PetHealthRecords)
```

### 路徑 B：MQTT 站台自動上傳（新增，透過 decode-mqtt）

```
BLE 裝置 ──藍牙── MQTT 站台（Raspberry Pi 等）
                    │ POST 原始封包（32包 hex）
                    ▼
         health-decoder-api  /api/health/decode-mqtt
                    │ 呼叫 SDK 解碼
                    │ POST 解碼結果
                    ▼
              .NET WebAPI  /api/ipetdata/health-data
                    │
                    ▼
              DB (PetHealthRecords)
```

### 路徑 C：開發測試用（decode / decode-multi）

```
開發者 / Swagger
  │ POST 單包或多包
  ▼
health-decoder-api  /api/health/decode
                    /api/health/decode-multi
  │ 回傳解碼結果（不轉送 .NET）
  ▼
開發者查看結果
```

### 路徑 D：MQTT 訂閱模式（Java API 主動訂閱）

```
裝置（WiFi）
    ↓ 發布到 MQTT Broker
broker.hivemq.com:1883  topic=esp32
    ↓ Java API 主動訂閱（MqttSubscriberService）
health-decoder-api  MqttProcessingService
    ↓ 呼叫 SDK 解碼
HealthDecoderService.decodeMqtt()
    ↓ POST 解碼結果
.NET WebAPI  /api/ipetdata/health-data
    ↓
DB (PetHealthRecords)
```

> `mqtt.enabled=true` 時服務啟動即訂閱；`mqtt.enabled=false` 時完全不啟動，HTTP 端點不受影響（見第十章）。

---

## 三、API 端點總覽

| Method | Path | 用途 | 會轉送 .NET？ |
|--------|------|------|------------|
| `GET` | `/api/health/ping` | 健康確認 | 否 |
| `POST` | `/api/health/decode` | 單包解碼 | 否 |
| `POST` | `/api/health/decode-multi` | 多包連續解碼（測試用） | 否 |
| `DELETE` | `/api/health/reset` | 清除 SDK buffer | 否 |
| `POST` | `/api/health/decode-mqtt` | MQTT 批次解碼 + 自動轉送 | **是** |

---

## 四、端點詳細說明

### 4.1 健康確認

```
GET /api/health/ping
```

**輸出：**
```
200 OK  →  "Health Decoder API is running!"
```

---

### 4.2 單包解碼

```
POST /api/health/decode
```

**輸入（Request Body）：**

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `rawData` | string | ✅ | 單個藍牙封包，支援 **Hex**（如 `ffb2be38...`）或 **Base64** |
| `animalType` | int | ✅ | `0`=人類 / `1`=貓 / `2`=兔子 / `3`=狗（預設 3） |

> 注意：deviceId 固定為 `"default"`（所有呼叫方共用同一個 SDK buffer）

**輸入範例：**
```json
{
  "rawData": "ffb2be386929f02144000000fb0b2f3d",
  "animalType": 3
}
```

**輸出（Response Body）：**

| 欄位 | 型別 | 說明 |
|------|------|------|
| `success` | boolean | 是否解碼成功（SDK 回傳 `RESULT_OK`） |
| `message` | string | 結果說明文字 |
| `data` | HealthData | 解碼後的生理數據（失敗時為 null） |

**輸出範例（成功）：**
```json
{
  "success": true,
  "message": "解碼成功",
  "data": {
    "timestamp": 177858985747,
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
    "petPose": 3
  }
}
```

> ⚠️ SDK 需累積 **16 包以上**才能計算出 HR / BR，單包通常 hrValue = brValue = 0。

---

### 4.3 多包連續解碼（測試用）

```
POST /api/health/decode-multi
```

**輸入（Request Body）：**

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `rawData` | string | ✅ | 同一封包，Hex 或 Base64 |
| `animalType` | int | ✅ | 同上（0~3，預設 3） |
| `repeatCount` | int | ✅ | 重複送入次數（建議 32~50，上限 200） |

**輸入範例：**
```json
{
  "rawData": "ffb2be386929f02144000000fb0b2f3d",
  "animalType": 3,
  "repeatCount": 32
}
```

**輸出（Response Body）：**

| 欄位 | 型別 | 說明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `message` | string | 說明（包含送入包數） |
| `processedCount` | int | 實際處理包數 |
| `data` | HealthData | 解碼結果（欄位同 4.2） |

> deviceId 固定為 `"test-multi"`，可用 `DELETE /api/health/reset` 清除 buffer

---

### 4.4 清除 SDK buffer

```
DELETE /api/health/reset?deviceId=test-multi
```

**輸入（Query Param）：**

| 參數 | 預設值 | 說明 |
|------|--------|------|
| `deviceId` | `test-multi` | 要清除的裝置 ID |

**輸出：**
```
200 OK  →  "已清除 deviceId="test-multi" 的 SDK buffer"
```

---

### 4.5 MQTT 批次解碼（核心整合端點）

```
POST /api/health/decode-mqtt
```

> 🔐 **此端點需要 API Key 認證**，請在 Header 帶上 `X-Api-Key: <secret>`

**輸入（Request Header）：**

| Header | 必填 | 說明 |
|--------|------|------|
| `X-Api-Key` | ✅ | API Key，值須與 `decoder.api.key` 設定一致 |

**輸入（Request Body）：**

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `device_name` | string | ✅ | 裝置名稱（僅 log 用，不影響邏輯） |
| `mac_address` | string | ✅ | 裝置 MAC 位址，同時作為 SDK 的 deviceId |
| `data_payload` | DataPayload[] | ✅ | 每筆含 32 個原始封包 |

**DataPayload 欄位：**

| 欄位 | 型別 | 說明 |
|------|------|------|
| `battery_level` | int | 電量（%），直接採用，不從 SDK 取 |
| `is_wearing` | boolean | 是否佩戴，直接採用，不從 SDK 取 |
| `timestamp` | long | 裝置 uptime ms（非 Unix epoch），存為 DeviceTimestamp |
| `packets` | Packet[] | 32 個原始封包（通常 n=0~31） |

**Packet 欄位：**

| 欄位 | 型別 | 說明 |
|------|------|------|
| `n` | int | 封包序號，依此升冪排序後再送 SDK |
| `hex_data` | string | 16 bytes 的 Hex 字串（如 `ffb2be38...`） |

**輸入範例：**
```json
{
  "device_name": "iPet_FE38",
  "mac_address": "08:F9:E0:1B:FE:38",
  "data_payload": [{
    "battery_level": 78,
    "is_wearing": true,
    "timestamp": 177858985747,
    "packets": [
      {"n": 0, "hex_data": "ffb2be386929f02144000000fb0b2f3d"},
      {"n": 1, "hex_data": "ffb5be386929701444000000fb0b2fc7"}
    ]
  }]
}
```

**animalType 取得流程（v2 動態化）：**
1. 查詢記憶體快取（key = macAddress）
2. Cache miss → 呼叫 `GET /api/pet/animal-type?macAddress=...`（.NET WebAPI）
3. 查詢失敗或裝置未綁定 → fallback = 3（狗）
4. 結果存入快取（服務重啟才清除）

**輸出（Response Body）：**

| 欄位 | 型別 | 說明 |
|------|------|------|
| `success` | boolean | 解碼是否成功 |
| `message` | string | 結果說明 |
| `forwardSuccess` | boolean | 轉送 .NET 是否成功（false 不影響解碼結果） |
| `data` | HealthData | 解碼結果（欄位同 4.2，但 powerValue / isWearing 來自 payload） |

**輸出範例（已驗證，mac_address: 08:F9:E0:1B:FE:38）：**
```json
{
  "success": true,
  "message": "解碼並送出成功",
  "forwardSuccess": true,
  "data": {
    "hrValue": 148,
    "brValue": 41,
    "wearing": true,
    "powerValue": 78,
    "petPose": 3,
    "rriValue": 405,
    "tempValue": 0.0,
    "humValue": 0,
    "stepValue": 0,
    "pressureValue": 11,
    "gyroX": 0,
    "gyroY": 0,
    "gyroZ": 0
  }
}
```

**HTTP 狀態碼：**

| 狀態碼 | 說明 |
|--------|------|
| `200` | 解碼成功（forwardSuccess 可能為 false） |
| `400` | mac_address 或 data_payload 為空 |
| `401` | X-Api-Key 無效或未提供 |
| `422` | 所有 32 包均無 RESULT_OK（格式異常） |
| `500` | 解碼過程發生例外 |

---

## 五、HealthData 欄位說明（decode-mqtt 輸出）

| 欄位 | 型別 | 來源 | 說明 |
|------|------|------|------|
| `hrValue` | int | SDK | 心率（bpm） |
| `brValue` | int | SDK | 呼吸率（次/分） |
| `rriValue` | int | SDK | RR Interval（ms） |
| `tempValue` | float | SDK | 體感溫度（°C） |
| `humValue` | int | SDK | 環境濕度（%） |
| `stepValue` | int | SDK | 步數 |
| `pressureValue` | int | SDK | 氣壓值 |
| `gyroX/Y/Z` | int | SDK | 陀螺儀三軸 |
| `petPose` | int | SDK | 姿態整數值（見下方對照） |
| `timestamp` | long | SDK | SDK 計算時間戳 |
| `isWearing` | boolean | **payload** | ⚠️ 直接採用 data_payload.is_wearing |
| `powerValue` | int | **payload** | ⚠️ 直接採用 data_payload.battery_level |

### PetPose 整數對照

| SDK 值 | 字串（送 .NET） | 說明 |
|--------|----------------|------|
| `0` | `walking` | 行走 |
| `1` | `sniffing` | 嗅探 |
| `2` | `trotting` | 小跑 |
| `3` | `galloping` | 奔跑 |
| `4` | `staticResting` | 靜止休息 |
| 其他 | `null` | 未知 |

---

## 六、轉送到 .NET WebAPI 的欄位

decode-mqtt 成功後，自動 POST 到 `application.properties` 設定的 `net.api.health.url`：

| 送出欄位 | 值來源 |
|----------|--------|
| `DeviceId` | `mac_address`（原始字串，含冒號） |
| `RecordTime` | **伺服器當前 UTC 時間**（`Instant.now()` → ISO 8601），不使用裝置 timestamp（裝置 timestamp 是開機 uptime ms，非 Unix epoch，直接轉換會得到約 1975 年的錯誤時間） |
| `HeartRate` | `healthData.hrValue` |
| `BreathRate` | `healthData.brValue` |
| `Temperature` | `healthData.tempValue` |
| `Humidity` | `healthData.humValue` |
| `StepCount` | `healthData.stepValue` |
| `BatteryLevel` | `data_payload.battery_level`（非 SDK 值） |
| `IsWearing` | `data_payload.is_wearing`（非 SDK 值） |
| `PetPose` | `petPoseToString(healthData.petPose)`（字串） |
| `ActivityLevel` | `null`（保留欄位） |
| `PetMood` | `null`（保留欄位） |

> 轉送失敗（網路錯誤、404 等）只印 log，**不拋例外**，decode-mqtt 仍回傳 200 和解碼結果。

---

## 七、SDK 實例管理

- `HealthDecoderService` 用 `ConcurrentHashMap<deviceId, HealthCalculate>` 管理每台裝置的 SDK 實例
- **decode / decode-multi** 各有固定的 deviceId（`"default"` / `"test-multi"`）
- **decode-mqtt** 用 `mac_address` 作為 deviceId，不同裝置的 SDK buffer 互相獨立
- SDK 為串流設計：同一 deviceId 的 buffer **跨請求保留**，需累積 16+ 包才能計算 HR/BR

---

## 八、本地啟動方式

```bash
./gradlew bootRun

# 確認啟動
curl http://localhost:8080/api/health/ping
```

`application.properties` 需設定（六項）：
```properties
net.api.health.url=https://<your-webapi>.azurewebsites.net/api/ipetdata/health-data
net.api.animal-type.url=https://<your-webapi>.azurewebsites.net/api/pet/animal-type
decoder.api.key=<your-secret-key>
mqtt.enabled=true
mqtt.broker.url=tcp://broker.hivemq.com:1883
mqtt.topic=esp32
```

---

## 九、已知限制與注意事項

| 項目 | 說明 |
|------|------|
| ~~animalType 寫死~~ | ✅ **已解決（v2）**：decode-mqtt 會查詢 .NET `GET /api/pet/animal-type`，依裝置綁定的寵物動態取得 animalType；查詢結果快取於記憶體，服務重啟後重新查詢。未綁定寵物時 fallback 為 3（狗）。 |
| ~~無認證機制~~ | ✅ **已解決（v2）**：decode-mqtt 端點加入 API Key 驗證（Header `X-Api-Key`）。`decoder.api.key` 留空或為預設值 `CHANGE_ME_BEFORE_DEPLOY` 時，開發環境跳過驗證並列印警告。正式部署必須透過環境變數設定。 |
| SDK buffer 不持久化 | 服務重啟後所有 SDK 實例歸零，HR/BR 需重新累積 |
| 轉送失敗不重試 | decode-mqtt 若 .NET 轉送失敗，不會重送，資料將遺失 |
| 單包解碼通常無 HR/BR | SDK 需 16+ 包，單包呼叫 decode 通常得到 hrValue=0 |
| animalType 快取無 TTL | 寵物重新綁定後，快取在服務重啟前不會自動刷新（目前視為可接受的限制） |

---

## 十、MQTT 訂閱設定

### 設定項說明

| 設定項 | 說明 | 預設值 |
|--------|------|--------|
| `mqtt.enabled` | `true` = 啟動時自動訂閱；`false` = 完全不啟動 | `true` |
| `mqtt.broker.url` | MQTT Broker 連線位址 | `tcp://broker.hivemq.com:1883` |
| `mqtt.topic` | 訂閱的 Topic 名稱 | `esp32` |
| `mqtt.client.id` | Client ID（`${random.uuid}` 確保每次重啟唯一） | `health-decoder-${random.uuid}` |
| `mqtt.qos` | QoS 等級（0=至多一次 / 1=至少一次 / 2=恰好一次） | `1` |

> ⚠️ 修改 MQTT 設定後需**重新打包部署**（Azure App Service 重啟），環境變數方式同樣需重啟才生效。

---

### 情境說明

#### 情境 A：換成自建 MQTT Server（EMQX / Mosquitto）

只需修改 `mqtt.broker.url` 和 `mqtt.topic`，不動程式碼：

```properties
mqtt.enabled=true
mqtt.broker.url=tcp://your-mqtt-server.com:1883
mqtt.topic=your/topic
```

> 若自建 Broker 需要帳號密碼，需同步在 `MqttSubscriberService` 的 `doConnect()` 加入 `options.setUserName()` 和 `options.setPassword()`。

#### 情境 B：切換到 EMQX Webhook 模式（推薦正式上線架構）

```properties
mqtt.enabled=false   ← 關掉 Java 直接訂閱
```

在 EMQX 設定 Rule Engine → Webhook：
- 收到 topic `esp32` 的訊息後，HTTP POST 到 `https://.../api/health/decode-mqtt`
- Webhook Header 帶上 `X-Api-Key: amicoipet-decoder`（或正式密鑰）
- **Java 程式碼完全不需修改**

#### 情境 C：更換 Topic 名稱

只改 `mqtt.topic`，重新打包部署：

```properties
mqtt.topic=ipet/health/data
```

---

### 訊息格式與錯誤處理

收到任何訊息都會先完整印出原始內容：

```
📨 MQTT 原始訊息:
  topic: esp32
  長度: 151 bytes
  內容: {"device_name":"iPet_FE38",...}
```

- **非本系統格式**（如其他裝置測試訊息）→ `⚠️  格式不符，略過（非本系統訊息）`
- **本系統格式且驗證通過** → `✅ 格式正確，開始解碼` + mac + packets 筆數
- `MqttDecodeRequest` 加了 `@JsonIgnoreProperties(ignoreUnknown = true)`，未知欄位不拋例外

> ⚠️ `broker.hivemq.com` 是公開 Broker，topic `esp32` 任何人都可發布，非本系統訊息會被靜默略過。正式上線前必須換成私有 Broker。
