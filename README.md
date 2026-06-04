# Filer Backend

Spring Boot file-transformation service — convert, compress, OCR, and process any file through a Kafka-driven async pipeline.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 (Java 17) |
| Database | MySQL 8 + MyBatis |
| Cache / Job state | Redis 7 |
| Async queue | Apache Kafka |
| PDF | Apache PDFBox 3 |
| Images | Thumbnailator + AWT |
| OCR | Tess4j (Tesseract 4) |
| Office files | Apache POI 5 |
| Archives | Apache Commons Compress |

## Quick Start

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Run the app
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

## API Reference

### Upload a file
```
POST /api/files/upload
Content-Type: multipart/form-data
body: file=<binary>

→ { fileId, originalName, fileSize, mimeType, extension }
```

### Create a conversion job
```
POST /api/jobs
{
  "fileId": "<uuid>",
  "conversionType": "IMAGE_TO_PNG",
  // optional params:
  "width": 800, "height": 600,   // IMAGE_RESIZE
  "quality": 0.7,                // IMAGE_COMPRESS (0–1)
  "angle": 90,                   // IMAGE_ROTATE
  "watermarkText": "FILER",      // IMAGE_WATERMARK
  "splitPage": 2,                // PDF_SPLIT
  "password": "secret",          // PDF_ENCRYPT / PDF_DECRYPT
  "fileIds": ["uuid2","uuid3"],  // PDF_MERGE / IMAGES_TO_PDF
  "language": "eng"              // OCR_IMAGE / OCR_PDF
}

→ { jobId, status: "PENDING", progress: 0, ... }
```

### Poll job status
```
GET /api/jobs/{jobId}
→ { jobId, status, progress, downloadUrl, errorMessage }
```

### Download result
```
GET /api/files/{fileId}/download
```

## Supported Conversion Types

### Image
`IMAGE_TO_JPG` `IMAGE_TO_PNG` `IMAGE_TO_WEBP` `IMAGE_TO_BMP` `IMAGE_TO_GIF`
`IMAGE_RESIZE` `IMAGE_COMPRESS` `IMAGE_ROTATE` `IMAGE_FLIP` `IMAGE_GRAYSCALE` `IMAGE_WATERMARK`

### PDF
`PDF_MERGE` `PDF_SPLIT` `PDF_TO_IMAGES` `IMAGES_TO_PDF` `PDF_TO_TEXT` `PDF_ENCRYPT` `PDF_DECRYPT`

### OCR
`OCR_IMAGE` `OCR_PDF` (requires Tesseract installed with lang packs)

### Office
`EXCEL_TO_CSV` `CSV_TO_EXCEL` `WORD_TO_TEXT`

### Archive
`ZIP_CREATE` `ZIP_EXTRACT`

## Architecture

```
HTTP Request
    │
    ▼
FileController / JobController
    │
    ▼
JobService ──► Redis (job state cache)
    │
    ▼
Kafka Producer  ──► [file-conversion topic]
                              │
                              ▼
                    FileConversionConsumer
                              │
                    ┌─────────┴─────────┐
                    │ ImageService          │
                    │ PdfService            │
                    │ OcrService            │
                    │ ArchiveService        │
                    │ OfficeService         │
                    └─────────┬─────────┘
                              │
                    MySQL (FileRecord, ConversionJob)
```

## Configuration

Key values in `application.yml`:

| Key | Default | Description |
|---|---|---|
| `filer.upload-dir` | `/tmp/filer/uploads` | Where uploaded files are stored |
| `filer.output-dir` | `/tmp/filer/outputs` | Where converted files are written |
| `tesseract.data-path` | `/usr/share/tesseract-ocr/4.00/tessdata` | Tesseract language data |
| `tesseract.language` | `eng+chi_sim` | Default OCR languages |
