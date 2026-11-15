package com.hydrogen;

/** 按行、列生成覆盖整张图片的重叠分段。 */
public final class ImageTiles {
    public final int imageWidth, imageHeight, tileWidth, tileHeight, columns, rows;
    public final float scale;
    private final int stepX, stepY;

    public ImageTiles(int width, int height, int viewWidth, int viewHeight,
                      boolean originalSize, int overlapPixels) {
        if (width <= 0 || height <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            throw new IllegalArgumentException("图片与可读区域必须为正尺寸");
        }
        imageWidth = width;
        imageHeight = height;
        scale = originalSize ? 1f : Math.min(1f, (float) viewWidth / width);
        tileWidth = originalSize ? Math.min(width, viewWidth) : width;
        tileHeight = Math.min(height, Math.max(1, (int) Math.floor(viewHeight / scale)));
        int overlap = Math.max(0, (int) Math.ceil(overlapPixels / scale));
        stepX = Math.max(1, tileWidth - Math.min(overlap, tileWidth / 4));
        stepY = Math.max(1, tileHeight - Math.min(overlap, tileHeight / 4));
        columns = count(width, tileWidth, stepX);
        rows = count(height, tileHeight, stepY);
    }

    private static int count(int length, int capacity, int step) {
        return 1 + (int) (((long) length - capacity + step - 1) / step);
    }

    public int size() {
        return Math.multiplyExact(columns, rows);
    }

    public int[] region(int index) {
        if (index < 0 || index >= size()) throw new IndexOutOfBoundsException();
        int x = (int) Math.min((long) (index % columns) * stepX, imageWidth - tileWidth);
        int y = (int) Math.min((long) (index / columns) * stepY, imageHeight - tileHeight);
        return new int[]{x, y, x + tileWidth, y + tileHeight};
    }

    public int sampleSize() {
        int sample = 1;
        while (sample <= 1024 && sample * 2 <= 1f / scale) sample *= 2;
        while ((long) (tileWidth / sample) * (tileHeight / sample) > 4_000_000) sample *= 2;
        return sample;
    }

    public int indexAt(int x, int y) {
        int column = Math.max(0, Math.min(columns - 1, x / stepX));
        int row = Math.max(0, Math.min(rows - 1, y / stepY));
        return row * columns + column;
    }
}
