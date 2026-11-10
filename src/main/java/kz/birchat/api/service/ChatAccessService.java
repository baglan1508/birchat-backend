package kz.birchat.api.service;

import kz.birchat.api.auth.CurrentUserService;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.enums.ChatType;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatMemberRepository;
import kz.birchat.api.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Единые правила доступа к чатам и файлам чатов.
 *
 * - GENERAL: доступен всем активным сотрудникам компании.
 * - GROUP: доступен участникам группы и директору компании.
 * - Файл с chat_id доступен только тем, кому доступен этот чат; файл без chat_id — всей компании.
 */
@Service
@RequiredArgsConstructor
public class ChatAccessService {

    private final CurrentUserService currentUserService;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;

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

    public ChatEntity requireCompanyChat(UUID companyId, UUID chatId) {
        return chatRepository.findById(chatId)
                .filter(chat -> companyId.equals(chat.getCompany().getId()))
                .orElseThrow(() -> ApiException.notFound(
                        ApiErrorCode.CHAT_NOT_FOUND,
                        "Чат не найден"
                ));
    }

    public boolean canAccess(ChatEntity chat, CompanyMemberEntity member, UUID userId) {
        if (isGeneral(chat) || CurrentUserService.isDirector(member)) {
            return true;
        }

        return chatMemberRepository.existsByChatIdAndUserId(chat.getId(), userId);
    }

    /**
     * Файл компании (без чата) виден всем сотрудникам, файл группы — только её участникам и директору.
     */
    public boolean canAccessFile(CompanyFileEntity file, CompanyMemberEntity member, UUID userId) {
        ChatEntity chat = file.getChat();

        if (chat == null) {
            return true;
        }

        return canAccess(chat, member, userId);
    }

    public static boolean isGeneral(ChatEntity chat) {
        return ChatType.GENERAL.name().equals(chat.getType());
    }

    public static boolean isGroup(ChatEntity chat) {
        return ChatType.GROUP.name().equals(chat.getType());
    }
}
