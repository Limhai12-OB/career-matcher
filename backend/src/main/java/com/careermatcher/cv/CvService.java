package com.careermatcher.cv;

import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.ratelimit.RateLimitProperties;
import com.careermatcher.common.ratelimit.RateLimiter;
import com.careermatcher.cv.dto.AnalysisContent;
import com.careermatcher.cv.dto.AnalysisDetail;
import com.careermatcher.cv.dto.AnalysisSummary;
import com.careermatcher.cv.dto.CvDocumentResponse;
import com.careermatcher.skill.CandidateSkillService;
import com.careermatcher.skill.CandidateSkillService.ConfirmedSkill;
import com.careermatcher.skill.SkillNormalizer;
import com.careermatcher.skill.dto.CandidateSkillResponse;
import com.careermatcher.storage.ObjectStorage;
import com.careermatcher.storage.StorageException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * CV upload, analysis history and confirmation. Every method takes the owner's id (from the
 * authenticated principal) and scopes every lookup to it; other users' resources are reported as
 * not found.
 */
@Service
public class CvService {

  private static final Logger log = LoggerFactory.getLogger(CvService.class);
  private static final Set<AnalysisStatus> ACTIVE = Set.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING);

  /** Original file for download. */
  public record DownloadedFile(String filename, String mediaType, byte[] content) {}

  private final CvDocumentRepository documents;
  private final CvAnalysisRepository analyses;
  private final CvAnalysisSkillRepository analysisSkills;
  private final DocumentInspector inspector;
  private final ObjectStorage storage;
  private final CandidateSkillService candidateSkills;
  private final SkillNormalizer catalog;
  private final RateLimiter rateLimiter;
  private final RateLimitProperties limits;
  private final CvProperties props;
  private final ApplicationEventPublisher events;
  private final JsonMapper json;
  private final Clock clock;

  public CvService(
      CvDocumentRepository documents,
      CvAnalysisRepository analyses,
      CvAnalysisSkillRepository analysisSkills,
      DocumentInspector inspector,
      ObjectStorage storage,
      CandidateSkillService candidateSkills,
      SkillNormalizer catalog,
      RateLimiter rateLimiter,
      RateLimitProperties limits,
      CvProperties props,
      ApplicationEventPublisher events,
      JsonMapper json,
      Clock clock) {
    this.documents = documents;
    this.analyses = analyses;
    this.analysisSkills = analysisSkills;
    this.inspector = inspector;
    this.storage = storage;
    this.candidateSkills = candidateSkills;
    this.catalog = catalog;
    this.rateLimiter = rateLimiter;
    this.limits = limits;
    this.props = props;
    this.events = events;
    this.json = json;
    this.clock = clock;
  }

  // ------------------------------------------------------------------ upload / reanalyze

  @Transactional
  public AnalysisSummary upload(UUID userId, String originalFilename, byte[] content) {
    DocumentType type = inspector.inspect(content, originalFilename);
    rateLimiter.consume("cv-upload", userId.toString(), limits.cvUploadPerUser());
    ensureNoActiveAnalysis(userId);
    if (documents.countByUserId(userId) >= props.maxDocumentsPerUser()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CV_LIMIT_REACHED",
          "You can keep up to " + props.maxDocumentsPerUser() + " CVs. Delete one to upload another.");
    }

    CvDocument doc =
        new CvDocument(
            userId,
            DocumentInspector.sanitizeFilename(originalFilename, type),
            type,
            content.length,
            sha256(content),
            clock.instant());
    try {
      storage.put(doc.getStorageKey(), content);
    } catch (StorageException e) {
      log.error("CV storage failed", e);
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "File storage is unavailable. Try again later.");
    }
    // If the transaction rolls back, don't leave an orphaned object behind.
    deleteObjectOnRollback(doc.getStorageKey());
    documents.save(doc);
    return startAnalysis(doc);
  }

  @Transactional
  public AnalysisSummary reanalyze(UUID userId, UUID documentId) {
    CvDocument doc = document(userId, documentId);
    rateLimiter.consume("cv-upload", userId.toString(), limits.cvUploadPerUser());
    ensureNoActiveAnalysis(userId);
    return startAnalysis(doc);
  }

  private AnalysisSummary startAnalysis(CvDocument doc) {
    CvAnalysis analysis = analyses.save(new CvAnalysis(doc.getId(), doc.getUserId(), clock.instant()));
    events.publishEvent(new CvAnalysisRequested(analysis.getId()));
    return summary(analysis);
  }

  private void ensureNoActiveAnalysis(UUID userId) {
    if (analyses.existsByUserIdAndStatusIn(userId, ACTIVE)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ANALYSIS_IN_PROGRESS",
          "Please wait for your current CV analysis to finish.");
    }
  }

  // ------------------------------------------------------------------ queries

  @Transactional(readOnly = true)
  public List<CvDocumentResponse> listDocuments(UUID userId) {
    List<CvDocument> docs = documents.findByUserIdOrderByCreatedAtDesc(userId);
    Map<UUID, List<AnalysisSummary>> byDoc =
        analyses.findByDocumentIdInOrderByCreatedAtDesc(docs.stream().map(CvDocument::getId).toList()).stream()
            .map(CvService::summary)
            .collect(Collectors.groupingBy(AnalysisSummary::documentId, LinkedHashMap::new, Collectors.toList()));
    return docs.stream()
        .map(
            d ->
                new CvDocumentResponse(
                    d.getId(),
                    d.getOriginalFilename(),
                    d.getDocumentType(),
                    d.getSizeBytes(),
                    d.getCreatedAt(),
                    byDoc.getOrDefault(d.getId(), List.of())))
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<AnalysisSummary> listAnalyses(UUID userId, int page, int size) {
    return analyses
        .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
        .map(CvService::summary);
  }

  @Transactional(readOnly = true)
  public AnalysisDetail getAnalysis(UUID userId, UUID analysisId) {
    CvAnalysis a = analysis(userId, analysisId);
    CvDocument doc = documents.findById(a.getDocumentId()).orElseThrow();
    Set<UUID> inProfile =
        candidateSkills.list(userId).stream().map(CandidateSkillResponse::skillId).collect(Collectors.toSet());
    List<AnalysisDetail.ExtractedSkill> skills =
        analysisSkills.findByAnalysisIdOrderByPosition(a.getId()).stream()
            .map(
                s ->
                    new AnalysisDetail.ExtractedSkill(
                        s.getRawName(),
                        s.getSkillId(),
                        s.getSkillId() == null
                            ? null
                            : catalog.byId(s.getSkillId()).map(r -> r.name()).orElse(null),
                        s.getCategory(),
                        s.getEvidence(),
                        s.getSkillId() != null && inProfile.contains(s.getSkillId())))
            .toList();
    AnalysisContent content =
        a.getResult() == null ? null : json.readValue(a.getResult(), AnalysisContent.class);
    return new AnalysisDetail(
        a.getId(),
        a.getDocumentId(),
        doc.getOriginalFilename(),
        a.getStatus(),
        a.getErrorCode(),
        a.getProvider(),
        a.getModel(),
        a.getCreatedAt(),
        a.getCompletedAt(),
        a.getAppliedAt(),
        a.getWarnings(),
        skills,
        content);
  }

  @Transactional(readOnly = true)
  public DownloadedFile download(UUID userId, UUID documentId) {
    CvDocument doc = document(userId, documentId);
    try {
      return new DownloadedFile(
          doc.getOriginalFilename(), doc.getDocumentType().mediaType(), storage.get(doc.getStorageKey()));
    } catch (StorageException e) {
      log.error("CV download failed", e);
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "File storage is unavailable. Try again later.");
    }
  }

  /** Latest completed analysis of the user, for matching (validated content + warnings). */
  public record CompletedAnalysis(UUID analysisId, AnalysisContent content, List<String> warnings) {}

  @Transactional(readOnly = true)
  public java.util.Optional<CompletedAnalysis> latestCompleted(UUID userId) {
    return analyses
        .findFirstByUserIdAndStatusOrderByCompletedAtDesc(userId, AnalysisStatus.COMPLETED)
        .map(a -> new CompletedAnalysis(a.getId(), json.readValue(a.getResult(), AnalysisContent.class), a.getWarnings()));
  }

  // ------------------------------------------------------------------ confirm skills

  /**
   * Adds the selected recognized skills from a completed analysis to the candidate's profile.
   * Only skill ids that the analysis actually contains are accepted.
   */
  @Transactional
  public List<CandidateSkillResponse> applySkills(UUID userId, UUID analysisId, List<UUID> skillIds) {
    CvAnalysis a = analysis(userId, analysisId);
    if (a.getStatus() != AnalysisStatus.COMPLETED) {
      throw new ApiException(HttpStatus.CONFLICT, "ANALYSIS_NOT_COMPLETED", "This analysis has not completed.");
    }
    Map<UUID, String> available = new LinkedHashMap<>();
    analysisSkills.findByAnalysisIdOrderByPosition(a.getId()).stream()
        .filter(s -> s.getSkillId() != null)
        .forEach(s -> available.putIfAbsent(s.getSkillId(), s.getEvidence()));
    Set<UUID> requested = Set.copyOf(skillIds);
    if (!available.keySet().containsAll(requested)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "UNKNOWN_SKILL", "Some selected skills are not part of this analysis.");
    }
    candidateSkills.addFromAnalysis(
        userId,
        a.getId(),
        requested.stream().map(id -> new ConfirmedSkill(id, available.get(id))).toList());
    a.markApplied(clock.instant());
    return candidateSkills.list(userId);
  }

  // ------------------------------------------------------------------ deletion

  /** Deletes the CV, its analyses and the stored file. Confirmed skills are kept. */
  @Transactional
  public void deleteDocument(UUID userId, UUID documentId) {
    CvDocument doc = document(userId, documentId);
    documents.delete(doc); // analyses cascade in the database
    deleteObjectAfterCommit(List.of(doc.getStorageKey()));
  }

  /**
   * Must be called inside the account-deletion transaction: captures the user's stored files and
   * removes them once the deletion commits.
   */
  public void scheduleDeletionOfAllFiles(UUID userId) {
    deleteObjectAfterCommit(documents.findStorageKeysByUserId(userId));
  }

  private void deleteObjectAfterCommit(List<String> keys) {
    if (keys.isEmpty()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            for (String key : keys) {
              try {
                storage.delete(key);
              } catch (StorageException e) {
                // Logged for operator follow-up; the DB no longer references the object.
                log.error("Failed to delete stored CV object after commit", e);
              }
            }
          }
        });
  }

  private void deleteObjectOnRollback(String key) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
              try {
                storage.delete(key);
              } catch (StorageException e) {
                log.error("Failed to remove object after rollback", e);
              }
            }
          }
        });
  }

  // ------------------------------------------------------------------ helpers

  private CvDocument document(UUID userId, UUID documentId) {
    return documents
        .findByIdAndUserId(documentId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "CV not found."));
  }

  private CvAnalysis analysis(UUID userId, UUID analysisId) {
    return analyses
        .findByIdAndUserId(analysisId, userId)
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analysis not found."));
  }

  static AnalysisSummary summary(CvAnalysis a) {
    return new AnalysisSummary(
        a.getId(), a.getDocumentId(), a.getStatus(), a.getErrorCode(), a.getCreatedAt(),
        a.getCompletedAt(), a.getAppliedAt());
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
