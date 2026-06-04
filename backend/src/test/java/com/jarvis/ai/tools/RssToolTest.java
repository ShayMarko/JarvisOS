package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class RssToolTest {

    private final RssTool rss = new RssTool(new ObjectMapper());

    @Test
    void parsesRssHeadlinesWithLimit() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <rss version="2.0"><channel>
                  <item><title>Markets rally</title><link>https://x/1</link><pubDate>Mon, 03 Jun</pubDate></item>
                  <item><title>BTC breaks out</title><link>https://x/2</link><pubDate>Tue, 04 Jun</pubDate></item>
                  <item><title>Third story</title><link>https://x/3</link></item>
                </channel></rss>
                """;
        String out = rss.parseFeed(xml.getBytes(StandardCharsets.UTF_8), 2);
        assertThat(out).contains("Markets rally").contains("https://x/1").contains("BTC breaks out");
        assertThat(out).doesNotContain("Third story");   // limit honored
    }

    @Test
    void parsesAtomEntries() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry><title>Atom item</title><link rel="alternate" href="https://a/1"/><updated>2026-06-03</updated></entry>
                </feed>
                """;
        String out = rss.parseFeed(xml.getBytes(StandardCharsets.UTF_8), 5);
        assertThat(out).contains("Atom item").contains("https://a/1");
    }
}
