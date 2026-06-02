package com.filer.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileInfoService {

    @Value("${filer.output-dir}")
    private String outputDir;

    private final ObjectMapper objectMapper;

    public Path extractImageMetadata(Path imagePath) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        // Basic image dimensions via ImageIO
        try {
            BufferedImage img = ImageIO.read(imagePath.toFile());
            if (img != null) {
                root.put("width",  img.getWidth());
                root.put("height", img.getHeight());
                root.put("colorModel", img.getColorModel().getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("ImageIO read failed: {}", e.getMessage());
        }

        // EXIF and other metadata
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());
            for (Directory dir : metadata.getDirectories()) {
                ObjectNode dirNode = objectMapper.createObjectNode();
                for (Tag tag : dir.getTags()) {
                    dirNode.put(tag.getTagName(), tag.getDescription());
                }
                if (!dir.getTags().isEmpty()) {
                    root.set(dir.getName(), dirNode);
                }
            }
        } catch (Exception e) {
            log.warn("Metadata extraction failed: {}", e.getMessage());
        }

        root.put("fileSizeBytes", Files.size(imagePath));
        Path out = Paths.get(outputDir, UUID.randomUUID() + "_metadata.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
        return out;
    }

    public Path extractPdfInfo(Path pdfPath) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDDocumentInformation info = doc.getDocumentInformation();
            root.put("pageCount",   doc.getNumberOfPages());
            root.put("title",       info.getTitle());
            root.put("author",      info.getAuthor());
            root.put("subject",     info.getSubject());
            root.put("creator",     info.getCreator());
            root.put("producer",    info.getProducer());
            root.put("keywords",    info.getKeywords());
            root.put("encrypted",   doc.isEncrypted());
            root.put("fileSizeBytes", Files.size(pdfPath));
            if (info.getCreationDate() != null) {
                root.put("createdAt", info.getCreationDate().getTime().toString());
            }
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + "_pdfinfo.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
        return out;
    }

    public Path computeChecksum(Path filePath, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (DigestInputStream dis = new DigestInputStream(
                    Files.newInputStream(filePath), digest)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) {}
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));

            ObjectNode root = objectMapper.createObjectNode();
            root.put("algorithm", algorithm);
            root.put("checksum",  hex.toString());
            root.put("file",      filePath.getFileName().toString());
            root.put("fileSizeBytes", Files.size(filePath));

            Path out = Paths.get(outputDir, UUID.randomUUID() + "_checksum.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
            return out;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }
}
