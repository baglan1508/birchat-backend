package kz.birchat.api.dto;

import java.util.UUID;

/** Собеседник в личном чате. */
public record ChatPeerResponse(
        UUID userId,
        String fullName,
        String displayName,
        String initials,
        String avatarUrl
) {
}
