package com.filer.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class HtmlService {

    @Value("${filer.output-dir}")
    private String outputDir;

    /** Sanitize HTML — remove scripts, unsafe attributes, XSS vectors. */
    public Path sanitize(Path src, String level) throws IOException {
        String html = Files.readString(src);
        Safelist safelist = switch (level == null ? "basic" : level.toLowerCase()) {
            case "relaxed"   -> Safelist.relaxed();
            case "none"      -> Safelist.none();
            case "basic_w_images" -> Safelist.basicWithImages();
            default          -> Safelist.basic();
        };
        String clean = Jsoup.clean(html, safelist);
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".html");
        Files.writeString(out, clean);
        return out;
    }

    /** Convert HTML to Markdown (headings, bold, italic, links, lists, code blocks). */
    public Path htmlToMarkdown(Path src) throws IOException {
        String html = Files.readString(src);
        Document doc = Jsoup.parse(html);
        String md = nodeToMarkdown(doc.body(), 0).strip();
        Path out = Paths.get(outputDir, UUID.randomUUID() + ".md");
        Files.writeString(out, md);
        return out;
    }

    private String nodeToMarkdown(Node node, int listDepth) {
        StringBuilder sb = new StringBuilder();
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode tn) {
                String text = tn.text();
                if (!text.isBlank()) sb.append(text);
            } else if (child instanceof Element el) {
                String tag = el.tagName().toLowerCase();
                String inner = nodeToMarkdown(el, listDepth);
                switch (tag) {
                    case "h1" -> sb.append("\n# ").append(inner.strip()).append("\n\n");
                    case "h2" -> sb.append("\n## ").append(inner.strip()).append("\n\n");
                    case "h3" -> sb.append("\n### ").append(inner.strip()).append("\n\n");
                    case "h4" -> sb.append("\n#### ").append(inner.strip()).append("\n\n");
                    case "h5","h6" -> sb.append("\n##### ").append(inner.strip()).append("\n\n");
                    case "p"  -> sb.append("\n").append(inner.strip()).append("\n\n");
                    case "br" -> sb.append("  \n");
                    case "strong","b" -> sb.append("**").append(inner.strip()).append("**");
                    case "em","i"     -> sb.append("_").append(inner.strip()).append("_");
                    case "code"       -> sb.append("`").append(inner).append("`");
                    case "pre"        -> sb.append("\n```\n").append(inner).append("\n```\n\n");
                    case "a"  -> sb.append("[").append(inner.strip()).append("](").append(el.attr("href")).append(")");
                    case "img"-> sb.append("![").append(el.attr("alt")).append("](").append(el.attr("src")).append(")");
                    case "li" -> sb.append("\n").append("  ".repeat(listDepth)).append("- ").append(inner.strip());
                    case "ul","ol" -> { sb.append(nodeToMarkdown(el, listDepth + 1)); sb.append("\n"); }
                    case "blockquote" -> {
                        for (String line : inner.strip().split("\n"))
                            sb.append("> ").append(line).append("\n");
                        sb.append("\n");
                    }
                    case "hr" -> sb.append("\n---\n\n");
                    case "table" -> sb.append(tableToMarkdown(el)).append("\n");
                    case "thead","tbody","tr","th","td","div","span","section","article",
                         "header","footer","main","nav","aside" -> sb.append(inner);
                    default -> sb.append(inner);
                }
            }
        }
        return sb.toString();
    }

    private String tableToMarkdown(Element table) {
        StringBuilder sb = new StringBuilder("\n");
        boolean headerDone = false;
        for (Element row : table.select("tr")) {
            StringBuilder rowLine = new StringBuilder("|");
            for (Element cell : row.select("th,td"))
                rowLine.append(" ").append(cell.text().replace("|", "\\|")).append(" |");
            sb.append(rowLine).append("\n");
            if (!headerDone) {
                int cols = row.select("th,td").size();
                sb.append("|");
                for (int i = 0; i < cols; i++) sb.append(" --- |");
                sb.append("\n");
                headerDone = true;
            }
        }
        return sb.toString();
    }
}
