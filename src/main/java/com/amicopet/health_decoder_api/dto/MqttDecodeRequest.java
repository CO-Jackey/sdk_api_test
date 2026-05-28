package com.amicopet.health_decoder_api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "MQTT 站台送來的裝置封包，含完整 32 包原始資料")
public class MqttDecodeRequest {

    @Schema(description = "裝置名稱", example = "iPet_FE38")
    @JsonProperty("device_name")
    private String deviceName;

    @Schema(description = "裝置 MAC 位址（作為 deviceId）", example = "08:F9:E0:1B:FE:38", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("mac_address")
    private String macAddress;

    @Schema(description = "資料批次，通常為 1 筆，每筆含 32 個封包", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("data_payload")
    private List<DataPayload> dataPayload;

    // ── Getters / Setters ──────────────────────────────────────────

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public List<DataPayload> getDataPayload() { return dataPayload; }
    public void setDataPayload(List<DataPayload> dataPayload) { this.dataPayload = dataPayload; }

    // ── Inner: DataPayload ─────────────────────────────────────────

    @Schema(description = "單一批次資料，含電量、佩戴狀態與封包列表")
    public static class DataPayload {

        @Schema(description = "電池電量（%）", example = "78")
        @JsonProperty("battery_level")
        private int batteryLevel;

        @Schema(description = "是否正在佩戴", example = "true")
        @JsonProperty("is_wearing")
        private boolean isWearing;

        @Schema(description = "批次時間戳（epoch ms）", example = "177858985747")
        private long timestamp;

        @Schema(description = "原始封包列表（建議 32 筆，依 n 排序）")
        private List<Packet> packets;

        public int getBatteryLevel() { return batteryLevel; }
        public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }

        public boolean isWearing() { return isWearing; }
        public void setWearing(boolean wearing) { isWearing = wearing; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public List<Packet> getPackets() { return packets; }
        public void setPackets(List<Packet> packets) { this.packets = packets; }
    }

    // ── Inner: Packet ──────────────────────────────────────────────

    @Schema(description = "單一原始封包")
    public static class Packet {

        @Schema(description = "封包序號（0 起始）", example = "0")
        private int n;

        @Schema(description = "封包 Hex 資料", example = "ffb2be386929f02144000000fb0b2f3d")
        @JsonProperty("hex_data")
        private String hexData;

        public int getN() { return n; }
        public void setN(int n) { this.n = n; }

        public String getHexData() { return hexData; }
        public void setHexData(String hexData) { this.hexData = hexData; }
    }
}
