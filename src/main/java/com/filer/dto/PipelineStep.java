package com.filer.dto;

import lombok.Data;
import java.util.Map;

@Data
public class PipelineStep {
    private String conversionType;
    private Map<String, Object> options;  // same keys as JobRequest fields
}
