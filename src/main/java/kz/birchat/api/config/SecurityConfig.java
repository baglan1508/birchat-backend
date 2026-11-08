package kz.birchat.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("beta")
public class SecurityConfig {
    @Bean
    SecurityFilterChain betaSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/api/health", "/api/auth/send-code", "/api/auth/verify-code",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // Explicit allowlist: an accidental future endpoint must not inherit access.
                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/me", "/api/users/me",
                                "/api/companies/my", "/api/companies/*/home",
                                "/api/companies/*/employees",
                                "/api/companies/*/chats/general/messages",
                                "/api/companies/*/files", "/api/companies/*/files/*",
                                "/api/companies/*/files/*/download-url",
                                "/api/companies/*/search",
                                "/api/companies/*/ai/history",
                                "/api/companies/*/ai/director/summary/today").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/companies",
                                "/api/companies/*/chats/general/messages",
                                "/api/companies/*/chats/general/messages/file",
                                "/api/companies/*/chats/general/read",
                                "/api/companies/*/employees",
                                "/api/companies/*/files/upload",
                                "/api/companies/*/ai/ask").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/users/me").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }
}
