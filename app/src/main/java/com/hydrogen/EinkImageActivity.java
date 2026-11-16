package com.hydrogen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 用当前区域解码和按键分段显示长图与宽图。 */
public class EinkImageActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ReadingKeyDispatcher keyDispatcher = new ReadingKeyDispatcher();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private ArrayList<String> urls;
    private ImageView image;
    private TextView status;
    private Button previous, next, mode;
    private ImageTiles tiles;
    private int imageIndex, tileIndex, sourceWidth, sourceHeight;
    private boolean originalSize, reverseKeys, fallbackPreview;
    private volatile boolean destroyed;
    private volatile int generation;
    private Future<?> job;
    private volatile File sourceFile;
    // 解码器只在串行 worker 中创建、使用和释放。
    private BitmapRegionDecoder decoder;
    private Bitmap preview;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        urls = getIntent().getStringArrayListExtra("images");
        if (urls == null || urls.isEmpty()) { finish(); return; }
        imageIndex = Math.max(0, Math.min(urls.size() - 1, getIntent().getIntExtra("index", 0)));
        reverseKeys = getIntent().getBooleanExtra("reverse", false);
        if (state != null) {
            imageIndex = Math.max(0, Math.min(urls.size() - 1, state.getInt("image")));
            originalSize = state.getBoolean("original");
            tileIndex = state.getInt("tile");
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(dp(8) + insets.getSystemWindowInsetLeft(), dp(8) + insets.getSystemWindowInsetTop(),
                    dp(8) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this);
        status.setTextColor(Color.BLACK);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout row = new LinearLayout(this);
        addButton(row, "返回", this::finish);
        addButton(row, "上一张", () -> openImage(imageIndex - 1, false));
        mode = addButton(row, "原尺寸", () -> {
            if (sourceWidth == 0 || fallbackPreview) return;
            originalSize = !originalSize;
            rebuildTiles(false);
        });
        addButton(row, "下一张", () -> openImage(imageIndex + 1, false));
        addButton(row, "保存", this::saveImage);
        root.addView(row);
        LinearLayout paging = new LinearLayout(this);
        previous = addButton(paging, "◀ 上一段", () -> turn(-1));
        next = addButton(paging, "下一段 ▶", () -> turn(1));
        root.addView(paging);
        setContentView(root);
        image.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) rebuildTiles(false);
        });
        int restoredTile = tileIndex;
        openImage(imageIndex, false);
        tileIndex = restoredTile;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Button addButton(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.BLACK);
        button.setTextSize(14);
        button.setPadding(0, 0, 0, 0);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void openImage(int index, boolean lastTile) {
        if (index < 0 || index >= urls.size() || destroyed) return;
        int request = ++generation;
        if (job != null) job.cancel(true);
        imageIndex = index;
        tileIndex = 0;
        sourceWidth = sourceHeight = 0;
        sourceFile = null;
        tiles = null;
        if (image != null) image.setImageDrawable(null);
        previous.setEnabled(false);
        next.setEnabled(false);
        status.setText("正在加载图片…");
        String url = urls.get(index);
        job = worker.submit(() -> {
            FutureTarget<File> target = null;
            try {
                releaseDecoder();
                target = Glide.with(getApplicationContext()).downloadOnly().load(url).submit();
                File file = target.get();
                if (destroyed || request != generation) return;
                boolean fallback = false;
                try { decoder = BitmapRegionDecoder.newInstance(file.getAbsolutePath(), false); }
                catch (Exception e) { fallback = true; }
                int width, height;
                if (!fallback && decoder != null) {
                    width = decoder.getWidth();
                    height = decoder.getHeight();
                } else {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalStateException();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 1;
                    while ((long) bounds.outWidth / options.inSampleSize * (bounds.outHeight / options.inSampleSize) > 2_000_000) {
                        options.inSampleSize *= 2;
                    }
                    preview = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (preview == null) throw new IllegalStateException();
                    width = preview.getWidth();
                    height = preview.getHeight();
                    fallback = true;
                }
                boolean isPreview = fallback;
                main.post(() -> {
                    if (destroyed || request != generation) return;
                    sourceFile = file;
                    sourceWidth = width;
                    sourceHeight = height;
                    fallbackPreview = isPreview;
                    if (isPreview) originalSize = false;
                    rebuildTiles(lastTile);
                });
            } catch (Exception e) {
                main.post(() -> { if (!destroyed && request == generation) status.setText("图片加载失败，点击下一张或重新打开重试"); });
            } finally {
                if (target != null) {
                    target.cancel(true);
                    FutureTarget<File> completed = target;
                    main.post(() -> Glide.with(getApplicationContext()).clear(completed));
                }
            }
        });
    }

    private void rebuildTiles(boolean lastTile) {
        if (sourceWidth <= 0 || image.getWidth() <= 0 || image.getHeight() <= 0 || destroyed) return;
        int[] anchor = tiles == null ? null : tiles.region(tileIndex);
        tiles = new ImageTiles(sourceWidth, sourceHeight, image.getWidth(), image.getHeight(), originalSize, dp(20));
        if (anchor != null && !lastTile) tileIndex = tiles.indexAt(anchor[0], anchor[1]);
        tileIndex = lastTile ? tiles.size() - 1 : Math.max(0, Math.min(tileIndex, tiles.size() - 1));
        mode.setText(originalSize ? "适应宽度" : "原尺寸");
        mode.setEnabled(!fallbackPreview);
        showTile();
    }

    private void turn(int direction) {
        if (tiles == null || destroyed) return;
        int target = tileIndex + direction;
        if (target < 0) { openImage(imageIndex - 1, true); return; }
        if (target >= tiles.size()) { openImage(imageIndex + 1, false); return; }
        tileIndex = target;
        showTile();
    }

    private void showTile() {
        int request = ++generation;
        if (job != null) job.cancel(true);
        ImageTiles plan = tiles;
        int segment = tileIndex;
        int index = imageIndex;
        String key = index + ":" + originalSize + ":" + plan.tileWidth + ":" + plan.tileHeight + ":"
                + plan.sampleSize() + ":" + segment;
        previous.setEnabled(segment > 0 || index > 0);
        next.setEnabled(segment + 1 < plan.size() || index + 1 < urls.size());
        status.setText("图片 " + (index + 1) + "/" + urls.size() + " · 段 " + (segment + 1) + "/" + plan.size()
                + " · 行 " + (segment / plan.columns + 1) + " 列 " + (segment % plan.columns + 1)
                + (fallbackPreview ? " · 静态预览" : ""));
        job = worker.submit(() -> {
            try {
                Bitmap bitmap = cache.get(key);
                if (bitmap == null) {
                    int[] area = plan.region(segment);
                    if (decoder != null) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = plan.sampleSize();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        bitmap = decoder.decodeRegion(new Rect(area[0], area[1], area[2], area[3]), options);
                    } else if (preview != null) {
                        bitmap = Bitmap.createBitmap(preview, area[0], area[1], area[2] - area[0], area[3] - area[1]);
                    }
                    if (bitmap == null) throw new IllegalStateException();
                    cache.put(key, bitmap);
                }
                Bitmap result = bitmap;
                main.post(() -> { if (!destroyed && request == generation) image.setImageBitmap(result); });
            } catch (Exception e) {
                main.post(() -> { if (!destroyed && request == generation) status.setText("当前图片段解码失败，可重新打开重试"); });
            }
        });
    }

    private void saveImage() {
        if (sourceFile == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_TITLE, "知乎图片-" + (imageIndex + 1));
        startActivityForResult(intent, 1);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        File file = sourceFile;
        Uri destination = data == null ? null : data.getData();
        if (request != 1 || result != RESULT_OK || file == null || destination == null) return;
        worker.execute(() -> {
            try (FileInputStream input = new FileInputStream(file); OutputStream output = getContentResolver().openOutputStream(destination)) {
                if (output == null) throw new IllegalStateException();
                byte[] buffer = new byte[32768];
                int n;
                while ((n = input.read(buffer)) >= 0) output.write(buffer, 0, n);
                main.post(() -> { if (!destroyed) android.widget.Toast.makeText(this, "图片已保存", android.widget.Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) {
                main.post(() -> { if (!destroyed) android.widget.Toast.makeText(this, "保存失败", android.widget.Toast.LENGTH_SHORT).show(); });
            }
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (!ReadingKeyDispatcher.isReadingKey(code)) return super.dispatchKeyEvent(event);
        if (keyDispatcher.dispatch(code, event.getAction(), event.getRepeatCount(), false, true, () -> {
            boolean volume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN;
            boolean up = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_PAGE_UP;
            if (volume && reverseKeys) up = !up;
            Button button = up ? previous : next;
            if (button != null && button.isEnabled()) button.performClick();
            return true;
        })) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onPause() {
        keyDispatcher.clear();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("image", imageIndex);
        state.putInt("tile", tileIndex);
        state.putBoolean("original", originalSize);
    }

    private void releaseDecoder() {
        if (decoder != null) { decoder.recycle(); decoder = null; }
        preview = null;
    }

    @Override protected void onDestroy() {
        destroyed = true;
        ++generation;
        if (job != null) job.cancel(true);
        main.removeCallbacksAndMessages(null);
        if (image != null) image.setImageDrawable(null);
        cache.evictAll();
        worker.execute(() -> { releaseDecoder(); cache.evictAll(); });
        worker.shutdown();
        super.onDestroy();
    }
}
