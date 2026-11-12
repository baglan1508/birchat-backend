package kz.birchat.api.service;

import kz.birchat.api.ai.AiMediaResult;
import kz.birchat.api.ai.AiProvider;
import kz.birchat.api.ai.AiProviderResolver;
import kz.birchat.api.config.AiProperties;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.repository.CompanyFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private FileAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new FileAnalysisService(
                companyFileRepository,
                storageService,
                aiProviderResolver,
                new AiProperties(),
                transactionManager
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
    void onlyPhotosAreQueued() {
        CompanyFileEntity photo = new CompanyFileEntity();
        photo.setContentType("image/png");
        CompanyFileEntity pdf = new CompanyFileEntity();
        pdf.setContentType("application/pdf");

        FileAnalysisService.markForAnalysisIfSupported(photo);
        FileAnalysisService.markForAnalysisIfSupported(pdf);

        assertEquals(FileAnalysisService.AI_PENDING, photo.getAiStatus());
        assertNull(pdf.getAiStatus());
    }

    @Test
    void nothingToDoWhenQueueIsEmpty() {
        when(companyFileRepository.findPendingForAi(any(), any(Pageable.class))).thenReturn(List.of());

        assertEquals(0, service.processPending());
        verifyNoInteractions(aiProviderResolver, storageService);
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
