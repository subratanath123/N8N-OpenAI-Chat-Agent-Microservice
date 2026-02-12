package net.ai.chatbot.service.training;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jsoup.internal.Normalizer.normalize;

public final class HtmlSanitizer {

    public static Map<String, Object> extractStructuredContent(String html, String baseUrl) {

        Document doc = Jsoup.parse(html, baseUrl);

        // --------------------------------------------------
        // 1. REMOVE HARD-NOISE ELEMENTS (BUT KEEP IMAGES!)
        // --------------------------------------------------
        doc.select(
                "script, style, noscript, iframe, canvas, svg, form, input, button[type='submit'], " +
                        "nav, footer, header, aside, ads, dialog"
        ).remove();


        // --------------------------------------------------
        // 2. FIND MAIN CONTENT ROOT
        // --------------------------------------------------
        Element root = doc.selectFirst("main, article");
        if (root == null) root = doc.body();

        // --------------------------------------------------
        // 3. EXTRACT PRODUCT CARDS/ITEMS (CRITICAL FOR E-COMMERCE)
        // --------------------------------------------------
        List<Map<String, Object>> products = extractProducts(root, baseUrl);

        // --------------------------------------------------
        // 4. EXTRACT STRUCTURED CONTENT (HEADINGS + TEXT)
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
        // 6. EXTRACT ALL IMAGES (NOT JUST PRODUCTS)
        // --------------------------------------------------
        List<Map<String, String>> images = extractImages(root, baseUrl);

        // --------------------------------------------------
        // 7. FLATTEN CLEAN TEXT (FOR VECTOR DB)
        // --------------------------------------------------
        StringBuilder flatText = new StringBuilder();

        // Add product information to flat text
        if (!products.isEmpty()) {
            flatText.append("PRODUCTS:\n");
            for (Map<String, Object> product : products) {
                flatText.append("Product: ").append(product.get("name")).append("\n");
                if (product.get("price") != null) {
                    flatText.append("Price: ").append(product.get("price")).append("\n");
                }
                if (product.get("description") != null) {
                    flatText.append("Description: ").append(product.get("description")).append("\n");
                }
                if (product.get("image") != null) {
                    flatText.append("Image: ").append(product.get("image")).append("\n");
                }
                if (product.get("link") != null) {
                    flatText.append("Link: ").append(product.get("link")).append("\n");
                }
                flatText.append("\n");
            }
            flatText.append("\n");
        }

        // Add sections
        for (Map<String, Object> sec : sections) {
            flatText.append(sec.get("heading")).append("\n");
            @SuppressWarnings("unchecked")
            List<String> content = (List<String>) sec.get("content");
            content.forEach(c -> flatText.append(c).append("\n"));
            flatText.append("\n");
        }

        // --------------------------------------------------
        // 8. EXTRACT INTERNAL LINKS
        // --------------------------------------------------
        List<String> links = root.select("a[href]").stream()
                .map(a -> a.absUrl("href"))
                .filter(h -> !h.isEmpty())
                .distinct()
                .toList();

        // --------------------------------------------------
        // 9. BUILD FINAL JSON STRUCTURE
        // --------------------------------------------------
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", baseUrl);
        result.put("text", flatText.toString().trim());
        result.put("sections", sections);
        result.put("products", products); // CRITICAL: Include products
        result.put("images", images); // CRITICAL: Include all images
        result.put("links", links);

        return result;
    }

