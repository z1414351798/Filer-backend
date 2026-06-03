package com.filer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DataFormatService {

    @Value("${filer.output-dir}")
    private String outputDir;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public Path jsonToYaml(Path src) throws IOException {
        JsonNode node = jsonMapper.readTree(src.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".yaml");
        Files.writeString(out, yamlMapper.writeValueAsString(node));
        return out;
    }

    public Path yamlToJson(Path src) throws IOException {
        JsonNode node = yamlMapper.readTree(src.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        return out;
    }

    public Path formatJson(Path src) throws IOException {
        JsonNode node = jsonMapper.readTree(src.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        return out;
    }

    public Path formatXml(Path src) throws Exception {
        String raw = Files.readString(src);
        javax.xml.transform.Transformer tr =
                javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        tr.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter sw = new StringWriter();
        tr.transform(new javax.xml.transform.stream.StreamSource(new StringReader(raw)),
                new javax.xml.transform.stream.StreamResult(sw));
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".xml");
        Files.writeString(out, sw.toString());
        return out;
    }

    public Path base64Encode(Path src) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(src));
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, encoded);
        return out;
    }

    public Path base64Decode(Path src) throws IOException {
        byte[] decoded = Base64.getDecoder().decode(Files.readString(src).trim());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".bin");
        Files.write(out, decoded);
        return out;
    }

    public Path textDiff(Path src1, Path src2) throws IOException {
        List<String> lines1 = Files.readAllLines(src1);
        List<String> lines2 = Files.readAllLines(src2);
        Patch<String> patch = DiffUtils.diff(lines1, lines2);
        StringBuilder sb = new StringBuilder();
        for (var delta : patch.getDeltas()) {
            sb.append(delta.getType()).append(" @ ").append(delta.getSource().getPosition()).append("\n");
            for (String l : delta.getSource().getLines()) sb.append("- ").append(l).append("\n");
            for (String l : delta.getTarget().getLines()) sb.append("+ ").append(l).append("\n");
            sb.append("\n");
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".diff");
        Files.writeString(out, sb.toString());
        return out;
    }

    public Path createTar(List<Path> sources) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".tar.gz");
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(
                new GzipCompressorOutputStream(new FileOutputStream(out.toFile())))) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            for (Path p : sources) {
                if (!Files.exists(p)) continue;
                TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), p.getFileName().toString());
                tos.putArchiveEntry(entry);
                Files.copy(p, tos);
                tos.closeArchiveEntry();
            }
        }
        return out;
    }

    public Path extractTar(Path src) throws IOException {
        String dirName = UUID.randomUUID().toString();
        File outDir = Paths.get(outputDir, dirName).toFile();
        outDir.mkdirs();
        boolean isGzip = src.toString().endsWith(".gz");
        InputStream raw = new FileInputStream(src.toFile());
        try (TarArchiveInputStream tis = new TarArchiveInputStream(
                isGzip ? new GZIPInputStream(raw) : raw)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                File dest = new File(outDir, entry.getName());
                if (entry.isDirectory()) { dest.mkdirs(); }
                else { dest.getParentFile().mkdirs(); Files.copy(tis, dest.toPath()); }
            }
        }
        Path zipOut = Paths.get(outputDir, UUID.randomUUID() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOut.toFile()))) {
            zipDir(outDir.toPath(), outDir.toPath(), zos);
        }
        return zipOut;
    }

    private void zipDir(Path root, Path dir, ZipOutputStream zos) throws IOException {
        try (var stream = Files.list(dir)) {
            for (Path f : stream.toList()) {
                if (Files.isDirectory(f)) { zipDir(root, f, zos); continue; }
                zos.putNextEntry(new ZipEntry(root.relativize(f).toString()));
                Files.copy(f, zos); zos.closeEntry();
            }
        }
    }
}
