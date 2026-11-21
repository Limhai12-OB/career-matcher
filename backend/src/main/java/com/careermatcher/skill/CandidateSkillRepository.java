package com.careermatcher.skill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CandidateSkillRepository extends JpaRepository<CandidateSkill, UUID> {

  List<CandidateSkill> findByUserId(UUID userId);

  Optional<CandidateSkill> findByUserIdAndSkillId(UUID userId, UUID skillId);
}
