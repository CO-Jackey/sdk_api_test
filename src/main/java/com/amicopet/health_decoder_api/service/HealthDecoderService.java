package com.amicopet.health_decoder_api.service;

import com.amicopet.health_decoder_api.dto.DecodeResponse;
import com.itri.multible.itriuwbhr32hz.HealthCalculate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthDecoderService {

    private final Map<String, HealthCalculate> sdkInstances = new ConcurrentHashMap<>();

    public DecodeResponse.HealthData decode(String deviceId, String rawDataBase64, int animalType) {
        try {
            System.out.println("   [Service] 取得/建立 SDK 實例...");

            // 取得或建立該設備的 SDK 實例
            HealthCalculate sdk = sdkInstances.computeIfAbsent(deviceId, k -> {
                // System.out.println(" [Service] 建立新的 SDK 實例 (Device: " + deviceId + ", Type: "
                // + animalType + ")");
                return new HealthCalculate(animalType);
            });

            // 如果動物類型改變，更新設定
            if (sdk.getType() != animalType) {
                // System.out.println(" [Service] 更新動物類型: " + sdk.getType() + " → " +
                // animalType);
                sdk.setType(animalType);
            }

            // Base64 解碼
            // System.out.println(" [Service] Base64 解碼中...");
            byte[] rawData = Base64.getDecoder().decode(rawDataBase64);
            // System.out.println(" [Service] 解碼後 bytes 長度: " + rawData.length);

            // 呼叫 SDK 解碼
            // System.out.println(" [Service] 呼叫 ITRI SDK 解碼...");
            int result = sdk.splitPackage(rawData);

            // System.out.println(" [Service] SDK 回傳結果碼: " + result);

            // 檢查結果
            if (result == HealthCalculate.RESULT_OK) {
                // System.out.println(" [Service] SDK 解碼成功，提取數據...");

                // 建立回應
                DecodeResponse.HealthData healthData = new DecodeResponse.HealthData();
                healthData.setTimestamp(sdk.getTimeStamp());
                healthData.setWearing(sdk.getIsWearing());
                healthData.setHrValue(sdk.getHRValue());
                healthData.setBrValue(sdk.getBRValue());
                healthData.setRriValue(sdk.getRRIValue());
                healthData.setTempValue(sdk.getTempValue());
                healthData.setStepValue(sdk.getStepValue());
                healthData.setHumValue(sdk.getHumValue());
                healthData.setPressureValue(sdk.getPressureValue());
                healthData.setPowerValue(sdk.getPowerValue());
                healthData.setGyroX(sdk.getGyroValueX());
                healthData.setGyroY(sdk.getGyroValueY());
                healthData.setGyroZ(sdk.getGyroValueZ());
                healthData.setPetPose(sdk.getPetPoseValue());

                // System.out.println(" [Service] 數據提取完成");

                return healthData;
            } else {
                String errorMsg = getErrorMessage(result);
                System.out.println("   [Service] ❌ SDK 解碼失敗: " + errorMsg);
                throw new RuntimeException("解碼失敗: " + errorMsg);
            }

        } catch (Exception e) {
            System.out.println("   [Service] ❌ 異常: " + e.getMessage());
            throw new RuntimeException("解碼過程發生錯誤: " + e.getMessage(), e);
        }
    }

    private String getErrorMessage(int errorCode) {
        if (errorCode == HealthCalculate.ERROR_FIRST_BYTE) {
            return "資料標頭錯誤 (ERROR_FIRST_BYTE)";
        } else if (errorCode == HealthCalculate.ERROR_CHECKSUM) {
            return "檢查碼錯誤 (ERROR_CHECKSUM)";
        } else {
            return "未知錯誤 (錯誤碼: " + errorCode + ")";
        }
    }

    public void clearDevice(String deviceId) {
        sdkInstances.remove(deviceId);
        System.out.println("   [Service] 清除設備 SDK 實例: " + deviceId);
    }
}