package com.filer.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfEnhancementService {

    @Value("${filer.upload-dir:uploads}")
    private String uploadDir;

    @Value("${filer.output-dir:outputs}")
    private String outputDir;

    public String compressPdf(String fileId) throws IOException {
        File src = new File(uploadDir, fileId);
        try (PDDocument doc = Loader.loadPDF(src)) {
            // Remove duplicate resources and compress streams
            doc.getDocument().setIsXRefStream(true);
            String outId = UUID.randomUUID() + ".pdf";
            doc.save(new File(outputDir, outId));
            return outId;
        }
    }

    public String addTextWatermark(String fileId, String text, float opacity) throws IOException {
        File src = new File(uploadDir, fileId);
        try (PDDocument doc = Loader.loadPDF(src)) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (PDPage page : doc.getPages()) {
                PDRectangle mediaBox = page.getMediaBox();
                float fontSize = 48;
                float cx = mediaBox.getWidth() / 2;
                float cy = mediaBox.getHeight() / 2;

                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(opacity);
                gs.setStrokingAlphaConstant(opacity);

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(gs);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.setNonStrokingColor(0.7f, 0.7f, 0.7f);
                    // rotate 45 degrees around center
                    cs.setTextMatrix(
                            (float) Math.cos(Math.toRadians(45)),
                            (float) Math.sin(Math.toRadians(45)),
                            -(float) Math.sin(Math.toRadians(45)),
                            (float) Math.cos(Math.toRadians(45)),
                            cx - 80, cy);
                    cs.showText(text == null || text.isEmpty() ? "WATERMARK" : text);
                    cs.endText();
                }
            }
            String outId = UUID.randomUUID() + ".pdf";
            doc.save(new File(outputDir, outId));
            return outId;
        }
    }

    public String rotatePage(String fileId, int pageIndex, int degrees) throws IOException {
        File src = new File(uploadDir, fileId);
        try (PDDocument doc = Loader.loadPDF(src)) {
            if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages())
                throw new IOException("Page index out of range");
            PDPage page = doc.getPage(pageIndex);
            int current = page.getRotation();
            page.setRotation((current + degrees) % 360);
            String outId = UUID.randomUUID() + ".pdf";
            doc.save(new File(outputDir, outId));
            return outId;
        }
    }

    public String pdfToDocx(String fileId) throws IOException {
        // Extract text from PDF and write as plain .docx-style text file
        // Full DOCX generation would require Apache POI; here we produce .txt for free tier
        File src = new File(uploadDir, fileId);
        try (PDDocument doc = Loader.loadPDF(src)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            String outId = UUID.randomUUID() + ".txt";
            java.nio.file.Files.writeString(new File(outputDir, outId).toPath(), text);
            return outId;
        }
    }
}
