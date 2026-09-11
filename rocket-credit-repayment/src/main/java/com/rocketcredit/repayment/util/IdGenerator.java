package com.rocketcredit.repayment.util;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class IdGenerator {
  public String generate(String prefix) { return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}