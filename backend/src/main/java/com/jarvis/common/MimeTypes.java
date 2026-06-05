package com.jarvis.common;

import java.util.Locale;

/** Maps an image file name to its MIME type (best-effort, defaults to PNG). */
public final class MimeTypes {

    private MimeTypes() {
    }

    public static String of(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".gif")) {
            return "image/gif";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
}
