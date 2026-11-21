package com.careermatcher.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileCipherTest {

  private final FileCipher cipher = new FileCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
  private final byte[] plain = "%PDF-1.4 secret cv".getBytes(StandardCharsets.UTF_8);

  @Test
  void roundTripsAndUsesFreshIvs() {
    byte[] a = cipher.encrypt(plain, "k1");
    byte[] b = cipher.encrypt(plain, "k1");
    assertThat(a).isNotEqualTo(b);
    assertThat(cipher.decrypt(a, "k1")).isEqualTo(plain);
  }

  @Test
  void detectsTampering() {
    byte[] env = cipher.encrypt(plain, "k1");
    env[env.length - 1] ^= 1;
    assertThatThrownBy(() -> cipher.decrypt(env, "k1")).isInstanceOf(StorageException.class);
  }

  @Test
  void ciphertextIsBoundToItsObjectKey() {
    byte[] env = cipher.encrypt(plain, "cv/alice/1");
    assertThatThrownBy(() -> cipher.decrypt(env, "cv/bob/1")).isInstanceOf(StorageException.class);
  }

  @Test
  void rejectsWrongKeyAndGarbage() {
    FileCipher other = new FileCipher(new byte[32]);
    assertThatThrownBy(() -> other.decrypt(cipher.encrypt(plain, "k"), "k")).isInstanceOf(StorageException.class);
    assertThatThrownBy(() -> cipher.decrypt(new byte[] {9, 9, 9}, "k")).isInstanceOf(StorageException.class);
  }
}
