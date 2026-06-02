package com.filer.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OfficeService {

    @Value("${filer.output-dir}")
    private String outputDir;

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
}
