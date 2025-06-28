package net.ai.chatbot.service.training;

import java.io.Serializable;

public class CrawlerStatistics implements Serializable {

    private int processedPageCount = 0;
    private int totalLinksCount = 0;

    public void incrementProcessedPageCount() {
        processedPageCount++;
    }

    public void incrementTotalLinksCount(int linksCount) {
        totalLinksCount += linksCount;
    }

}