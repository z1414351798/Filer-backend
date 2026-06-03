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
    // Subtitle shift
    private Long shiftMs;           // milliseconds (positive=later, negative=earlier)
    // Password generate
    private Integer pwdLength;      // default 16
    private Boolean pwdUppercase;   // include uppercase
    private Boolean pwdNumbers;     // include numbers
    private Boolean pwdSymbols;     // include symbols !@#$%
    // Passphrase generate
    private Integer passphraseWords; // number of words (default 4)
    // Color convert
    private String colorInput;       // e.g. "#FF5733" or "255,87,51"
    private String colorFrom;        // hex|rgb|hsl
    private String colorTo;          // hex|rgb|hsl
    // Placeholder image
    private String placeholderBg;    // background color hex, e.g. "cccccc"
    private String placeholderLabel; // text label on image
    // Regex test
    private String regexPattern;
    private String regexInput;
    private String regexFlags;       // i|m|s|im etc
    // Audio fade
    private Integer fadeInDuration;  // seconds
    private Integer fadeOutDuration; // seconds
    // Text to image
    private String codeTheme;       // "dark" | "light"
    // Image caption
    private String captionText;
    private String captionPosition; // "top" | "bottom"
    // Audio volume
    private Float volumeFactor;     // e.g. 2.0 = twice as loud
    // PDF split by size
    private Integer pagesPerChunk;
    // ZIP encrypt
    private String zipPassword;
    // Number base convert
    private String numberInput;
    private String numberFrom;      // "decimal" | "binary" | "octal" | "hex"
    private String numberTo;        // same options
    // Cron describe
    private String cronExpression;
    // CSV sort
    private String sortColumn;
    private Boolean sortAscending;
}
