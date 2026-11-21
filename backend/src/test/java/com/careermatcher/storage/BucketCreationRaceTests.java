package com.careermatcher.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.careermatcher.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Regression: on a fresh installation the bucket doesn't exist yet. Concurrent first uploads (in
 * one backend, or in two) must all succeed instead of failing with "bucket already owned".
 */
@IntegrationTest
class BucketCreationRaceTests {

  @Autowired S3Client s3;

  @Test
  void concurrentFirstUploadsToAFreshBucketAllSucceed() throws Exception {
    String bucket = "race-" + UUID.randomUUID().toString().substring(0, 8);
    FileCipher cipher = new FileCipher(new byte[32]);
    // Two storage instances simulate two backend replicas racing to create the bucket.
    List<S3ObjectStorage> backends = List.of(
        new S3ObjectStorage(s3, bucket, cipher), new S3ObjectStorage(s3, bucket, cipher));

    int uploads = 16;
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<String>> tasks = new ArrayList<>();
    for (int i = 0; i < uploads; i++) {
      S3ObjectStorage storage = backends.get(i % 2);
      String key = "cv/" + i;
      tasks.add(() -> {
        start.await();
        storage.put(key, ("content " + key).getBytes());
        return key;
      });
    }
    try (ExecutorService pool = Executors.newFixedThreadPool(uploads)) {
      List<Future<String>> results = tasks.stream().map(pool::submit).toList();
      start.countDown();
      for (Future<String> f : results) {
        String key = f.get(); // throws if any upload failed
        assertThat(new String(backends.get(0).get(key))).isEqualTo("content " + key);
      }
    }
  }
}
