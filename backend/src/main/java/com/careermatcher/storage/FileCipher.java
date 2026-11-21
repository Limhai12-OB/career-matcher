package com.careermatcher.storage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM envelope for stored files: {@code [version:1][iv:12][ciphertext+tag]}. The object key
 * is bound as additional authenticated data, so a ciphertext cannot be swapped to another key.
 */
final class FileCipher {

  private static final byte VERSION = 1;
  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  FileCipher(byte[] keyBytes) {
    if (keyBytes.length != 32) {
      throw new IllegalArgumentException("AES-256 key required");
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  byte[] encrypt(byte[] plain, String objectKey) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(objectKey.getBytes(StandardCharsets.UTF_8));
      byte[] ct = cipher.doFinal(plain);
      return ByteBuffer.allocate(1 + IV_LENGTH + ct.length).put(VERSION).put(iv).put(ct).array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  byte[] decrypt(byte[] envelope, String objectKey) {
    if (envelope.length < 1 + IV_LENGTH + TAG_BITS / 8 || envelope[0] != VERSION) {
      throw new StorageException("Unrecognized stored file format");
    }
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, envelope, 1, IV_LENGTH));
      cipher.updateAAD(objectKey.getBytes(StandardCharsets.UTF_8));
      return cipher.doFinal(envelope, 1 + IV_LENGTH, envelope.length - 1 - IV_LENGTH);
    } catch (GeneralSecurityException e) {
      throw new StorageException("Stored file failed integrity check", e);
    }
  }
}
