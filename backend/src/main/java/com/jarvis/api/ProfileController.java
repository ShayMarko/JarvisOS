package com.jarvis.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.profile.ProfileService;

import lombok.RequiredArgsConstructor;

/** The user's "About Me" profile — read + edit by the user (the Profile window). */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    public record ProfileRequest(String content) {}

    private final ProfileService profile;

    @GetMapping
    public Map<String, String> get() {
        return Map.of("content", profile.read());
    }

    @PutMapping
    public Map<String, String> save(@RequestBody ProfileRequest req) {
        return Map.of("content", profile.write(req.content()));
    }
}
