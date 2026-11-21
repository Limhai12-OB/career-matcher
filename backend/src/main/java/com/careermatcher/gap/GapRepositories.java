package com.careermatcher.gap;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

final class GapRepositories {
  private GapRepositories() {}
}

interface SkillResourceRepository extends JpaRepository<SkillResource, UUID> {
  List<SkillResource> findBySkillIdInOrderByResourceTypeAscTitleAsc(Collection<UUID> skillIds);
}

interface SkillGapReportRepository extends JpaRepository<SkillGapReportEntity, UUID> {
  Optional<SkillGapReportEntity> findByIdAndUserId(UUID id, UUID userId);

  List<SkillGapReportEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<SkillGapReportEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<SkillGapReportEntity> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);
}
