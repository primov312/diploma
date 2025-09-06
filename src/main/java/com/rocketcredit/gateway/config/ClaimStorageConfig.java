package com.rocketcredit.gateway.config;

import com.rocketcredit.claimcheck.storage.ClaimStorage;
import com.rocketcredit.claimcheck.storage.impl.S3ClaimStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class ClaimStorageConfig {

  @Bean
  public S3Client s3Client() {
    String endpoint = env("S3_ENDPOINT", "http://minio:9000");
    String access   = env("S3_ACCESS_KEY", "minio");
    String secret   = env("S3_SECRET_KEY", "minio123");
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)))
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .build();
  }

  @Bean
  public ClaimStorage claimStorage(S3Client s3) {
    return new S3ClaimStorage(s3);
  }

  private static String env(String k, String def) {
    String v = System.getenv(k);
    return v == null || v.isBlank() ? def : v;
  }
}
