package com.fairish.chat.exception;

import com.fairish.chat.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleDataAccessException should identify authentication failure")
    void testAuthFailure() {
        DataAccessException ex = new DataAccessException("Authentication failed for user", new RuntimeException("bad credentials")) {};
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("username and password"));
    }

    @Test
    @DisplayName("handleDataAccessException should identify connection timeout")
    void testConnectionTimeout() {
        DataAccessException ex = new DataAccessException("Timed out after 5000 ms while waiting for server", new RuntimeException("serverSelectionTimeout")) {};
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("IP address is whitelisted"));
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
    @DisplayName("handleDataAccessException should identify DNS resolution failure")
    void testDnsFailure() {
        DataAccessException ex = new DataAccessException("Failed looking up TXT record for host", new RuntimeException("unknownhost")) {};
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("cluster hostname"));
    }
}

