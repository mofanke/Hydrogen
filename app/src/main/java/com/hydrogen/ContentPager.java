package com.hydrogen;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

/** 查找当前可见的阅读控件，并按可读高度翻动原生列表。 */
@SuppressWarnings("unused")
public final class ContentPager {
    private ContentPager() {}

    public static boolean isVisible(View view) {
        Rect bounds = new Rect();
        return view != null && view.isShown() && view.getAlpha() > 0
                && view.getGlobalVisibleRect(bounds) && bounds.width() > 0 && bounds.height() > 0;
    }

    public static boolean contains(View root, View view) {
        while (view != null) {
            if (view == root) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    public static boolean isWebView(View view) { return view instanceof WebView; }

    public static boolean isEditing(View root) {
        View focus = root == null ? null : root.findFocus();
        return focus != null && focus.onCheckIsTextEditor();
    }

    public static boolean hasPagingControls(View root) {
        if (!isVisible(root)) return false;
        if (root instanceof Toolbar) {
            Toolbar toolbar = (Toolbar) root;
            if (toolbar.getMenu().findItem(614301) != null && toolbar.getMenu().findItem(614302) != null) return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                if (hasPagingControls(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    public static boolean sameContainer(View first, View second) {
        return first != null && second != null && first.getParent() == second.getParent();
    }

    public static boolean isScrollable(View view) {
        if (view instanceof RecyclerView) {
            RecyclerView.LayoutManager manager = ((RecyclerView) view).getLayoutManager();
            return manager != null && manager.canScrollVertically();
        }
        return view instanceof WebView || view instanceof ScrollView || view instanceof NestedScrollView;
    }

    public static View findScrollable(View root) {
        if (!isVisible(root)) return null;
        View selected = isScrollable(root) ? root : null;
        long area = visibleArea(selected);
        if (root instanceof ViewGroup && !(root instanceof RecyclerView) && !(root instanceof WebView)) {
            ViewGroup group = (ViewGroup) root;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View candidate = findScrollable(group.getChildAt(i));
                long candidateArea = visibleArea(candidate);
                if (candidateArea > area) { selected = candidate; area = candidateArea; }
            }
        }
        return selected;
    }

    private static long visibleArea(View view) {
        Rect rect = new Rect();
        return view != null && view.getGlobalVisibleRect(rect) ? (long) rect.width() * rect.height() : 0;
    }

    public static boolean turn(View view, View bottomOverlay, int direction, int overlapLines) {
        if (!isVisible(view) || !isScrollable(view) || view instanceof WebView) return false;
        int[] insets = ReadingViewport.getInsets(view, null, bottomOverlay, null);
        int readable = insets[2] - Math.max(insets[0], view.getPaddingTop())
                - Math.max(insets[1], view.getPaddingBottom());
        int lineHeight = Math.max(1, Math.round(24 * view.getResources().getDisplayMetrics().density));
        lineHeight = Math.max(lineHeight, visibleLineHeight(view));
        int distance = PageStep.distance(readable, lineHeight, overlapLines);
        if (distance == 0) return false;
        if (view instanceof RecyclerView) ((RecyclerView) view).stopScroll();
        view.scrollBy(0, direction < 0 ? -distance : distance);
        return true;
    }

    private static int visibleLineHeight(View view) {
        if (!isVisible(view)) return 0;
        int height = view instanceof TextView ? ((TextView) view).getLineHeight() : 0;
        if (view instanceof ViewGroup && !(view instanceof WebView)) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) height = Math.max(height, visibleLineHeight(group.getChildAt(i)));
        }
        return height;
    }
}
