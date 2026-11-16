package com.fairish.chat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimiterService rateLimiterService;
    private ClientIpResolver clientIpResolver;
    private RateLimitingFilter rateLimitingFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        clientIpResolver = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES);
        rateLimitingFilter = new RateLimitingFilter(rateLimiterService, clientIpResolver);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Should allow requests under rate limit and proceed through chain")
    void shouldAllowUnderLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject login requests exceeding rate limit with HTTP 429")
    void shouldRejectWhenLimitExceeded() throws ServletException, IOException {
        String clientIp = "10.0.0.50";

        // Login limit is 10 requests per minute
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, resp, filterChain);
            assertEquals(200, resp.getStatus());
        }

        // 11th request must be rejected with 429
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedReq.setRemoteAddr(clientIp);
        MockHttpServletResponse blockedResp = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(blockedReq, blockedResp, filterChain);

        assertEquals(429, blockedResp.getStatus());
        assertEquals("60", blockedResp.getHeader("Retry-After"));
        verify(filterChain, times(10)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Non-auth endpoints should bypass rate limiting completely")
    void shouldBypassNonAuthEndpoints() throws ServletException, IOException {
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/conversations");
            req.setRemoteAddr("10.0.0.99");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, resp, filterChain);
            assertEquals(200, resp.getStatus());
        }
        verify(filterChain, times(20)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should ignore X-Forwarded-For from an untrusted peer so it cannot be spoofed")
    void shouldIgnoreForwardedHeaderFromUntrustedPeer() throws ServletException, IOException {
        // The direct peer is public, so the header must not change the limiter key.
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("203.0.113.5");
            req.addHeader("X-Forwarded-For", "198.51.100." + i);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, resp, filterChain);
            assertEquals(200, resp.getStatus());
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/auth/login");
        blocked.setRemoteAddr("203.0.113.5");
        blocked.addHeader("X-Forwarded-For", "198.51.100.250");
        MockHttpServletResponse blockedResp = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(blocked, blockedResp, filterChain);

        assertEquals(429, blockedResp.getStatus());
    }

    @Test
    @DisplayName("Should honour X-Forwarded-For from a trusted proxy")
    void shouldHonourForwardedHeaderFromTrustedProxy() throws ServletException, IOException {
        String clientIp = "198.51.100.20";

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", clientIp);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, resp, filterChain);
            assertEquals(200, resp.getStatus());
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/auth/login");
        blocked.setRemoteAddr("10.0.0.1");
        blocked.addHeader("X-Forwarded-For", clientIp);
        MockHttpServletResponse blockedResp = new MockHttpServletResponse();
        rateLimitingFilter.doFilter(blocked, blockedResp, filterChain);

        assertEquals(429, blockedResp.getStatus());

        // A different client behind the same proxy must still be allowed.
        MockHttpServletRequest otherClient = new MockHttpServletRequest("POST", "/api/auth/login");
        otherClient.setRemoteAddr("10.0.0.1");
        otherClient.addHeader("X-Forwarded-For", "198.51.100.21");
        MockHttpServletResponse otherResp = new MockHttpServletResponse();
        rateLimitingFilter.doFilter(otherClient, otherResp, filterChain);

        assertEquals(200, otherResp.getStatus());
    }

    @Test
    @DisplayName("Should reject an oversized authentication payload with HTTP 413")
    void shouldRejectOversizedAuthPayload() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.100");
        request.setContent(new byte[17 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(request, response, filterChain);

        assertEquals(413, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should only rate limit exact authentication endpoints")
    void shouldNotRateLimitLookalikePaths() throws ServletException, IOException {
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login/extra");
            req.setRemoteAddr("10.0.0.77");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, resp, filterChain);
            assertEquals(200, resp.getStatus());
        }
        verify(filterChain, times(20)).doFilter(any(), any());
    }
}
