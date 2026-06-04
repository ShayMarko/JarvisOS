package com.jarvis.document;

import java.util.List;
import java.util.Locale;

/**
 * Dependency-free SVG chart renderer (bar | line | pie). Produces a clean, dark-themed standalone SVG
 * string the agent can save as an artifact and the user can open in any browser. Pure + static.
 */
final class ChartSvg {

    private ChartSvg() {
    }

    private static final int W = 760;
    private static final int H = 440;
    private static final String[] PALETTE = {
            "#45d6ff", "#4ad295", "#ffb060", "#b98cff", "#ff7e9a", "#7ee0c8", "#f6e05e", "#9aa8ff"
    };

    static String render(String type, String title, List<String> labels, List<Double> values) {
        String t = type == null ? "bar" : type.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(W).append("\" height=\"").append(H)
          .append("\" viewBox=\"0 0 ").append(W).append(' ').append(H).append("\" font-family=\"system-ui,Segoe UI,Arial\">\n");
        sb.append("<rect width=\"").append(W).append("\" height=\"").append(H).append("\" fill=\"#0a1220\"/>\n");
        if (title != null && !title.isBlank()) {
            sb.append(text(W / 2, 30, esc(title), "#dff0ff", 18, "middle", true));
        }
        if (labels == null || values == null || labels.isEmpty() || values.isEmpty()) {
            sb.append(text(W / 2, H / 2, "No data", "#7e93a8", 14, "middle", false));
            return sb.append("</svg>").toString();
        }
        switch (t) {
            case "pie", "donut" -> pie(sb, labels, values);
            case "line", "area" -> line(sb, labels, values);
            default -> bar(sb, labels, values);
        }
        return sb.append("</svg>").toString();
    }

    private static void bar(StringBuilder sb, List<String> labels, List<Double> values) {
        int n = Math.min(labels.size(), values.size());
        double max = 0;
        for (int i = 0; i < n; i++) {
            max = Math.max(max, values.get(i));
        }
        if (max <= 0) {
            max = 1;
        }
        int left = 60;
        int right = 24;
        int top = 56;
        int bottom = 60;
        int plotW = W - left - right;
        int plotH = H - top - bottom;
        sb.append(line(left, top + plotH, left + plotW, top + plotH, "#24364d"));   // x axis
        sb.append(line(left, top, left, top + plotH, "#24364d"));                   // y axis
        double gap = plotW / (double) n;
        double bw = gap * 0.6;
        for (int i = 0; i < n; i++) {
            double v = values.get(i);
            double bh = plotH * (v / max);
            double x = left + i * gap + (gap - bw) / 2;
            double y = top + plotH - bh;
            sb.append("<rect x=\"").append(f(x)).append("\" y=\"").append(f(y)).append("\" width=\"").append(f(bw))
              .append("\" height=\"").append(f(bh)).append("\" rx=\"3\" fill=\"").append(PALETTE[i % PALETTE.length]).append("\"/>\n");
            sb.append(text(x + bw / 2, y - 6, num(v), "#cfe0f0", 11, "middle", false));
            sb.append(text(x + bw / 2, top + plotH + 18, esc(clip(labels.get(i))), "#7e93a8", 11, "middle", false));
        }
    }

    private static void line(StringBuilder sb, List<String> labels, List<Double> values) {
        int n = Math.min(labels.size(), values.size());
        double max = 0;
        for (int i = 0; i < n; i++) {
            max = Math.max(max, values.get(i));
        }
        if (max <= 0) {
            max = 1;
        }
        int left = 60;
        int right = 24;
        int top = 56;
        int bottom = 60;
        int plotW = W - left - right;
        int plotH = H - top - bottom;
        sb.append(line(left, top + plotH, left + plotW, top + plotH, "#24364d"));
        sb.append(line(left, top, left, top + plotH, "#24364d"));
        StringBuilder pts = new StringBuilder();
        double step = n > 1 ? plotW / (double) (n - 1) : 0;
        for (int i = 0; i < n; i++) {
            double x = left + i * step;
            double y = top + plotH - plotH * (values.get(i) / max);
            pts.append(f(x)).append(',').append(f(y)).append(' ');
            sb.append(text(x, top + plotH + 18, esc(clip(labels.get(i))), "#7e93a8", 11, "middle", false));
        }
        sb.append("<polyline fill=\"none\" stroke=\"#45d6ff\" stroke-width=\"2.5\" points=\"").append(pts.toString().trim()).append("\"/>\n");
        for (int i = 0; i < n; i++) {
            double x = left + i * step;
            double y = top + plotH - plotH * (values.get(i) / max);
            sb.append("<circle cx=\"").append(f(x)).append("\" cy=\"").append(f(y)).append("\" r=\"3.5\" fill=\"#45d6ff\"/>\n");
        }
    }

    private static void pie(StringBuilder sb, List<String> labels, List<Double> values) {
        int n = Math.min(labels.size(), values.size());
        double total = 0;
        for (int i = 0; i < n; i++) {
            total += Math.max(0, values.get(i));
        }
        if (total <= 0) {
            total = 1;
        }
        double cx = 250;
        double cy = 250;
        double r = 150;
        double angle = -Math.PI / 2;
        for (int i = 0; i < n; i++) {
            double frac = Math.max(0, values.get(i)) / total;
            double next = angle + frac * 2 * Math.PI;
            double x1 = cx + r * Math.cos(angle);
            double y1 = cy + r * Math.sin(angle);
            double x2 = cx + r * Math.cos(next);
            double y2 = cy + r * Math.sin(next);
            int large = frac > 0.5 ? 1 : 0;
            sb.append("<path d=\"M").append(f(cx)).append(' ').append(f(cy)).append(" L").append(f(x1)).append(' ').append(f(y1))
              .append(" A").append(f(r)).append(' ').append(f(r)).append(" 0 ").append(large).append(" 1 ")
              .append(f(x2)).append(' ').append(f(y2)).append(" Z\" fill=\"").append(PALETTE[i % PALETTE.length]).append("\"/>\n");
            angle = next;
        }
        // legend
        int ly = 80;
        for (int i = 0; i < n; i++) {
            sb.append("<rect x=\"470\" y=\"").append(ly - 10).append("\" width=\"12\" height=\"12\" rx=\"2\" fill=\"")
              .append(PALETTE[i % PALETTE.length]).append("\"/>\n");
            sb.append(text(490, ly, esc(clip(labels.get(i))) + "  " + num(values.get(i)), "#cfe0f0", 12, "start", false));
            ly += 24;
        }
    }

    private static String line(double x1, double y1, double x2, double y2, String color) {
        return "<line x1=\"" + f(x1) + "\" y1=\"" + f(y1) + "\" x2=\"" + f(x2) + "\" y2=\"" + f(y2)
                + "\" stroke=\"" + color + "\" stroke-width=\"1\"/>\n";
    }

    private static String text(double x, double y, String s, String color, int size, String anchor, boolean bold) {
        return "<text x=\"" + f(x) + "\" y=\"" + f(y) + "\" fill=\"" + color + "\" font-size=\"" + size
                + "\" text-anchor=\"" + anchor + "\"" + (bold ? " font-weight=\"600\"" : "") + ">" + s + "</text>\n";
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 16 ? s.substring(0, 15) + "…" : s;
    }

    private static String num(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