    /**
     * Extract product information from common e-commerce patterns
     */
    private static List<Map<String, Object>> extractProducts(Element root, String baseUrl) {
        List<Map<String, Object>> products = new ArrayList<>();

        // Common product card selectors
        String[] productSelectors = {
                ".product, .product-card, .product-item, .woocommerce-LoopProduct-link, .item",
                "[class*='product'], [class*='item-card'], [data-product]",
                ".card, .grid-item, .shop-item"
        };

        for (String selector : productSelectors) {
            Elements productElements = root.select(selector);

            for (Element productEl : productElements) {
                Map<String, Object> product = new LinkedHashMap<>();

                // Extract product name
                Element nameEl = productEl.selectFirst("h1, h2, h3, h4, .title, .product-title, .product-name, [class*='name']");
                if (nameEl != null) {
                    product.put("name", normalize(nameEl.text()));
                }

                // Extract price
                Element priceEl = productEl.selectFirst(".price, .product-price, [class*='price'], .amount, .cost");
                if (priceEl != null) {
                    product.put("price", normalize(priceEl.text()));
                }

                // Extract description
                Element descEl = productEl.selectFirst("p, .description, .product-description, [class*='desc']");
                if (descEl != null) {
                    String desc = normalize(descEl.text());
                    if (!desc.isEmpty() && desc.length() > 10) {
                        product.put("description", desc);
                    }
                }

                // Extract image
                Element imgEl = productEl.selectFirst("img");
                if (imgEl != null) {
                    String imgSrc = imgEl.absUrl("src");
                    if (imgSrc.isEmpty()) imgSrc = imgEl.absUrl("data-src");
                    if (imgSrc.isEmpty()) imgSrc = imgEl.absUrl("data-lazy-src");
                    if (!imgSrc.isEmpty()) {
                        product.put("image", imgSrc);
                        product.put("image_alt", imgEl.attr("alt"));
                    }
                }

                // Extract product link
                Element linkEl = productEl.selectFirst("a[href]");
                if (linkEl != null) {
                    String link = linkEl.absUrl("href");
                    if (!link.isEmpty()) {
                        product.put("link", link);
                    }
                }

                // Only add if we found at least a name or image
                if (product.containsKey("name") || product.containsKey("image")) {
                    products.add(product);
                }
            }

            // If we found products, don't check other selectors
            if (!products.isEmpty()) break;
        }

        return products;
    }

    /**
     * Extract all images with context
     */
    private static List<Map<String, String>> extractImages(Element root, String baseUrl) {
        List<Map<String, String>> images = new ArrayList<>();

        Elements imgElements = root.select("img");
        for (Element img : imgElements) {
            Map<String, String> imageData = new LinkedHashMap<>();

            String src = img.absUrl("src");
            if (src.isEmpty()) src = img.absUrl("data-src");
            if (src.isEmpty()) src = img.absUrl("data-lazy-src");

            if (!src.isEmpty()) {
                imageData.put("url", src);
                imageData.put("alt", img.attr("alt"));
                imageData.put("title", img.attr("title"));

                // Get surrounding context
                Element parent = img.parent();
                if (parent != null) {
                    String context = normalize(parent.text());
                    if (!context.isEmpty() && context.length() < 200) {
                        imageData.put("context", context);
                    }
                }

                images.add(imageData);
            }
        }

        return images;
    }

    public static String mapToSemanticText(Map<String, Object> content) {
        StringBuilder sb = new StringBuilder();

        // Handle products specially
        if (content.containsKey("products")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> products = (List<Map<String, Object>>) content.get("products");

            if (!products.isEmpty()) {
                sb.append("PRODUCTS:\n");
                for (Map<String, Object> product : products) {
                    sb.append("---\n");
                    product.forEach((key, value) -> {
                        sb.append(key.toUpperCase()).append(": ").append(value).append("\n");
                    });
                }
                sb.append("\n");
            }
        }

        // Handle images specially
        if (content.containsKey("images")) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> images = (List<Map<String, String>>) content.get("images");

            if (!images.isEmpty()) {
                sb.append("IMAGES:\n");
                for (Map<String, String> image : images) {
                    sb.append("Image URL: ").append(image.get("url")).append("\n");
                    if (image.get("alt") != null && !image.get("alt").isEmpty()) {
                        sb.append("Alt text: ").append(image.get("alt")).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        // Handle other content
        content.forEach((key, value) -> {
            if (key.equals("products") || key.equals("images")) {
                return; // Already handled above
            }

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