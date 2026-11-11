package kz.birchat.api.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Все миграции V1…Vn на настоящем PostgreSQL через автонастройку Spring Boot — так же,
 * как при старте приложения на Render.
 *
 * В CI база — контейнер postgres:18 (как Neon). Локально тест пропускается,
 * если не задан MIGRATION_TEST_DB_URL.
 */
@ExtendWith(OutputCaptureExtension.class)
@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_DB_URL", matches = ".+")
class FlywayMigrationsPostgresTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    FlywayAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.datasource.url=" + System.getenv("MIGRATION_TEST_DB_URL"),
                    "spring.datasource.username=" + System.getenv("MIGRATION_TEST_DB_USER"),
                    "spring.datasource.password=" + System.getenv("MIGRATION_TEST_DB_PASSWORD"),
                    // те же настройки, что в application-prod.yml
                    "spring.flyway.schemas=birchat",
                    "spring.flyway.default-schema=birchat",
                    "spring.flyway.locations=classpath:db/migration",
                    "spring.flyway.baseline-on-migrate=true"
            );

    @Test
    void allMigrationsApplyWithoutUpgradeWarning(CapturedOutput output) {
        runner.run(context -> {
            if (context.getStartupFailure() != null) {
                throw context.getStartupFailure();
            }

            Flyway flyway = context.getBean(Flyway.class);
            MigrationInfo current = flyway.info().current();

            assertNotNull(current, "миграции не применились");
            assertEquals(0, flyway.info().pending().length, "остались неприменённые миграции");

            // Полнотекстовый поиск из V16 работает на этой версии PostgreSQL.
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertEquals("INV 2024 15", jdbc.queryForObject(
                    "SELECT birchat.search_text('INV-2024-15')", String.class));
            assertEquals(Boolean.TRUE, jdbc.queryForObject(
                    "SELECT to_tsvector('russian', birchat.search_text('Подписали договора'))"
                            + " @@ plainto_tsquery('russian', 'договоры')",
                    Boolean.class));
        });

        // Ради этого Flyway и обновлялся: на PostgreSQL 18 предупреждения быть не должно.
        assertFalse(output.getAll().contains("Flyway upgrade recommended"),
                "Flyway не поддерживает эту версию PostgreSQL");
    }
}
