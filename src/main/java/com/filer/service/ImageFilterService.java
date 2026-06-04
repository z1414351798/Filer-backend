package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class ImageFilterService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path crop(Path source, int x, int y, int w, int h) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        int safeW = Math.min(w, img.getWidth()  - x);
        int safeH = Math.min(h, img.getHeight() - y);
        BufferedImage cropped = img.getSubimage(Math.max(0, x), Math.max(0, y),
                Math.max(1, safeW), Math.max(1, safeH));
        return write(cropped, source);
    }

    public Path sepia(Path source) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
                int nr = clamp((int)(r * 0.393 + g * 0.769 + b * 0.189));
                int ng = clamp((int)(r * 0.349 + g * 0.686 + b * 0.168));
                int nb = clamp((int)(r * 0.272 + g * 0.534 + b * 0.131));
                out.setRGB(x, y, new Color(nr, ng, nb).getRGB());
            }
        }
        return write(out, source);
    }

    public Path invert(Path source) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                out.setRGB(x, y, new Color(255 - c.getRed(),
                        255 - c.getGreen(), 255 - c.getBlue()).getRGB());
            }
        }
        return write(out, source);
    }

    public Path blur(Path source, int radius) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        float value = 1.0f / (size * size);
        for (int i = 0; i < data.length; i++) data[i] = value;
        Kernel kernel = new Kernel(size, size, data);
        BufferedImageOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return write(op.filter(img, null), source);
    }

    public Path sharpen(Path source) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        float[] sharpenData = {
             0f, -1f,  0f,
            -1f,  5f, -1f,
             0f, -1f,  0f
        };
        Kernel kernel = new Kernel(3, 3, sharpenData);
        BufferedImageOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return write(op.filter(img, null), source);
    }

    public Path brightness(Path source, float factor) throws IOException {
        BufferedImage img = ImageIO.read(source.toFile());
        RescaleOp op = new RescaleOp(factor, 0, null);
        return write(op.filter(img, null), source);
    }

    private Path write(BufferedImage img, Path source) throws IOException {
        String ext = extension(source);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        String fmt = ext.equals("jpg") ? "jpeg" : ext;
        ImageIO.write(img, fmt, out.toFile());
        return out;
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "png" : name.substring(dot + 1).toLowerCase();
    }
}
