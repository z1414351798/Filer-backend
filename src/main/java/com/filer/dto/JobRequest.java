package com.filer.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class JobRequest {
    private String fileId;
    private String conversionType;
    private Map<String, Object> options;

    // Image resize
    private Integer width;
    private Integer height;
    // Image compress
    private Float quality;
    // Image rotate
    private Integer angle;
    // Image watermark
    private String watermarkText;
    // Image crop
    private Integer cropX;
    private Integer cropY;
    private Integer cropWidth;
    private Integer cropHeight;
    // Image brightness
    private Float brightness;
    // Image blur  <-- was missing, caused IMAGE_BLUR to always use radius=3
    private Integer blurRadius;
    // Image flip direction
    private Boolean horizontal;

    // PDF operations
    private Integer splitPage;
    private String password;
    private List<String> fileIds;

    // OCR language
    private String language;

    // QR / Barcode
    private String qrText;
    private Integer qrSize;
    private String barcodeFormat;

    // Data / text conversions
    private String textContent;

    // File utilities
    private String checksumAlgorithm;
}
