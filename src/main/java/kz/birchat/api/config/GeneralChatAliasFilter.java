package kz.birchat.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import kz.birchat.api.enums.ChatType;
import kz.birchat.api.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «general» вместо chatId общего чата — в любом адресе /api/companies/{companyId}/chats/general/…
 *
 * Старые адреса общего чата (/general/messages, /general/read, /general/messages/file) обрабатываются
 * своими методами контроллера и не трогаются. Остальные переписываются на настоящий id общего чата
 * компании: /chats/general/messages/{id}/readers → /chats/{generalChatId}/messages/{id}/readers.
 *
 * Работает после проверки токена (обычный фильтр, порядок ниже фильтров безопасности),
 * поэтому запрос без входа до базы не доходит.
 */
@Component
@RequiredArgsConstructor
public class GeneralChatAliasFilter extends OncePerRequestFilter {

    static final String ALIAS = "general";

    private static final Pattern PATH = Pattern.compile(
            "^(/api/companies/([0-9a-fA-F-]{36})/chats/)general(/.*)?$"
    );

    /** У этих адресов есть собственные методы для общего чата. */
    private static final Set<String> LEGACY_SUFFIXES = Set.of("/messages", "/read", "/messages/file");

    private final ChatRepository chatRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String rewritten = rewrite(request.getRequestURI());

        chain.doFilter(rewritten == null ? request : new RewrittenRequest(request, rewritten), response);
    }

    /** Новый путь или null, если переписывать не нужно (или компании/общего чата нет). */
    String rewrite(String uri) {
        if (uri == null || !uri.contains("/chats/" + ALIAS)) {
            return null;
        }

        Matcher matcher = PATH.matcher(uri);

        if (!matcher.matches()) {
            return null;
        }

        String suffix = matcher.group(3) == null ? "" : matcher.group(3);

        if (LEGACY_SUFFIXES.contains(suffix)) {
            return null;
        }

        UUID companyId;

        try {
            companyId = UUID.fromString(matcher.group(2));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        return chatRepository.findByCompanyIdAndType(companyId, ChatType.GENERAL.name())
                .map(chat -> matcher.group(1) + chat.getId() + suffix)
                .orElse(null);
    }

    static final class RewrittenRequest extends HttpServletRequestWrapper {

        private final String uri;

        RewrittenRequest(HttpServletRequest request, String uri) {
            super(request);
            this.uri = uri;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + uri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer original = super.getRequestURL();
            String originalUri = super.getRequestURI();
            int start = original.indexOf(originalUri);

            if (start < 0) {
                return original;
            }

            return new StringBuffer(original.substring(0, start)).append(getRequestURI());
        }

        @Override
        public String getServletPath() {
            return uri;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
