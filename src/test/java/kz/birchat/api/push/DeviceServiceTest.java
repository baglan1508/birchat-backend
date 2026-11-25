package kz.birchat.api.push;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceTokenRepository repository;

    @InjectMocks
    private DeviceService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void newDeviceIsSaved() {
        when(repository.findByToken("abcdef01")).thenReturn(Optional.empty());

        service.register(userId, new RegisterDeviceRequest("ios", "ABCDEF01", "sandbox", null));

        verify(repository).save(argThat(device -> device.getUserId().equals(userId)
                && device.getToken().equals("abcdef01")
                && device.getPlatform().equals("IOS")
                && device.getEnvironment().equals("sandbox")
                && device.getLocale().equals("ru")
                && device.getId() != null));
    }

    @Test
    void sameTokenIsRebondToTheNewUser() {
        DeviceTokenEntity existing = new DeviceTokenEntity();
        existing.setId(UUID.randomUUID());
        existing.setToken("abcdef01");
        existing.setUserId(UUID.randomUUID());
        existing.setLocale("ru");
        existing.setEnvironment("sandbox");
        when(repository.findByToken("abcdef01")).thenReturn(Optional.of(existing));

        service.register(userId, new RegisterDeviceRequest("IOS", "abcdef01", "production", "kk"));

        assertEquals(userId, existing.getUserId());
        assertEquals("kk", existing.getLocale());
        assertEquals("production", existing.getEnvironment());
        verify(repository).save(existing);
    }

    @Test
    void userCannotRemoveSomeoneElsesDevice() {
        DeviceTokenEntity other = new DeviceTokenEntity();
        other.setUserId(UUID.randomUUID());
        when(repository.findByToken("ABCDEF01")).thenReturn(Optional.empty());
        when(repository.findByToken("abcdef01")).thenReturn(Optional.of(other));

        service.unregister(userId, "ABCDEF01");

        verify(repository, never()).delete(any());
    }

    @Test
    void unknownTokenIsNotAnError() {
        when(repository.findByToken("abcdef01")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.unregister(userId, "abcdef01"));
    }

    @Test
    void androidTokenIsKeptAsIsAndEnvironmentIsProduction() {
        when(repository.findByToken("fcm:Token_AbC-1")).thenReturn(Optional.empty());

        service.register(userId, new RegisterDeviceRequest("android", "fcm:Token_AbC-1", null, "ru"));

        org.mockito.ArgumentCaptor<DeviceTokenEntity> saved = org.mockito.ArgumentCaptor.forClass(DeviceTokenEntity.class);
        verify(repository).save(saved.capture());
        assertEquals("fcm:Token_AbC-1", saved.getValue().getToken());
        assertEquals("ANDROID", saved.getValue().getPlatform());
        assertEquals("production", saved.getValue().getEnvironment());
    }

    @Test
    void iosNeedsEnvironmentAndHexToken() {
        kz.birchat.api.exception.ApiException noEnvironment = assertThrows(kz.birchat.api.exception.ApiException.class,
                () -> service.register(userId, new RegisterDeviceRequest("IOS", "abcdef01", null, null)));
        assertEquals(kz.birchat.api.exception.ApiErrorCode.VALIDATION, noEnvironment.getCode());

        assertThrows(kz.birchat.api.exception.ApiException.class,
                () -> service.register(userId, new RegisterDeviceRequest("IOS", "not-hex", "production", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void xcodeEnvironmentNameAndForeignLocaleAreAccepted() {
        when(repository.findByToken("abcdef02")).thenReturn(Optional.empty());

        service.register(userId, new RegisterDeviceRequest("IOS", "ABCDEF02", "development", "en_US"));

        org.mockito.ArgumentCaptor<DeviceTokenEntity> saved = org.mockito.ArgumentCaptor.forClass(DeviceTokenEntity.class);
        verify(repository).save(saved.capture());
        assertEquals("sandbox", saved.getValue().getEnvironment());
        assertEquals("ru", saved.getValue().getLocale());
    }

    @Test
    void environmentAndLocaleNormalisation() {
        assertEquals("production", DeviceService.normalizeEnvironment("Production"));
        assertEquals("sandbox", DeviceService.normalizeEnvironment("development"));
        assertNull(DeviceService.normalizeEnvironment("staging"));
        assertEquals("kk", DeviceService.normalizeLocale("kk-KZ"));
        assertEquals("ru", DeviceService.normalizeLocale(null));
    }
}
