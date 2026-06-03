package com.filer.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class PdfEnhancementService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path compressPdf(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            doc.getDocument().setIsXRefStream(true);
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    public Path addTextWatermark(Path src, String text, float opacity) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (PDPage page : doc.getPages()) {
                PDRectangle mb = page.getMediaBox();
                float cx = mb.getWidth() / 2, cy = mb.getHeight() / 2;
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(opacity);
                gs.setStrokingAlphaConstant(opacity);
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(gs);
                    cs.beginText();
                    cs.setFont(font, 48);
                    cs.setNonStrokingColor(0.7f, 0.7f, 0.7f);
                    cs.setTextMatrix(
                            (float) Math.cos(Math.toRadians(45)), (float) Math.sin(Math.toRadians(45)),
                            -(float) Math.sin(Math.toRadians(45)), (float) Math.cos(Math.toRadians(45)),
                            cx - 80, cy);
                    cs.showText(text == null || text.isEmpty() ? "WATERMARK" : text);
                    cs.endText();
                }
            }
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    public Path rotatePage(Path src, int pageIndex, int degrees) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages())
                throw new IOException("Page index out of range");
            PDPage page = doc.getPage(pageIndex);
            page.setRotation((page.getRotation() + degrees) % 360);
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    public Path pdfToText(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
            java.nio.file.Files.writeString(out, text);
            return out;
        }
    }
}
