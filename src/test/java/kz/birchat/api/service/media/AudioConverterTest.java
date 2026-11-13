package kz.birchat.api.service.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Настоящий ffmpeg: в CI он ставится в workflow, на Render — в Docker-образе. */
@EnabledIf("ffmpegAvailable")
class AudioConverterTest {

    private final AudioConverter converter = new AudioConverter();

    static boolean ffmpegAvailable() {
        try {
            return new ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    @Test
    void voiceIsSplitIntoChunksUnderThirtySeconds(@TempDir Path dir) throws Exception {
        Path voice = recording(dir, 62);
        Path chunksDir = Files.createDirectory(dir.resolve("chunks"));

        Double duration = converter.durationSeconds(voice);
        List<Path> chunks = converter.toWavChunks(voice, chunksDir, 600);

        assertEquals(62.0, duration, 0.5);
        assertEquals(3, chunks.size());

        for (Path chunk : chunks) {
            assertTrue(chunk.getFileName().toString().endsWith(".wav"));
            assertTrue(converter.durationSeconds(chunk) < 30, chunk + " длиннее 30 секунд");
        }
    }

    @Test
    void onlyFirstMaxSecondsAreTaken(@TempDir Path dir) throws Exception {
        Path voice = recording(dir, 62);
        Path chunksDir = Files.createDirectory(dir.resolve("chunks"));

        List<Path> chunks = converter.toWavChunks(voice, chunksDir, 30);

        assertEquals(2, chunks.size());
    }

    @Test
    void brokenFileGivesClearError(@TempDir Path dir) throws Exception {
        Path broken = Files.writeString(dir.resolve("broken"), "это не аудио");
        Path chunksDir = Files.createDirectory(dir.resolve("chunks"));

        assertThrows(java.io.IOException.class, () -> converter.toWavChunks(broken, chunksDir, 600));
    }

    /** Запись как с телефона: m4a (AAC) заданной длины. */
    private static Path recording(Path dir, int seconds) throws Exception {
        Path file = dir.resolve("voice.m4a");
        Process process = new ProcessBuilder(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
                "-c:a", "aac", "-b:a", "64k", file.toString()
        ).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertEquals(0, process.waitFor());
        return file;
    }
}
