package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.model.ConversionPreset;
import com.filer.service.PresetService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/presets")
@RequiredArgsConstructor
public class PresetController {

    private final PresetService presetService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConversionPreset>> create(
            Principal principal,
            @RequestBody CreatePresetRequest req) {
        ConversionPreset preset = presetService.create(
                principal.getName(), req.getName(), req.getConversionType(), req.getParamsJson());
        return ResponseEntity.ok(ApiResponse.success(preset));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversionPreset>>> list(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(presetService.list(principal.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        presetService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Data
    static class CreatePresetRequest {
        private String name;
        private String conversionType;
        private String paramsJson;
    }
}
