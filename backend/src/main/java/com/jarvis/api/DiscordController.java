package com.jarvis.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.discord.DiscordService;
import com.jarvis.discord.JarvisDiscordProperties;

import lombok.RequiredArgsConstructor;

/**
 * Discord control-channel status + a test ping. Discord is configured via {@code jarvis.discord.*}
 * (env/config file) for the headless box — this surfaces whether it's live and lets you verify the
 * round-trip, without exposing the bot token.
 */
@RestController
@RequestMapping("/api/discord")
@RequiredArgsConstructor
public class DiscordController {

    private final JarvisDiscordProperties props;
    private final DiscordService discord;

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", props.isEnabled());
        out.put("tokenSet", props.getBotToken() != null && !props.getBotToken().isBlank());
        out.put("channelSet", props.getChannelId() != null && !props.getChannelId().isBlank());
        out.put("active", discord.active());                 // enabled + token present
        out.put("pushReady", discord.pushNotifications());   // active + push on + channel set
        return out;
    }

    @PostMapping("/test")
    public Map<String, Object> test() {
        if (!discord.pushNotifications()) {
            return Map.of("sent", false, "message",
                    "Not ready — needs enabled + bot token + channel id + push on. Configure jarvis.discord.* and restart.");
        }
        discord.push("✅ Test from Jarvis — your Discord control channel is connected.");
        return Map.of("sent", true, "message", "Sent a test message to your Discord channel.");
    }
}
