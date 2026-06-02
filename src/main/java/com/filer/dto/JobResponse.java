package com.filer.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
public class JobResponse {
    private String jobId;
    private String sourceFileId;
    private String outputFileId;
    private String conversionType;
    private String status;
    private Integer progress;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String downloadUrl;
}
