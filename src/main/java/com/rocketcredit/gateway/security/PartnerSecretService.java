package com.rocketcredit.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PartnerSecretService {
  private final Map<String,String> secrets;

  public PartnerSecretService(@Value("${PARTNER_SECRETS:}") String env) {
    this.secrets = parse(env);
  }

  private Map<String,String> parse(String env) {
    Map<String,String> map = new HashMap<>();
    if (env != null && !env.isBlank()) {
      for (String pair : env.split(",")) {
        String p = pair.trim(); if (p.isEmpty()) continue;
        int i = p.indexOf('=');
        if (i > 0) {
          String k = p.substring(0, i).trim();
          String v = p.substring(i+1).trim();
          if (!k.isEmpty() && !v.isEmpty()) map.put(k, v);
        }
      }
    }
    return map;
  }

  public Optional<String> secretFor(String partnerId) {
    if (partnerId == null) return Optional.empty();
    String s = secrets.get(partnerId);
    return Optional.ofNullable(s);
  }
}

