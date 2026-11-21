package com.careermatcher.matching;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

final class MatchRepositories {
  private MatchRepositories() {}
}

interface JobMatchRepository extends JpaRepository<JobMatch, UUID> {

  /** Inserts a result version unless an identical one exists (safe under concurrent requests). */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "insert into job_matches (id, user_id, job_id, algorithm_version, total_score, breakdown, weights,"
              + " cv_analysis_id, result_hash, computed_at) values (:id, :userId, :jobId, :version, :score,"
              + " cast(:breakdown as jsonb), cast(:weights as jsonb), :cvAnalysisId, :hash, :now)"
              + " on conflict (user_id, job_id, algorithm_version, result_hash) do nothing")
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("jobId") UUID jobId,
      @Param("version") String version,
      @Param("score") java.math.BigDecimal score,
      @Param("breakdown") String breakdown,
      @Param("weights") String weights,
      @Param("cvAnalysisId") UUID cvAnalysisId,
      @Param("hash") String hash,
      @Param("now") Instant now);

  Optional<JobMatch> findByUserIdAndJobIdAndAlgorithmVersionAndResultHash(
      UUID userId, UUID jobId, String algorithmVersion, String resultHash);

  /** Existing result versions of a user, as "jobId:hash" keys. */
  @Query("select concat(cast(m.jobId as string), ':', m.resultHash) from JobMatch m where m.userId = :userId and m.algorithmVersion = :version")
  List<String> findKeys(@Param("userId") UUID userId, @Param("version") String version);

  long countByUserIdAndJobId(UUID userId, UUID jobId);
}

interface MatchExplanationRepository extends JpaRepository<MatchExplanation, UUID> {

  Optional<MatchExplanation> findByMatchId(UUID matchId);

  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "insert into match_explanations (id, match_id, provider, model, prompt_version, content, created_at)"
              + " values (:id, :matchId, :provider, :model, :promptVersion, cast(:content as jsonb), :now)"
              + " on conflict (match_id) do nothing")
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("matchId") UUID matchId,
      @Param("provider") String provider,
      @Param("model") String model,
      @Param("promptVersion") String promptVersion,
      @Param("content") String content,
      @Param("now") Instant now);
}
