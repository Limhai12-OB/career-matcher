package com.careermatcher.cv;

import com.careermatcher.common.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * First-line validation of uploads: size, extension and actual content must agree. Content type
 * headers from the client are ignored. Deep parsing happens later, sandboxed in the AI service.
 */
@Component
class DocumentInspector {

  private static final int MAX_ZIP_ENTRIES = 1000;
  private static final long MAX_ZIP_UNCOMPRESSED = 50L * 1024 * 1024;

  private final CvProperties props;

  DocumentInspector(CvProperties props) {
    this.props = props;
  }

  DocumentType inspect(byte[] content, String filename) {
    if (content.length == 0) {
      throw error(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The file is empty.");
    }
    if (content.length > props.maxFileSize().toBytes()) {
      throw error(
          HttpStatus.CONTENT_TOO_LARGE,
          "FILE_TOO_LARGE",
          "The file is larger than " + props.maxFileSize().toMegabytes() + " MB.");
    }
    String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    DocumentType byExtension =
        lower.endsWith(".pdf") ? DocumentType.PDF : lower.endsWith(".docx") ? DocumentType.DOCX : null;
    if (byExtension == null) {
      throw error(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UNSUPPORTED_FILE_TYPE",
          "Only PDF and DOCX files are supported.");
    }
    DocumentType byContent = sniff(content);
    if (byContent != byExtension) {
      throw error(
          HttpStatus.BAD_REQUEST,
          "FILE_CONTENT_MISMATCH",
          "The file content does not match its " + byExtension.extension() + " extension.");
    }
    if (byContent == DocumentType.DOCX) {
      checkDocxPackage(content);
    }
    return byContent;
  }

  private static DocumentType sniff(byte[] c) {
    int window = Math.min(c.length, 1024);
    for (int i = 0; i + 5 <= window; i++) {
      if (c[i] == '%' && c[i + 1] == 'P' && c[i + 2] == 'D' && c[i + 3] == 'F' && c[i + 4] == '-') {
        return DocumentType.PDF;
      }
    }
    if (c.length >= 4 && c[0] == 'P' && c[1] == 'K' && c[2] == 3 && c[3] == 4) {
      return DocumentType.DOCX;
    }
    return null;
  }

  /** Streams the ZIP counting real bytes (headers can lie) to stop zip bombs early. */
  private static void checkDocxPackage(byte[] content) {
    boolean hasDocument = false;
    boolean hasContentTypes = false;
    int entries = 0;
    long total = 0;
    byte[] buf = new byte[8192];
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > MAX_ZIP_ENTRIES) {
          throw unsafe();
        }
        String name = entry.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) {
          throw error(
              HttpStatus.BAD_REQUEST, "MACROS_NOT_ALLOWED", "Documents containing macros are not allowed.");
        }
        hasDocument |= name.equals("word/document.xml");
        hasContentTypes |= name.equals("[Content_Types].xml");
        int n;
        while ((n = zip.read(buf)) > 0) {
          total += n;
          if (total > MAX_ZIP_UNCOMPRESSED) {
            throw unsafe();
          }
        }
      }
    } catch (IOException | IllegalArgumentException e) {
      throw error(HttpStatus.BAD_REQUEST, "MALFORMED_DOCUMENT", "The DOCX file is damaged.");
    }
    if (!hasDocument || !hasContentTypes) {
      throw error(
          HttpStatus.BAD_REQUEST, "FILE_CONTENT_MISMATCH", "The file is not a Word (.docx) document.");
    }
  }

  private static ApiException unsafe() {
    return error(HttpStatus.BAD_REQUEST, "UNSAFE_DOCUMENT", "The document was rejected as unsafe.");
  }

  private static ApiException error(HttpStatus status, String code, String message) {
    return new ApiException(status, code, message);
  }

  /** Display-only file name: no path, no control characters, bounded length. */
  static String sanitizeFilename(String original, DocumentType type) {
    String name = original == null ? "" : original;
    name = name.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    name = name.replaceAll("[\\p{Cntrl}\"<>|*?:]", "").strip();
    if (name.length() > 120) {
      name = name.substring(0, 120 - type.extension().length()) + type.extension();
    }
    return name.isBlank() ? "cv" + type.extension() : name;
  }
}
