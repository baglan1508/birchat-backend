package kz.birchat.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.birchat.api.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlmAiProviderTest {

    private static final String MODEL = "glm-4.5-air";

    @Test
    void summaryCutByMaxTokensIsNotSaved() {
        // Так выглядела сломанная сводка в приложении: «С» и «TITLE: С».
        GlmAiProvider.GlmAnswer cut = new GlmAiProvider.GlmAnswer("TITLE: С", "length", null);

        ApiException exception = assertThrows(ApiException.class,
                () -> GlmAiProvider.toSummary(cut, MODEL));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void fullSummaryIsParsed() {
        GlmAiProvider.GlmAnswer answer = new GlmAiProvider.GlmAnswer("""
                TITLE: Сводка за сегодня
                SUMMARY: Баглан и Акишев проверили работу чата.
                Баглан отправил фото.
                ITEMS:
                - Проверка сообщений
                - Отправлено фото
                """, "stop", null);

        AiSummaryProviderResponse summary = GlmAiProvider.toSummary(answer, MODEL);

        assertEquals("Сводка за сегодня", summary.title());
        assertEquals("Баглан и Акишев проверили работу чата. Баглан отправил фото.", summary.summary());
        assertEquals(List.of("Проверка сообщений", "Отправлено фото"), summary.items());
    }

    @Test
    void answerWithoutFormatKeepsTextWithoutLabels() {
        AiSummaryProviderResponse summary = GlmAiProvider.parseSummaryResponse(
                "TITLE: Итоги\nСегодня обсуждали поставку.", MODEL);

        assertEquals("Итоги", summary.title());
        assertEquals("Сегодня обсуждали поставку.", summary.summary());
    }

    @Test
    void answerWithOnlyLabelsIsRejected() {
        assertThrows(ApiException.class,
                () -> GlmAiProvider.parseSummaryResponse("TITLE: С", MODEL));
    }

    @Test
    void thinkingIsSentOnlyWhenConfigured() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<GlmAiProvider.GlmMessage> messages = List.of(new GlmAiProvider.GlmMessage("user", "привет"));

        String disabled = mapper.writeValueAsString(new GlmAiProvider.GlmChatRequest(
                MODEL, messages, 1024, 0.6, new GlmAiProvider.GlmThinking("disabled")));
        String omitted = mapper.writeValueAsString(new GlmAiProvider.GlmChatRequest(
                MODEL, messages, 1024, 0.6, null));

        assertTrue(disabled.contains("\"thinking\":{\"type\":\"disabled\"}"), disabled);
        assertTrue(disabled.contains("\"max_tokens\":1024"), disabled);
        assertFalse(omitted.contains("thinking"), omitted);
    }

    @Test
    void visionRequestSendsImageLinkAndInstruction() throws Exception {
        kz.birchat.api.config.AiProperties.Glm glm = new kz.birchat.api.config.AiProperties.Glm();

        String json = new ObjectMapper().writeValueAsString(
                GlmAiProvider.buildVisionRequest(glm, "https://storage.example/signed/photo.jpg"));

        assertTrue(json.contains("\"model\":\"glm-4.6v\""), json);
        assertTrue(json.contains("{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://storage.example/signed/photo.jpg\"}}"), json);
        assertTrue(json.contains("\"type\":\"text\""), json);
        assertTrue(json.contains("Текст на фото"), json);
        assertTrue(json.contains("\"max_tokens\":2500"), json);
        assertTrue(json.contains("\"thinking\":{\"type\":\"disabled\"}"), json);
    }

    @Test
    void transcriptionFormCarriesFileModelAndTailOfPreviousText() {
        java.nio.file.Path chunk = java.nio.file.Path.of("chunk_001.wav");
        String previous = "а".repeat(2500) + "конец";

        org.springframework.util.MultiValueMap<String, Object> form =
                GlmAiProvider.transcriptionForm("glm-asr-2512", chunk, previous);

        assertEquals("glm-asr-2512", form.getFirst("model"));
        assertEquals("false", form.getFirst("stream"));
        assertTrue(form.getFirst("file") instanceof org.springframework.core.io.FileSystemResource);
        String prompt = (String) form.getFirst("prompt");
        assertEquals(2000, prompt.length());
        assertTrue(prompt.endsWith("конец"));
    }

    @Test
    void firstChunkHasNoPrompt() {
        org.springframework.util.MultiValueMap<String, Object> form =
                GlmAiProvider.transcriptionForm("glm-asr-2512", java.nio.file.Path.of("chunk_000.wav"), "");

        assertNull(form.getFirst("prompt"));
    }
}
