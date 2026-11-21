package com.careermatcher.cv;

import com.careermatcher.aiclient.AiAnalysisException;
import com.careermatcher.aiclient.AiCvAnalysisClient;
import com.careermatcher.aiclient.dto.AiCvAnalysis;
import com.careermatcher.cv.dto.AnalysisContent;
import com.careermatcher.skill.SkillNormalizer;
import com.careermatcher.skill.SkillRef;
import com.careermatcher.storage.ObjectStorage;
import com.careermatcher.storage.StorageException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs CV analyses in the background (bounded pool) after the creating transaction commits. Each
 * step that can fail records a stable error code on the analysis; nothing from a failed or invalid
 * AI response is persisted.
 */
@Component
class CvAnalysisWorker {

  private static final Logger log = LoggerFactory.getLogger(CvAnalysisWorker.class);

  private final CvAnalysisRepository analyses;
  private final CvDocumentRepository documents;
  private final CvAnalysisSkillRepository analysisSkills;
  private final ObjectStorage storage;
  private final AiCvAnalysisClient aiClient;
  private final SkillNormalizer normalizer;
  private final TransactionTemplate tx;
  private final Executor executor;
  private final JsonMapper json;
  private final Clock clock;

  CvAnalysisWorker(
      CvAnalysisRepository analyses,
      CvDocumentRepository documents,
      CvAnalysisSkillRepository analysisSkills,
      ObjectStorage storage,
      AiCvAnalysisClient aiClient,
      SkillNormalizer normalizer,
      TransactionTemplate tx,
      @Qualifier("cvAnalysisExecutor") Executor executor,
      JsonMapper json,
      Clock clock) {
    this.analyses = analyses;
    this.documents = documents;
    this.analysisSkills = analysisSkills;
    this.storage = storage;
    this.aiClient = aiClient;
    this.normalizer = normalizer;
    this.tx = tx;
    this.executor = executor;
    this.json = json;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void onRequested(CvAnalysisRequested event) {
    try {
      executor.execute(() -> process(event.analysisId()));
    } catch (TaskRejectedException e) {
      log.warn("CV analysis queue full; rejecting analysis {}", event.analysisId());
      fail(event.analysisId(), "SERVICE_BUSY");
    }
  }

  void process(UUID analysisId) {
    Integer claimed = tx.execute(s -> analyses.claim(analysisId, clock.instant()));
    if (claimed == null || claimed == 0) {
      return; // already processed or swept
    }
    try {
      CvAnalysis analysis = analyses.findById(analysisId).orElseThrow();
      CvDocument doc = documents.findById(analysis.getDocumentId()).orElseThrow();
      byte[] content = storage.get(doc.getStorageKey());
      AiCvAnalysis result = aiClient.analyze(content, "cv" + doc.getDocumentType().extension());
      tx.executeWithoutResult(s -> persist(analysisId, result));
      log.info("CV analysis {} completed: {} skills", analysisId, result.skills().size());
    } catch (AiAnalysisException e) {
      fail(analysisId, e.getCode());
    } catch (StorageException e) {
      log.error("CV analysis {}: storage error", analysisId, e);
      fail(analysisId, "STORAGE_ERROR");
    } catch (RuntimeException e) {
      log.error("CV analysis {} failed unexpectedly", analysisId, e);
      fail(analysisId, "INTERNAL_ERROR");
    }
  }

  private void persist(UUID analysisId, AiCvAnalysis r) {
    CvAnalysis analysis = analyses.findById(analysisId).orElseThrow();
    if (analysis.getStatus() != AnalysisStatus.PROCESSING) {
      return; // swept as stale meanwhile; don't resurrect
    }
    int position = 0;
    Set<String> seen = new HashSet<>();
    for (AiCvAnalysis.Skill s : r.skills()) {
      Optional<SkillRef> ref = normalizer.resolve(s.name());
      String dedupeKey = ref.map(x -> x.id().toString()).orElse("raw:" + s.name().toLowerCase());
      if (!seen.add(dedupeKey)) {
        continue;
      }
      analysisSkills.save(
          new CvAnalysisSkill(
              analysisId,
              position++,
              s.name(),
              ref.map(SkillRef::id).orElse(null),
              ref.map(x -> x.category().name()).orElse(s.category().name()),
              s.evidence()));
    }
    AnalysisContent content =
        new AnalysisContent(
            new AnalysisContent.DocumentStats(
                r.document().type().name(), r.document().pages(), r.document().characters()),
            r.education().stream()
                .map(e -> new AnalysisContent.Education(
                    e.institution(), e.degree(), e.fieldOfStudy(), e.level().name(),
                    e.startDate(), e.endDate(), e.ongoing(), e.evidence()))
                .toList(),
            r.experience().stream()
                .map(x -> new AnalysisContent.Experience(
                    x.title(), x.organization(), x.employmentType().name(), x.startDate(),
                    x.endDate(), x.isCurrent(), List.copyOf(x.skills()), x.evidence()))
                .toList(),
            r.projects().stream()
                .map(p -> new AnalysisContent.Project(p.name(), List.copyOf(p.skills()), p.evidence()))
                .toList(),
            r.certifications().stream()
                .map(c -> new AnalysisContent.Certification(c.name(), c.issuer(), c.year(), c.evidence()))
                .toList(),
            new AnalysisContent.DroppedItems(
                r.droppedItems().skills(), r.droppedItems().education(), r.droppedItems().experience(),
                r.droppedItems().projects(), r.droppedItems().certifications()));
    analysis.complete(
        r.provider(),
        r.model(),
        r.promptVersion(),
        r.schemaVersion(),
        json.writeValueAsString(content),
        r.warnings().stream().map(Enum::name).toList(),
        clock.instant());
  }

  private void fail(UUID analysisId, String code) {
    try {
      tx.executeWithoutResult(
          s ->
              analyses
                  .findById(analysisId)
                  .filter(a -> !a.getStatus().isTerminal())
                  .ifPresent(a -> a.fail(code, clock.instant())));
      log.info("CV analysis {} failed: {}", analysisId, code);
    } catch (RuntimeException e) {
      log.error("Could not record failure of analysis {}", analysisId, e);
    }
  }
}
