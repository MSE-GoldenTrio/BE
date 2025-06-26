package com.example.iplan.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
@Getter
@Setter
public class GoogleConfig {
    private String credentials;
}
