package com.rocketcredit.claimcheck.storage.impl;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class S3ObjectKeyBuilder {
  private S3ObjectKeyBuilder() {}

  public static String featuresKey(long userId, String correlationId) {
    String date = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    return "features/" + userId + "/" + date + "/" + correlationId + ".json";
  }
}