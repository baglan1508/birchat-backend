package kz.birchat.api.scheduler;

import kz.birchat.api.service.FileAnalysisService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileAnalysisScheduler {

    public static final String LOCK_NAME = "fileAiAnalysis";

    private final FileAnalysisService fileAnalysisService;

    /**
     * Каждые 30 секунд после окончания прошлого прохода: новые фото описываются в течение минуты.
     * Только на одном экземпляре приложения (ShedLock), чтобы один файл не отправлялся модели дважды.
     */
    @Scheduled(fixedDelayString = "${ai.media.poll-delay:PT30S}", initialDelayString = "PT1M")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M")
    public void processPending() {
        LockAssert.assertLocked();

        if (!fileAnalysisService.isEnabled()) {
            return;
        }

        fileAnalysisService.processPending();
    }
}
