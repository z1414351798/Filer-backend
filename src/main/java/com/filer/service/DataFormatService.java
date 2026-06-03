package com.filer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

@Service
@RequiredArgsConstructor
public class DataFormatService {

    @Value("${filer.upload-dir:uploads}")
    private String uploadDir;

    @Value("${filer.output-dir:outputs}")
    private String outputDir;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public String jsonToYaml(String fileId) throws IOException {
        String json = Files.readString(Path.of(uploadDir, fileId));
        JsonNode node = jsonMapper.readTree(json);
        String yaml = yamlMapper.writeValueAsString(node);
        String outId = UUID.randomUUID() + ".yaml";
        Files.writeString(Path.of(outputDir, outId), yaml);
        return outId;
    }

    public String yamlToJson(String fileId) throws IOException {
        String yaml = Files.readString(Path.of(uploadDir, fileId));
        JsonNode node = yamlMapper.readTree(yaml);
        String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        String outId = UUID.randomUUID() + ".json";
        Files.writeString(Path.of(outputDir, outId), json);
        return outId;
    }

    public String formatJson(String fileId) throws IOException {
        String raw = Files.readString(Path.of(uploadDir, fileId));
        JsonNode node = jsonMapper.readTree(raw);
        String pretty = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        String outId = UUID.randomUUID() + ".json";
        Files.writeString(Path.of(outputDir, outId), pretty);
        return outId;
    }

    public String formatXml(String fileId) throws Exception {
        String raw = Files.readString(Path.of(uploadDir, fileId));
        javax.xml.transform.Transformer transformer =
                javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        javax.xml.transform.Source xmlInput =
                new javax.xml.transform.stream.StreamSource(new StringReader(raw));
        StringWriter sw = new StringWriter();
        transformer.transform(xmlInput, new javax.xml.transform.stream.StreamResult(sw));
        String outId = UUID.randomUUID() + ".xml";
        Files.writeString(Path.of(outputDir, outId), sw.toString());
        return outId;
    }

    public String base64Encode(String fileId) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(uploadDir, fileId));
        String encoded = Base64.getEncoder().encodeToString(bytes);
        String outId = UUID.randomUUID() + ".txt";
        Files.writeString(Path.of(outputDir, outId), encoded);
        return outId;
    }

    public String base64Decode(String fileId) throws IOException {
        String encoded = Files.readString(Path.of(uploadDir, fileId)).trim();
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String outId = UUID.randomUUID() + ".bin";
        Files.write(Path.of(outputDir, outId), decoded);
        return outId;
    }

    public String textDiff(String fileId1, String fileId2) throws IOException {
        List<String> lines1 = Files.readAllLines(Path.of(uploadDir, fileId1));
        List<String> lines2 = Files.readAllLines(Path.of(uploadDir, fileId2));
        Patch<String> patch = DiffUtils.diff(lines1, lines2);
        StringBuilder sb = new StringBuilder();
        for (var delta : patch.getDeltas()) {
            sb.append(delta.getType()).append(" @ ").append(delta.getSource().getPosition()).append("\n");
            for (String line : delta.getSource().getLines()) sb.append("- ").append(line).append("\n");
            for (String line : delta.getTarget().getLines()) sb.append("+ ").append(line).append("\n");
            sb.append("\n");
        }
        String outId = UUID.randomUUID() + ".diff";
        Files.writeString(Path.of(outputDir, outId), sb.toString());
        return outId;
    }

    public String createTar(List<String> fileIds) throws IOException {
        String outId = UUID.randomUUID() + ".tar.gz";
        File outFile = new File(outputDir, outId);
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(
                new GzipCompressorOutputStream(new FileOutputStream(outFile)))) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            for (String id : fileIds) {
                File f = new File(uploadDir, id);
                if (!f.exists()) continue;
                TarArchiveEntry entry = new TarArchiveEntry(f, f.getName());
                tos.putArchiveEntry(entry);
                Files.copy(f.toPath(), tos);
                tos.closeArchiveEntry();
            }
        }
        return outId;
    }

    public String extractTar(String fileId) throws IOException {
        File src = new File(uploadDir, fileId);
        String dirName = UUID.randomUUID().toString();
        File outDir = new File(outputDir, dirName);
        outDir.mkdirs();
        try (TarArchiveInputStream tis = new TarArchiveInputStream(
                new java.util.zip.GZIPInputStream(new FileInputStream(src)))) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                File dest = new File(outDir, entry.getName());
                if (entry.isDirectory()) {
                    dest.mkdirs();
                } else {
                    dest.getParentFile().mkdirs();
                    Files.copy(tis, dest.toPath());
                }
            }
        }
        // Re-zip extracted dir into a zip for download
        String outId = UUID.randomUUID() + ".zip";
        File outZip = new File(outputDir, outId);
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new FileOutputStream(outZip))) {
            zipDir(outDir, outDir, zos);
        }
        return outId;
    }

    private void zipDir(File root, File dir, java.util.zip.ZipOutputStream zos) throws IOException {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) { zipDir(root, f, zos); continue; }
            String rel = root.toPath().relativize(f.toPath()).toString();
            zos.putNextEntry(new java.util.zip.ZipEntry(rel));
            Files.copy(f.toPath(), zos);
            zos.closeEntry();
        }
    }
}
