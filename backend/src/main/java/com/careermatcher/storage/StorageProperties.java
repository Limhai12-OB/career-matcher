package com.careermatcher.storage;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Private S3-compatible object storage and the application-level encryption key for CV files. */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
    @NotNull URI endpoint,
    @NotBlank String region,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucket,
    /** Base64-encoded 256-bit AES key. */
    @NotBlank String encryptionKey) {

  @AssertTrue(message = "app.storage.encryption-key (CV_ENCRYPTION_KEY) must be 32 bytes, base64")
  public boolean isEncryptionKeyValid() {
    try {
      return encryptionKey != null && Base64.getDecoder().decode(encryptionKey).length == 32;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  byte[] encryptionKeyBytes() {
    return Base64.getDecoder().decode(encryptionKey);
  }

  @Override
  public String toString() {
    return "StorageProperties[endpoint=" + endpoint + ", bucket=" + bucket + ", secrets=***]";
  }
}
