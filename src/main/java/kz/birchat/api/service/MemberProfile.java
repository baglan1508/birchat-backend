package kz.birchat.api.service;

import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.UserEntity;

/**
 * Как сотрудник выглядит для коллег.
 *
 * Пока человек не зашёл в компанию ({@link CompanyMemberEntity#isPending()}), показываем имя,
 * под которым его добавил директор, и без фото: иначе по номеру телефона можно узнать,
 * зарегистрирован ли человек в BirChat и как он себя подписал.
 */
public record MemberProfile(
        String fullName,
        String displayName,
        String initials,
        String avatarUrl
) {

    public static MemberProfile of(UserEntity user, CompanyMemberEntity membership) {
        if (membership != null && membership.isPending()
                && membership.getInvitedName() != null && !membership.getInvitedName().isBlank()) {
            String name = membership.getInvitedName().trim();
            return new MemberProfile(name, displayName(name), initials(name), null);
        }

        return new MemberProfile(
                user.getFullName(),
                user.getDisplayName(),
                user.getInitials(),
                user.getAvatarUrl()
        );
    }

    public static String displayName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Пользователь";
        }

        return fullName.trim().split("\\s+")[0];
    }

    public static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "П";
        }

        String[] parts = fullName.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }

        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
}
