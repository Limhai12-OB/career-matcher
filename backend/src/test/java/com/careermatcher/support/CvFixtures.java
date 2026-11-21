package com.careermatcher.support;

import com.careermatcher.aiclient.dto.AiCvAnalysis;
import com.careermatcher.aiclient.dto.AiCvAnalysis.DroppedItems;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CvFixtures {

  private CvFixtures() {}

  public static byte[] pdf() {
    return "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << >>\n%%EOF\n"
        .getBytes(StandardCharsets.ISO_8859_1);
  }

  public static byte[] docx() {
    return zip(
        Map.of(
            "[Content_Types].xml", "<Types/>",
            "word/document.xml", "<w:document><w:body><w:p><w:r><w:t>CV</w:t></w:r></w:p></w:body></w:document>"));
  }

  public static byte[] zip(Map<String, String> entries) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out)) {
      for (var e : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      zip.finish();
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static byte[] filled(int size, byte value) {
    byte[] b = new byte[size];
    Arrays.fill(b, value);
    byte[] header = pdf();
    System.arraycopy(header, 0, b, 0, Math.min(header.length, size));
    return b;
  }

  public static AiCvAnalysis.Skill skill(String name, String category, String evidence) {
    return new AiCvAnalysis.Skill(name, AiCvAnalysis.SkillCategory.valueOf(category), evidence);
  }

  /** A realistic, valid AI analysis result. */
  public static AiCvAnalysis analysis() {
    return analysis(
        List.of(
            skill("Java 17", "PROGRAMMING_LANGUAGE", "Java 17, Spring Boot, PostgreSQL"),
            skill("Spring Boot", "FRAMEWORK_LIBRARY", "Java 17, Spring Boot, PostgreSQL"),
            skill("ReactJS", "FRAMEWORK_LIBRARY", "a ReactJS web app"),
            skill("java", "PROGRAMMING_LANGUAGE", "Built REST APIs with java"),
            skill("Quantum Basket Weaving", "OTHER", "Quantum Basket Weaving club")));
  }

  public static AiCvAnalysis analysis(List<AiCvAnalysis.Skill> skills) {
    return new AiCvAnalysis(
        "cv-analysis-v1",
        "fake",
        "fake-model",
        "cv-extract-v1",
        new AiCvAnalysis.Document(AiCvAnalysis.DocType.PDF, 1, 1200),
        skills,
        List.of(
            new AiCvAnalysis.Education(
                "Royal University of Phnom Penh", "Bachelor of Science", "Computer Science",
                AiCvAnalysis.EducationLevel.BACHELOR, "2021", "2025", false,
                "Bachelor of Science in Computer Science, Royal University of Phnom Penh")),
        List.of(
            new AiCvAnalysis.Experience(
                "Backend Intern", "ABC Software Co.", AiCvAnalysis.ExperienceType.INTERNSHIP,
                "2024-06", "2024-12", false, List.of("Java"), "Backend Intern, ABC Software Co.")),
        List.of(new AiCvAnalysis.Project("Campus Events App", List.of("ReactJS"), "Campus Events App")),
        List.of(),
        new DroppedItems(1, 0, 0, 0, 0),
        List.of(AiCvAnalysis.Warning.UNVERIFIED_ITEMS_REMOVED));
  }
}
