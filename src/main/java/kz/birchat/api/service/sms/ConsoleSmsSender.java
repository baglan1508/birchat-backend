package kz.birchat.api.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender {

    @Override
    public SmsSendResult send(String phone, String message) {
        log.info("SMS console mode. phone={}, message={}", phone, message);
        return new SmsSendResult("console", null);
    }
}