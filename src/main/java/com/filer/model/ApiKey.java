package com.filer.model;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ApiKey {
    private Long id;
    private Long userId;
    private String keyValue;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private boolean active;
}
