package com.filer.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversionPreset {
    private Long id;
    private Long userId;
    private String name;
    private String conversionType;
    private String paramsJson;
    private LocalDateTime createdAt;
}
