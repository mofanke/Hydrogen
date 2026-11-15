// 屏幕按钮与实体键共用一屏滚动，保留相邻页文字重叠。
(() => {
    window.HydrogenReadingPager = {
        turn(direction, overlapLines = 2) {
            const active = document.activeElement;
            if (active && (active.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))) return window.scrollY;
            const viewport = window.visualViewport;
            const height = Math.min(window.innerHeight, viewport ? viewport.height : window.innerHeight);
            const content = document.querySelector('.RichText, .ztext') || document.body;
            let lineHeight = 24;
            for (const element of [content, ...content.querySelectorAll('p, li, pre, h1, h2, h3, blockquote')]) {
                const rect = element.getBoundingClientRect();
                if (rect.bottom <= 0 || rect.top >= height) continue;
                const style = getComputedStyle(element);
                lineHeight = Math.max(lineHeight, parseFloat(style.lineHeight) || (parseFloat(style.fontSize) || 16) * 1.5);
            }
            const lines = Math.max(1, Math.min(5, Number(overlapLines) || 2));
            const distance = Math.max(1, Math.floor(height - Math.ceil(lineHeight * lines)));
            // 保持屏幕按钮的 scrollBy 调用，文字重叠和可视视口限定步长。
            window.scrollBy({ top: direction * distance, left: 0, behavior: 'instant' });
            return window.scrollY;
        }
    };
})();
