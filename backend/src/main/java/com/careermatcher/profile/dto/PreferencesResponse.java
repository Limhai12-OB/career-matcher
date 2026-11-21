package com.careermatcher.profile.dto;

import com.careermatcher.profile.EmploymentType;
import com.careermatcher.profile.RemotePreference;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PreferencesResponse(
    List<String> desiredRoles,
    List<String> preferredLocations,
    List<EmploymentType> employmentTypes,
    RemotePreference remotePreference,
    boolean willingToRelocate,
    Integer expectedMinSalary,
    String salaryCurrency,
    LocalDate availableFrom,
    Instant updatedAt) {}
