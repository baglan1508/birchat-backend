package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.enums.ChatType;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Единые правила доступа к чатам и файлам чатов.
 *
 * - GENERAL: доступен всем активным сотрудникам компании.
 * - GROUP: доступен участникам группы и директору компании.
 * - DIRECT: только двум участникам; директор чужие личные чаты не видит.
 * - Файл с chat_id доступен только тем, кому доступен этот чат; файл без chat_id — всей компании.
 *   Пересланный файл доступен и тем, кому доступен чат, куда его переслали.
 */
@Service
@RequiredArgsConstructor
public class ChatAccessService {

    private final CurrentUserService currentUserService;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final CompanyMemberRepository companyMemberRepository;

    public ChatEntity requireGeneralChat(UUID companyId) {
        return chatRepository.findByCompanyIdAndType(companyId, ChatType.GENERAL.name())
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.CHAT_NOT_FOUND,
                        "Общий чат не найден"
                ));
    }

    /**
     * Проверяет, что пользователь — активный сотрудник и имеет доступ к чату.
     */
    public ChatEntity requireReadableChat(UUID companyId, UUID chatId, UUID userId) {
        CompanyMemberEntity member = currentUserService.requireActiveMember(companyId, userId);
        ChatEntity chat = requireCompanyChat(companyId, chatId);

        if (!canAccess(chat, member, userId)) {
            throw ApiException.forbidden(
                    ApiErrorCode.CHAT_ACCESS_DENIED,
                    "Вы не состоите в этом чате"
            );
        }

        return chat;
    }

    /**
     * Как {@link #requireReadableChat}, но для отправки: в личный чат нельзя писать,
     * если собеседника убрали из компании (переписка остаётся видна).
     */
    public ChatEntity requireWritableChat(UUID companyId, UUID chatId, UUID userId) {
        ChatEntity chat = requireReadableChat(companyId, chatId, userId);

        if (isDirect(chat)) {
            boolean peerActive = chatMemberRepository.findUserIdsByChatId(chat.getId()).stream()
                    .filter(id -> !id.equals(userId))
                    .allMatch(id -> companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(companyId, id, "ACTIVE"));

            if (!peerActive) {
                throw ApiException.forbidden(
                        ApiErrorCode.CHAT_ACCESS_DENIED,
                        "Собеседник больше не работает в компании"
                );
            }
        }

        return chat;
    }

    public ChatEntity requireCompanyChat(UUID companyId, UUID chatId) {
        return chatRepository.findById(chatId)
                .filter(chat -> companyId.equals(chat.getCompany().getId()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.CHAT_NOT_FOUND,
                        "Чат не найден"
                ));
    }

    public boolean canAccess(ChatEntity chat, CompanyMemberEntity member, UUID userId) {
        if (isGeneral(chat)) {
            return true;
        }

        if (!isDirect(chat) && CurrentUserService.isDirector(member)) {
            return true;
        }

        return chatMemberRepository.existsByChatIdAndUserId(chat.getId(), userId);
    }

    /**
     * Файл компании (без чата) виден всем сотрудникам, файл группы — только её участникам и директору.
     */
    public boolean canAccessFile(CompanyFileEntity file, CompanyMemberEntity member, UUID userId) {
        ChatEntity chat = file.getChat();

        if (chat == null || canAccess(chat, member, userId)) {
            return true;
        }

        // Файл переслали в другой чат — он виден и там.
        return file.getId() != null && chatAttachmentRepository.findChatsByFileId(file.getId()).stream()
                .filter(other -> !other.getId().equals(chat.getId()))
                .anyMatch(other -> canAccess(other, member, userId));
    }

    public static boolean isGeneral(ChatEntity chat) {
        return ChatType.GENERAL.name().equals(chat.getType());
    }

    public static boolean isGroup(ChatEntity chat) {
        return ChatType.GROUP.name().equals(chat.getType());
    }

    public static boolean isDirect(ChatEntity chat) {
        return ChatType.DIRECT.name().equals(chat.getType());
    }
}
