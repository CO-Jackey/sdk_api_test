package com.amicopet.health_decoder_api.dto;

public class DecodeResponse {
    private boolean success;
    private String message;
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

    public static class HealthData {
        private long timestamp;
        private boolean isWearing;
        private int hrValue;
        private int brValue;
        private int rriValue;
        private float tempValue;
        private int stepValue;
        private int humValue;
        private int pressureValue;
        private int powerValue;
        private int gyroX, gyroY, gyroZ;
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
    }
}