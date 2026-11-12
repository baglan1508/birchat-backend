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
    private Summary summary = new Summary();
    private Glm glm = new Glm();
    private Media media = new Media();

    /** Фоновая обработка вложений для AI (описания фото). */
    @Getter
    @Setter
    public static class Media {
        private boolean enabled = true;
        /** Сколько файлов обрабатывать за один проход. */
        private int batchSize = 5;
    }

    @Getter
    @Setter
    public static class Glm {
        private String apiKey = "";
        private String model = "glm-4.5-air";
        private String baseUrl = "https://api.z.ai/api/paas/v4";
        private Integer maxTokens = 1024;
        private Double temperature = 0.6;
        /** disabled — без рассуждений (быстрее, не тратит max_tokens); enabled; пусто — по умолчанию модели. */
        private String thinking = "disabled";
        /** Модель с «зрением» для описания фото. */
        private String visionModel = "glm-4.6v";
    }
    @Getter
    @Setter
    public static class Summary {
        private Integer intervalHours = 3;
        private Integer maxMessages = 100;
        private Integer maxMessageLength = 500;
        private String cron = "0 0 */3 * * *";
        private String zone = "Asia/Almaty";
    }
}