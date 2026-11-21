package com.careermatcher.cv;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.cv")
public record CvProperties(
    @NotNull @DefaultValue("5MB") DataSize maxFileSize,
    @Min(1) @Max(200) @DefaultValue("20") int maxDocumentsPerUser,
    @Min(1) @Max(64) @DefaultValue("4") int maxConcurrentAnalyses,
    @Min(1) @DefaultValue("100") int queueCapacity,
    /** Analyses still pending/processing after this long are marked FAILED (STALE). */
    @NotNull @DefaultValue("15m") Duration staleAfter) {}
