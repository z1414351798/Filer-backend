package com.filer.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ShareLink {
    private Long id;
    private String token;
    private Long fileId;
    private Long createdByUserId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
