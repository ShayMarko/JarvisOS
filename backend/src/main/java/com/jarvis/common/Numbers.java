package com.jarvis.common;

/** Small numeric formatting helpers used across read-models (cost/ROI/system metrics). */
public final class Numbers {

    private Numbers() {
    }

    /** Round to 2 decimals (currency). */
    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Round to 3 decimals. */
    public static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** Round to 6 decimals (fine-grained AI cost). */
    public static double round6(double v) {
        return Math.round(v * 1e6) / 1e6;
    }

    /** Bytes → whole gigabytes. */
    public static long toGb(long bytes) {
        return Math.round(bytes / 1_000_000_000.0);
    }
}
