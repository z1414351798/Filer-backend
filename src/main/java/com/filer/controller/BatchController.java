package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.dto.JobRequest;
import com.filer.dto.JobResponse;
import com.filer.dto.UploadResponse;
import com.filer.service.FileStorageService;
import com.filer.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final FileStorageService fileStorageService;
    private final JobService jobService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<List<UploadResponse>>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files) throws Exception {
        List<UploadResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(fileStorageService.store(file));
        }
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<List<JobResponse>>> createBatch(
            @RequestBody List<JobRequest> requests) {
        List<JobResponse> responses = new ArrayList<>();
        for (JobRequest req : requests) {
            responses.add(jobService.createJob(req));
        }
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
