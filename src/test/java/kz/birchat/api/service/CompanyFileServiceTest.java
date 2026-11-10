package kz.birchat.api.service;

import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyFileServiceTest {

    @Mock
    private CompanyFileRepository companyFileRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private ChatAccessService chatAccessService;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private CompanyFileService companyFileService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    @Test
    void outsiderCannotUploadFile() {
        mockOutsider();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> companyFileService.uploadFile(
                        companyId, outsiderId, null, multipartFile
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, exception.getCode());

        verifyNoInteractions(supabaseStorageService);
        verifyNoInteractions(companyFileRepository);
        verifyNoInteractions(companyRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void outsiderCannotGetSignedUrl() {
        mockOutsider();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> companyFileService.getDownloadUrl(
                        companyId, fileId, outsiderId, 300
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, exception.getCode());

        verifyNoInteractions(supabaseStorageService);
        verifyNoInteractions(companyFileRepository);
    }

    private void mockOutsider() {
        when(companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(
                        companyId, outsiderId, "ACTIVE"
                ))
                .thenReturn(Optional.empty());
    }
}
