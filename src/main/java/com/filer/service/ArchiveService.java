package com.filer.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArchiveService {

    @Value("${filer.output-dir}")
    private String outputDir;

    public Path createZip(List<Path> files, List<String> names) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(out.toFile())) {
            for (int i = 0; i < files.size(); i++) {
                Path file = files.get(i);
                String entryName = (names != null && i < names.size()) ? names.get(i)
                        : file.getFileName().toString();
                ZipArchiveEntry entry = new ZipArchiveEntry(file.toFile(), entryName);
                zos.putArchiveEntry(entry);
                Files.copy(file, zos);
                zos.closeArchiveEntry();
            }
        }
        return out;
    }

    /** Create a password-protected ZIP using Zip4j (AES-256 encryption). */
    public Path createEncryptedZip(List<Path> sources, String password) throws Exception {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".zip");
        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(out.toFile(), password.toCharArray());
        net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
        params.setEncryptFiles(true);
        params.setEncryptionMethod(net.lingala.zip4j.model.enums.EncryptionMethod.AES);
        params.setAesKeyStrength(net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256);
        for (Path src : sources) zipFile.addFile(src.toFile(), params);
        return out;
    }

    public List<Path> extractZip(Path zipFile) throws IOException {
        String extractDir = outputDir + "/" + UUID.randomUUID();
        Files.createDirectories(Paths.get(extractDir));
        List<Path> extracted = new ArrayList<>();
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path out = Paths.get(extractDir, entry.getName());
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                    extracted.add(out);
                }
            }
        }
        return extracted;
    }
}
