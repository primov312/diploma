package com.rocketcredit.backend.transactions;

import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.partners.PartnerEntity;
import com.rocketcredit.backend.partners.PartnerRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the purchase history. Every method takes the session user's ID. */
@Service
public class TransactionService {
    private final TransactionRepository transactions;
    private final PartnerRepository partners;

    public TransactionService(TransactionRepository transactions, PartnerRepository partners) {
        this.transactions = transactions;
        this.partners = partners;
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> listForUser(Long userId, String partnerSlug) {
        List<TransactionEntity> rows;
        if (partnerSlug == null || partnerSlug.isBlank()) {
            rows = transactions.findByUserIdOrderByOccurredOnDesc(userId);
        } else {
            var partner = partners.findBySlug(partnerSlug).orElseThrow(() -> ApiException.notFound("Partner"));
            rows = transactions.findByUserIdAndPartnerIdOrderByOccurredOnDesc(userId, partner.getId());
        }
        Map<Long, PartnerEntity> byId = partners.findAll().stream()
                .collect(Collectors.toMap(PartnerEntity::getId, Function.identity()));
        return rows.stream().map(t -> toDto(t, byId.get(t.getPartnerId()))).toList();
    }

    /** 404 for both "does not exist" and "belongs to someone else": no existence leak. */
    @Transactional(readOnly = true)
    public TransactionDto getForUser(Long userId, Long id) {
        var t = transactions.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Transaction"));
        var partner = partners.findById(t.getPartnerId()).orElse(null);
        return toDto(t, partner);
    }

    private static TransactionDto toDto(TransactionEntity t, PartnerEntity p) {
        return new TransactionDto(t.getId(),
                p == null ? null : p.getSlug(), p == null ? null : p.getDisplayName(),
                t.getAmount(), t.getCurrency(), t.getOccurredOn(), t.getStatus().name(),
                t.isPaidOnTime(), t.getDescription());
    }
}
