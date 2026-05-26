package com.amicopet.health_decoder_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "多包連續解碼請求（模擬裝置持續傳輸）")
public class DecodeMultiRequest {

    @Schema(
        description = "藍牙裝置的原始封包資料，支援 Hex 或 Base64 格式",
        example = "ffb2be386929f02144000000fb0b2f3d",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String rawData;

    @Schema(
        description = "動物種類。0=人類, 1=貓, 2=兔子, 3=狗（預設）",
        example = "3",
        allowableValues = {"0", "1", "2", "3"},
        defaultValue = "3"
    )
    private int animalType = 3;

    @Schema(
        description = "重複送入 SDK 的次數，模擬連續封包。建議 20~50 次以獲得穩定 HR/BR 數值",
        example = "32",
        minimum = "1",
        maximum = "200",
        defaultValue = "32"
    )
    private int repeatCount = 32;

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public int getAnimalType() { return animalType; }
    public void setAnimalType(int animalType) { this.animalType = animalType; }

    public int getRepeatCount() { return repeatCount; }
    public void setRepeatCount(int repeatCount) { this.repeatCount = repeatCount; }
}
