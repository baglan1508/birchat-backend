package kz.birchat.api.controller;

import kz.birchat.api.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    /** production или staging — чтобы по ответу было видно, с каким сервером работаешь. */
    @Value("${app.environment:production}")
    private String environment;

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "birchat-backend",
                "environment", environment,
                "time", TimeUtils.utcOffsetNow()
        );
    }
}