package com.filer.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileRecord {
    private Long id;
    private String fileId;
    private String originalName;
    private String storedName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private String extension;
    private LocalDateTime createdAt;
}
