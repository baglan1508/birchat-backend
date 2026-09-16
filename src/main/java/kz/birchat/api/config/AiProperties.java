package kz.birchat.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "mock";

    private Glm glm = new Glm();

    @Getter
    @Setter
    public static class Glm {
        private String apiKey = "";
        private String model = "glm-4.5-air";
        private String baseUrl = "https://api.z.ai/api/paas/v4";
        private Integer maxTokens = 1024;
        private Double temperature = 0.6;
    }
}