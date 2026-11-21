package com.careermatcher.cv;

public enum DocumentType {
  PDF("application/pdf", ".pdf"),
  DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");

  private final String mediaType;
  private final String extension;

  DocumentType(String mediaType, String extension) {
    this.mediaType = mediaType;
    this.extension = extension;
  }

  public String mediaType() { return mediaType; }
  public String extension() { return extension; }
}
