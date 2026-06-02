package com.filer.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filer.config.KafkaConfig;
import com.filer.model.enums.ConversionType;
import com.filer.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileConversionConsumer {

    private final JobService jobService;
    private final FileStorageService fileStorageService;
    private final ImageService imageService;
    private final ImageFilterService imageFilterService;
    private final PdfService pdfService;
    private final OcrService ocrService;
    private final ArchiveService archiveService;
    private final OfficeService officeService;
    private final QrCodeService qrCodeService;
    private final TextConversionService textConversionService;
    private final FileInfoService fileInfoService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.TOPIC_FILE_CONVERSION, groupId = "filer-group")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String jobId = record.key();
        log.info("Received conversion task: jobId={}", jobId);
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    record.value(), new TypeReference<>() {});
            jobService.updateProgress(jobId, 10);
            process(jobId, payload);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Conversion failed for job {}: {}", jobId, e.getMessage(), e);
            jobService.markFailed(jobId, e.getMessage());
            ack.acknowledge();
        }
    }

    private void process(String jobId, Map<String, Object> payload) throws Exception {
        ConversionType type = ConversionType.valueOf((String) payload.get("conversionType"));
        String sourceFileId = (String) payload.get("fileId");

        // QR_GENERATE and text-only conversions don't need a source file
        Path sourcePath = null;
        if (sourceFileId != null && !sourceFileId.isBlank()) {
            sourcePath = fileStorageService.getFilePath(sourceFileId);
        }

        jobService.updateProgress(jobId, 30);

        switch (type) {

            // ── Image format conversions ──────────────────────────────────────
            case IMAGE_TO_JPG  -> save(jobId, imageService.convertFormat(sourcePath, "jpg"),  "image/jpeg", "converted.jpg");
            case IMAGE_TO_PNG  -> save(jobId, imageService.convertFormat(sourcePath, "png"),  "image/png",  "converted.png");
            case IMAGE_TO_WEBP -> save(jobId, imageService.convertFormat(sourcePath, "webp"), "image/webp", "converted.webp");
            case IMAGE_TO_BMP  -> save(jobId, imageService.convertFormat(sourcePath, "bmp"),  "image/bmp",  "converted.bmp");
            case IMAGE_TO_GIF  -> save(jobId, imageService.convertFormat(sourcePath, "gif"),  "image/gif",  "converted.gif");

            // ── Image operations ──────────────────────────────────────────────
            case IMAGE_RESIZE -> {
                int w = getInt(payload, "width", 800), h = getInt(payload, "height", 600);
                save(jobId, imageService.resize(sourcePath, w, h), "image/jpeg", "resized.jpg");
            }
            case IMAGE_COMPRESS -> {
                float q = getFloat(payload, "quality", 0.7f);
                String ext = ext(sourcePath);
                save(jobId, imageService.compress(sourcePath, q), mime(ext), "compressed." + ext);
            }
            case IMAGE_ROTATE -> {
                double angle = getDouble(payload, "angle", 90.0);
                String ext = ext(sourcePath);
                save(jobId, imageService.rotate(sourcePath, angle), mime(ext), "rotated." + ext);
            }
            case IMAGE_FLIP -> {
                boolean horiz = Boolean.parseBoolean(str(payload, "horizontal", "true"));
                String ext = ext(sourcePath);
                save(jobId, imageService.flip(sourcePath, horiz), mime(ext), "flipped." + ext);
            }
            case IMAGE_GRAYSCALE -> {
                String ext = ext(sourcePath);
                save(jobId, imageService.grayscale(sourcePath), mime(ext), "grayscale." + ext);
            }
            case IMAGE_WATERMARK -> {
                String text = str(payload, "watermarkText", "FILER");
                String ext = ext(sourcePath);
                save(jobId, imageService.addWatermark(sourcePath, text), mime(ext), "watermarked." + ext);
            }

            // ── Image filters (NEW) ───────────────────────────────────────────
            case IMAGE_CROP -> {
                int x = getInt(payload, "cropX", 0), y = getInt(payload, "cropY", 0);
                int w = getInt(payload, "cropWidth", 100), h = getInt(payload, "cropHeight", 100);
                String ext = ext(sourcePath);
                save(jobId, imageFilterService.crop(sourcePath, x, y, w, h), mime(ext), "cropped." + ext);
            }
            case IMAGE_SEPIA -> {
                String ext = ext(sourcePath);
                save(jobId, imageFilterService.sepia(sourcePath), mime(ext), "sepia." + ext);
            }
            case IMAGE_INVERT -> {
                String ext = ext(sourcePath);
                save(jobId, imageFilterService.invert(sourcePath), mime(ext), "inverted." + ext);
            }
            case IMAGE_BLUR -> {
                int radius = getInt(payload, "blurRadius", 3);
                String ext = ext(sourcePath);
                save(jobId, imageFilterService.blur(sourcePath, radius), mime(ext), "blurred." + ext);
            }
            case IMAGE_SHARPEN -> {
                String ext = ext(sourcePath);
                save(jobId, imageFilterService.sharpen(sourcePath), mime(ext), "sharpened." + ext);
            }
            case IMAGE_BRIGHTNESS -> {
                float factor = getFloat(payload, "brightness", 1.3f);
                String ext = ext(sourcePath);
                save(jobId, imageFilterService.brightness(sourcePath, factor), mime(ext), "brightened." + ext);
            }

            // ── PDF operations ────────────────────────────────────────────────
            case PDF_MERGE -> {
                List<String> fileIds = castList(payload.get("fileIds"));
                List<Path> paths = new ArrayList<>();
                paths.add(sourcePath);
                for (String fid : fileIds) paths.add(fileStorageService.getFilePath(fid));
                save(jobId, pdfService.mergePdfs(paths), "application/pdf", "merged.pdf");
            }
            case PDF_SPLIT -> {
                int splitPage = getInt(payload, "splitPage", 1);
                List<Path> parts = pdfService.splitPdf(sourcePath, splitPage);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) names.add("part" + (i + 1) + ".pdf");
                save(jobId, archiveService.createZip(parts, names), "application/zip", "split.zip");
            }
            case PDF_TO_IMAGES -> {
                List<Path> images = pdfService.pdfToImages(sourcePath, "jpg");
                List<String> names = new ArrayList<>();
                for (int i = 0; i < images.size(); i++) names.add("page" + (i + 1) + ".jpg");
                save(jobId, archiveService.createZip(images, names), "application/zip", "pages.zip");
            }
            case PDF_TO_TEXT -> {
                String text = pdfService.extractText(sourcePath);
                save(jobId, pdfService.saveTextToFile(text), "text/plain", "extracted.txt");
            }
            case PDF_ENCRYPT -> save(jobId, pdfService.encryptPdf(sourcePath, str(payload, "password", "filer123")), "application/pdf", "encrypted.pdf");
            case PDF_DECRYPT -> save(jobId, pdfService.decryptPdf(sourcePath, str(payload, "password", "")),          "application/pdf", "decrypted.pdf");
            case IMAGES_TO_PDF -> {
                List<String> fileIds = castList(payload.getOrDefault("fileIds", List.of()));
                List<Path> imgs = new ArrayList<>();
                imgs.add(sourcePath);
                for (String fid : fileIds) imgs.add(fileStorageService.getFilePath(fid));
                save(jobId, pdfService.imagesToPdf(imgs), "application/pdf", "images.pdf");
            }

            // ── OCR ───────────────────────────────────────────────────────────
            case OCR_IMAGE -> save(jobId, ocrService.ocrImage(sourcePath),  "text/plain", "ocr.txt");
            case OCR_PDF   -> save(jobId, ocrService.ocrPdf(sourcePath),    "text/plain", "ocr.txt");

            // ── Archive ───────────────────────────────────────────────────────
            case ZIP_CREATE -> {
                List<String> fileIds = castList(payload.getOrDefault("fileIds", List.of()));
                List<Path> zipFiles = new ArrayList<>();
                if (sourcePath != null) zipFiles.add(sourcePath);
                for (String fid : fileIds) zipFiles.add(fileStorageService.getFilePath(fid));
                save(jobId, archiveService.createZip(zipFiles, null), "application/zip", "archive.zip");
            }
            case ZIP_EXTRACT -> {
                List<Path> extracted = archiveService.extractZip(sourcePath);
                save(jobId, archiveService.createZip(extracted, null), "application/zip", "extracted.zip");
            }

            // ── Office formats ────────────────────────────────────────────────
            case EXCEL_TO_CSV  -> save(jobId, officeService.excelToCsv(sourcePath),  "text/csv", "data.csv");
            case CSV_TO_EXCEL  -> save(jobId, officeService.csvToExcel(sourcePath),  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "data.xlsx");
            case WORD_TO_TEXT  -> save(jobId, officeService.wordToText(sourcePath),  "text/plain", "document.txt");

            // ── QR / Barcode (NEW) ────────────────────────────────────────────
            case QR_GENERATE -> {
                String text = str(payload, "qrText", "https://filer.app");
                int size = getInt(payload, "qrSize", 300);
                save(jobId, qrCodeService.generateQr(text, size), "image/png", "qrcode.png");
            }
            case QR_SCAN, BARCODE_SCAN -> save(jobId, qrCodeService.scanCode(sourcePath), "text/plain", "scan_result.txt");
            case BARCODE_GENERATE -> {
                String text    = str(payload, "qrText",       "123456789012");
                String format  = str(payload, "barcodeFormat","CODE_128");
                int    width   = getInt(payload, "width",     400);
                int    height  = getInt(payload, "height",    150);
                save(jobId, qrCodeService.generateBarcode(text, format, width, height), "image/png", "barcode.png");
            }

            // ── Data format conversions (NEW) ─────────────────────────────────
            case CSV_TO_JSON      -> save(jobId, textConversionService.csvToJson(sourcePath),      "application/json", "data.json");
            case JSON_TO_CSV      -> save(jobId, textConversionService.jsonToCsv(sourcePath),      "text/csv",         "data.csv");
            case XML_TO_JSON      -> save(jobId, textConversionService.xmlToJson(sourcePath),      "application/json", "data.json");
            case JSON_TO_XML      -> save(jobId, textConversionService.jsonToXml(sourcePath),      "application/xml",  "data.xml");
            case MARKDOWN_TO_HTML -> {
                String md = textContent(payload, sourcePath);
                save(jobId, textConversionService.markdownToHtml(md), "text/html", "document.html");
            }
            case MARKDOWN_TO_PDF -> {
                String md = textContent(payload, sourcePath);
                save(jobId, textConversionService.markdownToPdf(md), "application/pdf", "document.pdf");
            }
            case TEXT_TO_PDF -> {
                String txt = textContent(payload, sourcePath);
                save(jobId, textConversionService.textToPdf(txt), "application/pdf", "document.pdf");
            }
            case HTML_TO_PDF -> save(jobId, textConversionService.htmlToPdf(sourcePath), "application/pdf", "document.pdf");

            // ── File utilities (NEW) ──────────────────────────────────────────
            case IMAGE_METADATA -> save(jobId, fileInfoService.extractImageMetadata(sourcePath), "application/json", "metadata.json");
            case PDF_INFO       -> save(jobId, fileInfoService.extractPdfInfo(sourcePath),       "application/json", "pdfinfo.json");
            case FILE_CHECKSUM  -> {
                String algo = str(payload, "checksumAlgorithm", "SHA-256");
                save(jobId, fileInfoService.computeChecksum(sourcePath, algo), "application/json", "checksum.json");
            }

            default -> throw new IllegalArgumentException("Unsupported conversion type: " + type);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void save(String jobId, Path outputPath, String mimeType, String name) throws Exception {
        jobService.updateProgress(jobId, 80);
        var record = fileStorageService.saveOutputFile(outputPath, name, mimeType);
        jobService.markCompleted(jobId, record.getFileId());
    }

    private String textContent(Map<String, Object> payload, Path fallbackPath) throws Exception {
        Object tc = payload.get("textContent");
        if (tc != null && !tc.toString().isBlank()) return tc.toString();
        return fallbackPath != null ? java.nio.file.Files.readString(fallbackPath) : "";
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object o) {
        if (o instanceof List<?> l) return (List<String>) l;
        return List.of();
    }

    private int getInt(Map<String, Object> m, String k, int def) {
        Object v = m.get(k); return v == null ? def : Integer.parseInt(v.toString());
    }
    private double getDouble(Map<String, Object> m, String k, double def) {
        Object v = m.get(k); return v == null ? def : Double.parseDouble(v.toString());
    }
    private float getFloat(Map<String, Object> m, String k, float def) {
        Object v = m.get(k); return v == null ? def : Float.parseFloat(v.toString());
    }
    private String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k); return v == null ? def : v.toString();
    }
    private String ext(Path path) {
        if (path == null) return "bin";
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "bin" : name.substring(dot + 1).toLowerCase();
    }
    private String mime(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"  -> "image/png";
            case "gif"  -> "image/gif";
            case "bmp"  -> "image/bmp";
            case "webp" -> "image/webp";
            case "pdf"  -> "application/pdf";
            case "json" -> "application/json";
            case "xml"  -> "application/xml";
            case "html" -> "text/html";
            case "txt"  -> "text/plain";
            default     -> "application/octet-stream";
        };
    }
}
