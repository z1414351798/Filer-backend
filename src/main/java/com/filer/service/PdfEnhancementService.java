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
import java.nio.file.Files;
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

    /** Extract a page range (1-based, inclusive) from a PDF. */
    public Path extractPages(Path src, int fromPage, int toPage) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            int total = doc.getNumberOfPages();
            int from = Math.max(1, fromPage);
            int to   = Math.min(total, toPage <= 0 ? total : toPage);
            try (PDDocument result = new PDDocument()) {
                for (int i = from - 1; i < to; i++)
                    result.addPage(doc.getPage(i));
                Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
                result.save(out.toFile());
                return out;
            }
        }
    }

    /** Stamp page numbers at the bottom centre of every page. */
    public Path addPageNumbers(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = doc.getPage(i);
                PDRectangle mb = page.getMediaBox();
                String label = (i + 1) + " / " + total;
                float textWidth = font.getStringWidth(label) / 1000 * 11;
                float x = (mb.getWidth() - textWidth) / 2;
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.beginText();
                    cs.setFont(font, 11);
                    cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                    cs.newLineAtOffset(x, 20);
                    cs.showText(label);
                    cs.endText();
                }
            }
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    /** Extract text from PDF and wrap in basic HTML. */
    public Path pdfToHtml(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>")
                .append("body{font-family:sans-serif;max-width:860px;margin:40px auto;white-space:pre-wrap;line-height:1.6}")
                .append("</style></head><body>\n");
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                html.append("<section data-page=\"").append(i).append("\">\n");
                html.append(escapeHtml(stripper.getText(doc)));
                html.append("\n</section>\n<hr>\n");
            }
            html.append("</body></html>");
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".html");
            Files.writeString(out, html.toString());
            return out;
        }
    }

    /** Linearize (web-optimize) a PDF by saving with cross-reference streams. */
    public Path linearizePdf(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            doc.getDocument().setIsXRefStream(true);
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    private String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}
