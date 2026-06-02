package com.filer.dto;

import lombok.Data;
import java.util.Map;

@Data
public class JobRequest {
    private String fileId;
    private String conversionType;
    private Map<String, Object> options;

    // Image resize options
    private Integer width;
    private Integer height;
    // Image compress option
    private Float quality;
    // Image rotate option
    private Integer angle;
    // Image watermark option
    private String watermarkText;
    // PDF split option
    private Integer splitPage;
    // PDF encrypt option
    private String password;
    // ZIP option: list of fileIds to bundle
    private java.util.List<String> fileIds;
    // OCR language
    private String language;
}
