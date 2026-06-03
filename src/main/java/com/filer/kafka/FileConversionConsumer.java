package com.filer.kafka;

import com.filer.dto.JobResponse;
import com.filer.model.enums.ConversionType;
import com.filer.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileConversionConsumer {

    private final JobService jobService;
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
        String jobId = (String) payload.get("jobId");
        String fileId = (String) payload.get("fileId");
        String typeStr = (String) payload.get("conversionType");
        String notifyEmail = (String) payload.get("notifyEmail");

        try {
            jobService.updateProgress(jobId, 10);
            notify(jobId, 10);

            ConversionType type = ConversionType.valueOf(typeStr);
            String outputId = dispatch(type, fileId, payload);

            jobService.updateProgress(jobId, 90);
            notify(jobId, 90);
            jobService.markCompleted(jobId, outputId);
            notify(jobId, 100);

            if (notifyEmail != null) {
                emailService.sendJobCompleted(notifyEmail, jobId, "/api/files/" + outputId + "/download");
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            jobService.markFailed(jobId, e.getMessage());
            notifyFailed(jobId);
            if (notifyEmail != null) {
                emailService.sendJobFailed(notifyEmail, jobId, e.getMessage());
            }
            ack.acknowledge();
        }
    }

    @SuppressWarnings("unchecked")
    private String dispatch(ConversionType type, String fileId, Map<String, Object> p) throws Exception {
        return switch (type) {
            // ── Image format conversions ──
            case IMAGE_TO_PNG -> imageService.convertFormat(fileId, "png");
            case IMAGE_TO_JPG -> imageService.convertFormat(fileId, "jpg");
            case IMAGE_TO_WEBP -> imageService.convertFormat(fileId, "webp");
            case IMAGE_TO_BMP -> imageService.convertFormat(fileId, "bmp");
            case IMAGE_TO_GIF -> imageService.convertFormat(fileId, "gif");
            case IMAGE_TO_TIFF -> imageService.convertFormat(fileId, "tiff");
            // ── Image edits ──
            case IMAGE_RESIZE -> imageService.resize(fileId,
                    intVal(p, "targetWidth"), intVal(p, "targetHeight"));
            case IMAGE_COMPRESS -> imageService.compress(fileId, intVal(p, "quality"));
            case IMAGE_ROTATE -> imageService.rotate(fileId, intVal(p, "rotateDegrees"));
            case IMAGE_FLIP_H -> imageService.flip(fileId, true);
            case IMAGE_FLIP_V -> imageService.flip(fileId, false);
            case IMAGE_GRAYSCALE -> imageService.grayscale(fileId);
            case IMAGE_WATERMARK -> imageService.watermark(fileId, strVal(p, "watermarkText"));
            // ── Image filters ──
            case IMAGE_CROP -> imageFilterService.crop(fileId,
                    intVal(p, "cropX"), intVal(p, "cropY"),
                    intVal(p, "cropWidth"), intVal(p, "cropHeight"));
            case IMAGE_SEPIA -> imageFilterService.sepia(fileId);
            case IMAGE_INVERT -> imageFilterService.invert(fileId);
            case IMAGE_BLUR -> imageFilterService.blur(fileId, intValOrDefault(p, "blurRadius", 3));
            case IMAGE_SHARPEN -> imageFilterService.sharpen(fileId);
            case IMAGE_BRIGHTNESS -> imageFilterService.brightness(fileId, floatVal(p, "brightness"));
            // ── Image enhancements ──
            case IMAGE_COLLAGE -> imageEnhancementService.createCollage(
                    listVal(p, "fileIds"), intValOrDefault(p, "cols", 3));
            case IMAGE_BORDER -> imageEnhancementService.addBorder(fileId,
                    intValOrDefault(p, "borderSize", 20), strVal(p, "borderColor"));
            case IMAGE_ROUND_CORNERS -> imageEnhancementService.roundCorners(fileId,
                    intValOrDefault(p, "cornerRadius", 30));
            case IMAGE_COLOR_PALETTE -> {
                List<String> palette = imageEnhancementService.extractColorPalette(
                        fileId, intValOrDefault(p, "paletteCount", 6));
                String outId = java.util.UUID.randomUUID() + ".txt";
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(System.getProperty("user.dir"), "outputs", outId),
                        String.join("\n", palette));
                yield outId;
            }
            // ── PDF ──
            case PDF_MERGE -> pdfService.mergeDocuments(
                    listVal(p, "fileIds"));
            case PDF_SPLIT -> pdfService.splitDocument(fileId);
            case PDF_TO_IMAGES -> pdfService.pdfToImages(fileId);
            case IMAGES_TO_PDF -> pdfService.imagesToPdf(listVal(p, "fileIds"));
            case PDF_EXTRACT_TEXT -> pdfService.extractText(fileId);
            case PDF_ENCRYPT -> pdfService.encryptPdf(fileId, strVal(p, "password"));
            case PDF_DECRYPT -> pdfService.decryptPdf(fileId, strVal(p, "password"));
            case PDF_COMPRESS -> pdfEnhancementService.compressPdf(fileId);
            case PDF_WATERMARK -> pdfEnhancementService.addTextWatermark(fileId,
                    strVal(p, "watermarkText"), floatValOrDefault(p, "watermarkOpacity", 0.3f));
            case PDF_PAGE_ROTATE -> pdfEnhancementService.rotatePage(fileId,
                    intValOrDefault(p, "pageIndex", 0),
                    intValOrDefault(p, "rotationDegrees", 90));
            case PDF_TO_DOCX -> pdfEnhancementService.pdfToDocx(fileId);
            // ── OCR ──
            case OCR_IMAGE -> ocrService.ocrImage(fileId);
            case OCR_PDF -> ocrService.ocrPdf(fileId);
            // ── Archive ──
            case ZIP_CREATE -> archiveService.createZip(listVal(p, "fileIds"));
            case ZIP_EXTRACT -> archiveService.extractZip(fileId);
            case TAR_CREATE -> dataFormatService.createTar(listVal(p, "fileIds"));
            case TAR_EXTRACT -> dataFormatService.extractTar(fileId);
            // ── Office ──
            case EXCEL_TO_CSV -> officeService.excelToCsv(fileId);
            case CSV_TO_EXCEL -> officeService.csvToExcel(fileId);
            case WORD_TO_TEXT -> officeService.wordToText(fileId);
            // ── QR / Barcode ──
            case QR_GENERATE -> qrCodeService.generateQr(strVal(p, "qrContent"));
            case BARCODE_GENERATE -> qrCodeService.generateBarcode(strVal(p, "barcodeContent"));
            case QR_SCAN -> qrCodeService.scanCode(fileId);
            // ── Text / Data ──
            case CSV_TO_JSON -> textConversionService.csvToJson(fileId);
            case JSON_TO_CSV -> textConversionService.jsonToCsv(fileId);
            case XML_TO_JSON -> textConversionService.xmlToJson(fileId);
            case JSON_TO_XML -> textConversionService.jsonToXml(fileId);
            case MARKDOWN_TO_HTML -> textConversionService.markdownToHtml(fileId);
            case MARKDOWN_TO_PDF -> textConversionService.markdownToPdf(fileId);
            case TEXT_TO_PDF -> textConversionService.textToPdf(fileId);
            case HTML_TO_PDF -> textConversionService.htmlToPdf(fileId);
            case JSON_TO_YAML -> dataFormatService.jsonToYaml(fileId);
            case YAML_TO_JSON -> dataFormatService.yamlToJson(fileId);
            case JSON_FORMAT -> dataFormatService.formatJson(fileId);
            case XML_FORMAT -> dataFormatService.formatXml(fileId);
            case BASE64_ENCODE -> dataFormatService.base64Encode(fileId);
            case BASE64_DECODE -> dataFormatService.base64Decode(fileId);
            case TEXT_DIFF -> dataFormatService.textDiff(fileId, strVal(p, "diffFileId"));
            // ── SVG ──
            case SVG_TO_PNG -> svgService.svgToPng(fileId);
            case SVG_TO_PDF -> svgService.svgToPdf(fileId);
            // ── Video ──
            case VIDEO_THUMBNAIL -> videoService.extractThumbnail(fileId,
                    intValOrDefault(p, "videoSecond", 1));
            case VIDEO_TO_GIF -> videoService.videoToGif(fileId,
                    intValOrDefault(p, "videoSecond", 0),
                    intValOrDefault(p, "videoDuration", 5),
                    intValOrDefault(p, "videoFps", 10));
            case VIDEO_AUDIO_EXTRACT -> videoService.extractAudio(fileId);
        };
    }

    private void notify(String jobId, int progress) {
        try {
            JobResponse r = jobService.getJob(jobId);
            if (r != null) wsProgressService.sendProgress(jobId, r);
        } catch (Exception ignored) {}
    }

    private void notifyFailed(String jobId) {
        notify(jobId, -1);
    }

    private int intVal(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return 0;
        return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
    }

    private int intValOrDefault(Map<String, Object> p, String key, int def) {
        Object v = p.get(key);
        if (v == null) return def;
        return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
    }

    private float floatVal(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return 1.0f;
        return v instanceof Number ? ((Number) v).floatValue() : Float.parseFloat(v.toString());
    }

    private float floatValOrDefault(Map<String, Object> p, String key, float def) {
        Object v = p.get(key);
        if (v == null) return def;
        return v instanceof Number ? ((Number) v).floatValue() : Float.parseFloat(v.toString());
    }

    private String strVal(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? "" : v.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> listVal(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof List) return (List<String>) v;
        return List.of();
    }
}
