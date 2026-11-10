package kz.birchat.api.service;

import kz.birchat.api.config.AuthSmsProperties;
import kz.birchat.api.entity.AuthCodeEntity;
import kz.birchat.api.repository.AuthCodeRepository;
import kz.birchat.api.service.otp.OtpSendResult;
import kz.birchat.api.service.otp.OtpSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsCodeServiceTest {

    @Mock
    private AuthCodeRepository authCodeRepository;

    @Mock
    private OtpSender otpSender;

    @Test
    void fixedCodeModeSendsNothingAndAcceptsTheSameCode() {
        SmsCodeService service = service("1111");
        when(authCodeRepository.findByPhoneAndConsumedFalse("+77071234567")).thenReturn(List.of());

        service.sendCode("+7 707 123 45 67");

        verifyNoInteractions(otpSender);

        ArgumentCaptor<AuthCodeEntity> saved = ArgumentCaptor.forClass(AuthCodeEntity.class);
        verify(authCodeRepository).save(saved.capture());
        assertEquals("fixed", saved.getValue().getProvider());

        when(authCodeRepository.findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc("+77071234567"))
                .thenReturn(java.util.Optional.of(saved.getValue()));

        assertEquals("+77071234567", service.verifyCode("+77071234567", "1111"));
    }

    @Test
    void withoutFixedCodeTheSenderIsUsed() {
        SmsCodeService service = service(null);
        when(authCodeRepository.findFirstByPhoneOrderByCreatedAtDesc("+77071234567"))
                .thenReturn(java.util.Optional.empty());
        when(authCodeRepository.findByPhoneAndConsumedFalse("+77071234567")).thenReturn(List.of());
        when(otpSender.send(eq("+77071234567"), anyString(), anyInt()))
                .thenReturn(new OtpSendResult("telegram", "req-1"));

        service.sendCode("+77071234567");

        verify(otpSender).send(eq("+77071234567"), anyString(), anyInt());
        verify(authCodeRepository).save(any(AuthCodeEntity.class));
    }

    private SmsCodeService service(String fixedCode) {
        AuthSmsProperties properties = new AuthSmsProperties(
                300, 60, 5, null, null, "test-otp-secret", fixedCode
        );
        return new SmsCodeService(authCodeRepository, properties, otpSender);
    }
}
