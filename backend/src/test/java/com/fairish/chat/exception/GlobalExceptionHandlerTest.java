package com.fairish.chat.exception;

import com.fairish.chat.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private static final String GENERIC_DB_MESSAGE =
            "Database service is currently unavailable. Please try again later.";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleDataAccessException should not reveal authentication details")
    void testAuthFailure() {
        DataAccessException ex = new DataAccessException(
                "Authentication failed for user", new RuntimeException("bad credentials")) {
        };
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GENERIC_DB_MESSAGE, response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("username"));
        assertFalse(response.getBody().getMessage().contains("password"));
    }

    @Test
    @DisplayName("handleDataAccessException should not reveal connection timeout details")
    void testConnectionTimeout() {
        DataAccessException ex = new DataAccessException(
                "Timed out after 5000 ms while waiting for server",
                new RuntimeException("serverSelectionTimeout")) {
        };
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GENERIC_DB_MESSAGE, response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("IP address"));
    }

    @Test
    @DisplayName("handleMultipartException should explain that uploads need multipart/form-data")
    void testMultipartFailure() {
        MultipartException ex = new MultipartException("Current request is not a multipart request");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/me/avatar");

        ResponseEntity<ErrorResponse> response = handler.handleMultipartException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("multipart/form-data"));
    }

    @Test
    @DisplayName("handleDataAccessException should not reveal DNS details")
    void testDnsFailure() {
        DataAccessException ex = new DataAccessException(
                "Failed looking up TXT record for host", new RuntimeException("unknownhost")) {
        };
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GENERIC_DB_MESSAGE, response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("hostname"));
    }

    @Test
    @DisplayName("handleDuplicateKeyException should return 409 instead of a database outage")
    void testDuplicateKeyConflict() {
        DuplicateKeyException ex = new DuplicateKeyException(
                "E11000 duplicate key error collection: fairish_chat.users index: username_1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateKeyException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertFalse(response.getBody().getMessage().contains("users"));
        assertFalse(response.getBody().getMessage().contains("username_1"));
    }

    @Test
    @DisplayName("handleRateLimitExceededException should return 429 with Retry-After")
    void testRateLimitExceeded() {
        RateLimitExceededException ex =
                new RateLimitExceededException("Too many login attempts. Please try again later.");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        ResponseEntity<ErrorResponse> response =
                handler.handleRateLimitExceededException(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("60", response.getHeaders().getFirst("Retry-After"));
        assertNotNull(response.getBody());
        assertEquals(429, response.getBody().getStatus());
    }
}
