package com.amicopet.health_decoder_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SDK 解碼請求")
public class DecodeRequest {

    @Schema(
        description = "藍牙裝置的原始封包資料，支援兩種格式：\n" +
                      "- **Hex 字串**（如 `ffb2be386929f02144000000fb0b2f3d`）\n" +
                      "- **Base64 字串**（如 `/7K+OGkp8CFEAAAA+wsvPQ==`）\n\n" +
                      "API 會自動偵測格式。",
        example = "ffb2be386929f02144000000fb0b2f3d",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String rawData;

    @Schema(
        description = "動物種類。0=人類, 1=貓, 2=兔子, 3=狗（預設）。超出範圍自動 fallback 為 3",
        example = "3",
        allowableValues = {"0", "1", "2", "3"},
        defaultValue = "3"
    )
    private int animalType;

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public int getAnimalType() {
        return animalType;
    }

    public void setAnimalType(int animalType) {
        this.animalType = animalType;
    }
}
