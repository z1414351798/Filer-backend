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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class SvgService {

    @Value("${filer.upload-dir:uploads}")
    private String uploadDir;

    @Value("${filer.output-dir:outputs}")
    private String outputDir;

    public String svgToPng(String fileId) throws Exception {
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".png";
        File outFile = new File(outputDir, outId);
        PNGTranscoder transcoder = new PNGTranscoder();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(outFile)) {
            transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(out));
        }
        return outId;
    }

    public String svgToPdf(String fileId) throws Exception {
        // Rasterize SVG to PNG first, then embed into PDF via PDFBox
        String pngId = svgToPng(fileId);
        File pngFile = new File(outputDir, pngId);

        String outId = UUID.randomUUID() + ".pdf";
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject img = PDImageXObject.createFromFile(pngFile.getAbsolutePath(), doc);
            float scale = Math.min(
                    page.getMediaBox().getWidth() / img.getWidth(),
                    page.getMediaBox().getHeight() / img.getHeight());
            float w = img.getWidth() * scale;
            float h = img.getHeight() * scale;
            float x = (page.getMediaBox().getWidth() - w) / 2;
            float y = (page.getMediaBox().getHeight() - h) / 2;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(img, x, y, w, h);
            }
            doc.save(new File(outputDir, outId));
        }
        // Clean up intermediate PNG
        pngFile.delete();
        return outId;
    }
}
