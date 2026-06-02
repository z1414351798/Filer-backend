package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.dto.UploadResponse;
import com.filer.model.FileRecord;
import com.filer.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
        }
        UploadResponse resp = fileStorageService.store(file);
        return ResponseEntity.ok(ApiResponse.ok("File uploaded successfully", resp));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileId)
            throws MalformedURLException {
        Resource resource = fileStorageService.loadAsResource(fileId);
        FileRecord record = fileStorageService.getRecord(fileId);
        String contentType = record.getMimeType() != null
                ? record.getMimeType() : "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + record.getOriginalName() + "\"")
                .body(resource);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileRecord>>> listFiles() {
        return ResponseEntity.ok(ApiResponse.ok(fileStorageService.listAll()));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<ApiResponse<FileRecord>> getFile(@PathVariable String fileId) {
        FileRecord record = fileStorageService.getRecord(fileId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(record));
    }
}
