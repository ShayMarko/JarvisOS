package com.jarvis.document;

import java.util.List;

/**
 * Dependency-free landing-page generator — a self-contained, dark-themed marketing page (hero, feature
 * grid, pricing card, CTA) the agent can drop into a product folder to list/sell it. Pure + static.
 */
final class LandingPageHtml {

    private LandingPageHtml() {
    }

    static String render(String title, String tagline, List<String> features, String price, String cta) {
        String t = esc(title == null || title.isBlank() ? "Your Product" : title);
        String tag = esc(tagline == null ? "" : tagline);
        String priceLabel = price == null || price.isBlank() ? "" : esc(price);
        String ctaLabel = esc(cta == null || cta.isBlank() ? "Get it" : cta);

        StringBuilder feat = new StringBuilder();
        if (features != null) {
            for (String f : features) {
                if (f != null && !f.isBlank()) {
                    feat.append("      <li><span class=\"tick\">✓</span>").append(esc(f)).append("</li>\n");
                }
            }
        }

        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%TITLE%</title>
                <style>
                  :root{--bg:#0a1120;--card:#111a2e;--line:#22304d;--text:#dfe9f7;--muted:#8aa0bd;--accent:#45d6ff}
                  *{box-sizing:border-box} body{margin:0;font-family:system-ui,Segoe UI,Arial;background:
                    radial-gradient(900px 600px at 50% -10%,rgba(69,214,255,.14),transparent 60%),var(--bg);
                    color:var(--text);line-height:1.55}
                  .wrap{max-width:860px;margin:0 auto;padding:72px 24px}
                  .hero{text-align:center} h1{font-size:46px;margin:0 0 12px;letter-spacing:-.02em}
                  .tag{font-size:19px;color:var(--muted);margin:0 auto 28px;max-width:620px}
                  .cta{display:inline-block;background:linear-gradient(135deg,var(--accent),#3a86d8);color:#04101e;
                    font-weight:700;padding:14px 28px;border-radius:12px;text-decoration:none;font-size:16px}
                  .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;margin:56px 0}
                  .feat{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:18px 20px}
                  .feat ul{list-style:none;margin:0;padding:0} .feat li{padding:7px 0;color:var(--text)}
                  .tick{color:var(--accent);font-weight:700;margin-right:10px}
                  .price{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:34px;text-align:center;max-width:420px;margin:0 auto}
                  .price .amt{font-size:40px;font-weight:800;color:#fff;margin:6px 0 18px}
                  footer{text-align:center;color:var(--muted);font-size:13px;margin-top:64px}
                </style></head>
                <body><div class="wrap">
                  <div class="hero">
                    <h1>%TITLE%</h1>
                    <p class="tag">%TAGLINE%</p>
                    <a class="cta" href="#buy">%CTA%</a>
                  </div>
                  <div class="grid"><div class="feat"><ul>
                %FEATURES%      </ul></div></div>
                  <div class="price" id="buy">
                    <div>One-time purchase</div>
                    <div class="amt">%PRICE%</div>
                    <a class="cta" href="#">%CTA%</a>
                  </div>
                  <footer>Built with Jarvis — your local AI OS.</footer>
                </div></body></html>
                """
                .replace("%TITLE%", t)
                .replace("%TAGLINE%", tag)
                .replace("%CTA%", ctaLabel)
                .replace("%FEATURES%", feat.toString())
                .replace("%PRICE%", priceLabel.isBlank() ? "—" : priceLabel);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
