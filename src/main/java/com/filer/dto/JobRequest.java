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

    // PDF crop margins (points, 1 point ≈ 0.35mm)
    private Integer cropTop;
    private Integer cropRight;
    private Integer cropBottom;
    private Integer cropLeft;
    // PDF reorder pages
    private String pageOrder;   // e.g. "3,1,2"
    // Text case convert
    private String caseType;    // upper|lower|title|camel|snake|kebab
    // Video extract frames
    private Integer frameInterval;  // seconds between frames
    // Video watermark
    private String videoWatermarkText;
    // Excel merge / multi-file
    // (reuses existing fileIds field)
    // Video speed
    private Float videoSpeed;       // e.g. 2.0 = double speed, 0.5 = half speed
    // Audio split
    private Integer splitAtSec;
    // PDF metadata
    private String pdfTitle;
    private String pdfAuthor;
    private String pdfSubject;
    private String pdfKeywords;
    // HTML sanitize
    private String sanitizeLevel;   // basic | relaxed | none | basic_w_images
    // Generator tools
    private Integer uuidCount;      // for UUID_GENERATE
    private Integer loremParagraphs;
    private Integer randomRows;
    private String  randomColumns;  // comma-separated column names
    private Integer gifDelay;       // animated GIF delay in centiseconds (100 = 1s)
}
