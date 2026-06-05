package com.jarvis.ai.tools;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import com.jarvis.common.Http;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;

import lombok.RequiredArgsConstructor;

/**
 * Keyless RSS/Atom reader — fetch a feed URL and return the latest headlines (title · date · link).
 * Lets the Trading Research Agent (or any agent) pull structured news from a source the user trusts,
 * instead of scraping HTML. XXE-safe parser.
 */
@Component
@RequiredArgsConstructor
public class RssTool implements Tool {

    private static final HttpClient HTTP = Http.client(8);

    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("rss_fetch",
                "Fetch an RSS/Atom feed URL and return the latest headlines (title, date, link). "
                + "Provide 'url'; optional 'limit' (default 8).",
                "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}},"
                + "\"required\":[\"url\"]}");
    }

    @Override
    public String execute(String args) {
        String url = ToolArgs.firstStr(mapper, args, "url", "feed", "link");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Provide an http(s) feed 'url'.";
        }
        int limit = clamp(parseInt(ToolArgs.firstStr(mapper, args, "limit", "max"), 8), 1, 30);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "JarvisAIOS/1.0").GET().build();
            byte[] body = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
            return parseFeed(body, limit);
        } catch (Exception e) {
            return "Couldn't read the feed: " + e.getMessage();
        }
    }

    /** Parse RSS or Atom bytes into a headline list. Package-private so it's unit-testable without network. */
    String parseFeed(byte[] body, int limit) throws Exception {
        var doc = secureBuilder().parse(new ByteArrayInputStream(body));
        NodeList items = doc.getElementsByTagName("item");          // RSS
        boolean atom = items.getLength() == 0;
        if (atom) {
            items = doc.getElementsByTagName("entry");              // Atom
        }
        if (items.getLength() == 0) {
            return "No items found in the feed (is it a valid RSS/Atom URL?).";
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(limit, items.getLength());
        for (int i = 0; i < n; i++) {
            Element it = (Element) items.item(i);
            String title = text(it, "title");
            String date = atom ? text(it, "updated") : text(it, "pubDate");
            String link = atom ? atomLink(it) : text(it, "link");
            sb.append("• ").append(title.isBlank() ? "(untitled)" : title);
            if (!date.isBlank()) {
                sb.append("  [").append(date).append("]");
            }
            if (!link.isBlank()) {
                sb.append("\n  ").append(link);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent().trim();
    }

    /** Atom <link href="…"> (prefer rel="alternate"); falls back to the first link. */
    private static String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        String first = "";
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            if (node instanceof Element l) {
                String href = l.getAttribute("href");
                if (first.isEmpty()) {
                    first = href;
                }
                if ("alternate".equals(l.getAttribute("rel"))) {
                    return href;
                }
            }
        }
        return first;
    }

    private static DocumentBuilder secureBuilder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setNamespaceAware(false);
        return f.newDocumentBuilder();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null || s.isBlank() ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
