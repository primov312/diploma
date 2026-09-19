package com.rocketcredit.backend.applications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcredit.backend.analysis.AnalysisClient;
import com.rocketcredit.backend.analysis.AnalysisDecision;
import com.rocketcredit.backend.analysis.FeatureBundle;
import com.rocketcredit.backend.applications.features.FeaturePreparation;
import com.rocketcredit.backend.applications.features.FeatureProviders;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.partners.PartnerEntity;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.partners.ProductEntity;
import com.rocketcredit.backend.partners.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * request -> validate -> prepare features -> score -> save -> read.
 *
 * The analysis call happens outside any database transaction; the save is one transaction.
 * If analysis fails nothing is stored and the caller gets a technical error (503). If the
 * save fails the caller gets a 500 and no decision is shown: a decision is only ever
 * displayed after it exists in the database.
 */
@Service
public class ApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final PartnerRepository partners;
    private final ProductRepository products;
    private final CreditApplicationRepository applications;
    private final FeaturePreparation features;
    private final AnalysisClient analysis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public ApplicationService(PartnerRepository partners, ProductRepository products,
                              CreditApplicationRepository applications, FeaturePreparation features,
                              AnalysisClient analysis, ObjectMapper objectMapper, TransactionTemplate tx) {
        this.partners = partners;
        this.products = products;
        this.applications = applications;
        this.features = features;
        this.analysis = analysis;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    public ApplicationDtos.ApplicationDto submit(Long userId, ApplicationDtos.SubmitRequest req) {
        // 1) validate and resolve partner / product / amount on the server
        PartnerEntity partner = partners.findBySlug(req.partnerSlug())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PARTNER", "Unknown partner"));
        ProductEntity product = null;
        BigDecimal amount = req.requestedAmount();
        if (req.productId() != null) {
            product = products.findById(req.productId())
                    .filter(p -> p.getPartnerId().equals(partner.getId()))
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PRODUCT", "Product not found for this partner"));
            if (amount != null && amount.compareTo(product.getPrice()) != 0) {
                throw ApiException.badRequest("AMOUNT_MISMATCH", "Requested amount must equal the product price");
            }
            amount = product.getPrice();
        }
        if (amount == null) {
            throw ApiException.badRequest("AMOUNT_REQUIRED", "requestedAmount is required without a productId");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);

        // 2) prepare features (sequential for now; Step 7 adds the parallel implementation)
        OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.UTC);
        var ctx = new FeatureProviders.Context(userId, partner.getId(), observedAt);
        FeaturePreparation.Sections sections = features.prepare(ctx);
        FeatureBundle bundle = FeaturePreparation.toBundle(sections, amount, partner.getAmountCap(), req.useAi(), ctx);

        // 3) score — may throw AnalysisUnavailableException (-> 503, nothing saved)
        AnalysisDecision decision = analysis.score(bundle);

        // 4) save request + snapshot + result atomically
        final ProductEntity resolvedProduct = product;
        final BigDecimal resolvedAmount = amount;
        CreditApplicationEntity saved = tx.execute(status -> {
            var e = new CreditApplicationEntity();
            e.setUserId(userId);
            e.setPartnerId(partner.getId());
            e.setProductId(resolvedProduct == null ? null : resolvedProduct.getId());
            e.setRequestedAmount(resolvedAmount);
            e.setDecisionStatus(CreditApplicationEntity.DecisionStatus.valueOf(decision.decisionStatus()));
            e.setScore(decision.score().setScale(4, RoundingMode.HALF_UP));
            e.setPossibleAmount(decision.possibleAmount().setScale(2, RoundingMode.HALF_UP));
            e.setReasons(decision.reasons() == null ? List.of() : decision.reasons());
            e.setFactors(objectMapper.convertValue(decision.factors(), MAP));
            e.setFeatureSnapshot(objectMapper.convertValue(bundle, MAP));
            e.setAiRequested(decision.aiRequested());
            e.setAiStatus(CreditApplicationEntity.AiStatus.valueOf(decision.aiStatus()));
            e.setPolicyVersion(decision.policyVersion());
            e.setModelVersion(decision.modelVersion());
            e.setPreparationMode(features.mode());
            e.setObservedAt(observedAt);
            return applications.saveAndFlush(e);
        });

        log.info("application saved id={} user={} partner={} status={} policy={} ai={}",
                saved.getId(), userId, partner.getSlug(), saved.getDecisionStatus(), saved.getPolicyVersion(), saved.getAiStatus());
        return toDto(saved, partner, product, true);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDtos.ApplicationDto> listForUser(Long userId) {
        Map<Long, PartnerEntity> partnerById = partners.findAll().stream()
                .collect(Collectors.toMap(PartnerEntity::getId, Function.identity()));
        Map<Long, ProductEntity> productById = products.findAll().stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        return applications.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(a -> toDto(a, partnerById.get(a.getPartnerId()),
                        a.getProductId() == null ? null : productById.get(a.getProductId()), false))
                .toList();
    }

    /** 404 for foreign or missing IDs alike. */
    @Transactional(readOnly = true)
    public ApplicationDtos.ApplicationDto getForUser(Long userId, Long id) {
        var a = applications.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Application"));
        var partner = partners.findById(a.getPartnerId()).orElse(null);
        var product = a.getProductId() == null ? null : products.findById(a.getProductId()).orElse(null);
        return toDto(a, partner, product, true);
    }

    static ApplicationDtos.ApplicationDto toDto(CreditApplicationEntity a, PartnerEntity partner, ProductEntity product,
                                                boolean withSnapshot) {
        return new ApplicationDtos.ApplicationDto(
                a.getId(),
                partner == null ? null : partner.getSlug(),
                partner == null ? null : partner.getDisplayName(),
                a.getProductId(),
                product == null ? null : product.getName(),
                a.getRequestedAmount(), a.getCurrency(),
                a.getDecisionStatus().name(), a.getScore(), a.getPossibleAmount(),
                a.getReasons(), a.getFactors(),
                a.isAiRequested(), a.getAiStatus().name(), a.getPolicyVersion(), a.getModelVersion(),
                a.getPreparationMode().name(), a.getObservedAt(), a.getCreatedAt(),
                withSnapshot ? a.getFeatureSnapshot() : null);
    }
}
