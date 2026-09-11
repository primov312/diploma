package com.rocketcredit.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServiceEndpoints {
    public final String uds;
    public final String cas;
    public final String pay;
    public final String rep;
    public final String notif;

    public ServiceEndpoints(
        @Value("${USER_DATA_URL}") String uds,
        @Value("${CREDIT_ANALYSIS_URL}") String cas,
        @Value("${PAYMENT_URL}") String pay,
        @Value("${REPAYMENT_URL}") String rep,
        @Value("${NOTIFICATION_URL}") String notif
    ) {
        this.uds = trim(uds);
        this.cas = trim(cas);
        this.pay = trim(pay);
        this.rep = trim(rep);
        this.notif = trim(notif);
    }
    private static String trim(String s) { return s != null && s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
}