package kz.birchat.api.service;

import kz.birchat.api.dto.CompanyFileResponse;
import kz.birchat.api.dto.CreateUploadUrlRequest;
import kz.birchat.api.dto.DownloadUrlItem;
import kz.birchat.api.dto.DownloadUrlsRequest;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.RoleEntity;
import kz.birchat.api.entity.UserEntity;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

    // ---- прямая загрузка ----

    private final UUID memberId = UUID.randomUUID();

    @Test
    void outsiderCannotGetUploadUrl() {
        mockOutsider();

        ApiException exception = assertThrows(ApiException.class, () -> companyFileService.createUploadUrl(
                companyId, outsiderId, new CreateUploadUrlRequest("a.jpg", "image/jpeg", 100L, null)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(supabaseStorageService, companyFileRepository);
    }

    @Test
    void executablesAreNotAllowed() {
        mockMember();

        ApiException exception = assertThrows(ApiException.class, () -> companyFileService.createUploadUrl(
                companyId, memberId, new CreateUploadUrlRequest("a.exe", "application/x-msdownload", 100L, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(supabaseStorageService, companyFileRepository);
    }

    @Test
    void filesOver20MbAreRejectedBeforeUpload() {
        mockMember();

        ApiException exception = assertThrows(ApiException.class, () -> companyFileService.createUploadUrl(
                companyId, memberId, new CreateUploadUrlRequest("v.mp4", "video/mp4", 21L * 1024 * 1024, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(supabaseStorageService);
    }

    @Test
    void uploadUrlCreatesPendingFile() {
        mockMember();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company()));
        when(userRepository.findById(memberId)).thenReturn(Optional.of(user(memberId)));
        when(supabaseStorageService.normalizeFileName("photo.JPG")).thenReturn("photo.JPG");
        when(supabaseStorageService.buildStoredFileName("photo.JPG")).thenReturn("x.jpg");
        when(supabaseStorageService.buildStorageKey(companyId, "x.jpg")).thenReturn("companies/c/x.jpg");
        when(supabaseStorageService.createSignedUploadUrl("companies/c/x.jpg")).thenReturn("https://s/upload?token=t");

        var response = companyFileService.createUploadUrl(
                companyId, memberId, new CreateUploadUrlRequest("photo.JPG", "Image/JPEG", 1000L, null));

        assertEquals("https://s/upload?token=t", response.uploadUrl());
        assertEquals("PUT", response.method());
        assertEquals("image/jpeg", response.headers().get("Content-Type"));

        verify(companyFileRepository).save(argThat(file ->
                CompanyFileService.STATUS_PENDING.equals(file.getStatus())
                        && "companies/c/x.jpg".equals(file.getStorageKey())));
    }

    @Test
    void onlyUploaderCanCompleteUpload() {
        mockMember();
        CompanyFileEntity file = pendingFile(UUID.randomUUID());
        when(companyFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        ApiException exception = assertThrows(ApiException.class,
                () -> companyFileService.completeUpload(companyId, fileId, memberId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verifyNoInteractions(supabaseStorageService);
    }

    @Test
    void completeFailsWhenFileWasNotUploaded() {
        mockMember();
        when(companyFileRepository.findById(fileId)).thenReturn(Optional.of(pendingFile(memberId)));
        when(supabaseStorageService.getObjectInfo("k")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> companyFileService.completeUpload(companyId, fileId, memberId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(companyFileRepository, never()).save(any());
    }

    @Test
    void completeUsesRealSizeAndTypeFromStorage() {
        mockMember();
        when(companyFileRepository.findById(fileId)).thenReturn(Optional.of(pendingFile(memberId)));
        when(supabaseStorageService.getObjectInfo("k"))
                .thenReturn(Optional.of(new SupabaseStorageService.StoredObjectInfo(1234L, "image/png")));
        when(companyFileRepository.save(any(CompanyFileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyFileResponse response = companyFileService.completeUpload(companyId, fileId, memberId);

        assertEquals(1234L, response.fileSize());
        assertEquals("image/png", response.contentType());
        verify(companyFileRepository).save(argThat(f -> CompanyFileService.STATUS_READY.equals(f.getStatus())));
    }

    @Test
    void oversizedUploadIsDeleted() {
        mockMember();
        CompanyFileEntity file = pendingFile(memberId);
        when(companyFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(supabaseStorageService.getObjectInfo("k"))
                .thenReturn(Optional.of(new SupabaseStorageService.StoredObjectInfo(30L * 1024 * 1024, "video/mp4")));

        assertThrows(ApiException.class, () -> companyFileService.completeUpload(companyId, fileId, memberId));

        verify(supabaseStorageService).deleteObjects(List.of("k"));
        verify(companyFileRepository).delete(file);
    }

    @Test
    void downloadUrlsSkipFilesUserCannotSee() {
        CompanyMemberEntity member = mockMember();

        CompanyFileEntity visible = readyFile(UUID.randomUUID(), "k1");
        CompanyFileEntity hidden = readyFile(UUID.randomUUID(), "k2");
        CompanyFileEntity pending = pendingFile(memberId);

        when(companyFileRepository.findAllById(any())).thenReturn(List.of(visible, hidden, pending));
        when(chatAccessService.canAccessFile(visible, member, memberId)).thenReturn(true);
        when(chatAccessService.canAccessFile(hidden, member, memberId)).thenReturn(false);
        when(supabaseStorageService.normalizeDownloadExpiresIn(null)).thenReturn(300);
        when(supabaseStorageService.createSignedUrls(List.of("k1"), 300)).thenReturn(Map.of("k1", "https://s/k1"));

        List<DownloadUrlItem> urls = companyFileService.getDownloadUrls(
                companyId, memberId,
                new DownloadUrlsRequest(List.of(visible.getId(), hidden.getId(), pending.getId()), null));

        assertEquals(1, urls.size());
        assertEquals(visible.getId(), urls.get(0).fileId());
        assertEquals("https://s/k1", urls.get(0).downloadUrl());
    }

    private CompanyMemberEntity mockMember() {
        RoleEntity role = new RoleEntity();
        role.setCode("EMPLOYEE");

        CompanyMemberEntity member = new CompanyMemberEntity();
        member.setRole(role);

        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(companyId, memberId, "ACTIVE"))
                .thenReturn(Optional.of(member));
        return member;
    }

    private CompanyEntity company() {
        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        return company;
    }

    private UserEntity user(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setDisplayName("Тест");
        return user;
    }

    private CompanyFileEntity pendingFile(UUID uploaderId) {
        CompanyFileEntity file = readyFile(fileId, "k");
        file.setUploadedBy(user(uploaderId));
        file.setStatus(CompanyFileService.STATUS_PENDING);
        return file;
    }

    private CompanyFileEntity readyFile(UUID id, String storageKey) {
        CompanyFileEntity file = new CompanyFileEntity();
        file.setId(id);
        file.setCompany(company());
        file.setUploadedBy(user(memberId));
        file.setStorageKey(storageKey);
        file.setFileName("x.jpg");
        file.setOriginalFileName("photo.jpg");
        file.setContentType("image/jpeg");
        file.setFileSize(10L);
        file.setStatus(CompanyFileService.STATUS_READY);
        return file;
    }
}
