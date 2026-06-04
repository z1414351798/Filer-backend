package com.filer.service;

import com.filer.model.enums.ConversionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final ImageService imageService;
    private final ImageFilterService imageFilterService;
    private final ImageEnhancementService imageEnhancementService;
    private final PdfService pdfService;
    private final PdfEnhancementService pdfEnhancementService;
    private final VideoService videoService;
    private final DataFormatService dataFormatService;
    private final ArchiveService archiveService;
    private final TextConversionService textConversionService;

    /**
     * Execute a single pipeline step. Returns the output Path.
     * Only operations that take one input file and produce one output file are supported.
     */
    public Path executeStep(Path input, String conversionType, Map<String, Object> opts) throws Exception {
        ConversionType type = ConversionType.valueOf(conversionType);
        return switch (type) {
            // ── Image format conversions ──────────────────────────────────────
            case IMAGE_TO_JPG    -> imageService.convertFormat(input, "jpg");
            case IMAGE_TO_PNG    -> imageService.convertFormat(input, "png");
            case IMAGE_TO_WEBP   -> imageService.convertFormat(input, "webp");
            case IMAGE_TO_BMP    -> imageService.convertFormat(input, "bmp");
            case IMAGE_TO_GIF    -> imageService.convertFormat(input, "gif");
            case IMAGE_TO_TIFF   -> imageService.convertFormat(input, "tiff");
            // ── Image basic ops ───────────────────────────────────────────────
            case IMAGE_RESIZE    -> imageService.resize(input, intOrDef(opts,"targetWidth",800), intOrDef(opts,"targetHeight",600));
            case IMAGE_COMPRESS  -> imageService.compress(input, floatOrDef(opts,"quality",0.8f));
            case IMAGE_ROTATE    -> imageService.rotate(input, floatOrDef(opts,"rotateDegrees",90f));
            case IMAGE_FLIP_H    -> imageService.flip(input, true);
            case IMAGE_FLIP_V    -> imageService.flip(input, false);
            case IMAGE_GRAYSCALE -> imageService.grayscale(input);
            case IMAGE_WATERMARK -> imageService.addWatermark(input, strOrDef(opts,"watermarkText","WATERMARK"));
            // ── Image filters ─────────────────────────────────────────────────
            case IMAGE_CROP      -> imageFilterService.crop(input, intOrDef(opts,"cropX",0), intOrDef(opts,"cropY",0), intOrDef(opts,"cropWidth",100), intOrDef(opts,"cropHeight",100));
            case IMAGE_SEPIA     -> imageFilterService.sepia(input);
            case IMAGE_INVERT    -> imageFilterService.invert(input);
            case IMAGE_BLUR      -> imageFilterService.blur(input, intOrDef(opts,"blurRadius",3));
            case IMAGE_SHARPEN   -> imageFilterService.sharpen(input);
            case IMAGE_BRIGHTNESS -> imageFilterService.brightness(input, floatOrDef(opts,"brightness",1.2f));
            // ── Image enhancements ────────────────────────────────────────────
            case IMAGE_BORDER      -> imageEnhancementService.addBorder(input, intOrDef(opts,"borderSize",10), strOrDef(opts,"borderColor","000000"));
            case IMAGE_ROUND_CORNERS -> imageEnhancementService.roundCorners(input, intOrDef(opts,"cornerRadius",20));
            case IMAGE_EXIF_STRIP -> imageEnhancementService.stripExif(input);
            case IMAGE_NOISE_REDUCE -> imageEnhancementService.reduceNoise(input);
            case IMAGE_CAPTION   -> imageEnhancementService.addCaption(input, strOrDef(opts,"captionText",""), strOrDef(opts,"captionPosition","bottom"));
            // ── PDF ops ───────────────────────────────────────────────────────
            case PDF_COMPRESS    -> pdfEnhancementService.compressPdf(input);
            case PDF_WATERMARK   -> pdfEnhancementService.addTextWatermark(input, strOrDef(opts,"watermarkText","DRAFT"), floatOrDef(opts,"watermarkOpacity",0.3f));
            case PDF_ADD_PAGE_NUMBERS -> pdfEnhancementService.addPageNumbers(input);
            case PDF_LINEARIZE   -> pdfEnhancementService.linearizePdf(input);
            case PDF_GRAYSCALE   -> pdfEnhancementService.grayscalePdf(input);
            case PDF_FLATTEN     -> pdfEnhancementService.flattenPdf(input);
            case PDF_EXTRACT_TEXT -> pdfEnhancementService.pdfToText(input);
            case PDF_TO_HTML     -> pdfEnhancementService.pdfToHtml(input);
            // ── Audio/Video ops ───────────────────────────────────────────────
            case AUDIO_NORMALIZE -> videoService.normalizeAudio(input);
            case AUDIO_VOLUME    -> videoService.adjustVolume(input, floatOrDef(opts,"volumeFactor",1.5f));
            case VIDEO_COMPRESS  -> videoService.compressVideo(input, intOrDef(opts,"quality",70));
            case VIDEO_RESIZE    -> videoService.resizeVideo(input, intOrDef(opts,"targetWidth",1280), intOrDef(opts,"targetHeight",720));
            // ── Data format ops ───────────────────────────────────────────────
            case JSON_FLATTEN    -> dataFormatService.flattenJson(input);
            case JSON_UNFLATTEN  -> dataFormatService.unflattenJson(input);
            case JSON_FORMAT     -> dataFormatService.formatJson(input);
            case JSON_MINIFY     -> dataFormatService.minifyJson(input);
            case XML_FORMAT      -> dataFormatService.formatXml(input);
            case CSV_DEDUP       -> dataFormatService.dedupCsv(input);
            case CSV_SORT        -> dataFormatService.sortCsv(input, strOrDef(opts,"sortColumn","1"), boolOrDef(opts,"sortAscending",true));
            case HTML_MINIFY     -> dataFormatService.minifyHtml(input);
            case HTML_SANITIZE   -> dataFormatService.minifyHtml(input);
            case MARKDOWN_TO_HTML -> textConversionService.markdownToHtml(Files.readString(input));
            default -> throw new UnsupportedOperationException("Operation not supported in pipeline: " + type);
        };
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int intOrDef(Map<String,Object> p, String k, int def) {
        Object v = p == null ? null : p.get(k);
        if (v == null) return def;
        return v instanceof Number ? ((Number)v).intValue() : Integer.parseInt(v.toString());
    }
    private float floatOrDef(Map<String,Object> p, String k, float def) {
        Object v = p == null ? null : p.get(k);
        if (v == null) return def;
        return v instanceof Number ? ((Number)v).floatValue() : Float.parseFloat(v.toString());
    }
    private String strOrDef(Map<String,Object> p, String k, String def) {
        Object v = p == null ? null : p.get(k);
        return v != null ? v.toString() : def;
    }
    private boolean boolOrDef(Map<String,Object> p, String k, boolean def) {
        Object v = p == null ? null : p.get(k);
        if (v == null) return def;
        return v instanceof Boolean ? (Boolean)v : Boolean.parseBoolean(v.toString());
    }
}
