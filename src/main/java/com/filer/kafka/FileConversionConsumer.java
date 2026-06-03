package com.filer.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filer.dto.JobResponse;
import com.filer.model.FileRecord;
import com.filer.model.enums.ConversionType;
import com.filer.service.*;
import com.filer.service.FontPreviewService;
import com.filer.service.HtmlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileConversionConsumer {

    private final JobService jobService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
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
    private final FileEncryptionService fileEncryptionService;
    private final WebSocketProgressService wsProgressService;
    private final EmailService emailService;
    private final FontPreviewService fontPreviewService;
    private final HtmlService htmlService;

    @KafkaListener(topics = "file-conversion", groupId = "filer-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment ack) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse Kafka message: {}", e.getMessage());
            ack.acknowledge();
            return;
        }

        String jobId = str(payload, "jobId");
        String fileId = str(payload, "fileId");
        String typeStr = str(payload, "conversionType");
        String notifyEmail = str(payload, "notifyEmail");
        try {
            jobService.updateProgress(jobId, 10);
            notify(jobId);

            ConversionType type = ConversionType.valueOf(typeStr);
            Path srcPath = (fileId != null && !fileId.isEmpty())
                    ? fileStorageService.getFilePath(fileId) : null;

            Path outPath = dispatch(type, srcPath, payload);

            String filename = outPath.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String mime = mimeForExt(dot >= 0 ? filename.substring(dot + 1) : "bin");
            FileRecord outRecord = fileStorageService.saveOutputFile(
                    outPath, "output" + (dot >= 0 ? filename.substring(dot) : ""), mime);

            jobService.updateProgress(jobId, 90);
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

    private Path dispatch(ConversionType type, Path src, Map<String, Object> p) throws Exception {
        return switch (type) {
            case IMAGE_TO_PNG  -> imageService.convertFormat(src, "png");
            case IMAGE_TO_JPG  -> imageService.convertFormat(src, "jpg");
            case IMAGE_TO_WEBP -> imageService.convertFormat(src, "webp");
            case IMAGE_TO_BMP  -> imageService.convertFormat(src, "bmp");
            case IMAGE_TO_GIF  -> imageService.convertFormat(src, "gif");
            case IMAGE_TO_TIFF -> imageService.convertFormat(src, "tiff");
            case IMAGE_RESIZE     -> imageService.resize(src, intOf(p,"targetWidth"), intOf(p,"targetHeight"));
            case IMAGE_COMPRESS   -> imageService.compress(src, intOrDef(p,"quality",75) / 100f);
            case IMAGE_ROTATE     -> imageService.rotate(src, intOf(p,"rotateDegrees"));
            case IMAGE_FLIP_H     -> imageService.flip(src, true);
            case IMAGE_FLIP_V     -> imageService.flip(src, false);
            case IMAGE_GRAYSCALE  -> imageService.grayscale(src);
            case IMAGE_WATERMARK  -> imageService.addWatermark(src, str(p,"watermarkText"));
            case IMAGE_CROP       -> imageFilterService.crop(src, intOf(p,"cropX"), intOf(p,"cropY"),
                                       intOf(p,"cropWidth"), intOf(p,"cropHeight"));
            case IMAGE_SEPIA      -> imageFilterService.sepia(src);
            case IMAGE_INVERT     -> imageFilterService.invert(src);
            case IMAGE_BLUR       -> imageFilterService.blur(src, intOrDef(p,"blurRadius",3));
            case IMAGE_SHARPEN    -> imageFilterService.sharpen(src);
            case IMAGE_BRIGHTNESS -> imageFilterService.brightness(src, floatOf(p,"brightness"));
            case IMAGE_COLLAGE -> {
                List<File> files = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id).toFile()).toList();
                yield imageEnhancementService.createCollage(files, intOrDef(p,"cols",3));
            }
            case IMAGE_BORDER        -> imageEnhancementService.addBorder(src,
                                          intOrDef(p,"borderSize",20), str(p,"borderColor"));
            case IMAGE_ROUND_CORNERS -> imageEnhancementService.roundCorners(src,
                                          intOrDef(p,"cornerRadius",30));
            case IMAGE_COLOR_PALETTE -> imageEnhancementService.extractColorPalette(src,
                                          intOrDef(p,"paletteCount",6));
            case PDF_MERGE -> {
                List<Path> paths = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield pdfService.mergePdfs(paths);
            }
            case PDF_SPLIT -> {
                List<Path> parts = pdfService.splitPdf(src, intOrDef(p,"splitPage",1));
                yield archiveService.createZip(parts, null);
            }
            case PDF_TO_IMAGES -> {
                List<Path> imgs = pdfService.pdfToImages(src, "png");
                yield archiveService.createZip(imgs, null);
            }
            case IMAGES_TO_PDF -> {
                List<Path> paths = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield pdfService.imagesToPdf(paths);
            }
            case PDF_EXTRACT_TEXT -> pdfService.saveTextToFile(pdfService.extractText(src));
            case PDF_ENCRYPT      -> pdfService.encryptPdf(src, str(p,"password"));
            case PDF_DECRYPT      -> pdfService.decryptPdf(src, str(p,"password"));
            case PDF_COMPRESS     -> pdfEnhancementService.compressPdf(src);
            case PDF_WATERMARK    -> pdfEnhancementService.addTextWatermark(src,
                                       str(p,"watermarkText"), floatOrDef(p,"watermarkOpacity",0.3f));
            case PDF_PAGE_ROTATE  -> pdfEnhancementService.rotatePage(src,
                                       intOrDef(p,"pageIndex",0), intOrDef(p,"rotateDegrees",90));
            case PDF_TO_DOCX      -> officeService.pdfToDocx(src);
            case OCR_IMAGE -> ocrService.ocrImage(src);
            case OCR_PDF   -> ocrService.ocrPdf(src);
            case ZIP_CREATE -> {
                List<Path> paths = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield archiveService.createZip(paths, null);
            }
            case ZIP_EXTRACT -> {
                List<Path> extracted = archiveService.extractZip(src);
                yield archiveService.createZip(extracted, null);
            }
            case TAR_CREATE -> {
                List<Path> paths = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield dataFormatService.createTar(paths);
            }
            case TAR_EXTRACT  -> dataFormatService.extractTar(src);
            case EXCEL_TO_CSV -> officeService.excelToCsv(src);
            case CSV_TO_EXCEL -> officeService.csvToExcel(src);
            case WORD_TO_TEXT -> officeService.wordToText(src);
            case QR_GENERATE      -> qrCodeService.generateQr(str(p,"qrContent"), intOrDef(p,"qrSize",300));
            case BARCODE_GENERATE -> qrCodeService.generateBarcode(
                                       str(p,"barcodeContent"), str(p,"barcodeFormat"), 400, 150);
            case QR_SCAN          -> qrCodeService.scanCode(src);
            case CSV_TO_JSON      -> textConversionService.csvToJson(src);
            case JSON_TO_CSV      -> textConversionService.jsonToCsv(src);
            case XML_TO_JSON      -> textConversionService.xmlToJson(src);
            case JSON_TO_XML      -> textConversionService.jsonToXml(src);
            case MARKDOWN_TO_HTML -> textConversionService.markdownToHtml(Files.readString(src));
            case MARKDOWN_TO_PDF  -> textConversionService.markdownToPdf(Files.readString(src));
            case TEXT_TO_PDF      -> textConversionService.textToPdf(Files.readString(src));
            case HTML_TO_PDF      -> textConversionService.htmlToPdf(src);
            case JSON_TO_YAML     -> dataFormatService.jsonToYaml(src);
            case YAML_TO_JSON     -> dataFormatService.yamlToJson(src);
            case JSON_FORMAT      -> dataFormatService.formatJson(src);
            case XML_FORMAT       -> dataFormatService.formatXml(src);
            case BASE64_ENCODE    -> dataFormatService.base64Encode(src);
            case BASE64_DECODE    -> dataFormatService.base64Decode(src);
            case TEXT_DIFF -> {
                Path src2 = fileStorageService.getFilePath(str(p,"diffFileId"));
                yield dataFormatService.textDiff(src, src2);
            }
            case SVG_TO_PNG -> svgService.svgToPng(src);
            case SVG_TO_PDF -> svgService.svgToPdf(src);
            case VIDEO_THUMBNAIL     -> videoService.extractThumbnail(src, intOrDef(p,"videoSecond",1));
            case VIDEO_TO_GIF        -> videoService.videoToGif(src, intOrDef(p,"videoSecond",0),
                                          intOrDef(p,"videoDuration",5), intOrDef(p,"videoFps",10));
            case VIDEO_AUDIO_EXTRACT -> videoService.extractAudio(src);
            case AUDIO_CONVERT       -> videoService.convertAudio(src, str(p,"targetFormat"));
            case VIDEO_TRIM          -> videoService.trimVideo(src, intOrDef(p,"startSec",0), intOrDef(p,"durationSec",30));
            case VIDEO_COMPRESS      -> videoService.compressVideo(src, intOrDef(p,"quality",50));
            case VIDEO_TO_MP4        -> videoService.convertToMp4(src);
            case AUDIO_TRIM -> {
                // reuse trimVideo but audio-only: encode to mp3 with offset+duration
                Path trimmed = videoService.trimVideo(src, intOrDef(p,"startSec",0), intOrDef(p,"durationSec",30));
                // re-encode to mp3 only
                yield videoService.convertAudio(trimmed, "mp3");
            }
            case AUDIO_MERGE -> {
                List<Path> srcs = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield videoService.mergeAudio(srcs);
            }
            case FILE_AES_ENCRYPT -> fileEncryptionService.encrypt(src, str(p,"password"));
            case FILE_AES_DECRYPT -> fileEncryptionService.decrypt(src, str(p,"password"));
            case HASH_FILE         -> dataFormatService.hashFile(src, str(p,"hashAlgorithm"));
            case URL_ENCODE        -> dataFormatService.urlEncode(src);
            case URL_DECODE        -> dataFormatService.urlDecode(src);
            case JWT_DECODE        -> dataFormatService.jwtDecode(src);
            case EXCEL_TO_JSON     -> dataFormatService.excelToJson(src);
            case CSV_MERGE -> {
                List<Path> srcs = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield dataFormatService.mergeCsv(srcs);
            }
            case PDF_TO_HTML     -> pdfEnhancementService.pdfToHtml(src);
            case PDF_LINEARIZE   -> pdfEnhancementService.linearizePdf(src);
            case IMAGE_ASCII_ART -> imageEnhancementService.imageToAsciiArt(src);
            case IMAGE_MEME      -> imageEnhancementService.addMemeText(src, str(p,"topText"), str(p,"bottomText"));
            case IMAGE_COMPARE   -> {
                Path src2 = fileStorageService.getFilePath(str(p,"diffFileId"));
                yield imageEnhancementService.compareImages(src, src2);
            }
            case RTF_TO_TEXT  -> officeService.rtfToText(src);
            case RTF_TO_PDF   -> officeService.rtfToPdf(src);
            case EXCEL_MERGE -> {
                List<Path> paths = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield officeService.mergeExcel(paths);
            }
            case CSV_TO_HTML       -> dataFormatService.csvToHtml(src);
            case JSON_TO_HTML      -> dataFormatService.jsonToHtml(src);
            case TEXT_CASE_CONVERT -> dataFormatService.convertTextCase(src, str(p,"caseType"));
            case SUBTITLE_SRT_TO_VTT -> dataFormatService.srtToVtt(src);
            case VTT_TO_SRT          -> dataFormatService.vttToSrt(src);
            case VIDEO_EXTRACT_FRAMES -> {
                List<Path> frames = videoService.extractFrames(src, intOrDef(p,"frameInterval",5));
                yield archiveService.createZip(frames, null);
            }
            case VIDEO_ADD_WATERMARK -> videoService.addVideoWatermark(src, str(p,"videoWatermarkText"));
            case IMAGE_TO_ICO        -> imageEnhancementService.imageToIco(src);
            case PDF_CROP_MARGINS    -> pdfEnhancementService.cropMargins(src,
                                          intOrDef(p,"cropTop",36), intOrDef(p,"cropRight",36),
                                          intOrDef(p,"cropBottom",36), intOrDef(p,"cropLeft",36));
            case PDF_REORDER_PAGES   -> pdfEnhancementService.reorderPages(src, str(p,"pageOrder"));
            case FONT_PREVIEW        -> fontPreviewService.generateFontPreview(src);
            // PDF extras
            case PDF_METADATA_EDIT  -> pdfEnhancementService.editMetadata(src,
                                          str(p,"pdfTitle"), str(p,"pdfAuthor"),
                                          str(p,"pdfSubject"), str(p,"pdfKeywords"));
            case PDF_EXTRACT_IMAGES -> {
                List<java.nio.file.Path> imgs = pdfEnhancementService.extractImages(src);
                yield archiveService.createZip(imgs, null);
            }
            case PDF_GRAYSCALE -> pdfEnhancementService.grayscalePdf(src);
            case PDF_FLATTEN   -> pdfEnhancementService.flattenPdf(src);
            // Image extras
            case IMAGE_EXIF_STRIP    -> imageEnhancementService.stripExif(src);
            case IMAGE_NOISE_REDUCE  -> imageEnhancementService.reduceNoise(src);
            case IMAGE_ANIMATED_GIF  -> {
                List<java.io.File> files = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id).toFile()).toList();
                yield imageEnhancementService.createAnimatedGif(files, intOrDef(p,"gifDelay",100));
            }
            // Video/audio extras
            case VIDEO_SPEED_CHANGE -> videoService.changeSpeed(src, floatOrDef(p,"videoSpeed",1.5f));
            case AUDIO_SPLIT -> {
                List<java.nio.file.Path> parts = videoService.splitAudio(src, intOrDef(p,"splitAtSec",30));
                yield archiveService.createZip(parts, null);
            }
            // HTML tools
            case HTML_SANITIZE    -> htmlService.sanitize(src, str(p,"sanitizeLevel"));
            case HTML_TO_MARKDOWN -> htmlService.htmlToMarkdown(src);
            // Dev/data tools
            case CSV_STATS     -> dataFormatService.csvStats(src);
            case UUID_GENERATE -> dataFormatService.generateUuids(intOrDef(p,"uuidCount",10));
            case LOREM_IPSUM   -> dataFormatService.generateLoremIpsum(intOrDef(p,"loremParagraphs",5));
            case RANDOM_CSV    -> dataFormatService.generateRandomCsv(str(p,"randomColumns"), intOrDef(p,"randomRows",100));
            // Office → PDF
            case DOCX_TO_PDF -> officeService.officeToPdf(src);
            case XLSX_TO_PDF -> officeService.officeToPdf(src);
            case PPTX_TO_PDF -> officeService.officeToPdf(src);
            case PPTX_TO_IMAGES -> {
                List<Path> slides = officeService.pptxToImages(src);
                yield archiveService.createZip(slides, null);
            }
            // PDF extras
            case PDF_PAGE_EXTRACT -> pdfEnhancementService.extractPages(src,
                                       intOrDef(p,"fromPage",1), intOrDef(p,"toPage",0));
            case PDF_ADD_PAGE_NUMBERS -> pdfEnhancementService.addPageNumbers(src);
            // Data extras
            case JSON_TO_EXCEL -> officeService.jsonToExcel(src);
            // Wave 6 new cases
            case VIDEO_CONCAT -> {
                List<Path> srcs = listOf(p,"fileIds").stream()
                        .map(id -> fileStorageService.getFilePath(id)).toList();
                yield videoService.concatVideos(srcs.isEmpty() ? List.of(src) : srcs);
            }
            case VIDEO_RESIZE       -> videoService.resizeVideo(src, intOrDef(p,"targetWidth",0), intOrDef(p,"targetHeight",0));
            case AUDIO_NORMALIZE    -> videoService.normalizeAudio(src);
            case AUDIO_FADE         -> videoService.fadeAudio(src, intOrDef(p,"fadeInDuration",0), intOrDef(p,"fadeOutDuration",0));
            case SUBTITLE_SHIFT     -> dataFormatService.shiftSubtitle(src, longOrDef(p,"shiftMs",0L));
            case REGEX_TEST         -> dataFormatService.testRegex(strOrDef(p,"regexPattern",".*"), strOrDef(p,"regexInput",""), strOrDef(p,"regexFlags",""));
            case PASSWORD_GENERATE  -> dataFormatService.generatePassword(intOrDef(p,"pwdLength",16), boolOrDef(p,"pwdUppercase",true), boolOrDef(p,"pwdNumbers",true), boolOrDef(p,"pwdSymbols",false));
            case PASSPHRASE_GENERATE -> dataFormatService.generatePassphrase(intOrDef(p,"passphraseWords",4));
            case COLOR_CONVERT      -> dataFormatService.convertColor(strOrDef(p,"colorInput","#FFFFFF"), strOrDef(p,"colorFrom","hex"), strOrDef(p,"colorTo","all"));
            case PLACEHOLDER_IMAGE  -> imageEnhancementService.generatePlaceholder(intOrDef(p,"targetWidth",400), intOrDef(p,"targetHeight",300), strOrDef(p,"placeholderBg","CCCCCC"), strOrDef(p,"placeholderLabel",""));
            case HTML_MINIFY        -> dataFormatService.minifyHtml(src);
            case JSON_MINIFY        -> dataFormatService.minifyJson(src);
            case XML_TO_YAML        -> dataFormatService.xmlToYaml(src);
            case YAML_TO_XML        -> dataFormatService.yamlToXml(src);
            case CSV_TO_XML         -> dataFormatService.csvToXml(src);
            // File utility types are handled via /api/info/* endpoints, not Kafka jobs
            case FILE_CHECKSUM, IMAGE_METADATA, PDF_INFO ->
                throw new UnsupportedOperationException(type + " is handled by /api/info endpoints");
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
            case "pdf"        -> "application/pdf";
            case "png"        -> "image/png";
            case "jpg","jpeg" -> "image/jpeg";
            case "gif"        -> "image/gif";
            case "md"         -> "text/markdown";
            case "mp3"        -> "audio/mpeg";
            case "zip"        -> "application/zip";
            case "json"       -> "application/json";
            case "yaml","yml" -> "application/x-yaml";
            case "diff","txt" -> "text/plain";
            case "html"       -> "text/html";
            case "csv"        -> "text/csv";
            case "xml"        -> "application/xml";
            case "xlsx"       -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "docx"       -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx"       -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "wav"        -> "audio/wav";
            case "ogg"        -> "audio/ogg";
            case "aac","m4a"  -> "audio/aac";
            case "flac"       -> "audio/flac";
            case "ico"        -> "image/x-icon";
            case "vtt"        -> "text/vtt";
            case "srt"        -> "text/plain";
            default           -> "application/octet-stream";
        };
    }

    private int intOf(Map<String, Object> p, String k) {
        Object v = p.get(k); if (v == null) return 0;
        return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString());
    }
    private int intOrDef(Map<String, Object> p, String k, int def) {
        Object v = p.get(k); if (v == null) return def;
        return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString());
    }
    private float floatOf(Map<String, Object> p, String k) {
        Object v = p.get(k); if (v == null) return 1f;
        return v instanceof Number n ? n.floatValue() : Float.parseFloat(v.toString());
    }
    private float floatOrDef(Map<String, Object> p, String k, float def) {
        Object v = p.get(k); if (v == null) return def;
        return v instanceof Number n ? n.floatValue() : Float.parseFloat(v.toString());
    }
    private String str(Map<String, Object> p, String k) {
        Object v = p.get(k); return v == null ? "" : v.toString();
    }
    @SuppressWarnings("unchecked")
    private List<String> listOf(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v instanceof List<?> l ? (List<String>) l : List.of();
    }
    private long longOrDef(Map<String, Object> p, String k, long def) {
        Object v = p.get(k); if (v == null) return def;
        return v instanceof Number ? ((Number)v).longValue() : Long.parseLong(v.toString());
    }
    private boolean boolOrDef(Map<String, Object> p, String k, boolean def) {
        Object v = p.get(k); if (v == null) return def;
        if (v instanceof Boolean) return (Boolean)v;
        return Boolean.parseBoolean(v.toString());
    }
    private String strOrDef(Map<String, Object> p, String k, String def) {
        Object v = p.get(k); return v != null ? v.toString() : def;
    }
}
