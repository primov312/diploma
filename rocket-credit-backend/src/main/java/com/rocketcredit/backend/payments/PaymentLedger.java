package com.rocketcredit.backend.payments;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.users.FinancialInputsService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.LongFunction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** All money mutations share one lock order and one idempotency journal. */
@Service
public class PaymentLedger {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PaymentLedger.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final FinancialInputsService inputs;
    private final ObjectMapper mapper;
    private final Clock clock;
    public PaymentLedger(JdbcTemplate jdbc,TransactionTemplate tx,FinancialInputsService inputs,ObjectMapper mapper,Clock clock) {
        this.jdbc=jdbc;this.tx=tx;this.inputs=inputs;this.mapper=mapper;this.clock=clock;
    }
    public OffsetDateTime now() {return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);}
    public LocalDate today() {return LocalDate.now(clock.withZone(RepaymentCalculator.ZONE));}
    public OffsetDateTime expires(int minutes) {
        var end=today().plusDays(1).atStartOfDay(RepaymentCalculator.ZONE).toOffsetDateTime();
        var expiry=now().plusMinutes(minutes); return expiry.isBefore(end)?expiry:end;
    }
    public String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String fingerprint(Object request) { return canonical(mapper.valueToTree(request)).toString(); }
    private com.fasterxml.jackson.databind.JsonNode canonical(com.fasterxml.jackson.databind.JsonNode node) {
        if(node.isObject()) {
            var sorted=mapper.createObjectNode();
            var names=new ArrayList<String>();node.fieldNames().forEachRemaining(names::add);Collections.sort(names);
            for(String name:names)sorted.set(name,canonical(node.get(name)));
            return sorted;
        }
        if(node.isArray()) {var array=mapper.createArrayNode();node.forEach(value->array.add(canonical(value)));return array;}
        if(node.isNumber())return com.fasterxml.jackson.databind.node.DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        return node;
    }
    public <T> T read(String json,Class<T> type) {try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException(e);}}
    public Map<String,Object> map(String json) {try{return mapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    public void ensure(long user) {
        inputs.current(user);
        jdbc.update("INSERT INTO wallet_accounts(user_id,created_at) VALUES(?,?) ON CONFLICT DO NOTHING",user,now());
    }
    public long lock(long user) {
        Long generation=jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id=? FOR UPDATE",Long.class,user);
        jdbc.queryForObject("SELECT balance FROM wallet_accounts WHERE user_id=? FOR UPDATE",BigDecimal.class,user);
        return generation;
    }
    public static void key(String key) {
        if(key==null || key.isBlank() || key.length()>120) throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED","Provide an Idempotency-Key of 1–120 characters.");
    }
    public Map<String,Object> replay(long user,String key,String type,Object request) {
        key(key);
        var rows=jdbc.queryForList("SELECT operation_type,fingerprint,result::text FROM financial_operations WHERE user_id=? AND idempotency_key=?",user,key);
        if(rows.isEmpty()) return null;
        var row=rows.getFirst();
        if(!type.equals(row.get("operation_type")) || !fingerprint(request).equals(row.get("fingerprint")))
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT","This key was already used for a different request.");
        return unwrap(map((String)row.get("result")));
    }
    public Map<String,Object> execute(long user,String key,String type,Object request,LongFunction<Map<String,Object>> action) {
        key(key); ensure(user);
        var recordedOperation=new java.util.concurrent.atomic.AtomicLong();
        Map<String,Object> result=tx.execute(status->{
            lock(user);
            var existing=replay(user,key,type,request); if(existing!=null)return existing;
            long id=jdbc.queryForObject("INSERT INTO financial_operations(user_id,operation_type,idempotency_key,fingerprint,created_at) VALUES(?,?,?,?,?) RETURNING id",
                Long.class,user,type,key,fingerprint(request),now());
            recordedOperation.set(id);
            Map<String,Object> response=action.apply(id);
            jdbc.update("UPDATE financial_operations SET result=?::jsonb WHERE id=?",json(response),id);
            return response;
        });
        if(recordedOperation.get()!=0)log.info("financial operation id={} user={} type={} outcome={}",recordedOperation.get(),user,type,result.getOrDefault("error","COMPLETED"));
        return unwrap(result);
    }
    private Map<String,Object> unwrap(Map<String,Object> result) {
        if(result.containsKey("error")) throw ApiException.conflict((String)result.get("error"),(String)result.get("message"));
        return result;
    }
    public BigDecimal balance(long user) {return jdbc.queryForObject("SELECT balance FROM wallet_accounts WHERE user_id=?",BigDecimal.class,user);}
    public static Map<String,Object> insufficient(BigDecimal shortfall) {
        return Map.of("error","INSUFFICIENT_FUNDS","message","Insufficient balance. Add at least "+shortfall.toPlainString()+" USD ("+shortfall.multiply(new BigDecimal("360")).toPlainString()+" HUF).","shortfall",shortfall.toPlainString());
    }
    public long post(long user,long operation,String kind,BigDecimal amount,Long loan,String description) {
        BigDecimal next=balance(user).add(amount);
        if(next.signum()<0 || next.compareTo(RepaymentCalculator.MAX)>0) throw ApiException.badRequest("INVALID_AMOUNT","Balance would exceed its supported range.");
        jdbc.update("UPDATE wallet_accounts SET balance=?,version=version+1 WHERE user_id=?",next,user);
        return jdbc.queryForObject("INSERT INTO wallet_entries(user_id,operation_id,kind,amount,balance_after,loan_id,description,created_at) VALUES(?,?,?,?,?,?,?,?) RETURNING id",
            Long.class,user,operation,kind,amount,next,loan,description,now());
    }
}
