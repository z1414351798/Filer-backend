package com.filer.kafka;

import com.filer.dto.JobResponse;
import com.filer.model.FileRecord;
import com.filer.model.enums.ConversionType;
import com.filer.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileConversionConsumer {

    private final JobService jobService;
    private final FileStorageService fileStorageService;
    private final ImageService imageService;
    private final ImageFilterService imageFilterService;
    private final ImageEnhancementService imageEnhancementService;
    private final PdfService pdfService;
    private final PdfEnhancementService pdfEnhancementService;
    private final OcrService ocrService;
    private final ArchiveService archiveService;
    private final OfficeService officeService;
    private final QrCodeService qrCodeService;
    private final TextConversionService textConversionService;
    private final DataFormatService dataFormatService;
    private final SvgService svgService;
    private final VideoService videoService;
    private final WebSocketProgressService wsProgressService;
    private final EmailService emailService;

    @KafkaListener(topics = "file-conversion", groupId = "filer-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(Map<String, Object> payload, Acknowledgment ack) {
        String jobId = str(payload, "jobId");
        String fileId = str(payload, "fileId");
        String typeStr = str(payload, "conversionType");
        String notifyEmail = str(payload, "notifyEmail");
        try {
            jobService.updateProgress(jobId, 10);
            notify(jobId);

            ConversionType type = ConversionType.valueOf(typeStr);
            Path srcPath = fileId != null && !fileId.isEmpty()
                    ? fileStorageService.getFilePath(fileId) : null;

            Path outPath = dispatch(type, srcPath, payload);

            // Register output file and get a proper fileId
            String ext = outPath.getFileName().toString();
            int dot = ext.lastIndexOf('.');
            String mime = mimeForExt(dot >= 0 ? ext.substring(dot + 1) : "bin");
            FileRecord outRecord = fileStorageService.saveOutputFile(outPath,
                    "output" + (dot >= 0 ? ext.substring(dot) : ""), mime);

            jobService.updateProgress(jobId, 90);
            notify(jobId);
            jobService.markCompleted(jobId, outRecord.getFileId());
            notify(jobId);

            if (notifyEmail != null && !notifyEmail.isEmpty())
                emailService.sendJobCompleted(notifyEmail, jobId,
                        "/api/files/" + outRecord.getFileId() + "/download");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            jobService.markFailed(jobId, e.getMessage());
            notify(jobId);
            if (notifyEmail != null && !notifyEmail.isEmpty())
                emailService.sendJobFailed(notifyEmail, jobId, e.getMessage());
            ack.acknowledge();
        }
    }

    @SuppressWarnings("unchecked")
    private Path dispatch(ConversionType type, Path src, Map<String, Object> p) throws Exception {
        return switch (type) {
            // Image format
            case IMAGE_TO_PNG  -> imageService.convertFormat(src, "png");
            case IMAGE_TO_JPG  -> imageService.convertFormat(src, "jpg");
            case IMAGE_TO_WEBP -> imageService.convertFormat(src, "webp");
            case IMAGE_TO_BMP  -> imageService.convertFormat(src, "bmp");
            case IMAGE_TO_GIF  -> imageService.convertFormat(src, "gif");
            case IMAGE_TO_TIFF -> imageService.convertFormat(src, "tiff");
            // Image edit
            case IMAGE_RESIZE     -> imageService.resize(src, intOf(p,"targetWidth"), intOf(p,"targetHeight"));
            case IMAGE_COMPRESS   -> imageService.compress(src, intOf(p,"quality") / 100f);
            case IMAGE_ROTATE     -> imageService.rotate(src, intOf(p,"rotateDegrees"));
            case IMAGE_FLIP_H     -> imageService.flip(src, true);
            case IMAGE_FLIP_V     -> imageService.flip(src, false);
            case IMAGE_GRAYSCALE  -> imageService.grayscale(src);
            case IMAGE_WATERMARK  -> imageService.addWatermark(src, str(p,"watermarkText"));
            // Image filters
            case IMAGE_CROP       -> imageFilterService.crop(src, intOf(p,"cropX"), intOf(p,"cropY"),
                                       intOf(p,"cropWidth"), intOf(p,"cropHeight"));
            case IMAGE_SEPIA      -> imageFilterService.sepia(src);
            case IMAGE_INVERT     -> imageFilterService.invert(src);
            case IMAGE_BLUR       -> imageFilterService.blur(src, intOrDef(p,"blurRadius",3));
            case IMAGE_SHARPEN    -> imageFilterService.sharpen(src);
            case IMAGE_BRIGHTNESS -> imageFilterService.brightness(src, floatOf(p,"brightness"));
            // Image enhancements
            case IMAGE_COLLAGE -> {
                List<String> ids = listOf(p, "fileIds");
                List<File> files = ids.stream()
                        .map(id -> fileStorageService.getFilePath(id).toFile()).toList();
                yield imageEnhancementService.createCollage(files, intOrDef(p,"cols",3));
            }
            case IMAGE_BORDER         -> imageEnhancementService.addBorder(src,
                                           intOrDef(p,"borderSize",20), str(p,"borderColor"));
            case IMAGE_ROUND_CORNERS  -> imageEnhancementService.roundCorners(src,
                                           intOrDef(p,"cornerRadius",30));
            case IMAGE_COLOR_PALETTE  -> imageEnhancementService.extractColorPalette(src,
                                           intOrDef(p,"paletteCount",6));
            // PDF
            case PDF_MERGE        -> pdfService.mergeDocuments(listOf(p,"fileIds"));
            case PDF_SPLIT        -> pdfService.splitDocument(src);
            case PDF_TO_IMAGES    -> pdfService.pdfToImages(src);
            case IMAGES_TO_PDF   -> {
                List<String> ids = listOf(p,"fileIds");
                List<Path> paths = ids.stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield pdfService.imagesToPdf(paths);
            }
            case PDF_EXTRACT_TEXT -> pdfService.extractText(src);
            case PDF_ENCRYPT      -> pdfService.encryptPdf(src, str(p,"password"));
            case PDF_DECRYPT      -> pdfService.decryptPdf(src, str(p,"password"));
            case PDF_COMPRESS     -> pdfEnhancementService.compressPdf(src);
            case PDF_WATERMARK    -> pdfEnhancementService.addTextWatermark(src,
                                       str(p,"watermarkText"), floatOrDef(p,"watermarkOpacity",0.3f));
            case PDF_PAGE_ROTATE  -> pdfEnhancementService.rotatePage(src,
                                       intOrDef(p,"pageIndex",0), intOrDef(p,"rotateDegrees",90));
            case PDF_TO_DOCX      -> pdfEnhancementService.pdfToText(src);
            // OCR
            case OCR_IMAGE -> ocrService.ocrImage(src);
            case OCR_PDF   -> ocrService.ocrPdf(src);
            // Archive
            case ZIP_CREATE -> {
                List<Path> paths = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield archiveService.createZip(paths);
            }
            case ZIP_EXTRACT  -> archiveService.extractZip(src);
            case TAR_CREATE   -> {
                List<Path> paths = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield dataFormatService.createTar(paths);
            }
            case TAR_EXTRACT  -> dataFormatService.extractTar(src);
            // Office
            case EXCEL_TO_CSV -> officeService.excelToCsv(src);
            case CSV_TO_EXCEL -> officeService.csvToExcel(src);
            case WORD_TO_TEXT -> officeService.wordToText(src);
            // QR / Barcode
            case QR_GENERATE      -> qrCodeService.generateQr(str(p,"qrContent"), intOrDef(p,"qrSize",300));
            case BARCODE_GENERATE -> qrCodeService.generateBarcode(str(p,"barcodeContent"),
                                       str(p,"barcodeFormat"), 400, 150);
            case QR_SCAN          -> qrCodeService.scanCode(src);
            // Text / Data
            case CSV_TO_JSON      -> textConversionService.csvToJson(src);
            case JSON_TO_CSV      -> textConversionService.jsonToCsv(src);
            case XML_TO_JSON      -> textConversionService.xmlToJson(src);
            case JSON_TO_XML      -> textConversionService.jsonToXml(src);
            case MARKDOWN_TO_HTML -> textConversionService.markdownToHtml(src);
            case MARKDOWN_TO_PDF  -> textConversionService.markdownToPdf(src);
            case TEXT_TO_PDF      -> textConversionService.textToPdf(src);
            case HTML_TO_PDF      -> textConversionService.htmlToPdf(src);
            case JSON_TO_YAML     -> dataFormatService.jsonToYaml(src);
            case YAML_TO_JSON     -> dataFormatService.yamlToJson(src);
            case JSON_FORMAT      -> dataFormatService.formatJson(src);
            case XML_FORMAT       -> dataFormatService.formatXml(src);
            case BASE64_ENCODE    -> dataFormatService.base64Encode(src);
            case BASE64_DECODE    -> dataFormatService.base64Decode(src);
            case TEXT_DIFF        -> {
                String diffId = str(p, "diffFileId");
                Path src2 = fileStorageService.getFilePath(diffId);
                yield dataFormatService.textDiff(src, src2);
            }
            // SVG
            case SVG_TO_PNG -> svgService.svgToPng(src);
            case SVG_TO_PDF -> svgService.svgToPdf(src);
            // Video
            case VIDEO_THUMBNAIL    -> videoService.extractThumbnail(src, intOrDef(p,"videoSecond",1));
            case VIDEO_TO_GIF       -> videoService.videoToGif(src,
                                         intOrDef(p,"videoSecond",0),
                                         intOrDef(p,"videoDuration",5),
                                         intOrDef(p,"videoFps",10));
            case VIDEO_AUDIO_EXTRACT -> videoService.extractAudio(src);
        };
    }

    private void notify(String jobId) {
        try {
            JobResponse r = jobService.getJob(jobId);
            if (r != null) wsProgressService.sendProgress(jobId, r);
        } catch (Exception ignored) {}
    }

    private String mimeForExt(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf"  -> "application/pdf";
            case "png"  -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif"  -> "image/gif";
            case "mp3"  -> "audio/mpeg";
            case "zip"  -> "application/zip";
            case "json" -> "application/json";
            case "yaml","yml" -> "application/x-yaml";
            case "txt", "diff" -> "text/plain";
            case "html" -> "text/html";
            case "csv"  -> "text/csv";
            case "xml"  -> "application/xml";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default     -> "application/octet-stream";
        };
    }

    // Helpers
    private int intOf(Map<String, Object> p, String k) {
        Object v = p.get(k); if (v == null) return 0;
        return v instanceof Number ? ((Number)v).intValue() : Integer.parseInt(v.toString());
    }
    private int intOrDef(Map<String, Object> p, String k, int def) {
        Object v = p.get(k); if (v == null) return def;
        return v instanceof Number ? ((Number)v).intValue() : Integer.parseInt(v.toString());
    }
    private float floatOf(Map<String, Object> p, String k) {
        Object v = p.get(k); if (v == null) return 1f;
        return v instanceof Number ? ((Number)v).floatValue() : Float.parseFloat(v.toString());
    }
    private float floatOrDef(Map<String, Object> p, String k, float def) {
        Object v = p.get(k); if (v == null) return def;
        return v instanceof Number ? ((Number)v).floatValue() : Float.parseFloat(v.toString());
    }
    private String str(Map<String, Object> p, String k) {
        Object v = p.get(k); return v == null ? "" : v.toString();
    }
    @SuppressWarnings("unchecked")
    private List<String> listOf(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v instanceof List ? (List<String>)v : List.of();
    }
}
