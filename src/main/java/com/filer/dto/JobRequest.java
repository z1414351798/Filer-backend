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
    private Integer targetWidth;
    private Integer targetHeight;
    // Image collage
    private Integer cols;
    // Image compress (0-100)
    private Integer quality;
    // Image rotate
    private Integer rotateDegrees;
    // Image watermark
    private String watermarkText;
    // Image border
    private Integer borderSize;
    private String borderColor;
    // Image crop
    private Integer cropX, cropY, cropWidth, cropHeight;
    // Image filters
    private Float brightness;
    private Integer blurRadius;
    // Round corners
    private Integer cornerRadius;
    // Image flip
    private Boolean horizontal;
    // Color palette
    private Integer paletteCount;

    // PDF
    private Integer splitPage;
    private String password;
    private List<String> fileIds;
    private Integer pageIndex;
    private Float watermarkOpacity;

    // PDF page rotate (reuses rotateDegrees)

    // OCR
    private String language;

    // QR / Barcode
    private String qrContent;
    private Integer qrSize;
    private String barcodeContent;
    private String barcodeFormat;

    // Data / text
    private String textContent;
    private String checksumAlgorithm;
    private String diffFileId;

    // Video / audio
    private Integer videoSecond;
    private Integer videoDuration;
    private Integer videoFps;
    private String targetFormat;   // AUDIO_CONVERT target (mp3/wav/ogg/aac/flac)

    // PDF page extract
    private Integer fromPage;
    private Integer toPage;

    // Video trim
    private Integer startSec;
    private Integer durationSec;
    // Meme text
    private String topText;
    private String bottomText;
    // Hash
    private String hashAlgorithm;  // SHA-256, MD5, SHA-512

    // Notifications
    private String notifyEmail;
    private String webhookUrl;
}
