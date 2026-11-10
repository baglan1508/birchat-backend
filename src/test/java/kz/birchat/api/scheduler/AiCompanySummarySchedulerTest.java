package kz.birchat.api.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AiCompanySummarySchedulerTest {

    @Test
    void summaryRefreshIsLockedAcrossInstances() throws Exception {
        Method method = AiCompanySummaryScheduler.class.getMethod("refreshCompanySummaries");

        assertNotNull(method.getAnnotation(Scheduled.class));

        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
        assertNotNull(lock, "Фоновое обновление сводок должно быть под @SchedulerLock");
        assertEquals(AiCompanySummaryScheduler.LOCK_NAME, lock.name());

        // Блокировка должна сниматься раньше следующего запуска (каждые 3 часа).
        assertTrue(Duration.parse(lock.lockAtMostFor()).compareTo(Duration.ofHours(3)) < 0);
    }
}
