package com.filer.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversionJob {
    private Long id;
    private String jobId;
    private String sourceFileId;
    private String outputFileId;
    private String conversionType;
    private String status;
    private String options;
    private String errorMessage;
    private Integer progress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
