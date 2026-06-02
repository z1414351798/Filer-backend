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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileConversionConsumer {

    private final JobService jobService;
    private final FileStorageService fileStorageService;
    private final ImageService imageService;
    private final PdfService pdfService;
    private final OcrService ocrService;
    private final ArchiveService archiveService;
    private final OfficeService officeService;
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
        String typeStr = (String) payload.get("conversionType");
        ConversionType type = ConversionType.valueOf(typeStr);
        String sourceFileId = (String) payload.get("fileId");
        Path sourcePath = fileStorageService.getFilePath(sourceFileId);

        jobService.updateProgress(jobId, 30);

        switch (type) {
            case IMAGE_TO_JPG -> saveOutput(jobId, imageService.convertFormat(sourcePath, "jpg"), "image/jpeg", "converted.jpg");
            case IMAGE_TO_PNG -> saveOutput(jobId, imageService.convertFormat(sourcePath, "png"), "image/png", "converted.png");
            case IMAGE_TO_WEBP -> saveOutput(jobId, imageService.convertFormat(sourcePath, "webp"), "image/webp", "converted.webp");
            case IMAGE_TO_BMP -> saveOutput(jobId, imageService.convertFormat(sourcePath, "bmp"), "image/bmp", "converted.bmp");
            case IMAGE_TO_GIF -> saveOutput(jobId, imageService.convertFormat(sourcePath, "gif"), "image/gif", "converted.gif");

            case IMAGE_RESIZE -> {
                int w = getInt(payload, "width", 800);
                int h = getInt(payload, "height", 600);
                saveOutput(jobId, imageService.resize(sourcePath, w, h), "image/jpeg", "resized.jpg");
            }
            case IMAGE_COMPRESS -> {
                float q = getFloat(payload, "quality", 0.7f);
                String ext = extension(sourcePath);
                saveOutput(jobId, imageService.compress(sourcePath, q), mimeFor(ext), "compressed." + ext);
            }
            case IMAGE_ROTATE -> {
                double angle = getDouble(payload, "angle", 90.0);
                String ext = extension(sourcePath);
                saveOutput(jobId, imageService.rotate(sourcePath, angle), mimeFor(ext), "rotated." + ext);
            }
            case IMAGE_FLIP -> {
                boolean horiz = Boolean.parseBoolean((String) payload.getOrDefault("horizontal", "true"));
                String ext = extension(sourcePath);
                saveOutput(jobId, imageService.flip(sourcePath, horiz), mimeFor(ext), "flipped." + ext);
            }
            case IMAGE_GRAYSCALE -> {
                String ext = extension(sourcePath);
                saveOutput(jobId, imageService.grayscale(sourcePath), mimeFor(ext), "grayscale." + ext);
            }
            case IMAGE_WATERMARK -> {
                String text = (String) payload.getOrDefault("watermarkText", "FILER");
                String ext = extension(sourcePath);
                saveOutput(jobId, imageService.addWatermark(sourcePath, text), mimeFor(ext), "watermarked." + ext);
            }

            case PDF_MERGE -> {
                List<String> fileIds = (List<String>) payload.get("fileIds");
                List<Path> paths = new ArrayList<>();
                paths.add(sourcePath);
                for (String fid : fileIds) paths.add(fileStorageService.getFilePath(fid));
                saveOutput(jobId, pdfService.mergePdfs(paths), "application/pdf", "merged.pdf");
            }
            case PDF_SPLIT -> {
                int splitPage = getInt(payload, "splitPage", 1);
                List<Path> parts = pdfService.splitPdf(sourcePath, splitPage);
                List<Path> partList = new ArrayList<>(parts);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < partList.size(); i++) names.add("part" + (i + 1) + ".pdf");
                saveOutput(jobId, archiveService.createZip(partList, names), "application/zip", "split.zip");
            }
            case PDF_TO_IMAGES -> {
                List<Path> images = pdfService.pdfToImages(sourcePath, "jpg");
                List<String> names = new ArrayList<>();
                for (int i = 0; i < images.size(); i++) names.add("page" + (i + 1) + ".jpg");
                saveOutput(jobId, archiveService.createZip(images, names), "application/zip", "pages.zip");
            }
            case PDF_TO_TEXT -> {
                String text = pdfService.extractText(sourcePath);
                saveOutput(jobId, pdfService.saveTextToFile(text), "text/plain", "extracted.txt");
            }
            case PDF_ENCRYPT -> {
                String pwd = (String) payload.getOrDefault("password", "filer123");
                saveOutput(jobId, pdfService.encryptPdf(sourcePath, pwd), "application/pdf", "encrypted.pdf");
            }
            case PDF_DECRYPT -> {
                String pwd = (String) payload.getOrDefault("password", "");
                saveOutput(jobId, pdfService.decryptPdf(sourcePath, pwd), "application/pdf", "decrypted.pdf");
            }
            case IMAGES_TO_PDF -> {
                List<String> fileIds = (List<String>) payload.getOrDefault("fileIds", List.of());
                List<Path> imgs = new ArrayList<>();
                imgs.add(sourcePath);
                for (String fid : fileIds) imgs.add(fileStorageService.getFilePath(fid));
                saveOutput(jobId, pdfService.imagesToPdf(imgs), "application/pdf", "images.pdf");
            }

            case OCR_IMAGE -> saveOutput(jobId, ocrService.ocrImage(sourcePath), "text/plain", "ocr.txt");
            case OCR_PDF -> saveOutput(jobId, ocrService.ocrPdf(sourcePath), "text/plain", "ocr.txt");

            case ZIP_CREATE -> {
                List<String> fileIds = (List<String>) payload.getOrDefault("fileIds", List.of());
                List<Path> zipFiles = new ArrayList<>();
                zipFiles.add(sourcePath);
                for (String fid : fileIds) zipFiles.add(fileStorageService.getFilePath(fid));
                saveOutput(jobId, archiveService.createZip(zipFiles, null), "application/zip", "archive.zip");
            }
            case ZIP_EXTRACT -> {
                List<Path> extracted = archiveService.extractZip(sourcePath);
                saveOutput(jobId, archiveService.createZip(extracted, null), "application/zip", "extracted.zip");
            }

            case EXCEL_TO_CSV -> saveOutput(jobId, officeService.excelToCsv(sourcePath), "text/csv", "data.csv");
            case CSV_TO_EXCEL -> saveOutput(jobId, officeService.csvToExcel(sourcePath), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "data.xlsx");
            case WORD_TO_TEXT -> saveOutput(jobId, officeService.wordToText(sourcePath), "text/plain", "document.txt");

            default -> throw new IllegalArgumentException("Unsupported conversion type: " + type);
        }
    }

    private void saveOutput(String jobId, Path outputPath, String mimeType, String name) throws Exception {
        jobService.updateProgress(jobId, 80);
        var record = fileStorageService.saveOutputFile(outputPath, name, mimeType);
        jobService.markCompleted(jobId, record.getFileId());
    }

    private int getInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v == null ? def : Integer.parseInt(v.toString());
    }

    private double getDouble(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v == null ? def : Double.parseDouble(v.toString());
    }

    private float getFloat(Map<String, Object> m, String key, float def) {
        Object v = m.get(key);
        return v == null ? def : Float.parseFloat(v.toString());
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "bin" : name.substring(dot + 1).toLowerCase();
    }

    private String mimeFor(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
