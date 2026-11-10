package kz.birchat.api.scheduler;

import kz.birchat.api.service.DataCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupScheduler {

    public static final String LOCK_NAME = "dataCleanup";

    private final DataCleanupService dataCleanupService;

    /**
     * Раз в сутки ночью (по умолчанию 04:10 по Алматы), только на одном экземпляре приложения.
     */
    @Scheduled(cron = "${cleanup.cron:0 10 4 * * *}", zone = "${ai.summary.zone:Asia/Almaty}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void cleanup() {
        LockAssert.assertLocked();

        if (!dataCleanupService.isEnabled()) {
            log.info("Data cleanup is disabled (cleanup.enabled=false)");
            return;
        }

        dataCleanupService.cleanup();
    }
}
