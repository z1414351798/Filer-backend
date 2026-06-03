package com.filer.controller;

import com.filer.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mysql",  checkMysql());
        status.put("redis",  checkRedis());
        status.put("kafka",  checkKafka());
        boolean allUp = status.values().stream().allMatch(v -> "UP".equals(v));
        status.put("overall", allUp ? "UP" : "DEGRADED");
        return ApiResponse.ok(status);
    }

    private String checkMysql() {
        try { jdbcTemplate.execute("SELECT 1"); return "UP"; }
        catch (Exception e) { return "DOWN: " + e.getMessage(); }
    }

    private String checkRedis() {
        try { redisTemplate.getConnectionFactory().getConnection().ping(); return "UP"; }
        catch (Exception e) { return "DOWN: " + e.getMessage(); }
    }

    private String checkKafka() {
        try {
            kafkaTemplate.getDefaultTopic(); // just accessing the template
            return "UP";
        } catch (Exception e) { return "DOWN: " + e.getMessage(); }
    }
}
