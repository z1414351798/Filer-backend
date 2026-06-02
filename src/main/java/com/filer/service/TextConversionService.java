package com.filer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TextConversionService {

    @Value("${filer.output-dir}")
    private String outputDir;

    private final ObjectMapper objectMapper;

    // ---- CSV ↔ JSON -------------------------------------------------------

    public Path csvToJson(Path csvPath) throws IOException {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        List<Map<String, String>> rows = csvMapper
                .readerFor(new TypeReference<Map<String, String>>() {})
                .with(schema)
                .<Map<String, String>>readValues(csvPath.toFile())
                .readAll();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), rows);
        return out;
    }

    public Path jsonToCsv(Path jsonPath) throws IOException {
        JsonNode node = objectMapper.readTree(jsonPath.toFile());
        ArrayNode array = node.isArray() ? (ArrayNode) node
                : objectMapper.createArrayNode().add(node);

        // Build schema from first object's keys
        CsvSchema.Builder sb = CsvSchema.builder().setUseHeader(true);
        if (array.size() > 0) {
            array.get(0).fieldNames().forEachRemaining(sb::addColumn);
        }
        CsvSchema schema = sb.build();
        CsvMapper csvMapper = new CsvMapper();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".csv");
        csvMapper.writerFor(JsonNode.class).with(schema).writeValue(out.toFile(), array);
        return out;
    }

    // ---- XML ↔ JSON -------------------------------------------------------

    public Path xmlToJson(Path xmlPath) throws IOException {
        XmlMapper xmlMapper = new XmlMapper();
        JsonNode node = xmlMapper.readTree(xmlPath.toFile());
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), node);
        return out;
    }

    public Path jsonToXml(Path jsonPath) throws IOException {
        JsonNode node = objectMapper.readTree(jsonPath.toFile());
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".xml");
        // Wrap in a root element called "root"
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("root", node);
        Files.writeString(out, xmlMapper.writeValueAsString(wrapper));
        return out;
    }

    // ---- Markdown ---------------------------------------------------------

    public Path markdownToHtml(String markdown) throws IOException {
        String html = renderMarkdown(markdown);
        String full = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">\n"
                + "<style>body{font-family:sans-serif;max-width:860px;margin:2rem auto;"
                + "color:#222;line-height:1.6}pre{background:#f4f4f4;padding:1em;"
                + "border-radius:4px}code{background:#f4f4f4;padding:2px 4px;"
                + "border-radius:3px}</style></head><body>\n" + html + "\n</body></html>";
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".html");
        Files.writeString(out, full);
        return out;
    }

    public Path markdownToPdf(String markdown) throws IOException {
        String html = renderMarkdown(markdown);
        // Strip HTML tags for plain-text PDF rendering
        String plain = html.replaceAll("<[^>]+>", "").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">");
        return textToPdf(plain);
    }

    private String renderMarkdown(String markdown) {
        List<org.commonmark.Extension> exts = List.of(TablesExtension.create());
        Parser parser = Parser.builder().extensions(exts).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(exts).build();
        Node doc = parser.parse(markdown);
        return renderer.render(doc);
    }

    // ---- Text / HTML → PDF -----------------------------------------------

    public Path textToPdf(String text) throws IOException {
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            float margin = 50;
            float fontSize = 12;
            float leading = fontSize * 1.5f;
            PDType1Font font = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA);

            String[] paragraphs = text.split("\n");
            PDPageContentStream cs = null;
            float y = 0;

            for (String paragraph : paragraphs) {
                String[] words = paragraph.isEmpty()
                        ? new String[]{""} : splitIntoLines(paragraph, font, fontSize,
                        PDRectangle.A4.getWidth() - 2 * margin);
                for (String line : words) {
                    if (cs == null || y < margin + leading) {
                        if (cs != null) cs.endText();
                        if (cs != null) cs.close();
                        PDPage page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        cs.setFont(font, fontSize);
                        cs.setLeading(leading);
                        cs.beginText();
                        y = PDRectangle.A4.getHeight() - margin;
                        cs.newLineAtOffset(margin, y);
                    }
                    cs.showText(line);
                    cs.newLine();
                    y -= leading;
                }
            }
            if (cs != null) { cs.endText(); cs.close(); }
            doc.save(out.toFile());
        }
        return out;
    }

    private String[] splitIntoLines(String text, PDType1Font font,
            float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            float w = font.getStringWidth(test) / 1000 * fontSize;
            if (w > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.isEmpty() ? new String[]{""} : lines.toArray(new String[0]);
    }

    public Path htmlToPdf(Path htmlPath) throws IOException {
        String html = Files.readString(htmlPath);
        String plain = html.replaceAll("<[^>]+>", "").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        return textToPdf(plain);
    }
}
