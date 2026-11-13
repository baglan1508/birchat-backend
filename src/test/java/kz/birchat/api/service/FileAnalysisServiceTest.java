package kz.birchat.api.service;

import kz.birchat.api.ai.AiMediaResult;
import kz.birchat.api.ai.AiProvider;
import kz.birchat.api.ai.AiProviderResolver;
import kz.birchat.api.config.AiProperties;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.service.media.AudioConverter;
import kz.birchat.api.service.media.TestPdfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileAnalysisServiceTest {

    private static final String KEY = "companies/c/2026-11-12/photo.jpg";
    private static final String SIGNED_URL = "https://storage.example/signed/photo.jpg?token=t";

    @Mock
    private CompanyFileRepository companyFileRepository;

    @Mock
    private SupabaseStorageService storageService;

    @Mock
    private AiProviderResolver aiProviderResolver;

    @Mock
    private AiProvider aiProvider;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private AudioConverter audioConverter;

    private FileAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new FileAnalysisService(
                companyFileRepository,
                storageService,
                aiProviderResolver,
                new AiProperties(),
                transactionManager,
                audioConverter
        );
    }

    @Test
    void photoGetsDescription() {
        CompanyFileEntity photo = pendingFile("image/jpeg", 1_500_000L);
        when(companyFileRepository.findPendingForAi(any(), any(Pageable.class))).thenReturn(List.of(photo));
        when(companyFileRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(storageService.createSignedUrls(List.of(KEY), 600)).thenReturn(Map.of(KEY, SIGNED_URL));
        when(aiProviderResolver.resolve()).thenReturn(aiProvider);
        when(aiProvider.describeImage(SIGNED_URL))
                .thenReturn(new AiMediaResult("Накладная №15 от ТОО «Арман» на 150 000 тг", "glm-4.6v"));

        assertEquals(1, service.processPending());

        assertEquals(FileAnalysisService.AI_DONE, photo.getAiStatus());
        assertEquals("Накладная №15 от ТОО «Арман» на 150 000 тг", photo.getAiText());
        assertEquals("glm-4.6v", photo.getAiModel());
        assertNotNull(photo.getAiProcessedAt());
        verify(companyFileRepository).save(photo);
    }

    @Test
    void unsupportedFormatIsSkippedWithoutCallingModel() {
        CompanyFileEntity heic = pendingFile("image/heic", 1_000_000L);
        when(companyFileRepository.findById(heic.getId())).thenReturn(Optional.of(heic));

        service.process(heic.getId());

        assertEquals(FileAnalysisService.AI_SKIPPED, heic.getAiStatus());
        assertTrue(heic.getAiError().contains("image/heic"));
        verifyNoInteractions(storageService, aiProviderResolver);
    }

    @Test
    void photoOverFiveMegabytesIsSkipped() {
        CompanyFileEntity big = pendingFile("image/jpeg", 6L * 1024 * 1024);
        when(companyFileRepository.findById(big.getId())).thenReturn(Optional.of(big));

        service.process(big.getId());

        assertEquals(FileAnalysisService.AI_SKIPPED, big.getAiStatus());
        verifyNoInteractions(storageService, aiProviderResolver);
    }

    @Test
    void failedAttemptIsRetriedLaterAndGivesUpAfterThree() {
        CompanyFileEntity photo = pendingFile("image/png", 500_000L);
        when(companyFileRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(storageService.createSignedUrls(List.of(KEY), 600)).thenReturn(Map.of(KEY, SIGNED_URL));
        when(aiProviderResolver.resolve()).thenReturn(aiProvider);
        when(aiProvider.describeImage(anyString())).thenThrow(new IllegalStateException("AI provider временно недоступен"));

        service.process(photo.getId());

        assertEquals(FileAnalysisService.AI_PENDING, photo.getAiStatus());
        assertEquals(1, photo.getAiAttempts());
        assertNotNull(photo.getAiNextAttemptAt());

        service.process(photo.getId());
        service.process(photo.getId());

        assertEquals(FileAnalysisService.AI_FAILED, photo.getAiStatus());
        assertEquals(3, photo.getAiAttempts());
        assertNull(photo.getAiNextAttemptAt());
        assertTrue(photo.getAiError().contains("временно недоступен"));
    }

    @Test
    void alreadyProcessedFileIsLeftAlone() {
        CompanyFileEntity done = pendingFile("image/jpeg", 1_000L);
        done.setAiStatus(FileAnalysisService.AI_DONE);
        when(companyFileRepository.findById(done.getId())).thenReturn(Optional.of(done));

        service.process(done.getId());

        verify(companyFileRepository, never()).save(any());
        verifyNoInteractions(aiProviderResolver);
    }

    @Test
    void photosAndPdfsAreQueued() {
        CompanyFileEntity photo = new CompanyFileEntity();
        photo.setContentType("image/png");
        CompanyFileEntity pdf = new CompanyFileEntity();
        pdf.setContentType("application/pdf");
        CompanyFileEntity docx = new CompanyFileEntity();
        docx.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        CompanyFileEntity voice = new CompanyFileEntity();
        voice.setContentType("audio/m4a");

        FileAnalysisService.markForAnalysisIfSupported(photo);
        FileAnalysisService.markForAnalysisIfSupported(pdf);
        FileAnalysisService.markForAnalysisIfSupported(docx);
        FileAnalysisService.markForAnalysisIfSupported(voice);

        assertEquals(FileAnalysisService.AI_PENDING, photo.getAiStatus());
        assertEquals(FileAnalysisService.AI_PENDING, pdf.getAiStatus());
        assertNull(docx.getAiStatus());
        assertEquals(FileAnalysisService.AI_PENDING, voice.getAiStatus());
    }

    @Test
    void pdfTextIsReadWithoutModel() throws Exception {
        CompanyFileEntity pdf = pendingFile("application/pdf", 50_000L);
        when(companyFileRepository.findById(pdf.getId())).thenReturn(Optional.of(pdf));
        doAnswer(invocation -> {
            TestPdfs.textPdf(invocation.getArgument(1), "Kaspi Gold receipt", "Item A 2 x 500 = 1000", "TOTAL 1000 KZT");
            return null;
        }).when(storageService).downloadToFile(eq(KEY), any(Path.class));

        service.process(pdf.getId());

        assertEquals(FileAnalysisService.AI_DONE, pdf.getAiStatus());
        assertEquals(FileAnalysisService.PDF_TEXT_MODEL, pdf.getAiModel());
        assertTrue(pdf.getAiText().startsWith("PDF, 1 стр.:\n"), pdf.getAiText());
        assertTrue(pdf.getAiText().contains("Item A 2 x 500 = 1000"), pdf.getAiText());
        verifyNoInteractions(aiProviderResolver);
    }

    @Test
    void scannedPdfPagesAreReadByModelAndTemporaryPagesDeleted() throws Exception {
        System.setProperty("java.awt.headless", "true");
        CompanyFileEntity scan = pendingFile("application/pdf", 300_000L);
        String pageKey = KEY + ".ai-page-1.jpg";
        when(companyFileRepository.findById(scan.getId())).thenReturn(Optional.of(scan));
        doAnswer(invocation -> {
            TestPdfs.blankPdf(invocation.getArgument(1));
            return null;
        }).when(storageService).downloadToFile(eq(KEY), any(Path.class));
        when(storageService.createSignedUrls(List.of(pageKey), 600)).thenReturn(Map.of(pageKey, SIGNED_URL));
        when(aiProviderResolver.resolve()).thenReturn(aiProvider);
        when(aiProvider.describeImage(SIGNED_URL))
                .thenReturn(new AiMediaResult("Что на фото: Накладная\nТекст на фото: №15", "glm-4.6v"));

        service.process(scan.getId());

        assertEquals(FileAnalysisService.AI_DONE, scan.getAiStatus());
        assertEquals("PDF-скан, 1 стр.:\nЧто на фото: Накладная\nТекст на фото: №15", scan.getAiText());
        verify(storageService).uploadBytes(eq(pageKey), any(byte[].class), eq("image/jpeg"));
        verify(storageService).deleteObjects(List.of(pageKey));
    }

    @Test
    void nothingToDoWhenQueueIsEmpty() {
        when(companyFileRepository.findPendingForAi(any(), any(Pageable.class))).thenReturn(List.of());

        assertEquals(0, service.processPending());
        verifyNoInteractions(aiProviderResolver, storageService);
    }

    @Test
    void voiceIsTranscribedChunkByChunkWithPreviousTextAsHint() throws Exception {
        CompanyFileEntity voice = pendingFile("audio/m4a", 400_000L);
        when(companyFileRepository.findById(voice.getId())).thenReturn(Optional.of(voice));
        when(audioConverter.durationSeconds(any(Path.class))).thenReturn(52.0);
        Path first = Path.of("chunk_000.wav");
        Path second = Path.of("chunk_001.wav");
        when(audioConverter.toWavChunks(any(Path.class), any(Path.class), eq(600))).thenReturn(List.of(first, second));
        when(aiProviderResolver.resolve()).thenReturn(aiProvider);
        when(aiProvider.transcribeAudio(first, "")).thenReturn(new AiMediaResult("Ребята, завтра в 10 планёрка.", "glm-asr-2512"));
        when(aiProvider.transcribeAudio(second, "Ребята, завтра в 10 планёрка."))
                .thenReturn(new AiMediaResult("Возьмите отчёты по складу.", "glm-asr-2512"));

        service.process(voice.getId());

        assertEquals(FileAnalysisService.AI_DONE, voice.getAiStatus());
        assertEquals("Расшифровка (0:52):\nРебята, завтра в 10 планёрка. Возьмите отчёты по складу.", voice.getAiText());
        assertEquals("glm-asr-2512", voice.getAiModel());
        verify(storageService).downloadToFile(eq(KEY), any(Path.class));
    }

    @Test
    void silenceIsDoneNotRetried() throws Exception {
        CompanyFileEntity voice = pendingFile("audio/aac", 20_000L);
        when(companyFileRepository.findById(voice.getId())).thenReturn(Optional.of(voice));
        when(audioConverter.durationSeconds(any(Path.class))).thenReturn(3.0);
        when(audioConverter.toWavChunks(any(Path.class), any(Path.class), eq(600))).thenReturn(List.of(Path.of("chunk_000.wav")));
        when(aiProviderResolver.resolve()).thenReturn(aiProvider);
        when(aiProvider.transcribeAudio(any(Path.class), eq(""))).thenReturn(new AiMediaResult("", "glm-asr-2512"));

        service.process(voice.getId());

        assertEquals(FileAnalysisService.AI_DONE, voice.getAiStatus());
        assertEquals("Расшифровка (0:03):\nречь не распознана", voice.getAiText());
    }

    @Test
    void voiceIsSkippedWhenFfmpegIsMissing() throws Exception {
        CompanyFileEntity voice = pendingFile("audio/m4a", 20_000L);
        when(companyFileRepository.findById(voice.getId())).thenReturn(Optional.of(voice));
        when(audioConverter.durationSeconds(any(Path.class)))
                .thenThrow(new AudioConverter.ToolMissingException("ffprobe не установлен", null));

        service.process(voice.getId());

        assertEquals(FileAnalysisService.AI_SKIPPED, voice.getAiStatus());
        assertEquals("ffprobe не установлен", voice.getAiError());
        verifyNoInteractions(aiProviderResolver);
    }

    @Test
    void longVoiceHeaderSaysOnlyBeginningWasTranscribed() {
        assertEquals("Расшифровка (12:05, расшифрованы первые 10:00):\n", FileAnalysisService.voiceHeader(725.0, 600));
        assertEquals("Расшифровка:\n", FileAnalysisService.voiceHeader(null, 600));
    }

    private CompanyFileEntity pendingFile(String contentType, long size) {
        CompanyFileEntity file = new CompanyFileEntity();
        file.setId(UUID.randomUUID());
        file.setContentType(contentType);
        file.setFileSize(size);
        file.setStorageKey(KEY);
        file.setStatus("READY");
        file.setAiStatus(FileAnalysisService.AI_PENDING);
        return file;
    }
}
