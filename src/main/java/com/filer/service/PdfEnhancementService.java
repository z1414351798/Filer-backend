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
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.cos.COSName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

    /** Edit PDF document metadata (title, author, subject, keywords). */
    public Path editMetadata(Path src, String title, String author, String subject, String keywords) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            PDDocumentInformation info = doc.getDocumentInformation();
            if (title    != null && !title.isBlank())    info.setTitle(title);
            if (author   != null && !author.isBlank())   info.setAuthor(author);
            if (subject  != null && !subject.isBlank())  info.setSubject(subject);
            if (keywords != null && !keywords.isBlank()) info.setKeywords(keywords);
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    /** Extract all embedded images from a PDF and zip them. */
    public List<Path> extractImages(Path src) throws IOException {
        List<Path> images = new java.util.ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            int idx = 0;
            for (org.apache.pdfbox.pdmodel.PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                for (COSName name : res.getXObjectNames()) {
                    try {
                        org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = res.getXObject(name);
                        if (xobj instanceof PDImageXObject img) {
                            Path imgOut = Paths.get(outputDir, UUID.randomUUID() + "-img" + idx++ + ".png");
                            javax.imageio.ImageIO.write(img.getImage(), "png", imgOut.toFile());
                            images.add(imgOut);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return images;
    }

    /** Convert a colour PDF to grayscale by rasterising and re-embedding each page. */
    public Path grayscalePdf(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            try (PDDocument result = new PDDocument()) {
                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150,
                            org.apache.pdfbox.rendering.ImageType.GRAY);
                    PDPage newPage = new PDPage(doc.getPage(i).getMediaBox());
                    result.addPage(newPage);
                    PDImageXObject pdImg = PDImageXObject.createFromByteArray(result,
                            toPngBytes(img), "page-" + i);
                    try (PDPageContentStream cs = new PDPageContentStream(result, newPage)) {
                        cs.drawImage(pdImg, 0, 0, newPage.getMediaBox().getWidth(),
                                newPage.getMediaBox().getHeight());
                    }
                }
                Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
                result.save(out.toFile());
                return out;
            }
        }
    }

    /** Flatten all annotations and form fields in a PDF. */
    public Path flattenPdf(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            // Remove interactive form
            if (doc.getDocumentCatalog().getAcroForm() != null)
                doc.getDocumentCatalog().getAcroForm().flatten();
            // Remove all annotations
            for (org.apache.pdfbox.pdmodel.PDPage page : doc.getPages())
                page.setAnnotations(new java.util.ArrayList<>());
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    private byte[] toPngBytes(java.awt.image.BufferedImage img) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /**
     * Crop all pages by removing equal margins (points) from each side.
     * cropAll=single value applies to all sides; or specify top/right/bottom/left.
     */
    public Path cropMargins(Path src, int top, int right, int bottom, int left) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            for (PDPage page : doc.getPages()) {
                PDRectangle mb = page.getMediaBox();
                PDRectangle crop = new PDRectangle(
                    mb.getLowerLeftX()  + left,
                    mb.getLowerLeftY()  + bottom,
                    mb.getWidth()  - left - right,
                    mb.getHeight() - top  - bottom
                );
                page.setCropBox(crop);
            }
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    /**
     * Reorder PDF pages. {@code pageOrder} is a comma-separated list of 1-based page numbers,
     * e.g. "3,1,2" puts page 3 first, then 1, then 2.
     */
    public Path reorderPages(Path src, String pageOrder) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            String[] parts = pageOrder.split("[,\\s]+");
            try (PDDocument result = new PDDocument()) {
                for (String part : parts) {
                    int idx = Integer.parseInt(part.trim()) - 1;
                    if (idx >= 0 && idx < doc.getNumberOfPages())
                        result.addPage(doc.getPage(idx));
                }
                Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
                result.save(out.toFile());
                return out;
            }
        }
    }
}
