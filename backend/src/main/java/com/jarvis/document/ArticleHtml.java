package com.jarvis.document;

/**
 * Dependency-free SEO article-page generator — a clean, readable, search-friendly HTML page (proper
 * &lt;title&gt; + meta description, h1/h2 structure, optional affiliate CTA + disclosure). Accepts a
 * lite-markdown body (## headings, - bullets, blank-line paragraphs). Pure + static.
 */
final class ArticleHtml {

    private ArticleHtml() {
    }

    static String render(String title, String metaDescription, String bodyMarkdown,
                         String affiliateLabel, String affiliateUrl) {
        String t = esc(title == null || title.isBlank() ? "Untitled" : title);
        String desc = esc(metaDescription == null ? "" : metaDescription);
        String body = toHtml(bodyMarkdown == null ? "" : bodyMarkdown);

        String cta = "";
        if (affiliateUrl != null && !affiliateUrl.isBlank()) {
            cta = "  <p class=\"cta\"><a href=\"" + esc(affiliateUrl) + "\" rel=\"sponsored nofollow\" "
                    + "target=\"_blank\">" + esc(affiliateLabel == null || affiliateLabel.isBlank()
                            ? "Check the latest price" : affiliateLabel) + "</a></p>\n";
        }

        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%TITLE%</title>
                <meta name="description" content="%DESC%">
                <meta property="og:title" content="%TITLE%">
                <meta property="og:description" content="%DESC%">
                <style>
                  body{margin:0;font-family:Georgia,'Times New Roman',serif;color:#1a1a1a;background:#fff;line-height:1.7}
                  .wrap{max-width:720px;margin:0 auto;padding:48px 22px}
                  h1{font-size:34px;line-height:1.2;margin:0 0 18px} h2{font-size:23px;margin:34px 0 10px}
                  p{margin:0 0 16px;font-size:18px} ul{font-size:18px} a{color:#0b66c3}
                  .cta{margin:28px 0} .cta a{display:inline-block;background:#0b66c3;color:#fff;padding:12px 22px;
                    border-radius:8px;text-decoration:none;font-family:system-ui,Arial;font-weight:600}
                  footer{margin-top:48px;padding-top:18px;border-top:1px solid #eee;color:#777;font-size:13px;
                    font-family:system-ui,Arial}
                </style></head>
                <body><div class="wrap"><article>
                  <h1>%TITLE%</h1>
                %BODY%%CTA%  <footer>Disclosure: this article may contain affiliate links. If you buy through them
                  we may earn a commission, at no extra cost to you.</footer>
                </article></div></body></html>
                """
                .replace("%TITLE%", t)
                .replace("%DESC%", desc)
                .replace("%BODY%", body)
                .replace("%CTA%", cta);
    }

    /** Lite markdown → HTML: "## " heading, "- " bullets, blank-line-separated paragraphs. */
    private static String toHtml(String md) {
        StringBuilder out = new StringBuilder();
        boolean inList = false;
        for (String raw : md.split("\n", -1)) {
            String line = raw.strip();
            if (line.startsWith("## ")) {
                if (inList) { out.append("  </ul>\n"); inList = false; }
                out.append("  <h2>").append(esc(line.substring(3).strip())).append("</h2>\n");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) { out.append("  <ul>\n"); inList = true; }
                out.append("    <li>").append(esc(line.substring(2).strip())).append("</li>\n");
            } else if (line.isEmpty()) {
                if (inList) { out.append("  </ul>\n"); inList = false; }
            } else {
                if (inList) { out.append("  </ul>\n"); inList = false; }
                out.append("  <p>").append(esc(line)).append("</p>\n");
            }
        }
        if (inList) {
            out.append("  </ul>\n");
        }
        return out.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
