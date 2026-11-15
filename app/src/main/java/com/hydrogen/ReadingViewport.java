package com.hydrogen;

import android.graphics.Rect;
import android.view.View;

/** 将原生视图遮挡换算为 WebView 顶部、底部的不可读像素。 */
@SuppressWarnings("unused")
public final class ReadingViewport {
    private ReadingViewport() {}

    public static int[] getInsets(View webView, View topOverlay, View bottomOverlay,
                                  View buttonsOverlay) {
        int height = webView.getHeight();
        int[] location = new int[2];
        webView.getLocationOnScreen(location);
        int[] rootLocation = new int[2];
        webView.getRootView().getLocationOnScreen(rootLocation);
        location[0] -= rootLocation[0];
        location[1] -= rootLocation[1];
        Rect visible = new Rect();
        if (height <= 0 || !webView.getGlobalVisibleRect(visible)) {
            return new int[]{0, 0, 0};
        }
        int top = Math.max(0, visible.top - location[1]);
        int bottom = Math.max(0, location[1] + height - visible.bottom);
        Rect overlay = new Rect();
        if (intersects(topOverlay, visible, overlay)) {
            top = Math.max(top, overlay.bottom - location[1]);
        }
        if (intersects(bottomOverlay, visible, overlay)) {
            bottom = Math.max(bottom, location[1] + height - overlay.top);
        }
        if (intersects(buttonsOverlay, visible, overlay)) {
            bottom = Math.max(bottom, location[1] + height - overlay.top);
        }
        return new int[]{Math.min(top, height), Math.min(bottom, height), height};
    }

    private static boolean intersects(View view, Rect visible, Rect overlay) {
        return view != null && view.isShown() && view.getAlpha() > 0
                && view.getGlobalVisibleRect(overlay) && Rect.intersects(visible, overlay);
    }
}
