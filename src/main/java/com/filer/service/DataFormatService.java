package com.filer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
    private final XmlMapper xmlMapper = new XmlMapper();

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

    /** Compute SHA-256 (default), MD5, or SHA-512 hash of a file. */
    public Path hashFile(Path src, String algorithm) throws Exception {
        String algo = algorithm == null || algorithm.isBlank() ? "SHA-256" : algorithm.toUpperCase()
                .replace("SHA256","SHA-256").replace("SHA512","SHA-512");
        MessageDigest md = MessageDigest.getInstance(algo);
        byte[] data = Files.readAllBytes(src);
        String hex = HexFormat.of().formatHex(md.digest(data));
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, algo + "  " + src.getFileName() + "\n" + hex + "\n");
        return out;
    }

    /** URL-encode every line of a text file. */
    public Path urlEncode(Path src) throws IOException {
        String content = Files.readString(src, StandardCharsets.UTF_8);
        String encoded = URLEncoder.encode(content, StandardCharsets.UTF_8)
                .replace("+", "%20");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, encoded);
        return out;
    }

    /** URL-decode a text file. */
    public Path urlDecode(Path src) throws IOException {
        String content = Files.readString(src, StandardCharsets.UTF_8);
        String decoded = URLDecoder.decode(content, StandardCharsets.UTF_8);
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, decoded);
        return out;
    }

    /**
     * Decode a JWT token file (no signature verification — useful for inspection).
     * Input: text file containing a JWT string.
     */
    public Path jwtDecode(Path src) throws Exception {
        String token = Files.readString(src).strip();
        String[] parts = token.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Not a valid JWT (need at least header.payload)");
        java.util.Base64.Decoder dec = java.util.Base64.getUrlDecoder();
        JsonNode header  = jsonMapper.readTree(dec.decode(parts[0]));
        JsonNode payload = jsonMapper.readTree(dec.decode(parts[1]));
        ObjectNode result = jsonMapper.createObjectNode();
        result.set("header",  header);
        result.set("payload", payload);
        result.put("signature", parts.length > 2 ? parts[2] : "");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        return out;
    }

    /** Convert first sheet of an XLSX/XLS file to a JSON array of objects. */
    public Path excelToJson(Path src) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        try (Workbook wb = WorkbookFactory.create(src.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            ArrayNode array = jsonMapper.createArrayNode();
            // First row = headers
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) { Files.writeString(out, "[]"); return out; }
            List<String> headers = new java.util.ArrayList<>();
            for (Cell c : headerRow) headers.add(fmt.formatCellValue(c));

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                ObjectNode obj = jsonMapper.createObjectNode();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    obj.put(headers.get(c), cell == null ? "" : fmt.formatCellValue(cell));
                }
                array.add(obj);
            }
            Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(array));
        }
        return out;
    }

    /** Render a CSV file as an HTML table. */
    public Path csvToHtml(Path src) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
            .append("<style>table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:8px;text-align:left}")
            .append("th{background:#f0f0f0}tr:nth-child(even){background:#fafafa}</style></head><body><table>\n");
        try (java.io.BufferedReader reader = Files.newBufferedReader(src)) {
            boolean header = true;
            String line;
            while ((line = reader.readLine()) != null) {
                html.append("<tr>");
                for (String cell : parseCsvLine(line)) {
                    String tag = header ? "th" : "td";
                    html.append("<").append(tag).append(">")
                        .append(escHtml(cell)).append("</").append(tag).append(">");
                }
                html.append("</tr>\n");
                header = false;
            }
        }
        html.append("</table></body></html>");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".html");
        Files.writeString(out, html.toString());
        return out;
    }

    /** Render a JSON array-of-objects as an HTML table. */
    public Path jsonToHtml(Path src) throws IOException {
        JsonNode root = jsonMapper.readTree(src.toFile());
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
            .append("<style>table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:8px;text-align:left}")
            .append("th{background:#f0f0f0}tr:nth-child(even){background:#fafafa}</style></head><body><table>\n");
        if (root.isArray() && root.size() > 0) {
            List<String> keys = new java.util.ArrayList<>();
            root.get(0).fieldNames().forEachRemaining(keys::add);
            html.append("<tr>");
            for (String k : keys) html.append("<th>").append(escHtml(k)).append("</th>");
            html.append("</tr>\n");
            for (JsonNode row : root) {
                html.append("<tr>");
                for (String k : keys) {
                    JsonNode v = row.get(k);
                    html.append("<td>").append(escHtml(v == null ? "" : v.asText())).append("</td>");
                }
                html.append("</tr>\n");
            }
        }
        html.append("</table></body></html>");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".html");
        Files.writeString(out, html.toString());
        return out;
    }

    /** Convert text case. caseType: upper | lower | title | camel | snake | kebab */
    public Path convertTextCase(Path src, String caseType) throws IOException {
        String text = Files.readString(src, java.nio.charset.StandardCharsets.UTF_8);
        String result = switch (caseType == null ? "upper" : caseType.toLowerCase()) {
            case "lower"  -> text.toLowerCase();
            case "title"  -> toTitleCase(text);
            case "camel"  -> toCamelCase(text);
            case "snake"  -> toSnakeCase(text);
            case "kebab"  -> toKebabCase(text);
            default       -> text.toUpperCase();
        };
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, result);
        return out;
    }

    private String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            sb.append(cap && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            cap = !Character.isLetterOrDigit(c);
        }
        return sb.toString();
    }
    private String toCamelCase(String s) {
        String[] words = s.trim().split("[\\s_\\-]+");
        StringBuilder sb = new StringBuilder(words[0].toLowerCase());
        for (int i = 1; i < words.length; i++)
            if (!words[i].isEmpty())
                sb.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1).toLowerCase());
        return sb.toString();
    }
    private String toSnakeCase(String s) {
        return s.trim().replaceAll("[\\s\\-]+","_").replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase();
    }
    private String toKebabCase(String s) {
        return s.trim().replaceAll("[\\s_]+","-").replaceAll("([a-z])([A-Z])","$1-$2").toLowerCase();
    }

    /** Convert SRT subtitle format to WebVTT. */
    public Path srtToVtt(Path src) throws IOException {
        String srt = Files.readString(src, java.nio.charset.StandardCharsets.UTF_8);
        String vtt = "WEBVTT\n\n" + srt
                .replaceAll("(?m)^(\\d+)$\n", "")           // remove cue numbers
                .replace(",", ".")                            // SRT uses comma, VTT uses dot in timestamps
                .replaceAll("(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) --> (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})",
                            "$1 --> $2\n");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".vtt");
        Files.writeString(out, vtt);
        return out;
    }

    /** Convert WebVTT subtitle format to SRT. */
    public Path vttToSrt(Path src) throws IOException {
        String vtt = Files.readString(src, java.nio.charset.StandardCharsets.UTF_8);
        // Remove WEBVTT header and NOTE blocks
        String body = vtt.replaceFirst("WEBVTT[^\n]*\n", "")
                         .replaceAll("NOTE[^\n]*\n[^\n]*\n", "");
        // Replace dot-timestamps with comma
        body = body.replaceAll("(\\d{2}:\\d{2}:\\d{2})\\.(\\d{3})", "$1,$2");
        // Re-number cues
        StringBuilder sb = new StringBuilder();
        int cue = 1;
        for (String block : body.split("\n\n")) {
            block = block.trim();
            if (block.isEmpty()) continue;
            // Skip blocks that are just metadata (no --> )
            if (!block.contains("-->")) continue;
            sb.append(cue++).append('\n').append(block).append("\n\n");
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".srt");
        Files.writeString(out, sb.toString());
        return out;
    }

    private String escHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private String[] parseCsvLine(String line) {
        // Simple CSV split respecting quoted fields
        List<String> fields = new java.util.ArrayList<>();
        boolean inQuote = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQuote = !inQuote; }
            else if (c == ',' && !inQuote) { fields.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    /** Merge multiple CSV files (must all have same headers). */
    public Path mergeCsv(List<Path> sources) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".csv");
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(out)) {
            boolean headerWritten = false;
            for (Path src : sources) {
                java.io.BufferedReader reader = Files.newBufferedReader(src);
                String header = reader.readLine();
                if (!headerWritten && header != null) {
                    writer.write(header); writer.newLine();
                    headerWritten = true;
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line); writer.newLine();
                }
                reader.close();
            }
        }
        return out;
    }

    /** Compute per-column statistics (count, min, max, mean, nulls) from a CSV file. */
    public Path csvStats(Path src) throws IOException {
        List<String> lines = Files.readAllLines(src);
        if (lines.isEmpty()) {
            Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
            Files.writeString(out, "{}"); return out;
        }
        String[] headers = parseCsvLine(lines.get(0));
        Map<String, Object>[] stats = new java.util.LinkedHashMap[headers.length];
        double[] sum = new double[headers.length];
        double[] min = new double[headers.length];
        double[] max = new double[headers.length];
        int[] count  = new int[headers.length];
        int[] nulls  = new int[headers.length];
        boolean[] numeric = new boolean[headers.length];
        java.util.Arrays.fill(min, Double.MAX_VALUE);
        java.util.Arrays.fill(max, -Double.MAX_VALUE);
        java.util.Arrays.fill(numeric, true);
        for (int r = 1; r < lines.size(); r++) {
            String[] cells = parseCsvLine(lines.get(r));
            for (int c = 0; c < headers.length; c++) {
                String v = c < cells.length ? cells[c].trim() : "";
                if (v.isEmpty()) { nulls[c]++; continue; }
                count[c]++;
                try {
                    double d = Double.parseDouble(v);
                    sum[c] += d; min[c] = Math.min(min[c], d); max[c] = Math.max(max[c], d);
                } catch (NumberFormatException e) { numeric[c] = false; }
            }
        }
        ObjectNode root = jsonMapper.createObjectNode();
        for (int c = 0; c < headers.length; c++) {
            ObjectNode col = jsonMapper.createObjectNode();
            col.put("count",  count[c]);
            col.put("nulls",  nulls[c]);
            if (numeric[c] && count[c] > 0) {
                col.put("min",  min[c]);
                col.put("max",  max[c]);
                col.put("mean", sum[c] / count[c]);
                col.put("sum",  sum[c]);
            }
            root.set(headers[c], col);
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        Files.writeString(out, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return out;
    }

    /** Generate N UUIDs, one per line. */
    public Path generateUuids(int count) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(count, 10000); i++) sb.append(java.util.UUID.randomUUID()).append('\n');
        Path out = Paths.get(outputDir, java.util.UUID.randomUUID() + ".txt");
        Files.writeString(out, sb.toString());
        return out;
    }

    /** Generate Lorem Ipsum paragraphs as a text file. */
    public Path generateLoremIpsum(int paragraphs) throws IOException {
        String[] sentences = {
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.",
            "Duis aute irure dolor in reprehenderit in voluptate velit esse.",
            "Excepteur sint occaecat cupidatat non proident, sunt in culpa.",
            "Pellentesque habitant morbi tristique senectus et netus et malesuada.",
            "Praesent commodo cursus magna, vel scelerisque nisl consectetur.",
            "Fusce dapibus, tellus ac cursus commodo, tortor mauris condimentum.",
            "Nullam quis risus eget urna mollis ornare vel eu leo.",
            "Cras mattis consectetur purus sit amet fermentum."
        };
        Random rng = new Random(42);
        StringBuilder sb = new StringBuilder();
        int n = Math.min(Math.max(paragraphs, 1), 100);
        for (int p = 0; p < n; p++) {
            int sentCount = 4 + rng.nextInt(4);
            for (int s = 0; s < sentCount; s++) sb.append(sentences[rng.nextInt(sentences.length)]).append(' ');
            sb.append("\n\n");
        }
        Path out = Paths.get(outputDir, java.util.UUID.randomUUID() + ".txt");
        Files.writeString(out, sb.toString().strip());
        return out;
    }

    /**
     * Generate a random CSV file with given column names (comma-separated) and N data rows.
     * Column name hints: id=sequential int, name=random name, email=email, date=date, price/amount/score=number
     */
    public Path generateRandomCsv(String columns, int rows) throws IOException {
        String[] cols = columns == null || columns.isBlank() ? new String[]{"id","name","email","score"}
                : columns.split("[,;]+");
        String[] firstNames = {"Alice","Bob","Carol","Dave","Eve","Frank","Grace","Hank","Iris","Jack"};
        String[] lastNames  = {"Smith","Jones","Brown","Davis","Wilson","Taylor","Clark","Lee","Hall","Young"};
        String[] domains    = {"gmail.com","yahoo.com","outlook.com","example.com","mail.com"};
        Random rng = new Random();
        StringBuilder sb = new StringBuilder();
        // Header
        for (int c = 0; c < cols.length; c++) { if (c>0) sb.append(','); sb.append(cols[c].trim()); }
        sb.append('\n');
        for (int r = 0; r < Math.min(rows, 10000); r++) {
            for (int c = 0; c < cols.length; c++) {
                if (c > 0) sb.append(',');
                String col = cols[c].trim().toLowerCase();
                if (col.contains("id"))    sb.append(r + 1);
                else if (col.contains("name"))  { String n = firstNames[rng.nextInt(10)]+" "+lastNames[rng.nextInt(10)]; sb.append(n); }
                else if (col.contains("email")) { String n = firstNames[rng.nextInt(10)].toLowerCase(); sb.append(n).append(rng.nextInt(99)).append("@").append(domains[rng.nextInt(5)]); }
                else if (col.contains("date"))  sb.append(2020+rng.nextInt(5)).append("-").append(String.format("%02d",1+rng.nextInt(12))).append("-").append(String.format("%02d",1+rng.nextInt(28)));
                else if (col.matches(".*(price|amount|score|salary|revenue).*")) sb.append(String.format("%.2f", 10 + rng.nextDouble() * 990));
                else if (col.contains("age"))   sb.append(18 + rng.nextInt(50));
                else if (col.contains("phone")) sb.append("+1").append(200+rng.nextInt(800)).append(String.format("%07d",rng.nextInt(10_000_000)));
                else sb.append("value").append(r+1);
            }
            sb.append('\n');
        }
        Path out = Paths.get(outputDir, java.util.UUID.randomUUID() + ".csv");
        Files.writeString(out, sb.toString());
        return out;
    }

    /** Shift SRT or VTT subtitle timestamps by shiftMs milliseconds (positive=later). */
    public Path shiftSubtitle(Path src, long shiftMs) throws IOException {
        String content = Files.readString(src);
        String name = src.getFileName().toString().toLowerCase();
        boolean isSrt = name.endsWith(".srt");
        String shifted;
        if (isSrt) {
            // SRT time format: HH:MM:SS,mmm --> HH:MM:SS,mmm
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})").matcher(content);
            shifted = m.replaceAll(mr -> shiftTime(mr.group(), shiftMs, ','));
        } else {
            // VTT time format: HH:MM:SS.mmm
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})").matcher(content);
            shifted = m.replaceAll(mr -> shiftTime(mr.group(), shiftMs, '.'));
        }
        String ext = isSrt ? ".srt" : ".vtt";
        Path out = Paths.get(outputDir, UUID.randomUUID() + ext);
        Files.writeString(out, shifted);
        return out;
    }

    private String shiftTime(String ts, long shiftMs, char sep) {
        try {
            int sepIdx = ts.lastIndexOf(sep);
            String[] parts = ts.substring(0, sepIdx).split(":");
            long ms = Long.parseLong(ts.substring(sepIdx + 1));
            long h = Long.parseLong(parts[0]), m = Long.parseLong(parts[1]), sec = Long.parseLong(parts[2]);
            long total = (h * 3600 + m * 60 + sec) * 1000 + ms + shiftMs;
            if (total < 0) total = 0;
            long outH = total / 3_600_000; total %= 3_600_000;
            long outM = total / 60_000;    total %= 60_000;
            long outS = total / 1_000;     long outMs = total % 1_000;
            return String.format("%02d:%02d:%02d%c%03d", outH, outM, outS, sep, outMs);
        } catch (Exception e) { return ts; }
    }

    /** Test a regex pattern against input text. Returns match details as text. */
    public Path testRegex(String pattern, String input, String flags) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Pattern: ").append(pattern).append("\n");
        sb.append("Flags:   ").append(flags == null ? "none" : flags).append("\n\n");
        try {
            int jFlags = 0;
            if (flags != null) {
                if (flags.contains("i")) jFlags |= java.util.regex.Pattern.CASE_INSENSITIVE;
                if (flags.contains("m")) jFlags |= java.util.regex.Pattern.MULTILINE;
                if (flags.contains("s")) jFlags |= java.util.regex.Pattern.DOTALL;
            }
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile(pattern, jFlags);
            java.util.regex.Matcher mat = pat.matcher(input == null ? "" : input);
            int count = 0;
            while (mat.find()) {
                count++;
                sb.append("Match ").append(count).append(": [").append(mat.start()).append(",").append(mat.end()).append("] \"").append(mat.group()).append("\"\n");
                for (int g = 1; g <= mat.groupCount(); g++)
                    sb.append("  Group ").append(g).append(": \"").append(mat.group(g)).append("\"\n");
            }
            if (count == 0) sb.append("No matches found.\n");
            else sb.append("\nTotal: ").append(count).append(" match(es)\n");
        } catch (java.util.regex.PatternSyntaxException e) {
            sb.append("Invalid pattern: ").append(e.getMessage()).append("\n");
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, sb.toString());
        return out;
    }

    /** Generate passwords using SecureRandom. */
    public Path generatePassword(int length, boolean upper, boolean numbers, boolean symbols) throws IOException {
        String lower  = "abcdefghijklmnopqrstuvwxyz";
        String uppers = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits = "0123456789";
        String syms   = "!@#$%^&*()-_=+[]{}|;:,.<>?";
        StringBuilder chars = new StringBuilder(lower);
        if (upper)   chars.append(uppers);
        if (numbers) chars.append(digits);
        if (symbols) chars.append(syms);
        String pool = chars.toString();
        java.security.SecureRandom rng = new java.security.SecureRandom();
        int len = Math.min(Math.max(length, 8), 256);
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < 10; p++) {
            StringBuilder pwd = new StringBuilder();
            for (int i = 0; i < len; i++) pwd.append(pool.charAt(rng.nextInt(pool.length())));
            sb.append(pwd).append("\n");
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, sb.toString());
        return out;
    }

    /** Generate passphrase using a built-in word list. */
    public Path generatePassphrase(int wordCount) throws IOException {
        String[] words = {
            "apple","brave","cloud","dance","eagle","flame","grace","heart","ivory","joker",
            "kindle","lemon","mango","noble","ocean","peace","queen","river","storm","tower",
            "ultra","vivid","water","xenon","yield","zebra","alpha","bravo","coral","delta",
            "ember","frost","glass","honey","index","jewel","karma","lunar","maple","night",
            "orbit","pixel","quest","rocky","solar","tiger","umbra","valve","wheat","xerox",
            "yacht","zonal","amber","burst","cedar","dwell","feast","grove","hatch","inlet",
            "jelly","kneel","laser","mount","nerve","olive","pilot","quota","realm","swift",
            "trout","union","viola","waltz","xylem","youth","zesty","agent","blade","chess"
        };
        java.security.SecureRandom rng = new java.security.SecureRandom();
        int n = Math.min(Math.max(wordCount, 2), 12);
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < 5; p++) {
            List<String> phrase = new java.util.ArrayList<>();
            for (int w = 0; w < n; w++) phrase.add(words[rng.nextInt(words.length)]);
            sb.append(String.join("-", phrase)).append("\n");
        }
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, sb.toString());
        return out;
    }

    /** Convert color between HEX, RGB, and HSL formats. */
    public Path convertColor(String input, String from, String to) throws IOException {
        float[] rgb = parseColorInput(input, from == null ? "hex" : from.toLowerCase());
        String hex = String.format("#%02X%02X%02X", (int)rgb[0], (int)rgb[1], (int)rgb[2]);
        float[] hsl = rgbToHsl(rgb[0]/255f, rgb[1]/255f, rgb[2]/255f);
        StringBuilder sb = new StringBuilder();
        sb.append("HEX: ").append(hex).append("\n");
        sb.append("RGB: rgb(").append((int)rgb[0]).append(", ").append((int)rgb[1]).append(", ").append((int)rgb[2]).append(")\n");
        sb.append("HSL: hsl(").append(String.format("%.1f", hsl[0])).append("°, ")
          .append(String.format("%.1f", hsl[1]*100)).append("%, ")
          .append(String.format("%.1f", hsl[2]*100)).append("%)\n");
        String target = to == null ? "all" : to.toLowerCase();
        sb.append("\nRequested (").append(target).append("): ");
        if (target.equals("hex")) sb.append(hex);
        else if (target.equals("rgb")) sb.append("rgb(").append((int)rgb[0]).append(",").append((int)rgb[1]).append(",").append((int)rgb[2]).append(")");
        else if (target.equals("hsl")) sb.append("hsl(").append(String.format("%.1f",hsl[0])).append(",").append(String.format("%.1f",hsl[1]*100)).append("%,").append(String.format("%.1f",hsl[2]*100)).append("%)");
        else sb.append("(see above)");
        sb.append("\n");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".txt");
        Files.writeString(out, sb.toString());
        return out;
    }

    private float[] parseColorInput(String input, String format) {
        if (input == null) return new float[]{128,128,128};
        input = input.trim();
        if (format.equals("hex")) {
            String h = input.replace("#","");
            if (h.length() == 3) h = "" + h.charAt(0)+h.charAt(0)+h.charAt(1)+h.charAt(1)+h.charAt(2)+h.charAt(2);
            int v = Integer.parseInt(h, 16);
            return new float[]{(v>>16)&0xFF, (v>>8)&0xFF, v&0xFF};
        } else if (format.equals("rgb")) {
            String[] p = input.replaceAll("[^\\d,]","").split(",");
            return new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])};
        } else { // hsl
            String[] p = input.replaceAll("[^\\d.,]","").split(",");
            float h2 = Float.parseFloat(p[0]), s = Float.parseFloat(p[1])/100, l = Float.parseFloat(p[2])/100;
            return hslToRgb(h2, s, l);
        }
    }

    private float[] hslToRgb(float h, float s, float l) {
        float c = (1 - Math.abs(2*l - 1)) * s;
        float x = c * (1 - Math.abs((h/60f)%2 - 1));
        float m = l - c/2;
        float r,g,b;
        if (h<60){r=c;g=x;b=0;} else if(h<120){r=x;g=c;b=0;} else if(h<180){r=0;g=c;b=x;}
        else if(h<240){r=0;g=x;b=c;} else if(h<300){r=x;g=0;b=c;} else{r=c;g=0;b=x;}
        return new float[]{(r+m)*255, (g+m)*255, (b+m)*255};
    }

    private float[] rgbToHsl(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max+min)/2, s = 0, h = 0;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d/(2-max-min) : d/(max+min);
            if (max == r) h = (g-b)/d + (g < b ? 6 : 0);
            else if (max == g) h = (b-r)/d + 2;
            else h = (r-g)/d + 4;
            h *= 60;
        }
        return new float[]{h, s, l};
    }

    /** Minify HTML by removing unnecessary whitespace between tags. */
    public Path minifyHtml(Path src) throws IOException {
        String html = Files.readString(src);
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        String minified = doc.outerHtml().replaceAll(">\\s+<", "><").trim();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".html");
        Files.writeString(out, minified);
        return out;
    }

    /** Minify JSON by removing all whitespace. */
    public Path minifyJson(Path src) throws IOException {
        JsonNode node = jsonMapper.readTree(src.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        Files.writeString(out, jsonMapper.writeValueAsString(node));
        return out;
    }

    /** Convert XML to YAML. */
    public Path xmlToYaml(Path src) throws IOException {
        JsonNode node = xmlMapper.readTree(src.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".yaml");
        Files.writeString(out, yamlMapper.writeValueAsString(node));
        return out;
    }

    /** Convert YAML to XML. */
    public Path yamlToXml(Path src) throws IOException {
        JsonNode node = yamlMapper.readTree(src.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".xml");
        Files.writeString(out, xmlMapper.writeValueAsString(node));
        return out;
    }

    /** Convert CSV to XML. */
    public Path csvToXml(Path src) throws IOException {
        List<String> lines = Files.readAllLines(src);
        if (lines.isEmpty()) { Path out = Paths.get(outputDir, UUID.randomUUID()+".xml"); Files.writeString(out,"<data/>"); return out; }
        String[] headers = parseCsvLine(lines.get(0));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<data>\n");
        for (int r = 1; r < lines.size(); r++) {
            String[] cells = parseCsvLine(lines.get(r));
            xml.append("  <row>\n");
            for (int c = 0; c < headers.length; c++) {
                String v = c < cells.length ? cells[c] : "";
                String tag = headers[c].trim().replaceAll("[^a-zA-Z0-9_]","_");
                xml.append("    <").append(tag).append(">").append(v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")).append("</").append(tag).append(">\n");
            }
            xml.append("  </row>\n");
        }
        xml.append("</data>");
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".xml");
        Files.writeString(out, xml.toString());
        return out;
    }
}
