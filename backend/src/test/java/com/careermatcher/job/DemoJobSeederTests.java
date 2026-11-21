package com.careermatcher.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "app.jobs.demo-data-enabled=true")
class DemoJobSeederTests {

  @Autowired JdbcTemplate jdbc;
  @Autowired DemoJobSeeder seeder;
  @Autowired TestApi api;

  @Test
  void seedsClearlyMarkedDemoListingsOnceAtStartup() throws Exception {
    String dataset = "source_name = 'AI Career Matcher demo dataset'";
    assertThat(jdbc.queryForObject("select count(*) from jobs where " + dataset, Integer.class)).isEqualTo(20);
    assertThat(jdbc.queryForList("select data_origin from jobs where " + dataset, String.class)).containsOnly("DEMO");
    assertThat(jdbc.queryForList("select application_url from jobs where " + dataset, String.class))
        .allMatch(u -> u.startsWith("https://careers.example.com/demo/"));
    assertThat(jdbc.queryForList("select company_name from jobs where " + dataset, String.class))
        .allMatch(c -> c.endsWith("(Demo)"));
    assertThat(jdbc.queryForList("select description from jobs where " + dataset, String.class))
        .allMatch(d -> d.contains("not a real job opening"));
    assertThat(jdbc.queryForList("select status from jobs where " + dataset, String.class)).containsOnly("PUBLISHED");
    assertThat(jdbc.queryForObject(
            "select count(distinct job_id) from job_skills js join jobs j on j.id = js.job_id"
                + " where js.requirement = 'REQUIRED' and j." + dataset,
            Integer.class)).isEqualTo(20);

    assertThat(seeder.seed()).isZero(); // idempotent

    var candidate = api.register();
    api.get(candidate.accessToken(), "/api/v1/jobs?q=(Demo)&origin=DEMO&size=50")
        .andExpect(jsonPath("$.page.totalElements").value(20))
        .andExpect(jsonPath("$.content[0].dataOrigin").value("DEMO"));
  }
}
