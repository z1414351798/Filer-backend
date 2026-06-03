package com.filer.service;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.fop.svg.PDFTranscoder;
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
        File src = new File(uploadDir, fileId);
        String outId = UUID.randomUUID() + ".pdf";
        File outFile = new File(outputDir, outId);
        PDFTranscoder transcoder = new PDFTranscoder();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(outFile)) {
            transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(out));
        }
        return outId;
    }
}
