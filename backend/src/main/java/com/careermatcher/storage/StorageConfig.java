package com.careermatcher.storage;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

  @Bean(destroyMethod = "close")
  S3Client s3Client(StorageProperties props) {
    return S3Client.builder()
        .endpointOverride(props.endpoint())
        .region(Region.of(props.region()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
        .forcePathStyle(true)
        // S3-compatible stores do not all support the SDK's newer default checksum headers.
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .httpClient(
            UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(3))
                .socketTimeout(Duration.ofSeconds(30))
                .build())
        .build();
  }

  @Bean
  ObjectStorage objectStorage(S3Client s3, StorageProperties props) {
    return new S3ObjectStorage(s3, props.bucket(), new FileCipher(props.encryptionKeyBytes()));
  }
}
