package com.filer.controller;

import com.filer.mapper.JobMapper;
import com.filer.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final JobMapper jobMapper;
    private final UserService userService;

    public StatsController(JobMapper jobMapper, UserService userService) {
        this.jobMapper = jobMapper;
        this.userService = userService;
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
        com.filer.model.User u = userService.findByUsername(user.getUsername());
        return u != null ? u.getId() : -1L;
    }
}
