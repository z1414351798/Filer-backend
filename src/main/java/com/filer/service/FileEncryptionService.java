package com.filer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.UUID;

@Service
public class FileEncryptionService {

    private static final int SALT_LEN = 16;
    private static final int IV_LEN   = 16;
    private static final int ITER     = 65536;
    private static final int KEY_BITS = 256;

    @Value("${filer.output-dir}")
    private String outputDir;

    /**
     * AES-256-CBC encrypt with PBKDF2 key derivation.
     * Output format: [16-byte salt][16-byte IV][ciphertext]
     */
    public Path encrypt(Path src, String password) throws Exception {
        byte[] salt = new byte[SALT_LEN];
        byte[] iv   = new byte[IV_LEN];
        new SecureRandom().nextBytes(salt);
        new SecureRandom().nextBytes(iv);

        SecretKeySpec key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        String ext = src.getFileName().toString();
        int dot = ext.lastIndexOf('.');
        String suffix = dot >= 0 ? ext.substring(dot) : "";
        Path out = Paths.get(outputDir, UUID.randomUUID() + suffix + ".enc");

        try (InputStream in  = Files.newInputStream(src);
             OutputStream os = Files.newOutputStream(out)) {
            os.write(salt);
            os.write(iv);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                os.write(cipher.update(buf, 0, len));
            }
            os.write(cipher.doFinal());
        }
        return out;
    }

    /**
     * Decrypt a file produced by {@link #encrypt}.
     */
    public Path decrypt(Path src, String password) throws Exception {
        byte[] salt = new byte[SALT_LEN];
        byte[] iv   = new byte[IV_LEN];

        try (InputStream in = Files.newInputStream(src)) {
            in.readNBytes(salt, 0, SALT_LEN);
            in.readNBytes(iv, 0, IV_LEN);

            SecretKeySpec key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            String name = src.getFileName().toString();
            // strip .enc suffix if present
            String outName = name.endsWith(".enc") ? name.substring(0, name.length() - 4) : name + ".dec";
            Path out = Paths.get(outputDir, UUID.randomUUID() + "-" + outName);

            try (OutputStream os = Files.newOutputStream(out)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    os.write(cipher.update(buf, 0, len));
                }
                os.write(cipher.doFinal());
            }
            return out;
        }
    }

    private SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITER, KEY_BITS);
        byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }
}
