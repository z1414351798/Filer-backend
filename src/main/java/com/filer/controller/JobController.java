package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.dto.JobRequest;
import com.filer.dto.JobResponse;
import com.filer.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @RequestBody JobRequest request) {
        if (request.getFileId() == null || request.getConversionType() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("fileId and conversionType are required"));
        }
        JobResponse resp = jobService.createJob(request);
        return ResponseEntity.ok(ApiResponse.ok("Job created and queued", resp));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(jobService.getJob(jobId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobResponse>>> listJobs() {
        return ResponseEntity.ok(ApiResponse.ok(jobService.listAll()));
    }
}
