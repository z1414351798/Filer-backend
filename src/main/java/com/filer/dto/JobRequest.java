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
    // Image crop (NEW)
    private Integer cropX;
    private Integer cropY;
    private Integer cropWidth;
    private Integer cropHeight;
    // Image brightness/contrast (NEW)
    private Float brightness;   // 0.0–2.0, 1.0 = no change
    private Float contrast;     // 0.0–2.0, 1.0 = no change
    // Image flip direction
    private Boolean horizontal; // true = horizontal flip

    // PDF operations
    private Integer splitPage;
    private String password;
    private List<String> fileIds;

    // OCR language
    private String language;

    // QR / Barcode (NEW)
    private String qrText;
    private Integer qrSize;        // pixels, default 300
    private String barcodeFormat;  // EAN_13, CODE_128, etc.

    // Data / text conversions (NEW)
    private String textContent;    // inline text input (for TEXT_TO_PDF, MARKDOWN_TO_HTML, etc.)

    // File utilities (NEW)
    private String checksumAlgorithm; // MD5 | SHA-256 | SHA-512
}
