package kz.birchat.api.dto;

public record TokenPairResponse(String accessToken, String refreshToken,
                                String tokenType, long expiresIn) {}
