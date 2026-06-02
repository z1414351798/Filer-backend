package com.filer.model.enums;

public enum ConversionType {
    // ── Image format conversions ────────────────────────────────────────────
    IMAGE_TO_JPG,
    IMAGE_TO_PNG,
    IMAGE_TO_WEBP,
    IMAGE_TO_BMP,
    IMAGE_TO_GIF,

    // ── Image basic operations ──────────────────────────────────────────────
    IMAGE_RESIZE,
    IMAGE_COMPRESS,
    IMAGE_ROTATE,
    IMAGE_FLIP,
    IMAGE_GRAYSCALE,
    IMAGE_WATERMARK,

    // ── Image filters & adjustments (NEW) ──────────────────────────────────
    IMAGE_CROP,
    IMAGE_SEPIA,
    IMAGE_INVERT,
    IMAGE_BLUR,
    IMAGE_SHARPEN,
    IMAGE_BRIGHTNESS,

    // ── PDF operations ──────────────────────────────────────────────────────
    PDF_MERGE,
    PDF_SPLIT,
    PDF_TO_IMAGES,
    PDF_COMPRESS,
    PDF_TO_TEXT,
    PDF_ENCRYPT,
    PDF_DECRYPT,

    // ── Images → PDF ────────────────────────────────────────────────────────
    IMAGES_TO_PDF,

    // ── OCR ─────────────────────────────────────────────────────────────────
    OCR_IMAGE,
    OCR_PDF,

    // ── Archive ─────────────────────────────────────────────────────────────
    ZIP_CREATE,
    ZIP_EXTRACT,

    // ── Office formats ──────────────────────────────────────────────────────
    EXCEL_TO_CSV,
    CSV_TO_EXCEL,
    WORD_TO_TEXT,

    // ── QR / Barcode (NEW) ──────────────────────────────────────────────────
    QR_GENERATE,
    QR_SCAN,
    BARCODE_GENERATE,
    BARCODE_SCAN,

    // ── Data format conversions (NEW) ────────────────────────────────────────
    CSV_TO_JSON,
    JSON_TO_CSV,
    XML_TO_JSON,
    JSON_TO_XML,
    MARKDOWN_TO_HTML,
    MARKDOWN_TO_PDF,
    TEXT_TO_PDF,
    HTML_TO_PDF,

    // ── File utilities (NEW) ─────────────────────────────────────────────────
    FILE_CHECKSUM,
    IMAGE_METADATA,
    PDF_INFO
}
