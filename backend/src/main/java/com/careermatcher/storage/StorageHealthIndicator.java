package com.careermatcher.storage;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Health component {@code storage}; part of the dependencies group, not readiness. */
@Component
public class StorageHealthIndicator implements HealthIndicator {

  private final ObjectStorage storage;

  public StorageHealthIndicator(ObjectStorage storage) {
    this.storage = storage;
  }

  @Override
  public Health health() {
    return storage.isAvailable() ? Health.up().build() : Health.down().build();
  }
}
