// 按可读视口翻页，原生像素经视口比例换算为 CSS 像素。
(() => {
    window.HydrogenReadingPager = {
        turn(direction, nativeTop, nativeBottom, nativeHeight, overlapLines) {
            const active = document.activeElement;
            if (active && (active.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))) {
                return false;
            }
            const scroller = document.scrollingElement;
            const height = window.innerHeight;
            if (!scroller || nativeHeight <= 0 || height <= 0) return false;

            const ratio = height / nativeHeight;
            const top = Math.max(0, nativeTop * ratio);
            const bottom = Math.max(0, nativeBottom * ratio);
            const readableHeight = height - top - bottom;
            if (readableHeight <= 1) return false;

            const content = document.querySelector('.RichText, .ztext') || document.body;
            const candidates = [content, ...content.querySelectorAll('p, li, pre, h1, h2, h3, h4, blockquote')];
            let lineHeight = 24;
            for (const element of candidates) {
                const rect = element.getBoundingClientRect();
                if (rect.bottom <= top || rect.top >= height - bottom) continue;
                const style = getComputedStyle(element);
                const measured = parseFloat(style.lineHeight) || (parseFloat(style.fontSize) || 16) * 1.5;
                lineHeight = Math.max(lineHeight, measured);
            }
            const lines = Math.max(1, Math.min(5, Number(overlapLines) || 2));
            const overlap = Math.min(readableHeight - 1, Math.ceil(lineHeight * lines));
            const distance = Math.max(1, Math.floor(readableHeight - overlap));

            // 为正文末尾留出悬浮栏避让空间，让最后一行也能滚到可读区域。
            if (document.body) {
                const padding = parseFloat(getComputedStyle(document.body).paddingBottom) || 0;
                if (padding < bottom + lineHeight) {
                    document.body.style.paddingBottom = `${Math.ceil(bottom + lineHeight)}px`;
                }
            }
            const maxScroll = Math.max(0, scroller.scrollHeight - height);
            const target = Math.max(0, Math.min(maxScroll, scroller.scrollTop + direction * distance));
            // 翻页同步落位，页面的 smooth 样式不参与墨水屏翻页。
            const behavior = scroller.style.scrollBehavior;
            scroller.style.scrollBehavior = 'auto';
            window.scrollTo({ left: window.scrollX, top: target, behavior: 'instant' });
            scroller.style.scrollBehavior = behavior;
            return true;
        }
    };
})();
