package com.filer.service;

import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageEnhancementService {

    @Value("${filer.upload-dir:uploads}")
    private String uploadDir;

    @Value("${filer.output-dir:outputs}")
    private String outputDir;

    public String createCollage(List<String> fileIds, int cols) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        for (String id : fileIds) {
            File f = new File(uploadDir, id);
            if (f.exists()) images.add(ImageIO.read(f));
        }
        if (images.isEmpty()) throw new IOException("No valid images");

        int cellW = 400, cellH = 300;
        int rows = (int) Math.ceil((double) images.size() / cols);
        BufferedImage canvas = new BufferedImage(cols * cellW, rows * cellH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int i = 0; i < images.size(); i++) {
            int x = (i % cols) * cellW;
            int y = (i / cols) * cellH;
            BufferedImage scaled = scaleToFit(images.get(i), cellW, cellH);
            int ox = x + (cellW - scaled.getWidth()) / 2;
            int oy = y + (cellH - scaled.getHeight()) / 2;
            g.drawImage(scaled, ox, oy, null);
        }
        g.dispose();

        String outId = UUID.randomUUID() + ".jpg";
        ImageIO.write(canvas, "jpg", new File(outputDir, outId));
        return outId;
    }

    public String addBorder(String fileId, int size, String colorHex) throws IOException {
        File src = new File(uploadDir, fileId);
        BufferedImage img = ImageIO.read(src);
        Color border = parseColor(colorHex);
        int newW = img.getWidth() + size * 2;
        int newH = img.getHeight() + size * 2;
        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setColor(border);
        g.fillRect(0, 0, newW, newH);
        g.drawImage(img, size, size, null);
        g.dispose();

        String ext = getExtension(fileId);
        String outId = UUID.randomUUID() + "." + ext;
        ImageIO.write(result, ext.equals("jpg") ? "jpg" : "png", new File(outputDir, outId));
        return outId;
    }

    public String roundCorners(String fileId, int radius) throws IOException {
        File src = new File(uploadDir, fileId);
        BufferedImage img = ImageIO.read(src);
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(img, 0, 0, null);
        g.dispose();

        String outId = UUID.randomUUID() + ".png";
        ImageIO.write(result, "png", new File(outputDir, outId));
        return outId;
    }

    public List<String> extractColorPalette(String fileId, int count) throws IOException {
        File src = new File(uploadDir, fileId);
        BufferedImage img = ImageIO.read(src);
        // Sample pixels on a grid
        Map<Integer, Integer> freq = new HashMap<>();
        int step = Math.max(1, Math.min(img.getWidth(), img.getHeight()) / 50);
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                int quantized = (rgb & 0xF0F0F0); // quantize to reduce noise
                freq.merge(quantized, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(count)
                .map(e -> String.format("#%06X", e.getKey()))
                .toList();
    }

    private BufferedImage scaleToFit(BufferedImage img, int maxW, int maxH) throws IOException {
        double scale = Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight());
        int w = (int) (img.getWidth() * scale);
        int h = (int) (img.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private Color parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.BLACK;
        try { return Color.decode(hex.startsWith("#") ? hex : "#" + hex); }
        catch (NumberFormatException e) { return Color.BLACK; }
    }

    private String getExtension(String fileId) {
        int dot = fileId.lastIndexOf('.');
        return dot >= 0 ? fileId.substring(dot + 1).toLowerCase() : "png";
    }
}
