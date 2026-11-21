package com.careermatcher.storage;

/** Private, encrypted blob storage. Implementations never expose public URLs. */
public interface ObjectStorage {

  void put(String key, byte[] content);

  byte[] get(String key);

  void delete(String key);

  /** Lightweight availability check for health reporting. */
  boolean isAvailable();
}
