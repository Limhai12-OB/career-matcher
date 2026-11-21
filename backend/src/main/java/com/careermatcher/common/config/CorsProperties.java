package com.careermatcher.common.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Browser origins allowed to call the REST API. */
@Validated
@ConfigurationProperties("app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {}
