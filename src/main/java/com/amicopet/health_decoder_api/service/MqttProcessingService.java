package com.amicopet.health_decoder_api.service;

import com.amicopet.health_decoder_api.dto.DecodeResponse;
import com.amicopet.health_decoder_api.dto.MqttDecodeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 解碼流程的共用邏輯：
 * - resolveAnimalType()：依 MAC 查詢 .NET 取得寵物種類（有快取）
 * - forwardToNetApi()  ：將解碼結果 POST 到 .NET WebAPI
 *
 * 同時被 HealthController（HTTP 端點）和 MqttSubscriberService（MQTT 訂閱）使用，
 * animalTypeCache 共享，避免重複查詢。
 */
@Service
public class MqttProcessingService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${net.api.health.url:}")
    private String netApiHealthUrl;

    @Value("${net.api.animal-type.url:}")
    private String netApiAnimalTypeUrl;

    /** MAC 位址 → animalType 快取（服務重啟才清除） */
    private final Map<String, Integer> animalTypeCache = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    // resolveAnimalType
    // ─────────────────────────────────────────────────────────────

    /**
     * 依 MAC 位址取得 animalType（快取優先）。
     * 快取 miss 時呼叫 .NET GET /api/pet/animal-type，查不到時 fallback = 3（狗）。
     */
    public int resolveAnimalType(String macAddress) {
        if (animalTypeCache.containsKey(macAddress)) {
            return animalTypeCache.get(macAddress);
        }

        int animalType = 3; // fallback：狗
        if (netApiAnimalTypeUrl != null && !netApiAnimalTypeUrl.isBlank()) {
            try {
                String url = netApiAnimalTypeUrl + "?macAddress=" + macAddress;
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response != null && response.containsKey("Data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.get("Data");
                    if (data != null && data.containsKey("AnimalType")) {
                        animalType = (int) data.get("AnimalType");
                        System.out.println("🐾 查詢 animalType 成功: MAC=" + macAddress
                                + " → " + animalType + "(" + getAnimalTypeName(animalType) + ")");
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️  查詢 animalType 失敗，使用 fallback=3(狗): " + e.getMessage());
            }
        } else {
            System.out.println("⚠️  net.api.animal-type.url 未設定，使用 fallback=3(狗)");
        }

        animalTypeCache.put(macAddress, animalType);
        return animalType;
    }

    // ─────────────────────────────────────────────────────────────
    // forwardToNetApi
    // ─────────────────────────────────────────────────────────────

    /** 將解碼結果 POST 到 .NET WebAPI health-data endpoint */
    public boolean forwardToNetApi(
            String macAddress,
            List<MqttDecodeRequest.DataPayload> payloads,
            DecodeResponse.HealthData healthData) {

        if (netApiHealthUrl == null || netApiHealthUrl.isBlank()) {
            System.out.println("⚠️  net.api.health.url 未設定，跳過轉送");
            return false;
        }

        // MQTT payload 的 timestamp 是裝置開機後的 uptime（ms），不是 Unix epoch
        // 用伺服器當前 UTC 時間作為 RecordTime，確保寫入 DB 的時間正確
        MqttDecodeRequest.DataPayload firstPayload = payloads.get(0);
        String recordTime = LocalDateTime
                .ofInstant(Instant.now(), ZoneOffset.UTC)
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
        body.put("DeviceTimestamp", firstPayload.getTimestamp()); // 裝置 uptime ms，供偵錯

        try {
            restTemplate.postForEntity(netApiHealthUrl, body, String.class);
            System.out.println("✅ 轉送 .NET 成功: " + netApiHealthUrl);
            return true;
        } catch (Exception e) {
            System.out.println("❌ 轉送 .NET 失敗: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    public String getAnimalTypeName(int type) {
        switch (type) {
            case 0: return "人類";
            case 1: return "貓";
            case 2: return "兔子";
            case 3: return "狗";
            default: return "未知";
        }
    }
}
