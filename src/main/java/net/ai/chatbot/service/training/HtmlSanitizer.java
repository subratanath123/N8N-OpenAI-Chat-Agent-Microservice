package net.ai.chatbot.service.training;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jsoup.internal.Normalizer.normalize;

public final class HtmlSanitizer {

    public static Map<String, Object> extractStructuredContent(String html, String baseUrl) {

        Document doc = Jsoup.parse(html, baseUrl);

        // --------------------------------------------------
        // 1. REMOVE HARD-NOISE ELEMENTS
        // --------------------------------------------------
        doc.select(
                "script, style, noscript, iframe, canvas, svg, form, input, button, " +
                        "nav, footer, header, aside, ads, dialog"
        ).remove();


        // --------------------------------------------------
        // 3. FIND MAIN CONTENT ROOT
        // --------------------------------------------------
        Element root = doc.selectFirst("main, article");
        if (root == null) root = doc.body();

        // --------------------------------------------------
        // 4. EXTRACT STRUCTURED CONTENT
        // --------------------------------------------------
        List<Map<String, Object>> sections = new ArrayList<>();
        Map<String, Object> currentSection = null;

        for (Element el : root.select("h1, h2, h3, h4, h5, h6, p, li")) {

            String text = normalize(el.text());
            if (text.isEmpty()) continue;

            if (el.tagName().matches("h[1-6]")) {
                currentSection = new LinkedHashMap<>();
                currentSection.put("heading", text);
                currentSection.put("content", new ArrayList<String>());
                sections.add(currentSection);
            } else {
                if (currentSection == null) {
                    currentSection = new LinkedHashMap<>();
                    currentSection.put("heading", "Introduction");
                    currentSection.put("content", new ArrayList<String>());
                    sections.add(currentSection);
                }
                @SuppressWarnings("unchecked")
                List<String> content = (List<String>) currentSection.get("content");
                content.add(text);
            }
        }

        // --------------------------------------------------
        // 5. POST-FILTER LOW-VALUE SECTIONS
        // --------------------------------------------------
        sections.removeIf(section -> {
            @SuppressWarnings("unchecked")
            List<String> content = (List<String>) section.get("content");
            int totalLength = content.stream().mapToInt(String::length).sum();
            return totalLength < 80; // safe threshold
        });

        // --------------------------------------------------
        // 6. FLATTEN CLEAN TEXT (FOR VECTOR DB)
        // --------------------------------------------------
        StringBuilder flatText = new StringBuilder();
        for (Map<String, Object> sec : sections) {
            flatText.append(sec.get("heading")).append("\n");
            @SuppressWarnings("unchecked")
            List<String> content = (List<String>) sec.get("content");
            content.forEach(c -> flatText.append(c).append("\n"));
            flatText.append("\n");
        }

        // --------------------------------------------------
        // 7. EXTRACT INTERNAL LINKS (OPTIONAL)
        // --------------------------------------------------
        List<String> links = root.select("a[href]").stream()
                .map(a -> a.absUrl("href"))
                .filter(h -> !h.isEmpty())
                .distinct()
                .toList();

        // --------------------------------------------------
        // 8. BUILD FINAL JSON STRUCTURE
        // --------------------------------------------------
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", baseUrl);
        result.put("text", flatText.toString().trim());
        result.put("sections", sections);
        result.put("links", links);

        return result;
    }

    public static String mapToSemanticText(Map<String, Object> content) {
        StringBuilder sb = new StringBuilder();

        content.forEach((key, value) -> {
            sb.append(key.toUpperCase()).append(":\n");

            if (value instanceof List<?> list) {
                for (Object item : list) {
                    sb.append("- ").append(item.toString()).append("\n");
                }
            } else {
                sb.append(value.toString()).append("\n");
            }

            sb.append("\n");
        });

        return sb.toString().trim();
    }

}
