package com.careermatcher.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/** Fast checks on the bundled demo dataset, without starting Spring. */
class DemoDatasetTest {

  @Test
  void everyReferencedSkillExistsInTheCatalogMigration() throws Exception {
    String all = Files.readString(Path.of("src/main/resources/db/migration/V4__skill_catalog.sql"));
    // Only the skills insert block (aliases are not valid slugs).
    String sql = all.substring(all.indexOf("INSERT INTO skills"), all.indexOf("INSERT INTO skill_aliases"));
    Set<String> catalog = new HashSet<>();
    Matcher m = Pattern.compile("^\\s+\\('([a-z0-9-]+)', '", Pattern.MULTILINE).matcher(sql);
    while (m.find()) {
      catalog.add(m.group(1));
    }
    assertThat(catalog).hasSize(114);

    DemoJobSeeder.Dataset data;
    try (InputStream in = new ClassPathResource(DemoJobSeeder.RESOURCE).getInputStream()) {
      data = JsonMapper.builder().build().readValue(in, DemoJobSeeder.Dataset.class);
    }
    assertThat(data.notice()).contains("DEMONSTRATION DATA").contains("Not real job openings");
    assertThat(data.jobs()).hasSize(20);
    data.jobs().forEach(j -> {
      assertThat(catalog).as(j.slug()).containsAll(j.requiredSkills()).containsAll(j.preferredSkills());
      assertThat(j.requiredSkills()).as(j.slug()).isNotEmpty();
      assertThat(j.companyName()).as(j.slug()).endsWith("(Demo)");
      assertThat(j.description()).as(j.slug()).contains("not a real job opening");
    });
    assertThat(new String(Files.readAllBytes(Path.of("src/main/resources/" + DemoJobSeeder.RESOURCE)), StandardCharsets.UTF_8))
        .doesNotContainPattern("https://(?!([a-z0-9-]+\\.)*example\\.(com|org|net)/)");
  }
}
