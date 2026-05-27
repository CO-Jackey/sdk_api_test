package com.amicopet.health_decoder_api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI healthDecoderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Health Decoder API")
                        .description(
                                "ITRI SDK 生理數據解碼服務。\n\n" +
                                "接收藍牙裝置的原始 Base64 封包，透過 ITRI SDK 解析後，" +
                                "回傳心率、呼吸率、步數、溫度、濕度等生理數據。\n\n" +
                                "**Swagger UI：** `http://ipetsdk-g9fkcdb8etd9bbdg.japaneast-01.azurewebsites.net/swagger-ui/index.html`\n\n" +
                                "**OpenAPI JSON：** `http://ipetsdk-g9fkcdb8etd9bbdg.japaneast-01.azurewebsites.net/v3/api-docs`"
                        )
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AmicoiPet 開發團隊")
                        )
                );
    }
}
