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

    /**
     * Цифра на иконке одним запросом (ChatMessageRepository.UNREAD_TOTAL_SQL) — на настоящей схеме.
     * Сотрудник B: общий чат (2 от директора, одно прочитано), своя группа, чужая группа, личный чат.
     */
    @Test
    void unreadTotalCountsOnlyVisibleUnreadMessages() {
        runner.run(context -> {
            if (context.getStartupFailure() != null) {
                throw context.getStartupFailure();
            }

            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            java.util.UUID a = java.util.UUID.randomUUID();
            java.util.UUID b = java.util.UUID.randomUUID();
            java.util.UUID c = java.util.UUID.randomUUID();
            java.util.UUID company = java.util.UUID.randomUUID();
            String suffix = a.toString().substring(0, 8);

            for (java.util.UUID user : java.util.List.of(a, b, c)) {
                jdbc.update("INSERT INTO birchat.users (id, phone, full_name) VALUES (?, ?, 'U')",
                        user, "+7test" + user.toString().substring(0, 12));
            }

            jdbc.update("INSERT INTO birchat.companies (id, name, owner_user_id) VALUES (?, ?, ?)", company, "C" + suffix, a);
            jdbc.update("""
                    INSERT INTO birchat.company_members (company_id, user_id, role_id, status)
                    SELECT ?, ?, id, 'ACTIVE' FROM birchat.roles WHERE code = 'DIRECTOR'""", company, a);
            jdbc.update("""
                    INSERT INTO birchat.company_members (company_id, user_id, role_id, status)
                    SELECT ?, ?, id, 'ACTIVE' FROM birchat.roles WHERE code = 'EMPLOYEE'""", company, b);
            jdbc.update("""
                    INSERT INTO birchat.company_members (company_id, user_id, role_id, status)
                    SELECT ?, ?, id, 'ACTIVE' FROM birchat.roles WHERE code = 'EMPLOYEE'""", company, c);

            java.util.UUID general = chat(jdbc, company, "GENERAL");
            java.util.UUID ownGroup = chat(jdbc, company, "GROUP");
            java.util.UUID otherGroup = chat(jdbc, company, "GROUP");
            java.util.UUID deletedGroup = chat(jdbc, company, "GROUP");
            java.util.UUID direct = chat(jdbc, company, "DIRECT");
            jdbc.update("UPDATE birchat.chats SET deleted_at = NOW() WHERE id = ?", deletedGroup);

            for (Object[] member : new Object[][]{{ownGroup, b}, {deletedGroup, b}, {direct, b}, {direct, c}}) {
                jdbc.update("INSERT INTO birchat.chat_members (chat_id, user_id) VALUES (?, ?)", member);
            }

            java.util.UUID firstGeneral = message(jdbc, company, general, a, "2026-01-01 10:00:00", false);
            message(jdbc, company, general, a, "2026-01-01 10:01:00", false);
            message(jdbc, company, general, a, "2026-01-01 10:02:00", true);   // удалено
            message(jdbc, company, general, b, "2026-01-01 10:03:00", false);  // своё
            message(jdbc, company, ownGroup, a, "2026-01-01 10:04:00", false);
            message(jdbc, company, otherGroup, a, "2026-01-01 10:05:00", false); // чужая группа
            message(jdbc, company, deletedGroup, a, "2026-01-01 10:06:00", false);
            message(jdbc, company, direct, c, "2026-01-01 10:07:00", false);

            jdbc.update("""
                    INSERT INTO birchat.chat_read_states (company_id, chat_id, user_id, last_read_message_id, last_read_message_created_at)
                    VALUES (?, ?, ?, ?, '2026-01-01 10:00:00')""", company, general, b, firstGeneral);

            org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate named =
                    new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);

            // B: общий (1 непрочитанное) + своя группа (1) + личный (1)
            assertEquals(3L, named.queryForObject(kz.birchat.api.repository.ChatMessageRepository.UNREAD_TOTAL_SQL,
                    java.util.Map.of("userId", b), Long.class));
            // A (директор): общий — сообщение B (1) + чужие группы видит, но там только его сообщения; личный чат не его
            assertEquals(1L, named.queryForObject(kz.birchat.api.repository.ChatMessageRepository.UNREAD_TOTAL_SQL,
                    java.util.Map.of("userId", a), Long.class));
        });
    }

    private static java.util.UUID chat(JdbcTemplate jdbc, java.util.UUID company, String type) {
        java.util.UUID id = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO birchat.chats (id, company_id, name, type) VALUES (?, ?, ?, ?)", id, company, type, type);
        return id;
    }

    private static java.util.UUID message(JdbcTemplate jdbc, java.util.UUID company, java.util.UUID chat,
                                          java.util.UUID author, String createdAt, boolean deleted) {
        java.util.UUID id = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO birchat.chat_messages (id, company_id, chat_id, user_id, type, content, is_deleted, created_at)
                VALUES (?, ?, ?, ?, 'TEXT', 'x', ?, CAST(? AS timestamp))""", id, company, chat, author, deleted, createdAt);
        return id;
    }
}
