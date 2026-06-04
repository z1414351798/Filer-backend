package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.model.FileRecord;
import com.filer.service.FileInfoService;
import com.filer.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/**
 * Lightweight metadata endpoints — returns info synchronously without creating a job.
 */
@RestController
@RequestMapping("/api/info")
@RequiredArgsConstructor
public class FileInfoController {

    private final FileInfoService fileInfoService;
    private final FileStorageService fileStorageService;

    @GetMapping("/{fileId}/image")
    public ResponseEntity<ApiResponse<Object>> imageMetadata(@PathVariable String fileId) {
        try {
            Path p = fileStorageService.getFilePath(fileId);
            // Return JSON inline (read the output file and parse it)
            Path out = fileInfoService.extractImageMetadata(p);
            String json = java.nio.file.Files.readString(out);
            Object node = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            return ResponseEntity.ok(ApiResponse.ok(node));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{fileId}/pdf")
    public ResponseEntity<ApiResponse<Object>> pdfInfo(@PathVariable String fileId) {
        try {
            Path p = fileStorageService.getFilePath(fileId);
            Path out = fileInfoService.extractPdfInfo(p);
            String json = java.nio.file.Files.readString(out);
            Object node = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            return ResponseEntity.ok(ApiResponse.ok(node));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{fileId}/checksum")
    public ResponseEntity<ApiResponse<Object>> checksum(
            @PathVariable String fileId,
            @RequestParam(defaultValue = "SHA-256") String algorithm) {
        try {
            Path p = fileStorageService.getFilePath(fileId);
            Path out = fileInfoService.computeChecksum(p, algorithm);
            String json = java.nio.file.Files.readString(out);
            Object node = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            return ResponseEntity.ok(ApiResponse.ok(node));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/supported")
    public ResponseEntity<ApiResponse<Map<String, Object>>> supported() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("imageFormats",  new String[]{"jpg","png","webp","bmp","gif"});
        map.put("imageOps",      new String[]{"resize","compress","rotate","flip","grayscale","watermark","crop","sepia","invert","blur","sharpen","brightness"});
        map.put("pdfOps",        new String[]{"merge","split","toImages","toPdf","extractText","encrypt","decrypt"});
        map.put("ocrLanguages",  new String[]{"eng","chi_sim","chi_tra","jpn","kor","fra","deu","spa"});
        map.put("dataFormats",   new String[]{"csv_json","json_csv","xml_json","json_xml","markdown_html","markdown_pdf","text_pdf","html_pdf"});
        map.put("qrBarcode",     new String[]{"qr_generate","qr_scan","barcode_generate","barcode_scan"});
        map.put("fileUtils",     new String[]{"checksum","image_metadata","pdf_info"});
        return ResponseEntity.ok(ApiResponse.ok(map));
    }
}
