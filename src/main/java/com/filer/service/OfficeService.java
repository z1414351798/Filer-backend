package com.filer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.text.Document;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeService {

    @Value("${filer.output-dir}")
    private String outputDir;

    @Value("${filer.libreoffice-path:libreoffice}")
    private String libreofficePath;

    private final ObjectMapper objectMapper;

    public Path excelToCsv(Path excelPath) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".csv");
        try (Workbook wb = WorkbookFactory.create(excelPath.toFile());
             BufferedWriter writer = Files.newBufferedWriter(out)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            for (Row row : sheet) {
                StringBuilder line = new StringBuilder();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    if (c > 0) line.append(',');
                    Cell cell = row.getCell(c);
                    String val = cell == null ? "" : fmt.formatCellValue(cell);
                    if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
                        val = "\"" + val.replace("\"", "\"\"") + "\"";
                    }
                    line.append(val);
                }
                writer.write(line.toString());
                writer.newLine();
            }
        }
        return out;
    }

    public Path csvToExcel(Path csvPath) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook();
             BufferedReader reader = Files.newBufferedReader(csvPath)) {
            Sheet sheet = wb.createSheet("Sheet1");
            String line;
            int rowIdx = 0;
            while ((line = reader.readLine()) != null) {
                Row row = sheet.createRow(rowIdx++);
                String[] cols = line.split(",", -1);
                for (int c = 0; c < cols.length; c++) {
                    row.createCell(c).setCellValue(cols[c].replaceAll("^\"|\"$", ""));
                }
            }
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }
        }
        return out;
    }

    public Path wordToText(Path docxPath) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(docxPath));
             BufferedWriter writer = Files.newBufferedWriter(out)) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                writer.write(para.getText());
                writer.newLine();
            }
        }
        return out;
    }

    /** Convert any Office document (DOCX/XLSX/PPTX/ODT…) to PDF using LibreOffice headless. */
    public Path officeToPdf(Path src) throws IOException, InterruptedException {
        Path outDir = Paths.get(outputDir);
        Files.createDirectories(outDir);
        ProcessBuilder pb = new ProcessBuilder(
                libreofficePath, "--headless", "--norestore",
                "--convert-to", "pdf",
                "--outdir", outDir.toString(),
                src.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String loOut = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();
        if (exitCode != 0) throw new IOException("LibreOffice exited " + exitCode + ": " + loOut);

        // LO names the output file after the source basename
        String baseName = src.getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        String pdfName = (dot >= 0 ? baseName.substring(0, dot) : baseName) + ".pdf";
        Path produced = outDir.resolve(pdfName);
        if (!Files.exists(produced))
            throw new IOException("LibreOffice produced no output. Log: " + loOut);

        // Rename to a UUID so filenames never collide
        Path final_ = outDir.resolve(UUID.randomUUID() + ".pdf");
        Files.move(produced, final_);
        return final_;
    }

    /** Convert PPTX to a ZIP of PNG slide images via LibreOffice PDF then PDFBox rasterise. */
    public List<Path> pptxToImages(Path src) throws Exception {
        Path pdf = officeToPdf(src);
        List<Path> images = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 150);
                Path imgOut = Paths.get(outputDir, UUID.randomUUID() + ".png");
                ImageIO.write(img, "png", imgOut.toFile());
                images.add(imgOut);
            }
        }
        return images;
    }

    /** Convert RTF file to plain text. */
    public Path rtfToText(Path src) throws Exception {
        RTFEditorKit kit = new RTFEditorKit();
        Document doc = kit.createDefaultDocument();
        try (java.io.InputStream is = Files.newInputStream(src)) {
            kit.read(is, doc, 0);
        }
        String text = doc.getText(0, doc.getLength());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, text);
        return out;
    }

    /** Convert RTF file to PDF (text-based, preserves paragraphs). */
    public Path rtfToPdf(Path src) throws Exception {
        RTFEditorKit kit = new RTFEditorKit();
        Document doc = kit.createDefaultDocument();
        try (java.io.InputStream is = Files.newInputStream(src)) {
            kit.read(is, doc, 0);
        }
        String text = doc.getText(0, doc.getLength());
        // Use PDFBox to write text as a PDF
        try (PDDocument pdf = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float margin = 50, lineHeight = 14, fontSize = 11;
            float pageW = PDRectangle.A4.getWidth(), pageH = PDRectangle.A4.getHeight();
            float maxY = pageH - margin;
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(pdf, page);
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(margin, maxY);
            float y = maxY;
            for (String line : text.split("\n")) {
                // wrap long lines
                while (line.length() > 90) {
                    cs.showText(line.substring(0, 90));
                    cs.newLineAtOffset(0, -lineHeight);
                    y -= lineHeight;
                    line = line.substring(90);
                    if (y < margin) {
                        cs.endText(); cs.close();
                        page = new PDPage(PDRectangle.A4);
                        pdf.addPage(page);
                        cs = new PDPageContentStream(pdf, page);
                        cs.beginText(); cs.setFont(font, fontSize);
                        cs.newLineAtOffset(margin, maxY); y = maxY;
                    }
                }
                cs.showText(line);
                cs.newLineAtOffset(0, -lineHeight);
                y -= lineHeight;
                if (y < margin) {
                    cs.endText(); cs.close();
                    page = new PDPage(PDRectangle.A4);
                    pdf.addPage(page);
                    cs = new PDPageContentStream(pdf, page);
                    cs.beginText(); cs.setFont(font, fontSize);
                    cs.newLineAtOffset(margin, maxY); y = maxY;
                }
            }
            cs.endText(); cs.close();
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
            pdf.save(out.toFile());
            return out;
        }
    }

    /** Merge multiple Excel files: each file contributes its first sheet as a new sheet in the output. */
    public Path mergeExcel(List<Path> sources) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".xlsx");
        try (XSSFWorkbook outWb = new XSSFWorkbook()) {
            for (int fi = 0; fi < sources.size(); fi++) {
                try (Workbook srcWb = WorkbookFactory.create(sources.get(fi).toFile())) {
                    Sheet srcSheet = srcWb.getSheetAt(0);
                    Sheet dstSheet = outWb.createSheet("Sheet" + (fi + 1));
                    DataFormatter fmt = new DataFormatter();
                    for (Row srcRow : srcSheet) {
                        Row dstRow = dstSheet.createRow(srcRow.getRowNum());
                        for (Cell srcCell : srcRow) {
                            dstRow.createCell(srcCell.getColumnIndex())
                                  .setCellValue(fmt.formatCellValue(srcCell));
                        }
                    }
                }
            }
            try (OutputStream os = Files.newOutputStream(out)) { outWb.write(os); }
        }
        return out;
    }

    /** Convert a JSON array-of-objects to an XLSX workbook. */
    public Path jsonToExcel(Path src) throws IOException {
        JsonNode root = objectMapper.readTree(src.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");
            if (root.isArray() && root.size() > 0) {
                // Build header row from first object's keys
                JsonNode first = root.get(0);
                List<String> keys = new ArrayList<>();
                first.fieldNames().forEachRemaining(keys::add);

                Row header = sheet.createRow(0);
                for (int c = 0; c < keys.size(); c++)
                    header.createCell(c).setCellValue(keys.get(c));

                for (int r = 0; r < root.size(); r++) {
                    Row row = sheet.createRow(r + 1);
                    JsonNode obj = root.get(r);
                    for (int c = 0; c < keys.size(); c++) {
                        JsonNode val = obj.get(keys.get(c));
                        row.createCell(c).setCellValue(val == null ? "" : val.asText());
                    }
                }
            }
            try (OutputStream os = Files.newOutputStream(out)) { wb.write(os); }
        }
        return out;
    }
}
