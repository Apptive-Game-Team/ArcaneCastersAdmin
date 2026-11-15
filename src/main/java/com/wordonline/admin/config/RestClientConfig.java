package com.wordonline.admin.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RestClientConfig {

    @Value("${word-online.jwt.path}")
    private String jwtFilePath;

    @Bean
    @Primary
    public RestClient.Builder builder() {
        return RestClient.builder().defaultHeader("Authorization", loadJwt());
    }

    @Bean
    public RestClient.Builder accountAuthRestClientBuilder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory);
    }

    private String loadJwt() {
        try {
            return Files.readString(Path.of(jwtFilePath)).trim();
        } catch (IOException e) {
            log.error("Failed to load jwt {}", jwtFilePath, e);
            throw new IllegalStateException("Failed to load JWT from " + jwtFilePath, e);
        }
    }
}
