package com.filer.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class QrCodeService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path generateQr(String text, int size) throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "_qr.png");
        MatrixToImageWriter.writeToPath(matrix, "PNG", out);
        return out;
    }

    public Path generateBarcode(String text, String format, int width, int height)
            throws WriterException, IOException {
        Writer writer = switch (format.toUpperCase()) {
            case "EAN_13" -> new EAN13Writer();
            default       -> new Code128Writer();
        };
        BarcodeFormat fmt = switch (format.toUpperCase()) {
            case "EAN_13" -> BarcodeFormat.EAN_13;
            default       -> BarcodeFormat.CODE_128;
        };
        BitMatrix matrix = writer.encode(text, fmt, width, height);
        Path out = Paths.get(outputDir, UUID.randomUUID() + "_barcode.png");
        MatrixToImageWriter.writeToPath(matrix, "PNG", out);
        return out;
    }

    public Path scanCode(Path imagePath) throws IOException, NotFoundException {
        BufferedImage img = ImageIO.read(imagePath.toFile());
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(img)));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Result result = new MultiFormatReader().decode(bitmap, hints);
        String text = "Format: " + result.getBarcodeFormat() + "\nContent: " + result.getText();
        Path out = Paths.get(outputDir, UUID.randomUUID() + "_scan.txt");
        Files.writeString(out, text);
        return out;
    }
}
