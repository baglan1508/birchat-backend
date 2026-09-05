package kz.birchat.api.controller;

import kz.birchat.api.util.TimeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "birchat-backend",
                "time", TimeUtils.utcOffsetNow()
        );
    }
}