package com.filer.service;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class SvgService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path svgToPng(Path src) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        PNGTranscoder transcoder = new PNGTranscoder();
        try (InputStream in = new FileInputStream(src.toFile());
             OutputStream outStream = new FileOutputStream(out.toFile())) {
            transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(outStream));
        }
        return out;
    }

    public Path svgToPdf(Path src) throws Exception {
        Path pngPath = svgToPng(src);
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject img = PDImageXObject.createFromFile(pngPath.toString(), doc);
            float scale = Math.min(
                    page.getMediaBox().getWidth() / img.getWidth(),
                    page.getMediaBox().getHeight() / img.getHeight());
            float w = img.getWidth() * scale, h = img.getHeight() * scale;
            float x = (page.getMediaBox().getWidth() - w) / 2;
            float y = (page.getMediaBox().getHeight() - h) / 2;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(img, x, y, w, h);
            }
            doc.save(out.toFile());
        }
        Files.deleteIfExists(pngPath);
        return out;
    }
}
