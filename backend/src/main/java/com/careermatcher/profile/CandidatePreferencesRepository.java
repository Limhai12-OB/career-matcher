package com.careermatcher.profile;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CandidatePreferencesRepository extends JpaRepository<CandidatePreferences, UUID> {}
