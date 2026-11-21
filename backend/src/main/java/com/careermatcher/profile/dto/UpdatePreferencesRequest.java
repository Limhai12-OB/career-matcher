package com.careermatcher.profile.dto;

import com.careermatcher.common.validation.TextRules;
import com.careermatcher.profile.EmploymentType;
import com.careermatcher.profile.RemotePreference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record UpdatePreferencesRequest(
    @Size(max = 10, message = "At most 10 roles")
        List<
                @NotBlank(message = "Role must not be blank")
                @Size(max = 80, message = "Role must be at most 80 characters")
                @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters")
                String>
            desiredRoles,
    @Size(max = 10, message = "At most 10 locations")
        List<
                @NotBlank(message = "Location must not be blank")
                @Size(max = 100, message = "Location must be at most 100 characters")
                @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters")
                String>
            preferredLocations,
    @Size(max = 4) Set<@NotNull EmploymentType> employmentTypes,
    RemotePreference remotePreference,
    Boolean willingToRelocate,
    @PositiveOrZero(message = "Salary must be zero or positive")
        @Max(value = 100_000_000, message = "Salary is out of range")
        Integer expectedMinSalary,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Use a three-letter ISO currency code")
        String salaryCurrency,
    LocalDate availableFrom) {}
