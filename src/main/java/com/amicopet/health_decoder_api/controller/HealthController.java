package com.amicopet.health_decoder_api.controller;

import com.amicopet.health_decoder_api.dto.*;
import com.amicopet.health_decoder_api.service.HealthDecoderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
@Tag(name = "Health Decoder", description = "ITRI SDK 生理數據解碼。接收藍牙裝置的 Base64 原始封包，回傳解析後的生理數據。")
public class HealthController {

    @Autowired
    private HealthDecoderService decoderService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${net.api.health.url:}")
    private String netApiHealthUrl;

    private int requestCount = 0;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ─────────────────────────────────────────────────────────────
    // GET /api/health/ping
    // ─────────────────────────────────────────────────────────────

    @Operation(
        summary = "健康檢查",
        description = "確認 Health Decoder API 服務是否正常運行。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "服務正常運行",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "Health Decoder API is running!")
            )
        )
    })
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("Health Decoder API is running!");
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/health/decode
    // ─────────────────────────────────────────────────────────────

    @Operation(
        summary = "解碼生理數據",
        description = "接收藍牙裝置回傳的 Base64 原始封包，透過 ITRI SDK 解析，" +
                      "回傳心率、呼吸率、體溫、步數、濕度、電量、姿態等完整生理數據。\n\n" +
                      "**animalType 對照：**\n" +
                      "- `0` 人類\n" +
                      "- `1` 貓\n" +
                      "- `2` 兔子\n" +
                      "- `3` 狗（預設，超出範圍自動 fallback）"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "解碼成功，回傳生理數據",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DecodeResponse.class),
                examples = @ExampleObject(
                    name = "解碼成功範例",
                    value = """
                        {
                          "success": true,
                          "message": "解碼成功",
                          "data": {
                            "timestamp": 1716600000000,
                            "isWearing": true,
                            "hrValue": 85,
                            "brValue": 22,
                            "rriValue": 705,
                            "tempValue": 38.5,
                            "stepValue": 250,
                            "humValue": 65,
                            "pressureValue": 1013,
                            "powerValue": 78,
                            "gyroX": 0,
                            "gyroY": 0,
                            "gyroZ": 0,
                            "petPose": 3
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "請求參數錯誤（rawData 為空）",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DecodeResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "success": false,
                          "message": "rawData 不能為空",
                          "data": null
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "SDK 解碼失敗（封包格式不符或內部錯誤）",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DecodeResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "success": false,
                          "message": "解碼失敗: ...",
                          "data": null
                        }
                        """
                )
            )
        )
    })
    @PostMapping("/decode")
    public ResponseEntity<DecodeResponse> decode(
        @RequestBody(
            description = "解碼請求：包含 Base64 原始封包與動物種類",
            required = true,
            content = @Content(
                schema = @Schema(implementation = DecodeRequest.class),
                examples = @ExampleObject(
                    name = "狗的封包範例（Hex 格式）",
                    value = """
                        {
                          "rawData": "ffb2be386929f02144000000fb0b2f3d",
                          "animalType": 3
                        }
                        """
                )
            )
        )
        @org.springframework.web.bind.annotation.RequestBody DecodeRequest request
    ) {
        requestCount++;
        String timestamp = LocalDateTime.now().format(formatter);

        System.out.println("解碼請求:" + " #" + requestCount);

        try {
            // 驗證輸入
            if (request.getRawData() == null || request.getRawData().isEmpty()) {
                System.out.println("❌ 錯誤: rawData 為空");
                System.out.println("=".repeat(80) + "\n");
                return ResponseEntity.badRequest()
                        .body(new DecodeResponse(false, "rawData 不能為空"));
            }

            String deviceId = "default";
            int animalType = request.getAnimalType();
            if (animalType < 0 || animalType > 3) {
                animalType = 3;
            }

            // 顯示接收到的數據
            System.out.println("📥 接收數據:");
            System.out.println("   Device ID: " + deviceId);
            System.out.println("   Animal Type: " + animalType + " (" + getAnimalTypeName(animalType) + ")");
            System.out.println("   Raw Data (Base64): " + request.getRawData());

            // 解碼 Base64 看實際 bytes
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(request.getRawData());
                System.out.println("   Decoded Bytes: " + bytesToHex(bytes));
                System.out.println("   Byte Count: " + bytes.length + " bytes");
            } catch (Exception e) {
                System.out.println("   ⚠️  Base64 解碼失敗: " + e.getMessage());
            }

            // 呼叫 Service 解碼
            DecodeResponse.HealthData healthData = decoderService.decode(
                    deviceId,
                    request.getRawData(),
                    animalType);

            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(request.getRawData());
                System.out.println("   Decoded Bytes: " + bytesToDecArray(decodedBytes));
            } catch (Exception e) {
                System.out.println("   ⚠️  無法顯示解碼字節");
            }

            System.out.println("心率: " + healthData.getHrValue() + " bpm");
            System.out.println("呼吸率: " + healthData.getBrValue() + " 次/分");
            System.out.println("配戴: " + (healthData.isWearing() ? "是" : "否"));
            System.out.println("電量: " + healthData.getPowerValue() + "%");
            System.out.println("溫度: " + healthData.getTempValue() + "°C");
            System.out.println("濕度: " + healthData.getHumValue() + "%");

            return ResponseEntity.ok(
                    new DecodeResponse(true, "解碼成功", healthData));

        } catch (Exception e) {
            System.out.println("\n❌ 解碼失敗!");
            System.out.println("   錯誤訊息: " + e.getMessage());
            System.out.println("   錯誤類型: " + e.getClass().getSimpleName());
            e.printStackTrace();
            System.out.println("=".repeat(80) + "\n");

            return ResponseEntity.status(500)
                    .body(new DecodeResponse(false, "解碼失敗: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/health/decode-multi  （測試用：連續送 N 包）
    // ─────────────────────────────────────────────────────────────

    @Operation(
        summary = "【測試用】連續多包解碼",
        description = "將同一封包重複送入 SDK 指定次數，模擬裝置持續傳輸，" +
                      "讓 SDK 累積足夠 buffer 後計算出 HR / BR。\n\n" +
                      "**SDK 計算規則：**\n" +
                      "- 每 **16 包** 計算一次 HR/BR\n" +
                      "- 建議 `repeatCount` 設 **32~50** 以獲得穩定數值\n" +
                      "- 注意：同一 `deviceId` 的 SDK buffer 會跨請求保留，" +
                      "可用 `DELETE /api/health/reset` 清除"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "解碼成功，hrValue / brValue 應有非零數值",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DecodeMultiResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "success": true,
                          "message": "解碼成功（共送入 32 包）",
                          "processedCount": 32,
                          "data": {
                            "hrValue": 85,
                            "brValue": 22,
                            "wearing": true,
                            "powerValue": 78,
                            "rriValue": 68
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "400", description = "rawData 為空"),
        @ApiResponse(responseCode = "500", description = "SDK 解碼失敗")
    })
    @PostMapping("/decode-multi")
    public ResponseEntity<DecodeMultiResponse> decodeMulti(
        @RequestBody(
            description = "rawData + 重複次數（建議 32~50）",
            required = true,
            content = @Content(
                schema = @Schema(implementation = DecodeMultiRequest.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "rawData": "ffb2be386929f02144000000fb0b2f3d",
                          "animalType": 3,
                          "repeatCount": 32
                        }
                        """
                )
            )
        )
        @org.springframework.web.bind.annotation.RequestBody DecodeMultiRequest request
    ) {
        if (request.getRawData() == null || request.getRawData().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new DecodeMultiResponse(false, "rawData 不能為空"));
        }

        int repeatCount = Math.max(1, Math.min(request.getRepeatCount(), 200));
        int animalType = (request.getAnimalType() < 0 || request.getAnimalType() > 3) ? 3 : request.getAnimalType();

        System.out.println("多包解碼請求: rawData=" + request.getRawData()
                + " repeatCount=" + repeatCount + " animalType=" + animalType);

        try {
            DecodeResponse.HealthData healthData = decoderService.decodeMulti(
                    "test-multi", request.getRawData(), animalType, repeatCount);

            return ResponseEntity.ok(new DecodeMultiResponse(
                    true,
                    "解碼成功（共送入 " + repeatCount + " 包）",
                    repeatCount,
                    healthData));
        } catch (Exception e) {
            System.out.println("❌ 多包解碼失敗: " + e.getMessage());
            return ResponseEntity.status(500)
                    .body(new DecodeMultiResponse(false, "解碼失敗: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE /api/health/reset  （清除 SDK buffer）
    // ─────────────────────────────────────────────────────────────

    @Operation(
        summary = "【測試用】清除 SDK buffer",
        description = "清除指定 deviceId 的 SDK 累積 buffer，下次送包從零開始計算。\n\n" +
                      "若不帶 `deviceId` 參數，預設清除 `test-multi`（decode-multi 使用的 buffer）。"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "清除成功")
    })
    @DeleteMapping("/reset")
    public ResponseEntity<String> reset(
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "test-multi") String deviceId
    ) {
        decoderService.clearDevice(deviceId);
        return ResponseEntity.ok("已清除 deviceId=\"" + deviceId + "\" 的 SDK buffer");
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/health/decode-mqtt  （接收 MQTT 站台送來的封包）
    // ─────────────────────────────────────────────────────────────

    @Operation(
        summary = "MQTT 封包解碼並轉送 .NET",
        description = "接收 MQTT 站台送來的裝置封包（含 32 個原始封包），\n" +
                      "解碼後自動 POST 到 .NET WebAPI 寫入 PetHealthData 資料表。\n\n" +
                      "**處理流程：**\n" +
                      "1. 依 `n` 升冪排序封包\n" +
                      "2. 逐包呼叫 SDK `splitPackage()`，記錄每次 `RESULT_OK` 的結果\n" +
                      "3. 以 payload 的 `battery_level` / `is_wearing` 覆蓋 SDK 值\n" +
                      "4. 轉送 .NET API（失敗時只 log，解碼結果仍正常回傳）\n\n" +
                      "**注意：** 目前 `animalType` 固定為 3（狗）"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "解碼成功（forwardSuccess 表示 .NET 是否收到）",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = MqttDecodeResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "請求格式錯誤（mac_address / data_payload 為空）"),
        @ApiResponse(responseCode = "422", description = "所有封包均無法解碼（NO_VALID_PACKET_DECODED）"),
        @ApiResponse(responseCode = "500", description = "解碼過程發生例外")
    })
    @PostMapping("/decode-mqtt")
    public ResponseEntity<MqttDecodeResponse> decodeMqtt(
        @RequestBody(
            description = "MQTT 站台送來的裝置封包",
            required = true,
            content = @Content(
                schema = @Schema(implementation = MqttDecodeRequest.class),
                examples = @ExampleObject(
                    value = """
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
                        """
                )
            )
        )
        @org.springframework.web.bind.annotation.RequestBody MqttDecodeRequest request
    ) {
        if (request.getMacAddress() == null || request.getMacAddress().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new MqttDecodeResponse(false, "mac_address 不能為空"));
        }
        if (request.getDataPayload() == null || request.getDataPayload().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new MqttDecodeResponse(false, "data_payload 不能為空"));
        }

        System.out.println("MQTT 解碼請求: mac=" + request.getMacAddress()
                + " payloads=" + request.getDataPayload().size());

        DecodeResponse.HealthData healthData;
        try {
            healthData = decoderService.decodeMqtt(request.getMacAddress(), request.getDataPayload());
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("NO_VALID_PACKET_DECODED")) {
                return ResponseEntity.unprocessableEntity()
                        .body(new MqttDecodeResponse(false, e.getMessage()));
            }
            System.out.println("❌ MQTT 解碼失敗: " + e.getMessage());
            return ResponseEntity.status(500)
                    .body(new MqttDecodeResponse(false, "解碼失敗: " + e.getMessage()));
        }

        boolean forwardSuccess = forwardToNetApi(request.getMacAddress(), request.getDataPayload(), healthData);

        String msg = forwardSuccess ? "解碼並送出成功" : "解碼成功，但轉送 .NET 失敗（詳見 log）";
        return ResponseEntity.ok(new MqttDecodeResponse(true, msg, forwardSuccess, healthData));
    }

    /** 將解碼結果 POST 到 .NET WebAPI health-data endpoint */
    private boolean forwardToNetApi(
            String macAddress,
            java.util.List<MqttDecodeRequest.DataPayload> payloads,
            DecodeResponse.HealthData healthData) {

        if (netApiHealthUrl == null || netApiHealthUrl.isBlank()) {
            System.out.println("⚠️  net.api.health.url 未設定，跳過轉送");
            return false;
        }

        MqttDecodeRequest.DataPayload firstPayload = payloads.get(0);
        String recordTime = LocalDateTime
                .ofInstant(Instant.ofEpochMilli(firstPayload.getTimestamp()), ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_DATE_TIME) + "Z";

        Map<String, Object> body = new HashMap<>();
        body.put("DeviceId", macAddress);
        body.put("RecordTime", recordTime);
        body.put("HeartRate", healthData.getHrValue());
        body.put("BreathRate", healthData.getBrValue());
        body.put("Temperature", healthData.getTempValue());
        body.put("Humidity", healthData.getHumValue());
        body.put("StepCount", healthData.getStepValue());
        body.put("BatteryLevel", healthData.getPowerValue());
        body.put("IsWearing", healthData.isWearing());
        body.put("PetPose", HealthDecoderService.petPoseToString(healthData.getPetPose()));
        body.put("ActivityLevel", null);
        body.put("PetMood", null);

        try {
            restTemplate.postForEntity(netApiHealthUrl, body, String.class);
            System.out.println("✅ 轉送 .NET 成功: " + netApiHealthUrl);
            return true;
        } catch (Exception e) {
            System.out.println("❌ 轉送 .NET 失敗: " + e.getMessage());
            return false;
        }
    }

    private String getAnimalTypeName(int type) {
        switch (type) {
            case 0: return "人類";
            case 1: return "貓";
            case 2: return "兔子";
            case 3: return "狗";
            default: return "未知";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i] & 0xFF));
            if (i < bytes.length - 1) sb.append(" ");
        }
        sb.append("]");
        return sb.toString();
    }

    private String bytesToDecArray(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%d", bytes[i] & 0xFF));
            if (i < bytes.length - 1) sb.append(" ");
        }
        sb.append("]");
        return sb.toString();
    }
}