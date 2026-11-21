package com.careermatcher.cv;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analyses run in-process without a broker. If the backend restarts mid-analysis, or a run hangs,
 * the record would stay active forever and block new uploads; this marks such records FAILED so
 * the user can retry. Assumes a single backend instance (documented MVP constraint).
 */
@Component
class CvAnalysisSweeper {

  private static final Logger log = LoggerFactory.getLogger(CvAnalysisSweeper.class);

  private final CvAnalysisRepository analyses;
  private final CvProperties props;
  private final Clock clock;

  CvAnalysisSweeper(CvAnalysisRepository analyses, CvProperties props, Clock clock) {
    this.analyses = analyses;
    this.props = props;
    this.clock = clock;
  }

  /** On startup nothing can still be running in this process. */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void failInterrupted() {
    Instant now = clock.instant();
    int n = analyses.failActiveCreatedBefore(now, "INTERRUPTED", now);
    if (n > 0) {
      log.warn("Marked {} interrupted CV analyses as failed", n);
    }
  }

  @Scheduled(fixedDelayString = "${app.cv.sweep-interval:5m}")
  @Transactional
  public void failStale() {
    Instant now = clock.instant();
    int n = analyses.failActiveCreatedBefore(now.minus(props.staleAfter()), "STALE", now);
    if (n > 0) {
      log.warn("Marked {} stale CV analyses as failed", n);
    }
  }
}
