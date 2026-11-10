package kz.birchat.api.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class DataCleanupSchedulerTest {

    @Test
    void cleanupRunsOnOneInstanceOnly() throws Exception {
        Method method = DataCleanupScheduler.class.getMethod("cleanup");

        assertNotNull(method.getAnnotation(Scheduled.class));

        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
        assertNotNull(lock, "Ночная чистка должна быть под @SchedulerLock");
        assertEquals(DataCleanupScheduler.LOCK_NAME, lock.name());
    }
}
