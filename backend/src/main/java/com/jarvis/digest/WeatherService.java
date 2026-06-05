package com.jarvis.digest;

import java.net.URI;
import java.net.http.HttpClient;
import com.jarvis.common.Http;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Keyless current-weather lookup via open-meteo.com — no API key, no account. Used by the morning
 * briefing; returns a short one-liner like "🌤️ 22°C, partly cloudy" or blank if unavailable.
 */
@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final HttpClient HTTP = Http.client(6);

    private final ObjectMapper mapper;

    /** A short current-weather line for the given coordinates, or "" if it can't be fetched. */
    public String current(double lat, double lon) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code&timezone=auto";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "JarvisAIOS/1.0").GET().build();
            String body = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode cur = mapper.readTree(body).path("current");
            if (cur.isMissingNode()) {
                return "";
            }
            int temp = (int) Math.round(cur.path("temperature_2m").asDouble());
            String desc = describe(cur.path("weather_code").asInt(-1));
            return temp + "°C, " + desc;
        } catch (Exception e) {
            return "";
        }
    }

    /** WMO weather-interpretation code → human text (open-meteo's code table, condensed). */
    private static String describe(int code) {
        return CODES.getOrDefault(code, "unknown conditions");
    }

    private static final Map<Integer, String> CODES = Map.ofEntries(
            Map.entry(0, "clear sky"), Map.entry(1, "mainly clear"), Map.entry(2, "partly cloudy"),
            Map.entry(3, "overcast"), Map.entry(45, "fog"), Map.entry(48, "rime fog"),
            Map.entry(51, "light drizzle"), Map.entry(53, "drizzle"), Map.entry(55, "dense drizzle"),
            Map.entry(61, "light rain"), Map.entry(63, "rain"), Map.entry(65, "heavy rain"),
            Map.entry(71, "light snow"), Map.entry(73, "snow"), Map.entry(75, "heavy snow"),
            Map.entry(80, "rain showers"), Map.entry(81, "rain showers"), Map.entry(82, "violent rain showers"),
            Map.entry(95, "thunderstorm"), Map.entry(96, "thunderstorm with hail"), Map.entry(99, "thunderstorm with hail"));
}
