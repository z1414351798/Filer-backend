package com.filer.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitFilter implements Filter {

    @Value("${filer.rate-limit.anonymous-per-hour:100}")
    private int anonLimit;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        // Only rate-limit API endpoints
        if (!httpReq.getRequestURI().startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }
        String ip = getClientIp(httpReq);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> buildBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(429);
            httpRes.setContentType("application/json");
            httpRes.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
        }
    }

    private Bucket buildBucket() {
        Bandwidth limit = Bandwidth.classic(anonLimit,
                Refill.greedy(anonLimit, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
