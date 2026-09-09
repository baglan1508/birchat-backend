package kz.birchat.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.supabase")
public record SupabaseStorageProperties(
        String url,
        String serviceKey,
        String bucket
) {
}