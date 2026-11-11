package secondteam.iseonjwa.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme memberIdHeader = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(MEMBER_ID_HEADER);

        return new OpenAPI()
                .info(new Info()
                        .title("이선좌 API")
                        .description("공연 좌석 예매 시스템 API 문서")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(MEMBER_ID_HEADER, memberIdHeader))
                .addSecurityItem(new SecurityRequirement()
                        .addList(MEMBER_ID_HEADER));
    }
}