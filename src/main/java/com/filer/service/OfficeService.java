package com.filer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
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
