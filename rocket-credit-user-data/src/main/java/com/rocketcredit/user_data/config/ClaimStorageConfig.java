package com.rocketcredit.user_data.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import com.rocketcredit.claimcheck.storage.ClaimStorage;
import com.rocketcredit.claimcheck.storage.impl.S3ClaimStorage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class ClaimStorageConfig {
  @Bean
  public S3Client s3Client(
      @Value("${S3_ENDPOINT:http://minio:9000}") String endpoint,
      @Value("${S3_ACCESS_KEY:minio}") String access,
      @Value("${S3_SECRET_KEY:minio123}") String secret
  ) {
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
}