package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    /** Convert image to ASCII art text file. */
    public Path imageToAsciiArt(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        int width  = Math.min(img.getWidth(),  160);
        int height = Math.min(img.getHeight(), 80);
        // Scale down
        java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        scaled.getGraphics().drawImage(img.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        char[] chars = {'@','#','S','%','?','*','+',';',':',',',' '};
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int brightness = (r + g + b) / 3;
                sb.append(chars[brightness * (chars.length - 1) / 255]);
            }
            sb.append('\n');
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, sb.toString());
        return out;
    }

    /** Add meme-style top and bottom text to an image. */
    public Path addMemeText(Path src, String topText, String bottomText) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int fontSize = Math.max(24, img.getWidth() / 15);
        Font font = new Font("Arial Black", Font.BOLD, fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        // draw with white fill + black stroke
        if (topText != null && !topText.isBlank()) {
            int x = (img.getWidth() - fm.stringWidth(topText)) / 2;
            drawOutlinedText(g, topText.toUpperCase(), x, fontSize + 10);
        }
        if (bottomText != null && !bottomText.isBlank()) {
            int x = (img.getWidth() - fm.stringWidth(bottomText)) / 2;
            drawOutlinedText(g, bottomText.toUpperCase(), x, img.getHeight() - 15);
        }
        g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    private void drawOutlinedText(Graphics2D g, String text, int x, int y) {
        g.setColor(Color.BLACK);
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                if (dx != 0 || dy != 0) g.drawString(text, x + dx, y + dy);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }

    /**
     * Convert image to Windows ICO format (16×16 and 32×32 embedded).
     * Writes a minimal ICO file manually (no external lib needed).
     */
    public Path imageToIco(Path src) throws IOException {
        BufferedImage orig = ImageIO.read(src.toFile());
        int[] sizes = {16, 32};
        // Pre-render each size as a BMP-like ARGB image
        List<byte[]> bmps = new java.util.ArrayList<>();
        for (int size : sizes) {
            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(orig.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(scaled, "png", baos);
            bmps.add(baos.toByteArray());
        }

        Path out = Paths.get(outputDir, UUID.randomUUID() + ".ico");
        try (java.io.DataOutputStream dos = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(Files.newOutputStream(out)))) {
            // ICO header
            dos.writeShort(leShort(0));          // reserved
            dos.writeShort(leShort(1));          // type: ICO
            dos.writeShort(leShort(sizes.length)); // image count

            // Directory entries (6-byte header + 16-byte entries each)
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                dos.writeByte(sizes[i]);  // width
                dos.writeByte(sizes[i]);  // height
                dos.writeByte(0);         // color count (0 = more than 256)
                dos.writeByte(0);         // reserved
                dos.writeShort(leShort(1));  // color planes
                dos.writeShort(leShort(32)); // bits per pixel
                dos.writeInt(leInt(bmps.get(i).length)); // data size
                dos.writeInt(leInt(offset));              // data offset
                offset += bmps.get(i).length;
            }
            // Image data
            for (byte[] bmp : bmps) dos.write(bmp);
        }
        return out;
    }

    private short leShort(int v) { return Short.reverseBytes((short) v); }
    private int leInt(int v) { return Integer.reverseBytes(v); }

    /** Strip all EXIF/IPTC/XMP metadata from an image by re-encoding through AWT. */
    public Path stripExif(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        // Determine format from extension
        String name = src.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "jpg";
        if (ext.equals("jpeg")) ext = "jpg";
        // Writing through ImageIO drops all metadata
        Path out = Paths.get(outputDir, UUID.randomUUID() + "." + ext);
        // Re-draw to new image to ensure clean slate
        BufferedImage clean = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = clean.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        ImageIO.write(clean, ext.equals("jpg") ? "jpeg" : ext, out.toFile());
        return out;
    }

    /** Simple noise reduction using a 3×3 averaging kernel. */
    public Path reduceNoise(Path src) throws IOException {
        BufferedImage img = ImageIO.read(src.toFile());
        java.awt.image.Kernel kernel = new java.awt.image.Kernel(3, 3, new float[]{
            1/9f,1/9f,1/9f, 1/9f,1/9f,1/9f, 1/9f,1/9f,1/9f
        });
        java.awt.image.ConvolveOp op = new java.awt.image.ConvolveOp(kernel,
                java.awt.image.ConvolveOp.EDGE_NO_OP, null);
        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        op.filter(img, result);
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        ImageIO.write(result, "png", out.toFile());
        return out;
    }

    /**
     * Create an animated GIF from multiple image files.
     * delayCs = delay between frames in centiseconds (100 = 1 second).
     */
    public Path createAnimatedGif(List<File> files, int delayCs) throws IOException {
        if (files.isEmpty()) throw new IOException("No images provided");
        List<BufferedImage> frames = new java.util.ArrayList<>();
        int w = 0, h = 0;
        for (File f : files) {
            BufferedImage img = ImageIO.read(f);
            if (img != null) { frames.add(img); w = Math.max(w, img.getWidth()); h = Math.max(h, img.getHeight()); }
        }
        if (frames.isEmpty()) throw new IOException("Could not read any images");

        Path out = Paths.get(outputDir, UUID.randomUUID() + ".gif");
        try (javax.imageio.stream.ImageOutputStream ios =
                javax.imageio.ImageIO.createImageOutputStream(out.toFile())) {
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (BufferedImage frame : frames) {
                // Scale to common size
                BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.setColor(Color.WHITE); g.fillRect(0,0,w,h);
                g.drawImage(frame.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
                g.dispose();

                javax.imageio.IIOImage iioImage = new javax.imageio.IIOImage(scaled, null, null);
                javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                writer.writeToSequence(iioImage, param);
            }
            writer.endWriteSequence();
        }
        return out;
    }

    /** Generate a placeholder image with given dimensions and optional label. */
    public Path generatePlaceholder(int width, int height, String bg, String label) throws IOException {
        int w = Math.min(Math.max(width, 16), 4000);
        int h = Math.min(Math.max(height, 16), 4000);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        // Background
        try {
            String hex = (bg == null || bg.isBlank()) ? "CCCCCC" : bg.replace("#","");
            g.setColor(java.awt.Color.decode("#" + hex));
        } catch (Exception e) { g.setColor(java.awt.Color.LIGHT_GRAY); }
        g.fillRect(0, 0, w, h);
        // Border
        g.setColor(java.awt.Color.GRAY);
        g.drawRect(0, 0, w-1, h-1);
        // Label
        String text = (label == null || label.isBlank()) ? w + " × " + h : label;
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(80,80,80));
        int fontSize = Math.max(10, Math.min(w / (text.length() + 2), h / 4));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, fontSize));
        java.awt.FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(text, tx, ty);
        g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        return out;
    }

    /** Render text/code as a styled PNG image (dark or light theme). */
    public Path textToImage(String text, String theme, int fontSize) throws IOException {
        boolean dark = !"light".equalsIgnoreCase(theme);
        String[] lines = (text == null ? "Hello, World!" : text).split("\n");
        int lineH = fontSize + 6;
        int padding = 24;
        int w = 800;
        int h = lines.length * lineH + padding * 2;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Background
        g.setColor(dark ? new java.awt.Color(30, 30, 30) : new java.awt.Color(250, 250, 250));
        g.fillRect(0, 0, w, h);
        // Top bar
        g.setColor(dark ? new java.awt.Color(50, 50, 50) : new java.awt.Color(220, 220, 220));
        g.fillRect(0, 0, w, 28);
        // Dots
        int[] dotColors = {0xFF5F57, 0xFFBD2E, 0x28C840};
        for (int i = 0; i < 3; i++) {
            g.setColor(new java.awt.Color(dotColors[i]));
            g.fillOval(12 + i * 20, 8, 12, 12);
        }
        // Text
        int fs = Math.max(10, Math.min(fontSize, 32));
        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, fs));
        g.setColor(dark ? new java.awt.Color(204, 204, 204) : new java.awt.Color(50, 50, 50));
        for (int i = 0; i < lines.length; i++)
            g.drawString(lines[i], padding, 28 + padding + i * lineH);
        g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        return out;
    }

    /** Add a text caption above or below an image. */
    public Path addCaption(Path src, String caption, String position) throws IOException {
        java.awt.image.BufferedImage orig = javax.imageio.ImageIO.read(src.toFile());
        int capH = 40, fs = 16;
        int w = orig.getWidth(), h = orig.getHeight() + capH;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        boolean top = "top".equalsIgnoreCase(position);
        g.drawImage(orig, 0, top ? capH : 0, null);
        g.setColor(new java.awt.Color(40, 40, 40));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.ITALIC, fs));
        java.awt.FontMetrics fm = g.getFontMetrics();
        String cap = caption == null ? "" : caption;
        int tx = (w - fm.stringWidth(cap)) / 2;
        int ty = top ? (capH + fm.getAscent()) / 2 + 2 : h - (capH - fm.getAscent()) / 2;
        g.drawString(cap, tx, ty);
        g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        return out;
    }

    /** Generate a QR code with a logo image overlaid in the center. */
    public Path qrWithLogo(String content, int size, Path logoPath) throws Exception {
        // Generate QR at higher error correction to survive logo overlay
        java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
        hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
        int sz = Math.min(Math.max(size, 100), 1000);
        com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter()
                .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, sz, sz, hints);
        java.awt.image.BufferedImage qr = new java.awt.image.BufferedImage(sz, sz, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < sz; x++)
            for (int y = 0; y < sz; y++)
                qr.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
        // Overlay logo
        if (logoPath != null && logoPath.toFile().exists()) {
            java.awt.image.BufferedImage logo = javax.imageio.ImageIO.read(logoPath.toFile());
            int logoSize = sz / 4;
            java.awt.Image scaled = logo.getScaledInstance(logoSize, logoSize, java.awt.Image.SCALE_SMOOTH);
            java.awt.Graphics2D g = qr.createGraphics();
            int lx = (sz - logoSize) / 2, ly = (sz - logoSize) / 2;
            g.setColor(java.awt.Color.WHITE);
            g.fillRoundRect(lx - 4, ly - 4, logoSize + 8, logoSize + 8, 10, 10);
            g.drawImage(scaled, lx, ly, null);
            g.dispose();
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        javax.imageio.ImageIO.write(qr, "png", out.toFile());
        return out;
    }

    /** Convert an image file to a base64 data URI string (saved as .txt). */
    public Path imageToDataUri(Path src) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(src);
        String name = src.getFileName().toString().toLowerCase();
        String mime = name.endsWith(".png") ? "image/png" : name.endsWith(".gif") ? "image/gif"
                    : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
        String dataUri = "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        java.nio.file.Files.writeString(out, dataUri);
        return out;
    }

    /** Generate a pixel-diff image highlighting differences between two images. */
    public Path compareImages(Path src1, Path src2) throws IOException {
        BufferedImage img1 = ImageIO.read(src1.toFile());
        BufferedImage img2 = ImageIO.read(src2.toFile());
        int w = Math.min(img1.getWidth(),  img2.getWidth());
        int h = Math.min(img1.getHeight(), img2.getHeight());
        BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int diffCount = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);
                if (rgb1 != rgb2) {
                    diff.setRGB(x, y, 0xFF0000); // red for diff pixels
                    diffCount++;
                } else {
                    // dim the unchanged area to make diffs stand out
                    int r = ((rgb1 >> 16) & 0xFF) / 3;
                    int gv = ((rgb1 >> 8) & 0xFF) / 3;
                    int bv = (rgb1 & 0xFF) / 3;
                    diff.setRGB(x, y, (r << 16) | (gv << 8) | bv);
                }
            }
        }
        // Write summary text onto image
        Graphics2D g = diff.createGraphics();
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.YELLOW);
        g.drawString(String.format("Diff pixels: %d / %d (%.2f%%)", diffCount, w * h,
                diffCount * 100.0 / (w * h)), 10, 20);
        g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        ImageIO.write(diff, "png", out.toFile());
        return out;
    }
}
