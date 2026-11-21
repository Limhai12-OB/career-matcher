package com.careermatcher.job.dto;

import com.careermatcher.job.JobEnums.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusRequest(@NotNull JobStatus status) {}
