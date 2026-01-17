package net.ai.chatbot.service.training;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.ScrappedData;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
public class PlaywrightWebsiteCrawler {

    private static final Pattern EXCLUSIONS = Pattern.compile(".*(\\.(css|js|xml|gif|jpg|png|mp3|mp4|zip|gz|pdf))$");
    private static final int DEFAULT_WAIT_TIMEOUT = 30000; // 30 seconds
    private static final int DEFAULT_NAVIGATION_TIMEOUT = 60000; // 60 seconds

    /**
     * Crawl a website using Playwright, which can handle React and other JavaScript-rendered websites
     *
     * @param websiteUrl The base URL to start crawling from
     * @param email Email identifier for storage paths
     * @param maxDepthOfCrawling Maximum depth to crawl
     * @param maxPagesToFetch Maximum number of pages to fetch
     * @param consumer Consumer to process each scraped page
     */
    public static void crawl(String websiteUrl,
                             String email,
                             int maxDepthOfCrawling,
                             int maxPagesToFetch,
                             Consumer<ScrappedData> consumer) throws Exception {
        
        Playwright playwright = null;
        Browser browser = null;
        
        try {
            playwright = Playwright.create();
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(java.util.Arrays.asList("--no-sandbox", "--disable-setuid-sandbox"));
            browser = playwright.chromium().launch(launchOptions);

            // Create context with options using the correct API
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

            context.setDefaultNavigationTimeout(DEFAULT_NAVIGATION_TIMEOUT);
            context.setDefaultTimeout(DEFAULT_WAIT_TIMEOUT);

            Set<String> visitedUrls = new HashSet<>();
            Set<String> urlsToVisit = new HashSet<>();
            urlsToVisit.add(websiteUrl);
            
            int pagesFetched = 0;
            int currentDepth = 0;

            // Normalize base URL
            URI baseUri = new URI(websiteUrl);
            String baseUrl = baseUri.getScheme() + "://" + baseUri.getHost();
            if (baseUri.getPort() != -1) {
                baseUrl += ":" + baseUri.getPort();
            }

            while (!urlsToVisit.isEmpty() && pagesFetched < maxPagesToFetch && currentDepth <= maxDepthOfCrawling) {
                Set<String> nextLevelUrls = new HashSet<>();
                
                for (String url : urlsToVisit) {
                    if (visitedUrls.contains(url) || pagesFetched >= maxPagesToFetch) {
                        continue;
                    }

                    try {
                        if (!isValidUrl(url, baseUrl)) {
                            continue;
                        }

                        visitedUrls.add(url);
                        Page page = context.newPage();

                        try {
                            log.info("Crawling URL: {}", url);
                            
                            // Navigate to the page
                            page.navigate(url);
                            
                            // Wait for page to be fully loaded (crucial for React apps)
                            // Wait for DOM content to be loaded first
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            
                            // Then wait for network to be idle (ensures all async requests complete)
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                            
                            // Additional wait for React content to fully render
                            Thread.sleep(2000);

                            // Extract page content
                            String title = page.title();
                            String html = page.content();
                            String text = page.textContent("body");

                            // Extract links for next level crawling
                            if (currentDepth < maxDepthOfCrawling) {
                                Set<String> links = extractLinks(page, baseUrl);
                                for (String link : links) {
                                    if (!visitedUrls.contains(link) && isValidUrl(link, baseUrl)) {
                                        nextLevelUrls.add(link);
                                    }
                                }
                            }

                            // Process the scraped data
                            consumer.accept(new ScrappedData(url, title, text != null ? text : "", html));
                            pagesFetched++;

                            log.info("Successfully scraped page: {} (Total: {})", url, pagesFetched);

                        } catch (Exception e) {
                            log.warn("Failed to crawl URL: {} - {}", url, e.getMessage());
                        } finally {
                            page.close();
                        }

                    } catch (URISyntaxException e) {
                        log.warn("Invalid URL format: {} - {}", url, e.getMessage());
                    } catch (Exception e) {
                        log.warn("Error processing URL: {} - {}", url, e.getMessage());
                    }
                }

                urlsToVisit = nextLevelUrls;
                currentDepth++;
            }

            log.info("Crawling completed. Total pages fetched: {}", pagesFetched);

        } finally {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        }
    }

    /**
     * Extract all valid links from a page
     */
    @SuppressWarnings("unchecked")
    public static Set<String> extractLinks(Page page, String currentPageUrl) {
        Set<String> links = new HashSet<>();

        log.info("Trying to extract link for: {}", currentPageUrl);

        try {
            Object result = page.evaluate("""
            () => {
                return Array.from(document.querySelectorAll('a[href]'))
                    .map(a => a.getAttribute('href'))
                    .filter(h => h && h.trim().length > 0);
            }
        """);

            if (!(result instanceof java.util.List<?> hrefList)) {
                log.info("SKipping extract link");
                return links;
            }

            URI baseUri = new URI(currentPageUrl);

            for (Object hrefObj : hrefList) {
                if (hrefObj == null) continue;

                String href = hrefObj.toString().trim();

                // ✅ FIX 1: skip fragment-only
                if (href.startsWith("#")) continue;

                try {
                    URI resolved = baseUri.resolve(href).normalize();

                    String normalizedUrl = resolved.toString();

                    // Remove fragment safely
                    int hashIndex = normalizedUrl.indexOf('#');
                    if (hashIndex != -1) {
                        normalizedUrl = normalizedUrl.substring(0, hashIndex);
                    }

                    log.info("Trying to extract link from normalizedUrl: {}", normalizedUrl);

                    // Same-host constraint
                    if (resolved.getHost() != null &&
                            resolved.getHost().equalsIgnoreCase(baseUri.getHost()) &&
                            !EXCLUSIONS.matcher(normalizedUrl.toLowerCase()).matches()) {

                        links.add(normalizedUrl);
                    }

                } catch (IllegalArgumentException ignored) {
                    log.warn("Error extracting links", ignored);
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting links: {}", e.getMessage());
        }

        return links;
    }

    /**
     * Check if URL is valid for crawling
     */
    private static boolean isValidUrl(String url, String baseUrl) throws URISyntaxException {
        if (url == null || url.isEmpty()) {
            return false;
        }

        URI uri = new URI(url);
        
        // Must be HTTP or HTTPS
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            return false;
        }

        // Must be from the same base domain
        if (!url.startsWith(baseUrl)) {
            return false;
        }

        // Exclude certain file types
        String urlLower = url.toLowerCase();
        if (EXCLUSIONS.matcher(urlLower).matches()) {
            return false;
        }

        return true;
    }
}

