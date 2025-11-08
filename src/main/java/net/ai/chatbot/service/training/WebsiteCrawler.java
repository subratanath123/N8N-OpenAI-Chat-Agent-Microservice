package net.ai.chatbot.service.training;

import com.goikosoft.crawler4j.crawler.CrawlConfig;
import com.goikosoft.crawler4j.crawler.CrawlController;
import com.goikosoft.crawler4j.crawler.Page;
import com.goikosoft.crawler4j.crawler.WebCrawler;
import com.goikosoft.crawler4j.fetcher.PageFetcher;
import com.goikosoft.crawler4j.parser.HtmlParseData;
import com.goikosoft.crawler4j.robotstxt.RobotstxtConfig;
import com.goikosoft.crawler4j.robotstxt.RobotstxtServer;
import com.goikosoft.crawler4j.url.WebURL;
import net.ai.chatbot.entity.ScrappedData;
import net.ai.chatbot.utils.Utils;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class WebsiteCrawler extends WebCrawler {
    private final static Pattern EXCLUSIONS = Pattern.compile(".*(\\.(css|js|xml|gif|jpg|png|mp3|mp4|zip|gz|pdf))$");

    private final CrawlerStatistics stats;
    private final String websiteUrl;
    private final Consumer<ScrappedData> consumer;

    public WebsiteCrawler(CrawlerStatistics stats,
                          String websiteUrl,
                          Consumer<ScrappedData> consumer) {

        this.stats = stats;
        this.websiteUrl = websiteUrl;
        this.consumer = consumer;
    }

    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        String urlString = url.getURL().toLowerCase();

        return !EXCLUSIONS.matcher(urlString).matches()
                && urlString.startsWith(websiteUrl);
    }

    @Override
    public void visit(Page page) {

        if (page.getParseData() instanceof HtmlParseData htmlParseData) {
            String title = htmlParseData.getTitle();
            String html = htmlParseData.getHtml();
            String text = htmlParseData.getText();
            Set<WebURL> links = htmlParseData.getOutgoingUrls();

            stats.incrementTotalLinksCount(links.size());

            System.out.println("----------------Scrapper Data :: Start-----------");
            System.out.println(page.getWebURL().getURL());
            consumer.accept(new ScrappedData(page.getWebURL().getURL(), title, text, html));
            System.out.println("----------------Scrapper Data :: End-----------");
        }
    }

    //Call this to start a new crawler
    public static void crawl(String websiteUrl,
                             String email,
                             int maxDepthOfCrawling,
                             int maxPagesToFetch,
                             Consumer<ScrappedData> consumer) throws Exception {

        String crawlStoragePath = Utils.sanitizePath("/tmp/" + email + "/" + websiteUrl);
        Utils.createDirectoryIfNotExists(crawlStoragePath);
        File crawlStorage = new File(crawlStoragePath);

        CrawlConfig htmlConfig = new CrawlConfig();
        htmlConfig.setCrawlStorageFolder(crawlStorage.getAbsolutePath());
        htmlConfig.setMaxDepthOfCrawling(maxDepthOfCrawling);
        htmlConfig.setMaxPagesToFetch(maxPagesToFetch);

        PageFetcher pageFetcher = new PageFetcher(htmlConfig);
        RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
        RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);
        CrawlController htmlController = new CrawlController(htmlConfig, pageFetcher, robotstxtServer);

        htmlController.addSeed(websiteUrl);

        CrawlerStatistics stats = new CrawlerStatistics();

        CrawlController.WebCrawlerFactory<WebsiteCrawler> htmlFactory = () -> new WebsiteCrawler(stats, websiteUrl, consumer);

        htmlController.startNonBlocking(htmlFactory, 10);
        htmlController.waitUntilFinish();
    }

}
