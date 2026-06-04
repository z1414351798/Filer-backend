package com.filer.dto;

import lombok.Data;
import java.util.List;

@Data
public class PipelineRequest {
    private String fileId;
    private List<PipelineStep> steps;
}
