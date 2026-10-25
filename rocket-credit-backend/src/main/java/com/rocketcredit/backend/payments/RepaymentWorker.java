package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.affordability.AffordabilityJobs;
import com.rocketcredit.backend.common.ApiException;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@Component
public class RepaymentWorker {
    private static final Logger log=LoggerFactory.getLogger(RepaymentWorker.class);
    private final JdbcTemplate jdbc;private final PaymentLedger ledger;private final PaymentService payments;
    private final AffordabilityJobs jobs;private final TransactionTemplate tx;private final boolean enabled;
    public RepaymentWorker(JdbcTemplate jdbc,PaymentLedger ledger,PaymentService payments,AffordabilityJobs jobs,TransactionTemplate tx,@Value("${rocket.payments.collection-enabled:true}") boolean enabled) {
        this.jdbc=jdbc;this.ledger=ledger;this.payments=payments;this.jobs=jobs;this.tx=tx;this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${rocket.payments.poll-delay:60000}",initialDelayString="${rocket.payments.poll-delay:60000}")
    public void poll() {
        if(!enabled)return;
        var users=jdbc.queryForList("SELECT DISTINCT user_id FROM loans WHERE status='ACTIVE' ORDER BY user_id",Long.class);
        for(long user:users)try {
            tx.executeWithoutResult(s->{
                ledger.lock(user);
                int changed=jdbc.update("UPDATE wallet_accounts SET obligation_date=? WHERE user_id=? AND obligation_date IS DISTINCT FROM ?",ledger.today(),user,ledger.today());
                if(changed>0)jobs.recalculate(user);
            });
            var due=jdbc.queryForList("SELECT i.id FROM repayment_installments i JOIN loans l ON l.id=i.loan_id WHERE l.user_id=? AND l.status='ACTIVE' AND i.paid_at IS NULL AND i.due_date<=? ORDER BY i.due_date,l.id,i.sequence",Long.class,user,ledger.today());
            for(long id:due)try {payments.pay(user,id,"auto-"+UUID.randomUUID(),true);}
            catch(ApiException e){if("INSUFFICIENT_FUNDS".equals(e.getCode()))break;throw e;}
        }catch(Exception e){log.warn("Repayment collection for user {} failed: {}",user,e.getClass().getSimpleName());}
    }
    @Scheduled(fixedDelayString="${rocket.payments.reconciliation-delay:3600000}")
    public void reconcile() {
        Long mismatches=jdbc.queryForObject("SELECT count(*) FROM wallet_accounts w WHERE w.balance<>(SELECT COALESCE(sum(amount),0) FROM wallet_entries e WHERE e.user_id=w.user_id)",Long.class);
        if(mismatches!=null && mismatches>0)log.error("Wallet reconciliation failed: {} accounts",mismatches);
    }
}
