package com.example.iplan.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Configuration
public class IPlanOpenApiConfig {

    static {
        // `@AuthenticationPrincipal` 어노테이션이 있는 매개변수 무시
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticationPrincipal.class);
    }

    @Bean
    public OpenAPI openAPI(){

        Info info = new Info()
                .version("v1.0.0")
                .title("iPlan API")
                .description("주도적인 아이를 위한 계획 도우미");

        return new OpenAPI()
                .info(info)
                .components(getComponents())
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /**
     * Swagger 문서에 Authorization 헤더가 표시되도록 구성
     * @return
     */
    private Components getComponents(){
        return new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
