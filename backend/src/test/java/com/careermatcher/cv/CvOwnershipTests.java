package com.careermatcher.cv;

import static com.careermatcher.support.TestApi.json;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.CvFixtures;
import com.careermatcher.support.CvTestBase;
import com.careermatcher.support.TestApi.Session;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** One candidate can never read, download, change or delete another candidate's CV data. */
class CvOwnershipTests extends CvTestBase {

  @Test
  void anotherUsersCvAndAnalysesAreInvisible() throws Exception {
    when(aiClient.analyze(any(), any())).thenReturn(CvFixtures.analysis());
    Session alice = api.register();
    Session bob = api.register();
    UUID analysis = uploadAccepted(alice.accessToken(), "alice.pdf", CvFixtures.pdf());
    String detail = awaitTerminal(alice.accessToken(), analysis);
    String doc = JsonPath.read(detail, "$.documentId");
    String skillId = JsonPath.read(detail, "$.skills[0].skillId");
    String b = bob.accessToken();

    api.get(b, "/api/v1/me/cv-analyses/" + analysis)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    api.get(b, "/api/v1/me/cvs/" + doc + "/file").andExpect(status().isNotFound());
    api.post(b, "/api/v1/me/cvs/" + doc + "/analyses", null).andExpect(status().isNotFound());
    api.post(b, "/api/v1/me/cv-analyses/" + analysis + "/apply", json("{'skillIds':['%s']}", skillId))
        .andExpect(status().isNotFound());
    api.delete(b, "/api/v1/me/cvs/" + doc, "{}").andExpect(status().isNotFound());
    api.get(b, "/api/v1/me/cvs").andExpect(jsonPath("$.length()").value(0));
    api.get(b, "/api/v1/me/cv-analyses").andExpect(jsonPath("$.content.length()").value(0));

    // Alice's data is untouched.
    api.get(alice.accessToken(), "/api/v1/me/cv-analyses/" + analysis).andExpect(status().isOk());
    api.get(alice.accessToken(), "/api/v1/me/cvs/" + doc + "/file").andExpect(status().isOk());
  }
}
