package com.jarvis.common;

import java.net.http.HttpClient;
import java.time.Duration;

/** Factory for {@link HttpClient} instances with a consistent connect timeout. */
public final class Http {

    private Http() {
    }

    /** A java.net.http client with the given connect timeout (seconds). */
    public static HttpClient client(int connectTimeoutSeconds) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
    }
}
