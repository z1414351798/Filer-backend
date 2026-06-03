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
    private Integer progress;
    private String options;
    private String errorMessage;
    private String notifyEmail;
    private String webhookUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
