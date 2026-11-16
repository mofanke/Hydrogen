package com.hydrogen;

import android.view.KeyEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** 将一次实体翻页键序列交给应用处理，隔离控件默认翻页。 */
public final class ReadingKeyDispatcher {
    private final Set<Integer> held = new HashSet<>();

    public static boolean isReadingKey(int code) {
        return code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_PAGE_DOWN;
    }

    public boolean dispatch(int code, int action, int repeats, boolean editing,
                            boolean pagingControls, BooleanSupplier invoke) {
        boolean volume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN;
        boolean page = code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_PAGE_DOWN;
        if (!volume && !page) return false;
        if (action == KeyEvent.ACTION_UP && held.remove(code)) return true;
        if (held.contains(code)) return true;
        if (page && pagingControls && !editing && action == KeyEvent.ACTION_UP) return true;
        if (editing) return false;
        if (page && pagingControls && action == KeyEvent.ACTION_MULTIPLE) {
            invoke.getAsBoolean();
            return true;
        }
        if (action != KeyEvent.ACTION_DOWN || (repeats != 0 && !(page && pagingControls))) return false;
        boolean handled = invoke.getAsBoolean();
        // 页面已提供应用翻页入口，Page 键的失败结果也不触发控件的第二次翻页。
        if (handled || (page && pagingControls)) {
            held.add(code);
            return true;
        }
        return false;
    }

    public void clear() { held.clear(); }
}
