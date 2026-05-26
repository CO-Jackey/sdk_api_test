package com.amicopet.health_decoder_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "多包連續解碼回應")
public class DecodeMultiResponse {

    @Schema(description = "是否成功", example = "true")
    private boolean success;

    @Schema(description = "訊息說明", example = "解碼成功（共送入 32 包）")
    private String message;

    @Schema(description = "實際送入 SDK 的包數", example = "32")
    private int processedCount;

    @Schema(description = "最後一包解碼後的生理數據")
    private DecodeResponse.HealthData data;

    public DecodeMultiResponse(boolean success, String message, int processedCount, DecodeResponse.HealthData data) {
        this.success = success;
        this.message = message;
        this.processedCount = processedCount;
        this.data = data;
    }

    public DecodeMultiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getProcessedCount() { return processedCount; }
    public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }

    public DecodeResponse.HealthData getData() { return data; }
    public void setData(DecodeResponse.HealthData data) { this.data = data; }
}
