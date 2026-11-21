package com.careermatcher.cv;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositories of the CV module. Every user-facing query is scoped by owner id. */
final class Repositories {
  private Repositories() {}
}

interface CvDocumentRepository extends JpaRepository<CvDocument, UUID> {

  Optional<CvDocument> findByIdAndUserId(UUID id, UUID userId);

  List<CvDocument> findByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserId(UUID userId);

  @Query("select d.storageKey from CvDocument d where d.userId = :userId")
  List<String> findStorageKeysByUserId(@Param("userId") UUID userId);
}

interface CvAnalysisRepository extends JpaRepository<CvAnalysis, UUID> {

  Optional<CvAnalysis> findByIdAndUserId(UUID id, UUID userId);

  Page<CvAnalysis> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  List<CvAnalysis> findByDocumentIdInOrderByCreatedAtDesc(Collection<UUID> documentIds);

  boolean existsByUserIdAndStatusIn(UUID userId, Collection<AnalysisStatus> statuses);

  Optional<CvAnalysis> findFirstByUserIdAndStatusOrderByCompletedAtDesc(UUID userId, AnalysisStatus status);

  /** Atomically claims a pending analysis for processing; returns 1 if this caller won. */
  @Modifying
  @Query(
      "update CvAnalysis a set a.status = com.careermatcher.cv.AnalysisStatus.PROCESSING,"
          + " a.startedAt = :now, a.version = a.version + 1"
          + " where a.id = :id and a.status = com.careermatcher.cv.AnalysisStatus.PENDING")
  int claim(@Param("id") UUID id, @Param("now") Instant now);

  @Modifying
  @Query(
      "update CvAnalysis a set a.status = com.careermatcher.cv.AnalysisStatus.FAILED,"
          + " a.errorCode = :code, a.completedAt = :now, a.version = a.version + 1"
          + " where a.status in (com.careermatcher.cv.AnalysisStatus.PENDING,"
          + " com.careermatcher.cv.AnalysisStatus.PROCESSING) and a.createdAt < :before")
  int failActiveCreatedBefore(
      @Param("before") Instant before, @Param("code") String code, @Param("now") Instant now);
}

interface CvAnalysisSkillRepository extends JpaRepository<CvAnalysisSkill, UUID> {

  List<CvAnalysisSkill> findByAnalysisIdOrderByPosition(UUID analysisId);

  long countByAnalysisId(UUID analysisId);

  long countByAnalysisIdAndSkillIdIsNotNull(UUID analysisId);
}
