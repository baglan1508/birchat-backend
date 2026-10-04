package kz.birchat.api.scheduler;

import kz.birchat.api.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCompanySummaryScheduler {

    private final AiService aiService;

    @Scheduled(cron = "${ai.summary.cron:0 0 */3 * * *}", zone = "${ai.summary.zone:Asia/Almaty}")
    public void refreshCompanySummaries() {
        log.info("AI company summary scheduler started");
        aiService.refreshCompanySummaries();
        log.info("AI company summary scheduler finished");
    }
}