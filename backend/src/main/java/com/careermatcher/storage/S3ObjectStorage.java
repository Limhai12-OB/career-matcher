package com.careermatcher.storage;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3-compatible storage (SeaweedFS/MinIO/AWS) with client-side AES-GCM encryption. */
public class S3ObjectStorage implements ObjectStorage {

  private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);
  private static final String CONTENT_TYPE = "application/octet-stream";

  private final S3Client s3;
  private final String bucket;
  private final FileCipher cipher;
  private final AtomicBoolean bucketReady = new AtomicBoolean(false);

  S3ObjectStorage(S3Client s3, String bucket, FileCipher cipher) {
    this.s3 = s3;
    this.bucket = bucket;
    this.cipher = cipher;
  }

  @Override
  public void put(String key, byte[] content) {
    ensureBucket();
    try {
      s3.putObject(
          b -> b.bucket(bucket).key(key).contentType(CONTENT_TYPE),
          RequestBody.fromBytes(cipher.encrypt(content, key)));
    } catch (SdkException e) {
      throw new StorageException("Failed to store object", e);
    }
  }

  @Override
  public byte[] get(String key) {
    try {
      return cipher.decrypt(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray(), key);
    } catch (NoSuchKeyException e) {
      throw new StorageException("Object not found", e);
    } catch (SdkException e) {
      throw new StorageException("Failed to read object", e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      s3.deleteObject(b -> b.bucket(bucket).key(key));
    } catch (SdkException e) {
      throw new StorageException("Failed to delete object", e);
    }
  }

  @Override
  public boolean isAvailable() {
    try {
      s3.headBucket(b -> b.bucket(bucket));
      return true;
    } catch (SdkException e) {
      return false;
    }
  }

  /**
   * Creates the bucket on first use. Serialized, and tolerant of a concurrent creator (another
   * request or backend instance): "already exists / already owned" counts as success.
   */
  private void ensureBucket() {
    if (bucketReady.get()) {
      return;
    }
    synchronized (bucketReady) {
      if (bucketReady.get()) {
        return;
      }
      try {
        s3.headBucket(b -> b.bucket(bucket));
      } catch (NoSuchBucketException e) {
        createBucket();
      } catch (S3Exception e) {
        if (e.statusCode() != 404) {
          throw new StorageException("Storage unavailable", e);
        }
        createBucket();
      } catch (SdkException e) {
        throw new StorageException("Storage unavailable", e);
      }
      bucketReady.set(true);
    }
  }

  private void createBucket() {
    log.info("Creating storage bucket {}", bucket);
    try {
      s3.createBucket(b -> b.bucket(bucket));
    } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
      log.debug("Storage bucket {} was created concurrently", bucket);
    } catch (S3Exception e) {
      if (e.statusCode() != 409) {
        throw new StorageException("Could not create storage bucket", e);
      }
    } catch (SdkException e) {
      throw new StorageException("Could not create storage bucket", e);
    }
  }
}
