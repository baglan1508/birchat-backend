package kz.birchat.api.dto;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String field,
        String logoUrl,
        String initial,
        String color,
        Integer employees,
        String role,
        String roleLabel,
        String position
) {
}