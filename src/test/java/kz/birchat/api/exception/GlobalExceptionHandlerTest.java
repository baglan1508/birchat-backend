package kz.birchat.api.exception;

import kz.birchat.api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrongHttpMethodIs405NotServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/companies");

        ResponseEntity<ErrorResponse> response = handler.handleException(
                new HttpRequestMethodNotSupportedException("GET"),
                request
        );

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(ApiErrorCode.METHOD_NOT_ALLOWED.name(), response.getBody().code());
    }

    @Test
    void unknownAddressIs404NotServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/unknown");

        ResponseEntity<ErrorResponse> response = handler.handleException(
                new ResponseStatusException(HttpStatus.NOT_FOUND),
                request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ApiErrorCode.NOT_FOUND.name(), response.getBody().code());
    }

    @Test
    void malformedJsonIs400() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/companies");

        ResponseEntity<ErrorResponse> response = handler.handleNotReadableException(
                new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0])),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ApiErrorCode.BAD_REQUEST.name(), response.getBody().code());
    }

    @Test
    void unexpectedErrorStays500() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/companies/my");

        ResponseEntity<ErrorResponse> response = handler.handleException(
                new IllegalStateException("boom"),
                request
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ApiErrorCode.INTERNAL_ERROR.name(), response.getBody().code());
    }
}
