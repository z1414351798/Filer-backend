package com.filer.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class UploadResponse {
    private String fileId;
    private String originalName;
    private Long fileSize;
    private String mimeType;
    private String extension;
}
