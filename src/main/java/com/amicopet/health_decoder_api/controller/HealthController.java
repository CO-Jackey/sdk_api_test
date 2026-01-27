package com.amicopet.health_decoder_api.controller;

import com.amicopet.health_decoder_api.dto.*;
import com.amicopet.health_decoder_api.service.HealthDecoderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired
    private HealthDecoderService decoderService;

    private int requestCount = 0;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        // System.out.println("=".repeat(60));
        // System.out.println("[PING] " + LocalDateTime.now().format(formatter));
        // System.out.println("=".repeat(60));
        return ResponseEntity.ok("Health Decoder API is running!");
    }

    @PostMapping("/decode")
    public ResponseEntity<DecodeResponse> decode(@RequestBody DecodeRequest request) {
        requestCount++;
        String timestamp = LocalDateTime.now().format(formatter);

        // System.out.println("\n" + "=".repeat(80));
        // System.out.println("▶ 收到解碼請求 #" + requestCount + " - " + timestamp);
        // System.out.println("=".repeat(80));

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
            // System.out.println(" Raw Data Length: " + request.getRawData().length() + "
            // characters");

            // 解碼 Base64 看實際 bytes
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(request.getRawData());
                System.out.println("   Decoded Bytes: " + bytesToHex(bytes));
                System.out.println("   Byte Count: " + bytes.length + " bytes");
            } catch (Exception e) {
                System.out.println("   ⚠️  Base64 解碼失敗: " + e.getMessage());
            }

            // 顯示解碼後的原始字節 (十進制格式)

            // System.out.println("\n⚙️ 開始解碼...");

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
            // 顯示解碼結果
            // System.out.println("✅ 解碼成功!");
            // System.out.println("\n📤 回傳數據:");
            System.out.println("心率: " + healthData.getHrValue() + " bpm");
            System.out.println("呼吸率: " + healthData.getBrValue() + " 次/分");
            // System.out.println(" 👕 配戴: " + (healthData.isWearing() ? "是" : "否"));
            // System.out.println(" 🔋 電量: " + healthData.getPowerValue() + "%");
            // System.out.println(" 🚶 步數: " + healthData.getStepValue());
            // System.out.println(" 🌡️ 溫度: " + healthData.getTempValue() + "°C");
            // System.out.println(" 💧 濕度: " + healthData.getHumValue() + "%");
            // System.out.println(" 📊 RRI: " + healthData.getRriValue());
            // System.out.println(" 🎯 Gyro: X=" + healthData.getGyroX() +
            // ", Y=" + healthData.getGyroY() +
            // ", Z=" + healthData.getGyroZ());
            // System.out.println(" 🐕 姿態: " + healthData.getPetPose());
            // System.out.println(" ⏰ 時間戳: " + healthData.getTimestamp());

            // System.out.println("=".repeat(80) + "\n");

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

    // 輔助方法：動物類型名稱
    private String getAnimalTypeName(int type) {
        switch (type) {
            case 0:
                return "人類";
            case 1:
                return "貓";
            case 2:
                return "兔子";
            case 3:
                return "狗";
            default:
                return "未知";
        }
    }

    // 輔助方法：bytes 轉 hex string
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i] & 0xFF));
            if (i < bytes.length - 1) {
                sb.append(" ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // 輔助方法：bytes 轉十進制字串
    private String bytesToDecArray(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%d", bytes[i] & 0xFF));
            if (i < bytes.length - 1) {
                sb.append(" ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}