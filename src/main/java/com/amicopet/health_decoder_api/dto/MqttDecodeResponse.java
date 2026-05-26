package com.amicopet.health_decoder_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MQTT 解碼回應")
public class MqttDecodeResponse {

    @Schema(description = "解碼是否成功", example = "true")
    private boolean success;

    @Schema(description = "訊息說明", example = "解碼並送出成功")
    private String message;

    @Schema(description = "是否成功轉送到 .NET API", example = "true")
    private boolean forwardSuccess;

    @Schema(description = "解碼後的生理數據，失敗時為 null")
    private DecodeResponse.HealthData data;

    public MqttDecodeResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.forwardSuccess = false;
    }

    public MqttDecodeResponse(boolean success, String message, boolean forwardSuccess, DecodeResponse.HealthData data) {
        this.success = success;
        this.message = message;
        this.forwardSuccess = forwardSuccess;
        this.data = data;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isForwardSuccess() { return forwardSuccess; }
    public void setForwardSuccess(boolean forwardSuccess) { this.forwardSuccess = forwardSuccess; }

    public DecodeResponse.HealthData getData() { return data; }
    public void setData(DecodeResponse.HealthData data) { this.data = data; }
}
