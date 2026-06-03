package com.filer.controller;

import com.filer.mapper.JobMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final JobMapper jobMapper;

    public StatsController(JobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    @GetMapping("/conversions")
    public Map<String, Object> getConversionStats(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "30") int days) {
        Long userId = getUserId(user);
        return Map.of(
            "totalJobs",     jobMapper.countByUserId(userId),
            "completedJobs", jobMapper.countByUserIdAndStatus(userId, "COMPLETED"),
            "failedJobs",    jobMapper.countByUserIdAndStatus(userId, "FAILED"),
            "byType",        jobMapper.countByTypeForUser(userId, days),
            "byDate",        jobMapper.countByDateForUser(userId, days)
        );
    }

    private Long getUserId(UserDetails user) {
        if (user == null) return -1L;
        try {
            return ((com.filer.model.User) user).getId();
        } catch (Exception e) { return -1L; }
    }
}
