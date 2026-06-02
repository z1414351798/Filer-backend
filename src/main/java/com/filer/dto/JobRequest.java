package com.filer.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class JobRequest {
    private String fileId;
    private String conversionType;
    private Map<String, Object> options;

    // Image resize / collage
    private Integer width;
    private Integer height;
    private Integer cols;           // collage columns
    // Image compress
    private Float quality;
    // Image rotate
    private Integer angle;
    // Image watermark / border
    private String watermarkText;
    private Integer borderSize;
    private String borderColor;     // hex e.g. #FF0000
    // Image crop
    private Integer cropX, cropY, cropWidth, cropHeight;
    // Image filters
    private Float brightness;
    private Integer blurRadius;
    private Integer cornerRadius;   // round corners
    // Image flip
    private Boolean horizontal;
    // Color palette
    private Integer paletteCount;   // number of dominant colors

    // PDF
    private Integer splitPage;
    private String password;
    private List<String> fileIds;
    private Integer pageIndex;          // PDF page rotate target page
    private Integer rotationDegrees;    // 90 / 180 / 270
    private Float watermarkOpacity;     // 0.0–1.0

    // OCR
    private String language;

    // QR / Barcode
    private String qrText;
    private Integer qrSize;
    private String barcodeFormat;

    // Data / text
    private String textContent;
    private String checksumAlgorithm;
    private String diffFileId;          // second file for TEXT_DIFF

    // Video
    private Integer videoSecond;        // second to extract thumbnail
    private Integer videoDuration;      // GIF duration in seconds
    private Integer videoFps;           // GIF fps

    // Notifications
    private String notifyEmail;
    private String webhookUrl;
}
