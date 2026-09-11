package com.rocketcredit.claimcheck.storage;

import com.rocketcredit.claimcheck.ClaimRef;

public interface ClaimStorage {
  ClaimRef put(String bucket, String key, byte[] bytes, String contentType);
  byte[]   get(ClaimRef ref);
}