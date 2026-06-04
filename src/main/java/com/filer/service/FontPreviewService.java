package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FontPreviewService {

    @Value("${filer.output-dir}")
    private String outputDir;

    private static final String PREVIEW_TEXT = "AaBbCcDd EeFfGgHh\n0123456789 !@#$%&\nThe quick brown fox";
    private static final int[] SIZES = {12, 18, 24, 36, 48};
    private static final int WIDTH = 800;

    public Path generateFontPreview(Path fontFile) throws IOException, FontFormatException {
        Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile.toFile());

        // Calculate total height needed
        int lineHeight = 60;
        int blockHeight = SIZES.length * lineHeight + 20;
        int totalHeight = blockHeight + 80;

        BufferedImage img = new BufferedImage(WIDTH, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Background
        g.setColor(new Color(0xfafafa));
        g.fillRect(0, 0, WIDTH, totalHeight);

        // Title bar
        g.setColor(new Color(0x6366f1));
        g.fillRect(0, 0, WIDTH, 50);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        String fontName = fontFile.getFileName().toString();
        g.drawString("Font Preview: " + fontName, 20, 32);

        int y = 70;
        for (int size : SIZES) {
            Font f = baseFont.deriveFont(Font.PLAIN, size);
            g.setColor(new Color(0x333333));
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString(size + "px", 10, y + 4);

            g.setFont(f);
            g.setColor(new Color(0x111111));
            String line = "AaBbCcDd 0123456789 The quick brown fox";
            g.drawString(line, 55, y + 10);
            y += lineHeight;

            // Separator
            g.setColor(new Color(0xeeeeee));
            g.drawLine(0, y - 10, WIDTH, y - 10);
        }

        g.dispose();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".png");
        ImageIO.write(img, "png", out.toFile());
        return out;
    }
}
