package com.careermatcher.profile.dto;

import com.careermatcher.common.validation.TextRules;
import com.careermatcher.profile.CareerStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
        @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters")
        String fullName,
    @Size(max = 160, message = "Headline must be at most 160 characters")
        @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters")
        String headline,
    @Size(max = 2000, message = "Bio must be at most 2000 characters")
        @Pattern(regexp = TextRules.MULTI_LINE, message = "Contains invalid characters")
        String bio,
    @Size(max = 100, message = "City must be at most 100 characters")
        @Pattern(regexp = TextRules.SINGLE_LINE, message = "Contains invalid characters")
        String city,
    @Pattern(regexp = "^[A-Z]{2}$", message = "Use a two-letter ISO country code")
        String countryCode,
    CareerStage careerStage,
    @Min(value = 1950, message = "Graduation year is out of range")
        @Max(value = 2100, message = "Graduation year is out of range")
        Integer graduationYear,
    @Size(max = 300, message = "URL is too long") String linkedinUrl,
    @Size(max = 300, message = "URL is too long") String githubUrl,
    @Size(max = 300, message = "URL is too long") String portfolioUrl) {}
