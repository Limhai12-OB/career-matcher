package com.careermatcher.skill;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SkillCatalogTests {

  @Autowired TestApi api;

  @Test
  void searchesNamesAndAliases() throws Exception {
    String t = api.register().accessToken();
    api.get(t, "/api/v1/skills?q=react").andExpect(jsonPath("$[*].name", hasItems("React", "React Native")));
    api.get(t, "/api/v1/skills?q=k8s").andExpect(jsonPath("$[*].name", hasItem("Kubernetes")));
    api.get(t, "/api/v1/skills").andExpect(jsonPath("$.length()").value(114));
    api.get(null, "/api/v1/skills").andExpect(status().isUnauthorized());
  }
}
