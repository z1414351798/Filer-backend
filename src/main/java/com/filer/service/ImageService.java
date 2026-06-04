package com.filer.service;

import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path convertFormat(Path source, String targetFormat) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + targetFormat.toLowerCase());
        String fmt = targetFormat.equalsIgnoreCase("jpg") ? "jpeg" : targetFormat.toLowerCase();
        Thumbnails.of(source.toFile())
                .scale(1.0)
                .outputFormat(fmt)
                .toFile(out.toFile());
        return out;
    }

    public Path resize(Path source, int width, int height) throws IOException {
        String ext = extension(source);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        Thumbnails.of(source.toFile())
                .size(width, height)
                .keepAspectRatio(false)
                .outputFormat(ext.equals("jpg") ? "jpeg" : ext)
                .toFile(out.toFile());
        return out;
    }

    public Path compress(Path source, float quality) throws IOException {
        String ext = extension(source);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        Thumbnails.of(source.toFile())
                .scale(1.0)
                .outputQuality(quality)
                .outputFormat(ext.equals("jpg") ? "jpeg" : ext)
                .toFile(out.toFile());
        return out;
    }

    public Path rotate(Path source, double angle) throws IOException {
        String ext = extension(source);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        Thumbnails.of(source.toFile())
                .scale(1.0)
                .rotate(angle)
                .outputFormat(ext.equals("jpg") ? "jpeg" : ext)
                .toFile(out.toFile());
        return out;
    }

    public Path flip(Path source, boolean horizontal) throws IOException {
        String ext = extension(source);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        BufferedImage img = ImageIO.read(source.toFile());
        BufferedImage flipped = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics2D g = flipped.createGraphics();
        if (horizontal) {
            g.drawImage(img, img.getWidth(), 0, -img.getWidth(), img.getHeight(), null);
        } else {
            g.drawImage(img, 0, img.getHeight(), img.getWidth(), -img.getHeight(), null);
        }
        g.dispose();
        String fmtName = ext.equals("jpg") ? "jpeg" : ext;
        ImageIO.write(flipped, fmtName, out.toFile());
        return out;
    }

    public Path grayscale(Path source) throws IOException {
        String ext = extension(source);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        BufferedImage img = ImageIO.read(source.toFile());
        BufferedImage gray = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        String fmtName = ext.equals("jpg") ? "jpeg" : ext;
        ImageIO.write(gray, fmtName, out.toFile());
        return out;
    }

    public Path addWatermark(Path source, String text) throws IOException {
        String ext = extension(source);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        BufferedImage img = ImageIO.read(source.toFile());
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("Arial", Font.BOLD, Math.max(24, img.getWidth() / 20)));
        AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f);
        g.setComposite(ac);
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        int x = (img.getWidth() - fm.stringWidth(text)) / 2;
        int y = img.getHeight() / 2;
        g.drawString(text, x, y);
        g.dispose();
        String fmtName = ext.equals("jpg") ? "jpeg" : ext;
        ImageIO.write(img, fmtName, out.toFile());
        return out;
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "png" : name.substring(dot + 1).toLowerCase();
    }
}
