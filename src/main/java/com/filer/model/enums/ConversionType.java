package com.filer.model.enums;

public enum ConversionType {
    // Image format conversions
    IMAGE_TO_JPG,
    IMAGE_TO_PNG,
    IMAGE_TO_WEBP,
    IMAGE_TO_BMP,
    IMAGE_TO_GIF,

    // Image operations
    IMAGE_RESIZE,
    IMAGE_COMPRESS,
    IMAGE_ROTATE,
    IMAGE_FLIP,
    IMAGE_GRAYSCALE,
    IMAGE_WATERMARK,

    // PDF operations
    PDF_MERGE,
    PDF_SPLIT,
    PDF_TO_IMAGES,
    PDF_COMPRESS,
    PDF_TO_TEXT,
    PDF_ENCRYPT,
    PDF_DECRYPT,

    // Images → PDF
    IMAGES_TO_PDF,

    // OCR
    OCR_IMAGE,
    OCR_PDF,

    // Archive
    ZIP_CREATE,
    ZIP_EXTRACT,

    // Office formats
    EXCEL_TO_CSV,
    CSV_TO_EXCEL,
    WORD_TO_TEXT
}
