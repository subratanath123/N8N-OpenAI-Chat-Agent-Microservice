package net.ai.chatbot.service;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static net.ai.chatbot.service.training.PlaywrightWebsiteCrawler.extractLinks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkExtractorTest {

    @Mock
    Page page;

    @Test
    void shouldResolveRelativeAndAbsoluteLinksCorrectly() {
        // given
        String baseUrl = "https://example.com/docs/page.html";

        List<Object> mockEvaluateResult = List.of(
                "/about",
                "contact",
                "../pricing",
                "https://example.com/blog",
                "https://external.com/home", // should be excluded
                "#fragment",
                "  "
        );

        when(page.evaluate(anyString()))
                .thenReturn(mockEvaluateResult);

        // when
        Set<String> result = extractLinks(page, baseUrl);

        // then
        assertEquals(
                Set.of(
                        "https://example.com/about",
                        "https://example.com/docs/contact",
                        "https://example.com/pricing",
                        "https://example.com/blog"
                ),
                result
        );
    }
}

