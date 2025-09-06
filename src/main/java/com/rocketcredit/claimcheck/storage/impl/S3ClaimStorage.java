package com.rocketcredit.claimcheck.storage.impl;

import com.rocketcredit.claimcheck.ClaimRef;
import com.rocketcredit.claimcheck.storage.ClaimStorage;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.security.MessageDigest;
import java.util.HexFormat;

public class S3ClaimStorage implements ClaimStorage {

  private final S3Client s3;

  public S3ClaimStorage(S3Client s3) {
    this.s3 = s3;
  }

  @Override
  public ClaimRef put(String bucket, String key, byte[] bytes, String contentType) {
    s3.putObject(PutObjectRequest.builder()
            .bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(bytes));
    try {
      var md = MessageDigest.getInstance("SHA-256");
      String sha = HexFormat.of().formatHex(md.digest(bytes));
      return ClaimRef.builder()
          .bucket(bucket).key(key).contentType(contentType)
          .size(bytes.length).sha256(sha)
          .build();
    } catch (Exception e) { throw new RuntimeException(e); }
  }

  @Override
  public byte[] get(ClaimRef ref) {
    var resp = s3.getObject(GetObjectRequest.builder()
        .bucket(ref.getBucket()).key(ref.getKey()).build());
    try {
      return resp.readAllBytes();
    } catch (Exception e) { throw new RuntimeException(e); }
  }
}