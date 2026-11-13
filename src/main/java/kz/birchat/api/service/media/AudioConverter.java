package kz.birchat.api.service.media;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Подготовка голосовых для распознавания речи через ffmpeg (установлен в Docker-образе).
 *
 * Сервис распознавания (GLM-ASR) принимает только wav/mp3 и не длиннее 30 секунд,
 * а телефон записывает m4a/aac произвольной длины. Поэтому голосовое переводится в wav
 * 16 кГц моно и режется на куски по 25 секунд. Всё — во временных файлах, не в памяти.
 */
@Component
public class AudioConverter {

    /** Кусок короче лимита сервиса (30 с) с запасом. */
    public static final int CHUNK_SECONDS = 25;
    private static final long PROCESS_TIMEOUT_SECONDS = 180;

    /** ffmpeg нет на машине (например, локальный запуск без Docker). */
    public static class ToolMissingException extends IOException {
        public ToolMissingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Длительность записи в секундах; null — если определить не удалось. */
    public Double durationSeconds(Path audio) throws IOException {
        String output = run(List.of(
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=nw=1:nk=1",
                audio.toString()
        )).trim();

        try {
            return output.isEmpty() ? null : Double.parseDouble(output);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Перевести запись в wav 16 кГц моно и нарезать на куски по {@link #CHUNK_SECONDS} секунд.
     * Берутся только первые maxSeconds. Куски возвращаются по порядку.
     */
    public List<Path> toWavChunks(Path audio, Path workDir, int maxSeconds) throws IOException {
        run(List.of(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-i", audio.toString(),
                "-vn",
                "-t", String.valueOf(maxSeconds),
                "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le",
                "-f", "segment",
                "-segment_time", String.valueOf(CHUNK_SECONDS),
                "-reset_timestamps", "1",
                workDir.resolve("chunk_%03d.wav").toString()
        ));

        try (Stream<Path> files = Files.list(workDir)) {
            List<Path> chunks = new ArrayList<>(files
                    .filter(path -> path.getFileName().toString().startsWith("chunk_"))
                    .sorted()
                    .toList());

            if (chunks.isEmpty()) {
                throw new IOException("ffmpeg не создал ни одного куска записи");
            }

            return chunks;
        }
    }

    private String run(List<String> command) throws IOException {
        Process process;

        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            throw new ToolMissingException(command.get(0) + " не установлен", ex);
        }

        try {
            byte[] output = process.getInputStream().readAllBytes();

            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(command.get(0) + " не уложился в " + PROCESS_TIMEOUT_SECONDS + " с");
            }

            String text = new String(output, StandardCharsets.UTF_8);

            if (process.exitValue() != 0) {
                throw new IOException(command.get(0) + " завершился с кодом " + process.exitValue() + ": "
                        + text.substring(0, Math.min(text.length(), 300)));
            }

            return text;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException(command.get(0) + " прерван", ex);
        }
    }
}
