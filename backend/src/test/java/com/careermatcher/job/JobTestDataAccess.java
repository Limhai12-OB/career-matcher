package com.careermatcher.job;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Public bridge to the package-private job test data builder. */
public final class JobTestDataAccess {
  private JobTestDataAccess() {}

  public static String job(JdbcTemplate jdbc, Map<String, String> overrides) {
    return JobTestData.job(jdbc, overrides);
  }
}
