package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.common.ApiException;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentQueries {
    private final JdbcTemplate jdbc;private final PaymentLedger ledger;private final LoanObligations obligations;
    public PaymentQueries(JdbcTemplate jdbc,PaymentLedger ledger,LoanObligations obligations){this.jdbc=jdbc;this.ledger=ledger;this.obligations=obligations;}
    public Map<String,Object> wallet(long user) {
        ledger.ensure(user);
        var result=clean(jdbc.queryForMap("SELECT balance,currency,version,created_at FROM wallet_accounts WHERE user_id=?",user));
        var summary=jdbc.queryForMap("""
            SELECT COALESCE(sum(i.principal+i.interest),0) AS outstanding,
              COALESCE(sum(i.principal+i.interest) FILTER(WHERE i.due_date<?),0) AS overdue,
              min(i.due_date) AS next_due_date
            FROM repayment_installments i JOIN loans l ON l.id=i.loan_id WHERE l.user_id=? AND i.paid_at IS NULL
            """,ledger.today(),user);
        result.putAll(clean(summary));result.put("monthlyObligations",obligations.monthly(user).toPlainString());
        var next=jdbc.queryForList("SELECT i.principal+i.interest AS amount FROM repayment_installments i JOIN loans l ON l.id=i.loan_id WHERE l.user_id=? AND i.paid_at IS NULL ORDER BY i.due_date,l.id,i.sequence LIMIT 1",user);
        result.put("nextPayment",next.isEmpty()?null:((BigDecimal)next.getFirst().get("amount")).toPlainString());return result;
    }
    public Map<String,Object> loan(long user,long id) {
        var rows=jdbc.queryForList("SELECT l.*,p.display_name AS partner_name,p.slug AS partner_slug,pr.name AS product_name FROM loans l JOIN partners p ON p.id=l.partner_id LEFT JOIN products pr ON pr.id=l.product_id WHERE l.id=? AND l.user_id=?",id,user);
        if(rows.isEmpty())throw ApiException.notFound("Loan");
        var result=clean(rows.getFirst());result.put("installments",repayments(user,id,null,"ALL",null,null,0,100).get("items"));return result;
    }
    private static final String INSTALLMENTS="""
        SELECT i.*,l.user_id,l.application_id,l.status AS loan_status,p.slug AS partner_slug,p.display_name AS partner_name,pr.name AS product_name,
          i.principal+i.interest AS total,
          CASE WHEN i.paid_at IS NOT NULL THEN CASE WHEN e.kind='EARLY_SETTLEMENT' THEN 'SETTLED_EARLY' ELSE 'PAID' END
               WHEN i.due_date<? THEN 'OVERDUE' WHEN i.due_date=? THEN 'DUE' ELSE 'UPCOMING' END AS status,
          (i.paid_at IS NULL AND i.due_date<=? AND NOT EXISTS(SELECT 1 FROM repayment_installments older WHERE older.loan_id=i.loan_id AND older.sequence<i.sequence AND older.paid_at IS NULL)) AS can_pay
        FROM repayment_installments i JOIN loans l ON l.id=i.loan_id JOIN partners p ON p.id=l.partner_id
        LEFT JOIN products pr ON pr.id=l.product_id LEFT JOIN wallet_entries e ON e.id=i.payment_entry_id
        """;
    public Map<String,Object> repayments(long user,Long loan,String partner,String status,LocalDate from,LocalDate to,int page,int size) {
        List<Object> args=new ArrayList<>(List.of(ledger.today(),ledger.today(),ledger.today(),user));
        String sql="WITH rows AS ("+INSTALLMENTS+" WHERE l.user_id=?) SELECT * FROM rows WHERE true";
        if(loan!=null){sql+=" AND loan_id=?";args.add(loan);}
        if(partner!=null && !partner.isBlank()){sql+=" AND partner_slug=?";args.add(partner);}
        if(status==null || status.equals("UNPAID"))sql+=" AND paid_at IS NULL";
        else if(!status.equals("ALL")){
            if(!Set.of("UPCOMING","DUE","OVERDUE","PAID","SETTLED_EARLY").contains(status))throw ApiException.badRequest("INVALID_FILTER","Unknown repayment status.");
            sql+=" AND status=?";args.add(status);
        }
        if(from!=null){sql+=" AND due_date>=?";args.add(from);}if(to!=null){sql+=" AND due_date<=?";args.add(to);}
        return page(sql,"due_date,loan_id,sequence",args,page,size);
    }
    private static final String HISTORY="""
        WITH rows AS (
          SELECT 'purchase:'||t.id AS id,t.user_id,t.occurred_on AS date,t.created_at,
            CASE WHEN t.payment_method='CREDIT' THEN 'FINANCED_PURCHASE' ELSE 'PURCHASE' END AS kind,
            t.description,t.amount,COALESCE(e.amount,0) AS balance_change,t.status,t.payment_method,t.paid_on_time,
            p.slug AS partner_slug,p.display_name AS partner_name,t.loan_id,l.application_id
          FROM transactions t JOIN partners p ON p.id=t.partner_id LEFT JOIN wallet_entries e ON e.id=t.wallet_entry_id LEFT JOIN loans l ON l.id=t.loan_id
          UNION ALL
          SELECT 'entry:'||e.id,e.user_id,(e.created_at AT TIME ZONE 'Europe/Budapest')::date,e.created_at,e.kind,
            e.description,abs(e.amount),e.amount,'COMPLETED','BALANCE',NULL::boolean,
            p.slug,p.display_name,e.loan_id,l.application_id
          FROM wallet_entries e LEFT JOIN loans l ON l.id=e.loan_id LEFT JOIN partners p ON p.id=l.partner_id WHERE e.kind<>'PURCHASE'
        ) SELECT * FROM rows WHERE user_id=?
        """;
    public Map<String,Object> history(long user,String partner,String kind,LocalDate from,LocalDate to,int page,int size) {
        String sql=HISTORY;List<Object> args=new ArrayList<>(List.of(user));
        if(partner!=null && !partner.isBlank()){sql+=" AND partner_slug=?";args.add(partner);}
        if(kind!=null && !kind.isBlank()){sql+=" AND kind=?";args.add(kind);}
        if(from!=null){sql+=" AND date>=?";args.add(from);}if(to!=null){sql+=" AND date<=?";args.add(to);}
        return page(sql,"date DESC,created_at DESC,id DESC",args,page,size);
    }
    private Map<String,Object> page(String sql,String order,List<Object> args,int page,int size) {
        if(page<0 || page>100000 || size<1 || size>100)throw ApiException.badRequest("INVALID_PAGE","Choose a valid page and a page size between 1 and 100.");
        long total=jdbc.queryForObject("SELECT count(*) FROM ("+sql+") counted",Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        return Map.of("items",jdbc.queryForList(sql+" ORDER BY "+order+" LIMIT ? OFFSET ?",args.toArray()).stream().map(PaymentQueries::clean).toList(),"total",total,"page",page,"size",size);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> chart(long user,String range) {
        if(!Set.of("30d","90d","1y").contains(range))throw ApiException.badRequest("INVALID_RANGE","Choose 30d, 90d or 1y.");
        LocalDate today=ledger.today(), requested=range.equals("1y")?today.minusYears(1).plusDays(1):today.minusDays(range.equals("30d")?29:89);
        var created=jdbc.query("SELECT created_at FROM wallet_accounts WHERE user_id=?",(rs,n)->rs.getObject(1,OffsetDateTime.class).atZoneSameInstant(RepaymentCalculator.ZONE).toLocalDate(),user);
        if(created.isEmpty())return Map.of("points",List.of(),"flows",List.of(),"openingBalance","0.00");
        LocalDate from=created.getFirst().isAfter(requested)?created.getFirst():requested;
        var start=from.atStartOfDay(RepaymentCalculator.ZONE).toOffsetDateTime();
        BigDecimal balance=jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM wallet_entries WHERE user_id=? AND created_at<?",BigDecimal.class,user,start);
        String opening=balance.toPlainString();
        var daily=jdbc.queryForList("SELECT (created_at AT TIME ZONE 'Europe/Budapest')::date AS date,kind,sum(amount) AS amount FROM wallet_entries WHERE user_id=? AND created_at>=? GROUP BY 1,2 ORDER BY 1",user,start);
        List<Map<String,Object>> points=new ArrayList<>();Map<LocalDate,BigDecimal[]> flows=new LinkedHashMap<>();
        for(LocalDate day=from;!day.isAfter(today);day=day.plusDays(1)) {
            LocalDate bucket=range.equals("1y")?day.withDayOfMonth(1):range.equals("90d")?from.plusDays(java.time.temporal.ChronoUnit.DAYS.between(from,day)/7*7):day;
            BigDecimal[] values=flows.computeIfAbsent(bucket,k->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO});
            for(var row:daily)if(PaymentService.date(row,"date").equals(day)) {
                BigDecimal amount=(BigDecimal)row.get("amount");balance=balance.add(amount);
                int index=row.get("kind").equals("TOP_UP")?0:row.get("kind").equals("PURCHASE")?1:2;values[index]=values[index].add(amount.abs());
            }
            points.add(Map.of("date",day,"balance",balance.toPlainString()));
        }
        var groups=flows.entrySet().stream().map(e->Map.of("date",e.getKey(),"topUps",e.getValue()[0].toPlainString(),"purchases",e.getValue()[1].toPlainString(),"repayments",e.getValue()[2].toPlainString())).toList();
        return Map.of("points",points,"flows",groups,"openingBalance",opening,"closingBalance",balance.toPlainString());
    }
    static Map<String,Object> clean(Map<String,Object> row) {
        Map<String,Object> result=new LinkedHashMap<>();row.forEach((key,value)->{
            StringBuilder name=new StringBuilder();boolean upper=false;for(char c:key.toCharArray()){if(c=='_'){upper=true;continue;}name.append(upper?Character.toUpperCase(c):c);upper=false;}
            Object converted=value instanceof BigDecimal b?b.toPlainString():value instanceof java.sql.Date d?d.toLocalDate().toString():value instanceof java.sql.Timestamp t?t.toInstant().toString():value;
            result.put(name.toString(),converted);
        });return result;
    }
}
