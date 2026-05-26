package com.amicopet.health_decoder_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SDK 解碼回應")
public class DecodeResponse {

    @Schema(description = "是否解碼成功", example = "true")
    private boolean success;

    @Schema(description = "訊息說明", example = "解碼成功")
    private String message;

    @Schema(description = "解碼後的生理數據。失敗時為 null")
    private HealthData data;

    public DecodeResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public DecodeResponse(boolean success, String message, HealthData data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public HealthData getData() {
        return data;
    }

    public void setData(HealthData data) {
        this.data = data;
    }

    @Schema(description = "解碼後的生理數據")
    public static class HealthData {

        @Schema(description = "資料時間戳（epoch ms）", example = "1716600000000")
        private long timestamp;

        @Schema(description = "是否正在佩戴裝置", example = "true")
        private boolean isWearing;

        @Schema(description = "心率（bpm）", example = "85")
        private int hrValue;

        @Schema(description = "呼吸率（次/分）", example = "22")
        private int brValue;

        @Schema(description = "RRI 心率間隔（ms）", example = "705")
        private int rriValue;

        @Schema(description = "體感溫度（°C）", example = "38.5")
        private float tempValue;

        @Schema(description = "步數", example = "250")
        private int stepValue;

        @Schema(description = "環境濕度（%）", example = "65")
        private int humValue;

        @Schema(description = "氣壓值", example = "1013")
        private int pressureValue;

        @Schema(description = "電池電量（%）", example = "78")
        private int powerValue;

        @Schema(description = "陀螺儀 X 軸", example = "0")
        private int gyroX;

        @Schema(description = "陀螺儀 Y 軸", example = "0")
        private int gyroY;

        @Schema(description = "陀螺儀 Z 軸", example = "0")
        private int gyroZ;

        @Schema(
            description = "寵物姿態。0=站立, 1=行走, 2=跑步, 3=趴下, 4=坐下, 5=靜止",
            example = "3"
        )
        private int petPose;

        // Getters and Setters
        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public boolean isWearing() {
            return isWearing;
        }

        public void setWearing(boolean wearing) {
            isWearing = wearing;
        }

        public int getHrValue() {
            return hrValue;
        }

        public void setHrValue(int hrValue) {
            this.hrValue = hrValue;
        }

        public int getBrValue() {
            return brValue;
        }

        public void setBrValue(int brValue) {
            this.brValue = brValue;
        }

        public int getRriValue() {
            return rriValue;
        }

        public void setRriValue(int rriValue) {
            this.rriValue = rriValue;
        }

        public float getTempValue() {
            return tempValue;
        }

        public void setTempValue(float tempValue) {
            this.tempValue = tempValue;
        }

        public int getStepValue() {
            return stepValue;
        }

        public void setStepValue(int stepValue) {
            this.stepValue = stepValue;
        }

        public int getHumValue() {
            return humValue;
        }

        public void setHumValue(int humValue) {
            this.humValue = humValue;
        }

        public int getPressureValue() {
            return pressureValue;
        }

        public void setPressureValue(int pressureValue) {
            this.pressureValue = pressureValue;
        }

        public int getPowerValue() {
            return powerValue;
        }

        public void setPowerValue(int powerValue) {
            this.powerValue = powerValue;
        }

        public int getGyroX() {
            return gyroX;
        }

        public void setGyroX(int gyroX) {
            this.gyroX = gyroX;
        }

        public int getGyroY() {
            return gyroY;
        }

        public void setGyroY(int gyroY) {
            this.gyroY = gyroY;
        }

        public int getGyroZ() {
            return gyroZ;
        }

        public void setGyroZ(int gyroZ) {
            this.gyroZ = gyroZ;
        }

        public int getPetPose() {
            return petPose;
        }

        public void setPetPose(int petPose) {
            this.petPose = petPose;
        }

        @Override
        public String toString() {
            return "HealthData{" +
                    "timestamp=" + timestamp +
                    ", isWearing=" + isWearing +
                    ", hrValue=" + hrValue +
                    ", brValue=" + brValue +
                    ", rriValue=" + rriValue +
                    ", tempValue=" + tempValue +
                    ", stepValue=" + stepValue +
                    ", humValue=" + humValue +
                    ", pressureValue=" + pressureValue +
                    ", powerValue=" + powerValue +
                    ", gyro=(" + gyroX + "," + gyroY + "," + gyroZ + ")" +
                    ", petPose=" + petPose +
                    '}';
        }
    }
}
