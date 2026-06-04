package com.filer.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class OcrService {

    @Value("${tesseract.data-path}")
    private String tessDataPath;

    @Value("${tesseract.language}")
    private String language;

    @Value("${filer.output-dir}")
    private String outputDir;

    private Tesseract buildTesseract() {
        Tesseract t = new Tesseract();
        t.setDatapath(tessDataPath);
        t.setLanguage(language);
        return t;
    }

    public Path ocrImage(Path imagePath) throws TesseractException, IOException {
        String text = buildTesseract().doOCR(imagePath.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + "_ocr.txt");
        Files.writeString(out, text);
        return out;
    }

    public Path ocrPdf(Path pdfPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            Tesseract tess = buildTesseract();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 200, ImageType.RGB);
                try {
                    sb.append("=== Page ").append(i + 1).append(" ===\n");
                    sb.append(tess.doOCR(img));
                    sb.append("\n");
                } catch (TesseractException e) {
                    log.warn("OCR failed on page {}: {}", i + 1, e.getMessage());
                    sb.append("[OCR error on page ").append(i + 1).append("]\n");
                }
            }
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + "_ocr.txt");
        Files.writeString(out, sb.toString());
        return out;
    }
}
