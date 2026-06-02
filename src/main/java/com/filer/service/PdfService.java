package com.filer.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path mergePdfs(List<Path> sources) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        for (Path p : sources) merger.addSource(p.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
        merger.setDestinationFileName(out.toString());
        merger.mergeDocuments(null);
        return out;
    }

    public List<Path> splitPdf(Path source, int splitAfterPage) throws IOException {
        try (PDDocument doc = Loader.loadPDF(source.toFile())) {
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(splitAfterPage);
            List<PDDocument> parts = splitter.split(doc);
            List<Path> results = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Path out = Paths.get(outputDir, UUID.randomUUID() + "_part" + (i + 1) + ".pdf");
                parts.get(i).save(out.toFile());
                parts.get(i).close();
                results.add(out);
            }
            return results;
        }
    }

    public List<Path> pdfToImages(Path source, String format) throws IOException {
        List<Path> results = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(source.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                String fmt = format.equalsIgnoreCase("jpg") ? "jpeg" : format.toLowerCase();
                Path out = Paths.get(outputDir, UUID.randomUUID() + "_page" + (i + 1) + "." + format.toLowerCase());
                ImageIO.write(img, fmt, out.toFile());
                results.add(out);
            }
        }
        return results;
    }

    public Path imagesToPdf(List<Path> imagePaths) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            for (Path imgPath : imagePaths) {
                BufferedImage bImg = ImageIO.read(imgPath.toFile());
                PDPage page = new PDPage();
                doc.addPage(page);
                PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bImg);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    float scale = Math.min(
                            page.getMediaBox().getWidth() / bImg.getWidth(),
                            page.getMediaBox().getHeight() / bImg.getHeight());
                    cs.drawImage(pdImage, 0, 0,
                            bImg.getWidth() * scale, bImg.getHeight() * scale);
                }
            }
            doc.save(out.toFile());
        }
        return out;
    }

    public String extractText(Path source) throws IOException {
        try (PDDocument doc = Loader.loadPDF(source.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    public Path encryptPdf(Path source, String password) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
        try (PDDocument doc = Loader.loadPDF(source.toFile())) {
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, ap);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(out.toFile());
        }
        return out;
    }

    public Path decryptPdf(Path source, String password) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
        try (PDDocument doc = Loader.loadPDF(source.toFile(), password)) {
            doc.setAllSecurityToBeRemoved(true);
            doc.save(out.toFile());
        }
        return out;
    }

    public Path saveTextToFile(String text) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, text);
        return out;
    }
}
