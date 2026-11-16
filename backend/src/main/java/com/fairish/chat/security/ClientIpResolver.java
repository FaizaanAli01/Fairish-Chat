package com.fairish.chat.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves the client IP used for rate limiting without trusting arbitrary
 * {@code X-Forwarded-For} headers.
 *
 * <p>Forwarded headers are only consulted when the immediate TCP peer is a
 * configured trusted proxy (for example a load balancer or the platform edge).
 * The header is then walked from right to left so a client cannot spoof an
 * arbitrary left-most value. When no trusted proxy matches, the socket address
 * is used verbatim.</p>
 */
@Component
public class ClientIpResolver {

    /**
     * Loopback and RFC1918/ULA ranges are trusted by default because the
     * application is normally reached through a reverse proxy on a private
     * network. Override with TRUSTED_PROXIES when the proxy uses other ranges.
     * Set the property to an empty string to trust no proxy at all.
     */
    public static final String DEFAULT_TRUSTED_PROXIES =
            "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(
            @Value("${app.rate-limit.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}")
            String trustedProxies) {
        this.trustedProxies = parseTrustedProxies(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwardedFor)) {
            return remoteAddress;
        }

        String[] candidates = forwardedFor.split(",");
        for (int i = candidates.length - 1; i >= 0; i--) {
            String candidate = candidates[i].trim();
            if (!isValidIpAddress(candidate)) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }

        // Every hop was a trusted proxy. Return the left-most valid address so
        // the rate limiter still has a stable, non-empty key.
        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if (isValidIpAddress(trimmed)) {
                return trimmed;
            }
        }
        return remoteAddress;
    }

    private List<IpAddressMatcher> parseTrustedProxies(String configured) {
        if (!StringUtils.hasText(configured)) {
            return List.of();
        }
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String entry : configured.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(trimmed));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "Invalid TRUSTED_PROXIES entry [" + trimmed + "]. Use IP addresses or CIDR ranges.", ex);
            }
        }
        return List.copyOf(matchers);
    }

    private boolean isTrustedProxy(String address) {
        if (!isValidIpAddress(address)) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidIpAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.indexOf(':') >= 0 && IPV6_PATTERN.matcher(trimmed).matches();
    }
}
