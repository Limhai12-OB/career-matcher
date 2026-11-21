package com.careermatcher.cv;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DocumentInspectorTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "My CV.pdf|My CV.pdf",
        "../../etc/passwd.pdf|passwd.pdf",
        "C:\\Users\\me\\cv.docx|cv.docx",
        "bad<name>?.pdf|badname.pdf",
        "'   '|cv.pdf",
      })
  void sanitizesFilenames(String input, String expected) {
    DocumentType type = expected.endsWith(".docx") ? DocumentType.DOCX : DocumentType.PDF;
    assertThat(DocumentInspector.sanitizeFilename(input.strip(), type)).isEqualTo(expected);
  }

  @org.junit.jupiter.api.Test
  void truncatesLongNamesKeepingExtension() {
    String name = DocumentInspector.sanitizeFilename("a".repeat(300) + ".pdf", DocumentType.PDF);
    assertThat(name).hasSize(120).endsWith(".pdf");
  }
}
