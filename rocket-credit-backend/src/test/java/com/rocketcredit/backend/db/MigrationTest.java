// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.rocketcredit.backend.AbstractIntegrationTest;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V17 on an empty database and on data written by V1 to V16, each in its own database of the test server. */
class MigrationTest extends AbstractIntegrationTest {

    private DriverManagerDataSource freshDatabase() {
        String name = "migration_" + UUID.randomUUID().toString().substring(0, 8);
        testJdbc.execute("CREATE DATABASE " + name);
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Flyway flyway(DriverManagerDataSource ds, String target) {
        var config = Flyway.configure().dataSource(ds).locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    @Test
    void appliesOnAnEmptyDatabase() {
        var ds = freshDatabase();
        var result = flyway(ds, null).migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("17");
        var jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('seller_accounts','application_reviews','audit_events')",
                Long.class)).isEqualTo(3);
    }

    @Test
    void existingUsersBecomeEnabledCustomersAndCatalogueStaysActive() {
        var ds = freshDatabase();
        flyway(ds, "16").migrate();
        var jdbc = new JdbcTemplate(ds);
        long user = jdbc.queryForObject("INSERT INTO users(email,password_hash,display_name) VALUES ('old@example.test','x','Old') RETURNING id", Long.class);
        long products = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);

        var result = flyway(ds, null).migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, user)).isEqualTo("CUSTOMER");
        assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE id=?", Boolean.class, user)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE active", Long.class)).isEqualTo(products);
        assertThat(jdbc.queryForObject("SELECT bool_and(active) FROM partners", Boolean.class)).isTrue();
        // re-running is a no-op
        assertThat(flyway(ds, null).migrate().migrationsExecuted).isZero();
    }
}
