package com.jarvis.connectors;

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarvis.common.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Keyless RSS / Atom feed reader (spec §9 Communication & Media). No credential
 * needed — fetches any feed URL and returns its latest items. Real HTTP + XML
 * parsing (JDK DOM, external entities disabled for safety).
 */
@Component
@RequiredArgsConstructor
public class RssConnector implements Connector {

    private static final String UA = "Mozilla/5.0 (compatible; JarvisAIOS/1.0)";
    private final RestClient client = RestClient.builder().defaultHeader("User-Agent", UA).build();
    private final ObjectMapper mapper;

    @Override public String id() { return "rss"; }
    @Override public String name() { return "RSS / News"; }
    @Override public String category() { return "Media"; }
    @Override public String requiredSecret() { return null; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(new ConnectorAction("feed", "Read feed",
                "Fetch the latest items from an RSS/Atom feed (args: url, limit?)"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String credential) throws Exception {
        if (!"feed".equals(actionId)) {
            throw new NotFoundException("Unknown RSS action '" + actionId + "'");
        }
        JsonNode args = Json.read(mapper, argumentsJson);
        String url = args.path("url").asText("");
        int limit = args.path("limit").asInt(8);
        if (url.isBlank()) {
            return "Provide a feed 'url' (e.g. https://hnrss.org/frontpage).";
        }
        String xml = client.get().uri(url).retrieve().body(String.class);
        return parse(xml, limit);
    }

    /** Parse RSS <item> or Atom <entry> into a titled list. */
    String parse(String xml, int limit) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        DocumentBuilder builder = f.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));

        NodeList items = doc.getElementsByTagName("item");
        boolean atom = items.getLength() == 0;
        if (atom) {
            items = doc.getElementsByTagName("entry");
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(items.getLength(), Math.max(1, limit));
        for (int i = 0; i < n; i++) {
            Element item = (Element) items.item(i);
            String title = text(item, "title");
            String link = atom ? atomLink(item) : text(item, "link");
            sb.append("• ").append(title.isBlank() ? "(untitled)" : title);
            if (!link.isBlank()) {
                sb.append("\n  ").append(link);
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? "No items found in the feed." : sb.toString().trim();
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent().trim();
    }

    private static String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Node href = links.item(i).getAttributes().getNamedItem("href");
            if (href != null) {
                return href.getTextContent();
            }
        }
        return "";
    }
}
