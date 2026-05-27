package com.amicopet.health_decoder_api.service;

import com.amicopet.health_decoder_api.dto.DecodeResponse;
import com.amicopet.health_decoder_api.dto.MqttDecodeRequest;
import com.itri.multible.itriuwbhr32hz.HealthCalculate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Comparator;
import java.util.List;
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
                return new HealthCalculate(animalType);
            });

            // 如果動物類型改變，更新設定
            if (sdk.getType() != animalType) {
                sdk.setType(animalType);
            }

            // 自動偵測格式：hex 或 Base64
            byte[] rawData = decodeRawData(rawDataBase64);
            System.out.println("   [Service] 解碼後 bytes 長度: " + rawData.length);

            // 呼叫 SDK 解碼
            int result = sdk.splitPackage(rawData);

            // 檢查結果
            if (result == HealthCalculate.RESULT_OK) {
                DecodeResponse.HealthData healthData = snapshotHealthData(sdk);

                System.out.println("   [Service] 健康數據: " + healthData.toString());
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

    /**
     * 自動偵測 rawData 格式，支援：
     * 1. Hex 字串（如 "ffb2be38..."）→ 直接轉 bytes
     * 2. Base64 字串（如 "/7K+OGkp..."）→ Base64 解碼
     */
    private byte[] decodeRawData(String rawData) {
        String trimmed = rawData.trim();
        if (isHexString(trimmed)) {
            System.out.println("   [Service] 偵測格式：Hex → 轉換為 bytes");
            return hexToBytes(trimmed);
        } else {
            System.out.println("   [Service] 偵測格式：Base64 → 解碼");
            return Base64.getDecoder().decode(trimmed);
        }
    }

    /** 判斷是否為純 hex 字串（偶數長度、只有 0-9 a-f A-F） */
    private boolean isHexString(String s) {
        return s.length() % 2 == 0 && s.matches("[0-9a-fA-F]+");
    }

    /** Hex 字串轉 byte 陣列 */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
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

    /**
     * 連續送入多包，模擬裝置持續傳輸，適合測試 HR/BR 計算
     *
     * @param deviceId   裝置 ID（相同 ID 共用同一個 SDK buffer）
     * @param rawData    Hex 或 Base64 原始封包
     * @param animalType 動物種類
     * @param repeatCount 重複送入次數
     */
    public DecodeResponse.HealthData decodeMulti(String deviceId, String rawData, int animalType, int repeatCount) {
        try {
            HealthCalculate sdk = sdkInstances.computeIfAbsent(deviceId, k -> new HealthCalculate(animalType));

            if (sdk.getType() != animalType) {
                sdk.setType(animalType);
            }

            byte[] rawBytes = decodeRawData(rawData);
            System.out.println("   [Service] 多包測試：送入 " + repeatCount + " 包");

            int lastResult = HealthCalculate.RESULT_OK;
            for (int i = 0; i < repeatCount; i++) {
                lastResult = sdk.splitPackage(rawBytes);
            }

            if (lastResult == HealthCalculate.RESULT_OK) {
                DecodeResponse.HealthData healthData = snapshotHealthData(sdk);
                System.out.println("   [Service] 多包結果: HR=" + healthData.getHrValue() + " BR=" + healthData.getBrValue());
                return healthData;
            } else {
                throw new RuntimeException("解碼失敗: " + getErrorMessage(lastResult));
            }
        } catch (Exception e) {
            throw new RuntimeException("多包解碼失敗: " + e.getMessage(), e);
        }
    }

    public void clearDevice(String deviceId) {
        sdkInstances.remove(deviceId);
        System.out.println("   [Service] 清除設備 SDK 實例: " + deviceId);
    }

    /**
     * 處理 MQTT 送來的完整封包批次
     *
     * @param macAddress  裝置 MAC 位址（作為 deviceId）
     * @param payloads    MQTT data_payload 陣列
     * @return 最後一次 RESULT_OK 的 HealthData snapshot（battery / wearing 已覆蓋為 payload 值）
     * @throws RuntimeException 所有封包都無 RESULT_OK 時
     */
    /**
     * 解碼 MQTT 封包（animalType 由呼叫方傳入，不再寫死為 3）
     *
     * @param macAddress  裝置 MAC 位址（用於 SDK 實例快取）
     * @param payloads    MQTT 封包列表
     * @param animalType  動物種類（0=人類 / 1=貓 / 2=兔子 / 3=狗），由 Controller 查詢 .NET 取得
     */
    public DecodeResponse.HealthData decodeMqtt(String macAddress, List<MqttDecodeRequest.DataPayload> payloads, int animalType) {
        // 若快取的 SDK 實例 animalType 與目前不符（例如裝置重新綁定不同種類的寵物），重建實例
        HealthCalculate sdk = sdkInstances.computeIfAbsent(macAddress, k -> new HealthCalculate(animalType));
        if (sdk.getType() != animalType) {
            sdk.setType(animalType);
        }

        DecodeResponse.HealthData lastOk = null;
        int lastBattery = 0;
        boolean lastWearing = false;

        for (MqttDecodeRequest.DataPayload payload : payloads) {
            lastBattery = payload.getBatteryLevel();
            lastWearing = payload.isWearing();

            List<MqttDecodeRequest.Packet> sorted = payload.getPackets().stream()
                    .sorted(Comparator.comparingInt(MqttDecodeRequest.Packet::getN))
                    .toList();

            System.out.println("   [Service] MQTT 批次：" + sorted.size() + " 包，MAC=" + macAddress);

            for (MqttDecodeRequest.Packet packet : sorted) {
                try {
                    byte[] rawBytes = decodeRawData(packet.getHexData());
                    int result = sdk.splitPackage(rawBytes);

                    if (result == HealthCalculate.RESULT_OK) {
                        lastOk = snapshotHealthData(sdk);
                    }
                } catch (Exception e) {
                    System.out.println("   [Service] ⚠️ 封包 n=" + packet.getN() + " 解析失敗: " + e.getMessage());
                }
            }
        }

        if (lastOk == null) {
            throw new RuntimeException("NO_VALID_PACKET_DECODED：所有封包均無法解碼，請確認資料格式");
        }

        // 以 payload 的 battery / wearing 覆蓋 SDK 值（SDK 從 packet 解出的值不可靠）
        lastOk.setPowerValue(lastBattery);
        lastOk.setWearing(lastWearing);

        System.out.println("   [Service] MQTT 解碼完成: HR=" + lastOk.getHrValue()
                + " BR=" + lastOk.getBrValue() + " wearing=" + lastWearing);
        return lastOk;
    }

    /** 將 SDK 目前狀態快照成新的 HealthData 物件（避免參考污染） */
    private DecodeResponse.HealthData snapshotHealthData(HealthCalculate sdk) {
        DecodeResponse.HealthData d = new DecodeResponse.HealthData();
        d.setTimestamp(sdk.getTimeStamp());
        d.setWearing(sdk.getIsWearing());
        d.setHrValue(sdk.getHRValue());
        d.setBrValue(sdk.getBRValue());
        d.setRriValue(sdk.getRRIValue());
        d.setTempValue(sdk.getTempValue());
        d.setStepValue(sdk.getStepValue());
        d.setHumValue(sdk.getHumValue());
        d.setPressureValue(sdk.getPressureValue());
        d.setPowerValue(sdk.getPowerValue());
        d.setGyroX(sdk.getGyroValueX());
        d.setGyroY(sdk.getGyroValueY());
        d.setGyroZ(sdk.getGyroValueZ());
        d.setPetPose(sdk.getPetPoseValue());
        return d;
    }

    /**
     * 將 SDK 的 petPose int 轉換為 .NET API 期待的字串（與 Flutter PetPose enum name 一致）
     * 0=walking, 1=sniffing, 2=trotting, 3=galloping, 4=staticResting
     */
    public static String petPoseToString(int pose) {
        switch (pose) {
            case 0: return "walking";
            case 1: return "sniffing";
            case 2: return "trotting";
            case 3: return "galloping";
            case 4: return "staticResting";
            default: return null;
        }
    }
}
