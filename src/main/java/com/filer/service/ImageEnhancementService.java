package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

@Service
public class ImageEnhancementService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path createCollage(List<File> files, int cols) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        for (File f : files) {
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
            int x = (i % cols) * cellW, y = (i / cols) * cellH;
            BufferedImage scaled = scaleToFit(images.get(i), cellW, cellH);
            g.drawImage(scaled, x + (cellW - scaled.getWidth()) / 2, y + (cellH - scaled.getHeight()) / 2, null);
        }
        g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".jpg");
        ImageIO.write(canvas, "jpg", out.toFile());
        return out;
    }

    public Path addBorder(Path src, int size, String colorHex) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        Color border = parseColor(colorHex);
        int newW = img.getWidth() + size * 2, newH = img.getHeight() + size * 2;
        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setColor(border); g.fillRect(0, 0, newW, newH);
        g.drawImage(img, size, size, null); g.dispose();
        String ext = ext(src);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        ImageIO.write(result, ext.equals("jpg") ? "jpeg" : ext, out.toFile());
        return out;
    }

    public Path roundCorners(Path src, int radius) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(img, 0, 0, null); g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        ImageIO.write(result, "png", out.toFile());
        return out;
    }

    public Path extractColorPalette(Path src, int count) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        Map<Integer, Integer> freq = new HashMap<>();
        int step = Math.max(1, Math.min(img.getWidth(), img.getHeight()) / 50);
        for (int y = 0; y < img.getHeight(); y += step)
            for (int x = 0; x < img.getWidth(); x += step)
                freq.merge(img.getRGB(x, y) & 0xF0F0F0, 1, Integer::sum);
        List<String> colors = freq.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(count).map(e -> String.format("#%06X", e.getKey())).toList();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        java.nio.file.Files.writeString(out, String.join("\n", colors));
        return out;
    }

    private BufferedImage scaleToFit(BufferedImage img, int maxW, int maxH) {
        double scale = Math.min((double) maxW / img.getWidth(), (double) maxH / img.getHeight());
        int w = (int)(img.getWidth() * scale), h = (int)(img.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null); g.dispose(); return out;
    }

    private Color parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.BLACK;
        try { return Color.decode(hex.startsWith("#") ? hex : "#" + hex); }
        catch (NumberFormatException e) { return Color.BLACK; }
    }

    private String ext(Path p) {
        String n = p.getFileName().toString();
        int d = n.lastIndexOf('.'); return d < 0 ? "png" : n.substring(d + 1).toLowerCase();
    }
}
