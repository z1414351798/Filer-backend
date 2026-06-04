package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.model.FileRecord;
import com.filer.service.FileStorageService;
import com.filer.service.ShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkService shareLinkService;
    private final FileStorageService fileStorageService;

    @PostMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> createLink(@PathVariable String fileId) {
        String token = shareLinkService.createShareLink(fileId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("token", token)));
    }

    @GetMapping("/{token}")
    public ResponseEntity<Resource> downloadByToken(@PathVariable String token) {
        try {
            FileRecord file = shareLinkService.resolveShareLink(token);
            Resource resource = fileStorageService.loadAsResource(file.getFileId());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getOriginalName() + "\"")
                    .body(resource);
        } catch (java.net.MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
