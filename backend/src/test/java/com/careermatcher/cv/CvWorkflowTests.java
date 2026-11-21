package com.careermatcher.cv;

import static com.careermatcher.support.TestApi.PASSWORD;
import static com.careermatcher.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.aiclient.AiAnalysisException;
import com.careermatcher.support.CvFixtures;
import com.careermatcher.support.CvTestBase;
import com.careermatcher.support.TestApi.Session;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class CvWorkflowTests extends CvTestBase {

  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;
  @Autowired CvAnalysisSweeper sweeper;

  @BeforeEach
  void defaultAi() {
    reset(aiClient);
    when(aiClient.analyze(any(), any())).thenReturn(CvFixtures.analysis());
  }

  private String storageKey(UUID analysisId) {
    return jdbc.queryForObject(
        "select d.storage_key from cv_documents d join cv_analyses a on a.document_id = d.id where a.id = ?",
        String.class,
        analysisId);
  }

  // ---------------------------------------------------------------- happy path

  @Test
  void pdfUploadIsAnalyzedNormalizedAndStoredWithEvidence() throws Exception {
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "My CV.pdf", CvFixtures.pdf());
    String detail = awaitTerminal(s.accessToken(), id);

    assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("COMPLETED");
    assertThat((String) JsonPath.read(detail, "$.documentFilename")).isEqualTo("My CV.pdf");
    List<String> canonical = JsonPath.read(detail, "$.skills[*].canonicalName");
    List<String> raw = JsonPath.read(detail, "$.skills[*].rawName");
    // "Java 17" and "java" both normalize to Java (deduplicated); ReactJS -> React; unknown kept.
    assertThat(raw).containsExactly("Java 17", "Spring Boot", "ReactJS", "Quantum Basket Weaving");
    assertThat(canonical).containsExactly("Java", "Spring Boot", "React", null);
    List<String> evidence = JsonPath.read(detail, "$.skills[*].evidence");
    assertThat(evidence).allSatisfy(e -> assertThat(e).isNotBlank());
    assertThat((String) JsonPath.read(detail, "$.content.education[0].institution"))
        .isEqualTo("Royal University of Phnom Penh");
    assertThat((String) JsonPath.read(detail, "$.content.experience[0].employmentType"))
        .isEqualTo("INTERNSHIP");
    assertThat((Integer) JsonPath.read(detail, "$.content.droppedItems.skills")).isEqualTo(1);
    assertThat((List<String>) JsonPath.read(detail, "$.warnings")).contains("UNVERIFIED_ITEMS_REMOVED");
    assertThat((String) JsonPath.read(detail, "$.provider")).isEqualTo("fake");
  }

  @Test
  void docxUploadIsAccepted() throws Exception {
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.docx", CvFixtures.docx());
    assertThat((String) JsonPath.read(awaitTerminal(s.accessToken(), id), "$.status")).isEqualTo("COMPLETED");
  }

  @Test
  void storedFileIsEncryptedAndDownloadReturnsOriginal() throws Exception {
    Session s = api.register();
    byte[] original = CvFixtures.pdf();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", original);
    awaitTerminal(s.accessToken(), id);

    byte[] raw = s3.getObjectAsBytes(b -> b.bucket("test-cvs").key(storageKey(id))).asByteArray();
    assertThat(raw).isNotEqualTo(original);
    assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");

    String docId = jdbc.queryForObject("select document_id::text from cv_analyses where id = ?", String.class, id);
    api.get(s.accessToken(), "/api/v1/me/cvs/" + docId + "/file")
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().bytes(original));
  }

  @Test
  void storageKeyContainsNoUserSuppliedName() throws Exception {
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "../../etc/passwd.pdf", CvFixtures.pdf());
    awaitTerminal(s.accessToken(), id);
    assertThat(storageKey(id)).startsWith("cv/" + s.userId() + "/").doesNotContain("passwd");
    String name =
        jdbc.queryForObject(
            "select original_filename from cv_documents d join cv_analyses a on a.document_id = d.id where a.id = ?",
            String.class,
            id);
    assertThat(name).isEqualTo("passwd.pdf");
  }

  // ---------------------------------------------------------------- upload validation

  @Test
  void invalidUploadsAreRejectedBeforeStorageOrAi() throws Exception {
    Session s = api.register();
    String t = s.accessToken();
    upload(t, "cv.pdf", new byte[0]).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    upload(t, "cv.txt", "plain text".getBytes())
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    upload(t, "cv.exe", new byte[] {'M', 'Z', 0, 0}).andExpect(status().isUnsupportedMediaType());
    upload(t, "cv.pdf", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));
    upload(t, "cv.docx", CvFixtures.pdf())
        .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));
    upload(t, "cv.docx", CvFixtures.zip(Map.of("readme.txt", "x")))
        .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));
    upload(
            t,
            "cv.docx",
            CvFixtures.zip(
                Map.of("[Content_Types].xml", "<Types/>", "word/document.xml", "<d/>", "word/vbaProject.bin", "m")))
        .andExpect(jsonPath("$.code").value("MACROS_NOT_ALLOWED"));
    // A truncated archive exposes no Word parts: rejected as not-a-docx.
    upload(t, "cv.docx", new byte[] {'P', 'K', 3, 4, 1, 2, 3})
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));
    upload(t, "cv.pdf", CvFixtures.filled(5 * 1024 * 1024 + 1, (byte) 'a'))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    upload(t, "cv.pdf", CvFixtures.filled(7 * 1024 * 1024, (byte) 'a')).andExpect(status().isContentTooLarge());

    verify(aiClient, never()).analyze(any(), any());
    assertThat(jdbc.queryForObject("select count(*) from cv_documents where user_id = ?", Integer.class, s.userId()))
        .isZero();
  }

  @Test
  void uploadRequiresAuthentication() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/me/cvs")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "cv.pdf", null, CvFixtures.pdf())))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------- AI / document failures

  @Test
  void aiProviderFailureMarksAnalysisFailedWithoutPersistingData() throws Exception {
    when(aiClient.analyze(any(), any())).thenThrow(new AiAnalysisException("AI_UNAVAILABLE", "down"));
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    String detail = awaitTerminal(s.accessToken(), id);
    assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("FAILED");
    assertThat((String) JsonPath.read(detail, "$.errorCode")).isEqualTo("AI_UNAVAILABLE");
    assertThat((List<?>) JsonPath.read(detail, "$.skills")).isEmpty();
    assertThat((Object) JsonPath.read(detail, "$.content")).isNull();
  }

  @Test
  void documentErrorsFromAiServiceAreRecorded() throws Exception {
    for (String code : List.of("NO_EXTRACTABLE_TEXT", "ENCRYPTED_DOCUMENT", "AI_INVALID_OUTPUT", "AI_TIMEOUT")) {
      doThrow(new AiAnalysisException(code, "x")).when(aiClient).analyze(any(), any());
      Session s = api.register();
      UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
      assertThat((String) JsonPath.read(awaitTerminal(s.accessToken(), id), "$.errorCode")).isEqualTo(code);
    }
  }

  @Test
  void unknownErrorCodesAreNormalized() {
    assertThat(new AiAnalysisException("SOMETHING_ODD", "x").getCode()).isEqualTo("AI_UNAVAILABLE");
  }

  // ---------------------------------------------------------------- history, concurrency

  @Test
  void reanalysisKeepsHistory() throws Exception {
    Session s = api.register();
    UUID first = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    awaitTerminal(s.accessToken(), first);
    String docId = jdbc.queryForObject("select document_id::text from cv_analyses where id = ?", String.class, first);

    String body =
        api.post(s.accessToken(), "/api/v1/me/cvs/" + docId + "/analyses", null)
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
    UUID second = UUID.fromString(JsonPath.read(body, "$.id"));
    awaitTerminal(s.accessToken(), second);

    api.get(s.accessToken(), "/api/v1/me/cvs")
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].analyses.length()").value(2))
        .andExpect(jsonPath("$[0].analyses[0].id").value(second.toString()));
    api.get(s.accessToken(), "/api/v1/me/cv-analyses")
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void onlyOneAnalysisMayRunPerUser() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    when(aiClient.analyze(any(), any()))
        .thenAnswer(
            inv -> {
              release.await(10, TimeUnit.SECONDS);
              return CvFixtures.analysis();
            });
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    upload(s.accessToken(), "cv2.pdf", CvFixtures.pdf())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ANALYSIS_IN_PROGRESS"));
    release.countDown();
    awaitTerminal(s.accessToken(), id);
    uploadAccepted(s.accessToken(), "cv2.pdf", CvFixtures.pdf());
  }

  @Test
  void staleAndInterruptedAnalysesAreFailed() throws Exception {
    Session s = api.register();
    UUID docId = UUID.randomUUID();
    jdbc.update(
        "insert into cv_documents (id, user_id, storage_key, original_filename, document_type, size_bytes, sha256, created_at)"
            + " values (?, ?, ?, 'cv.pdf', 'PDF', 10, ?, now())",
        docId, s.userId(), "cv/" + s.userId() + "/" + UUID.randomUUID(), "a".repeat(64));
    UUID stale = UUID.randomUUID();
    jdbc.update(
        "insert into cv_analyses (id, document_id, user_id, status, created_at) values (?, ?, ?, 'PROCESSING', now() - interval '1 hour')",
        stale, docId, s.userId());
    sweeper.failStale();
    assertThat(jdbc.queryForObject("select error_code from cv_analyses where id = ?", String.class, stale))
        .isEqualTo("STALE");
  }

  // ---------------------------------------------------------------- confirming skills

  @Test
  void recognizedSkillsCanBeConfirmedIntoTheProfile() throws Exception {
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    String detail = awaitTerminal(s.accessToken(), id);
    List<String> skillIds = JsonPath.read(detail, "$.skills[?(@.skillId != null)].skillId");
    assertThat(skillIds).hasSize(3);

    api.post(s.accessToken(), "/api/v1/me/cv-analyses/" + id + "/apply",
            json("{'skillIds':['%s','%s']}", skillIds.get(0), skillIds.get(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].name", hasItem("Java")))
        .andExpect(jsonPath("$[0].source").value("CV"));

    api.get(s.accessToken(), "/api/v1/me/cv-analyses/" + id)
        .andExpect(jsonPath("$.appliedAt").isNotEmpty())
        .andExpect(jsonPath("$.skills[0].inProfile").value(true))
        .andExpect(jsonPath("$.skills[2].inProfile").value(false));

    api.delete(s.accessToken(), "/api/v1/me/skills/" + skillIds.get(0), "{}").andExpect(status().isNoContent());
    api.get(s.accessToken(), "/api/v1/me/skills").andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void onlySkillsFromTheAnalysisCanBeConfirmed() throws Exception {
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    awaitTerminal(s.accessToken(), id);
    String kubernetes = jdbc.queryForObject("select id::text from skills where slug = 'kubernetes'", String.class);
    api.post(s.accessToken(), "/api/v1/me/cv-analyses/" + id + "/apply", json("{'skillIds':['%s']}", kubernetes))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_SKILL"));
  }

  @Test
  void failedAnalysesCannotBeApplied() throws Exception {
    when(aiClient.analyze(any(), any())).thenThrow(new AiAnalysisException("AI_REFUSED", "x"));
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    awaitTerminal(s.accessToken(), id);
    api.post(s.accessToken(), "/api/v1/me/cv-analyses/" + id + "/apply", json("{'skillIds':[]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_COMPLETED"));
  }

  // ---------------------------------------------------------------- deletion

  @Test
  void deletingACvRemovesFileAndAnalysesButKeepsConfirmedSkills() throws Exception {
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    String detail = awaitTerminal(s.accessToken(), id);
    String key = storageKey(id);
    String docId = JsonPath.read(detail, "$.documentId");
    List<String> skillIds = JsonPath.read(detail, "$.skills[?(@.skillId != null)].skillId");
    api.post(s.accessToken(), "/api/v1/me/cv-analyses/" + id + "/apply", json("{'skillIds':['%s']}", skillIds.get(0)))
        .andExpect(status().isOk());

    api.delete(s.accessToken(), "/api/v1/me/cvs/" + docId, "{}").andExpect(status().isNoContent());

    assertThatThrownBy(() -> s3.headObject(b -> b.bucket("test-cvs").key(key)))
        .isInstanceOf(NoSuchKeyException.class);
    api.get(s.accessToken(), "/api/v1/me/cv-analyses/" + id).andExpect(status().isNotFound());
    api.get(s.accessToken(), "/api/v1/me/skills")
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].analysisId").doesNotExist());
  }

  @Test
  void deletingTheAccountRemovesStoredFiles() throws Exception {
    Session s = api.register();
    UUID id = uploadAccepted(s.accessToken(), "cv.pdf", CvFixtures.pdf());
    awaitTerminal(s.accessToken(), id);
    String key = storageKey(id);
    api.delete(s.accessToken(), "/api/v1/me", json("{'password':'%s'}", PASSWORD)).andExpect(status().isNoContent());
    assertThatThrownBy(() -> s3.headObject(b -> b.bucket("test-cvs").key(key)))
        .isInstanceOf(NoSuchKeyException.class);
    assertThat(jdbc.queryForObject("select count(*) from cv_analyses where id = ?", Integer.class, id)).isZero();
  }
}
