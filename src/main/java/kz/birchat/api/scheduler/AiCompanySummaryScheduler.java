package kz.birchat.api.scheduler;

import kz.birchat.api.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCompanySummaryScheduler {

    public static final String LOCK_NAME = "aiCompanySummaryRefresh";

    private final AiService aiService;

    /**
     * Раз в 3 часа. При нескольких экземплярах приложения выполняется только на одном:
     * остальные видят блокировку и пропускают запуск (без повторных вызовов AI и лишних расходов).
     *
     * lockAtMostFor — страховка, если сервер упадёт посреди обновления (меньше интервала 3 ч).
     * lockAtLeastFor — защита от повторного запуска при небольшом расхождении часов серверов.
     */
    @Scheduled(cron = "${ai.summary.cron:0 0 */3 * * *}", zone = "${ai.summary.zone:Asia/Almaty}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    public void refreshCompanySummaries() {
        LockAssert.assertLocked();

        log.info("AI company summary scheduler started");
        aiService.refreshCompanySummaries();
        log.info("AI company summary scheduler finished");
    }
}
