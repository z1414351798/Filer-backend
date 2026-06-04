package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.dto.PipelineRequest;
import com.filer.dto.PipelineStep;
import com.filer.model.FileRecord;
import com.filer.service.FileStorageService;
import com.filer.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> execute(
            @RequestBody PipelineRequest request) {
        if (request.getFileId() == null || request.getFileId().isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.error("fileId is required"));
        if (request.getSteps() == null || request.getSteps().isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.error("steps cannot be empty"));
        if (request.getSteps().size() > 10)
            return ResponseEntity.badRequest().body(ApiResponse.error("Maximum 10 steps per pipeline"));

        try {
            Path current = fileStorageService.getFilePath(request.getFileId());
            List<String> log = new ArrayList<>();
            for (PipelineStep step : request.getSteps()) {
                log.add("Executing: " + step.getConversionType());
                current = pipelineService.executeStep(current, step.getConversionType(),
                        step.getOptions() != null ? step.getOptions() : Map.of());
            }
            // Register the final output file
            String outputFileName = current.getFileName().toString();
            String mimeType = java.nio.file.Files.probeContentType(current);
            if (mimeType == null) mimeType = "application/octet-stream";
            FileRecord record = fileStorageService.saveOutputFile(current, outputFileName, mimeType);
            String outputFileId = record.getFileId();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outputFileId",  outputFileId);
            result.put("downloadUrl",   "/api/files/" + outputFileId + "/download");
            result.put("stepsExecuted", log.size());
            result.put("steps",         log);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Pipeline failed: " + e.getMessage()));
        }
    }
}
