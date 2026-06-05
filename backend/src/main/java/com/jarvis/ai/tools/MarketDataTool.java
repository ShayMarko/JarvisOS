package com.jarvis.ai.tools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import com.jarvis.common.Http;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;

import lombok.RequiredArgsConstructor;

/**
 * Keyless market data from the public Binance API (no key needed for read-only price/candles). Returns a
 * compact, decision-ready summary — current price + 24h move, the recent range (support/resistance),
 * trend, and volume — for the Trading Research Agent to reason over. ADVISORY only; never trades.
 */
@Component
@RequiredArgsConstructor
public class MarketDataTool implements Tool {

    private static final HttpClient HTTP = Http.client(8);
    private static final String BASE = "https://api.binance.com";

    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("market_data",
                "Get keyless market data for a symbol (price, 24h move, recent range/support-resistance, trend, "
                + "volume) from the public Binance API. 'symbol' like BTCUSDT (or just BTC → BTCUSDT). Optional "
                + "'interval' (default 1d) and 'limit' (default 60 candles).",
                "{\"type\":\"object\",\"properties\":{\"symbol\":{\"type\":\"string\"},"
                + "\"interval\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}},\"required\":[\"symbol\"]}");
    }

    @Override
    public String execute(String args) {
        String symbol = normalize(ToolArgs.firstStr(mapper, args, "symbol", "pair", "ticker"));
        if (symbol.isBlank()) {
            return "Error: provide a 'symbol' (e.g. BTCUSDT).";
        }
        String interval = orDefault(ToolArgs.firstStr(mapper, args, "interval", "timeframe"), "1d");
        int limit = clamp(parseInt(ToolArgs.firstStr(mapper, args, "limit", "candles"), 60), 10, 200);
        try {
            JsonNode t = json(BASE + "/api/v3/ticker/24hr?symbol=" + symbol);
            if (t.has("code")) {   // Binance error payload {code,msg}
                return "No market data for '" + symbol + "': " + t.path("msg").asText("unknown symbol");
            }
            JsonNode k = json(BASE + "/api/v3/klines?symbol=" + symbol + "&interval=" + enc(interval) + "&limit=" + limit);

            double last = t.path("lastPrice").asDouble();
            double chg = t.path("priceChangePercent").asDouble();
            double hi24 = t.path("highPrice").asDouble();
            double lo24 = t.path("lowPrice").asDouble();

            double periodHigh = Double.MIN_VALUE, periodLow = Double.MAX_VALUE, firstClose = 0, volSum = 0, lastVol = 0;
            int n = 0;
            for (JsonNode c : k) {   // [openTime, open, high, low, close, volume, ...]
                double high = c.get(2).asDouble(), low = c.get(3).asDouble(), close = c.get(4).asDouble(), vol = c.get(5).asDouble();
                if (n == 0) { firstClose = close; }
                periodHigh = Math.max(periodHigh, high);
                periodLow = Math.min(periodLow, low);
                volSum += vol;
                lastVol = vol;
                n++;
            }
            double trendPct = firstClose > 0 ? (last - firstClose) / firstClose * 100 : 0;
            double avgVol = n > 0 ? volSum / n : 0;
            String volNote = avgVol > 0 ? (lastVol >= avgVol ? "above-average" : "below-average") : "n/a";

            return String.format(
                    "%s%n• Price: %.4f (24h %+.2f%%, 24h range %.4f–%.4f)%n"
                    + "• %d×%s range: %.4f (support) – %.4f (resistance)%n"
                    + "• Trend over %d candles: %+.2f%%%n"
                    + "• Volume: latest is %s (latest %.0f vs avg %.0f)",
                    symbol, last, chg, lo24, hi24, n, interval, periodLow, periodHigh, n, trendPct, volNote, lastVol, avgVol);
        } catch (Exception e) {
            return "Couldn't fetch market data for " + symbol + ": " + e.getMessage()
                    + " (the exchange API may be unreachable from this network).";
        }
    }

    private JsonNode json(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "JarvisAIOS/1.0").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }

    /** "btc" → "BTCUSDT"; leave full pairs (containing a quote asset) as-is. */
    private static String normalize(String s) {
        String u = s == null ? "" : s.trim().toUpperCase().replace("/", "").replace("-", "");
        if (u.isBlank()) {
            return "";
        }
        boolean hasQuote = u.endsWith("USDT") || u.endsWith("USD") || u.endsWith("BTC") || u.endsWith("ETH") || u.endsWith("EUR");
        return hasQuote ? u : u + "USDT";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String orDefault(String s, String d) {
        return s == null || s.isBlank() ? d : s;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null || s.isBlank() ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
