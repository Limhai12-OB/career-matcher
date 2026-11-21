package com.careermatcher.skill;

import com.careermatcher.common.error.ApiException;
import com.careermatcher.skill.dto.CandidateSkillResponse;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The candidate's confirmed skill set. All operations are scoped to the owning user. */
@Service
public class CandidateSkillService {

  /** A skill confirmed from a CV analysis, with the quote that supports it. */
  public record ConfirmedSkill(UUID skillId, String evidence) {}

  private final CandidateSkillRepository repository;
  private final SkillNormalizer catalog;
  private final Clock clock;

  public CandidateSkillService(
      CandidateSkillRepository repository, SkillNormalizer catalog, Clock clock) {
    this.repository = repository;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<CandidateSkillResponse> list(UUID userId) {
    return repository.findByUserId(userId).stream()
        .map(this::toResponse)
        .sorted(
            Comparator.comparing(CandidateSkillResponse::category)
                .thenComparing(CandidateSkillResponse::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /** Adds (or refreshes) CV-sourced skills. Manually added skills are left untouched. */
  @Transactional
  public void addFromAnalysis(UUID userId, UUID analysisId, List<ConfirmedSkill> confirmed) {
    for (ConfirmedSkill c : confirmed) {
      repository
          .findByUserIdAndSkillId(userId, c.skillId())
          .ifPresentOrElse(
              existing -> existing.refreshFromCv(analysisId, c.evidence()),
              () ->
                  repository.save(
                      new CandidateSkill(
                          userId,
                          c.skillId(),
                          CandidateSkill.Source.CV,
                          analysisId,
                          c.evidence(),
                          clock.instant())));
    }
  }

  @Transactional
  public void remove(UUID userId, UUID skillId) {
    CandidateSkill skill =
        repository
            .findByUserIdAndSkillId(userId, skillId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "Skill not found in your profile."));
    repository.delete(skill);
  }

  private CandidateSkillResponse toResponse(CandidateSkill s) {
    SkillRef ref =
        catalog.byId(s.getSkillId()).orElseThrow(() -> new IllegalStateException("Unknown skill"));
    return new CandidateSkillResponse(
        ref.id(),
        ref.name(),
        ref.category(),
        s.getSource().name(),
        s.getAnalysisId(),
        s.getEvidence(),
        s.getCreatedAt());
  }
}
