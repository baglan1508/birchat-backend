package kz.birchat.api.dto;

public record SignedUrlResponse(
        String signedUrl,
        Integer expiresInSeconds
) {
}