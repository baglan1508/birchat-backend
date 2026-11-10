package kz.birchat.api.service;

import kz.birchat.api.config.DataCleanupProperties;
import kz.birchat.api.repository.DataCleanupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataCleanupServiceTest {

    @Mock
    private DataCleanupRepository repository;

    private DataCleanupService service(Integer batchSize) {
        return new DataCleanupService(
                repository,
                new DataCleanupProperties(true, null, null, null, null, batchSize)
        );
    }

    @Test
    void deletesInBatchesUntilBatchIsNotFull() {
        when(repository.deleteMinuteQuotaWindowsBefore(any(), eq(100))).thenReturn(100, 100, 7);

        Map<String, Integer> report = service(100).cleanup();

        assertEquals(207, report.get("aiQuotaMinuteWindows"));
        verify(repository, times(3)).deleteMinuteQuotaWindowsBefore(any(), eq(100));
        verify(repository, times(1)).deleteAuthCodesBefore(any(), eq(100));
    }

    @Test
    void usesDefaultRetentionPeriods() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        service(null).cleanup();

        ArgumentCaptor<OffsetDateTime> minuteCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteMinuteQuotaWindowsBefore(minuteCutoff.capture(), anyInt());
        assertAround(before.minusDays(1), minuteCutoff.getValue().toLocalDateTime());

        ArgumentCaptor<OffsetDateTime> dayCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteDayQuotaWindowsBefore(dayCutoff.capture(), anyInt());
        assertAround(before.minusDays(30), dayCutoff.getValue().toLocalDateTime());

        ArgumentCaptor<LocalDateTime> tokenCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteExpiredRefreshTokensBefore(tokenCutoff.capture(), anyInt());
        assertAround(before.minusDays(7), tokenCutoff.getValue());

        ArgumentCaptor<LocalDateTime> codeCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteAuthCodesBefore(codeCutoff.capture(), eq(DataCleanupService.DEFAULT_BATCH_SIZE));
        assertAround(before.minusDays(7), codeCutoff.getValue());
    }

    @Test
    void failureInOneTableDoesNotStopTheOthers() {
        when(repository.deleteExpiredRefreshTokensBefore(any(), anyInt()))
                .thenThrow(new RuntimeException("db error"));
        when(repository.deleteAuthCodesBefore(any(), anyInt())).thenReturn(3);

        Map<String, Integer> report = service(100).cleanup();

        assertEquals(-1, report.get("expiredRefreshTokens"));
        assertEquals(3, report.get("authCodes"));
        verify(repository).deleteRevokedRefreshTokensBefore(any(), anyInt());
    }

    @Test
    void canBeDisabled() {
        DataCleanupService disabled = new DataCleanupService(
                repository,
                new DataCleanupProperties(false, null, null, null, null, null)
        );

        assertFalse(disabled.isEnabled());
        assertTrue(service(null).isEnabled());
    }

    private static void assertAround(LocalDateTime expected, LocalDateTime actual) {
        assertTrue(Duration.between(expected, actual).abs().compareTo(Duration.ofMinutes(1)) < 0,
                () -> "expected ~" + expected + " but was " + actual);
    }
}
