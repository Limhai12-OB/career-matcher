package com.careermatcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import com.careermatcher.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BackendApplicationTests {

  @Autowired JdbcTemplate jdbc;

  @Test
  void flywayBaselineMigrationIsApplied() {
    List<String> versions =
        jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
            String.class);
    assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
  }

  @Test
  void requiredPostgresExtensionsAreInstalled() {
    List<String> extensions =
        jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
    assertThat(extensions).contains("citext", "pg_trgm");
  }
}
