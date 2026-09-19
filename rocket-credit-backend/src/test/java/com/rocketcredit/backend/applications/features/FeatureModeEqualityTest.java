package com.rocketcredit.backend.applications.features;

import static org.assertj.core.api.Assertions.assertThat;

import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.partners.PartnerRepository;
import com.rocketcredit.backend.users.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 7.4: sequential and parallel preparation must produce identical features, also under concurrency. */
class FeatureModeEqualityTest extends AbstractIntegrationTest {

    @Autowired SequentialFeaturePreparation sequential;
    @Autowired ParallelFeaturePreparation parallel;
    @Autowired UserRepository users;
    @Autowired PartnerRepository partners;

    private static final OffsetDateTime OBSERVED = OffsetDateTime.of(2026, 9, 19, 12, 0, 0, 0, ZoneOffset.UTC);

    private FeatureProviders.Context ctx(String who, String partner) {
        Long userId = users.findByEmail(who + "@demo.rocket.local").orElseThrow().getId();
        Long partnerId = partners.findBySlug(partner).orElseThrow().getId();
        return new FeatureProviders.Context(userId, partnerId, OBSERVED);
    }

    @Test
    void everyPersonaAtEveryPartnerGivesIdenticalSections() {
        for (String who : List.of("avery", "riley", "drew", "casey")) {
            for (String partner : List.of("streambox", "markethub", "threadly")) {
                var c = ctx(who, partner);
                var seq = sequential.prepare(c);
                var par = parallel.prepare(c);
                assertThat(par).as("%s at %s", who, partner).isEqualTo(seq);
                assertThat(seq.history()).isNotNull();
            }
        }
    }

    @Test
    void simultaneousRequestsFromTwoUsersDoNotLeakIntoEachOther() throws Exception {
        var avery = ctx("avery", "markethub");
        var casey = ctx("casey", "markethub");
        var expectedAvery = sequential.prepare(avery);
        var expectedCasey = sequential.prepare(casey);
        assertThat(expectedAvery).isNotEqualTo(expectedCasey);

        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                boolean isAvery = i % 2 == 0;
                Callable<Boolean> task = () -> parallel.prepare(isAvery ? avery : casey)
                        .equals(isAvery ? expectedAvery : expectedCasey);
                results.add(pool.submit(task));
            }
            for (Future<Boolean> r : results) assertThat(r.get()).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
