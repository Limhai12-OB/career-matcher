package com.careermatcher.skill;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SkillRepository extends JpaRepository<Skill, UUID> {}
