package com.careermatcher.cv;

import java.util.UUID;

/** Published inside the creating transaction; processing starts after commit. */
record CvAnalysisRequested(UUID analysisId) {}
