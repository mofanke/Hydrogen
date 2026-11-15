package com.hydrogen;

/** 在可读范围内保留文字重叠，并保证小视口仍能逐步前进。 */
public final class PageStep {
    private PageStep() {}

    public static int distance(int readableHeight, int lineHeight, int overlapLines) {
        if (readableHeight <= 1) return 0;
        int lines = Math.max(1, Math.min(5, overlapLines));
        long overlap = (long) Math.max(1, lineHeight) * lines;
        return (int) Math.max(1, readableHeight - overlap);
    }
}
