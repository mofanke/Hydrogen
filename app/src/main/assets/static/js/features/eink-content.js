// 正文图片按比例排版，文末显示操作入口，并在图片排版后保持阅读锚点。
(() => {
    if (window.HydrogenEinkContent) return;
    const style = document.createElement('style');
    style.textContent = `
        html, body { overflow-anchor: none; }
        .RichText img, .ztext img { max-width:100% !important; width:auto !important; height:auto !important; max-height:none !important; object-fit:contain; }
        .RichText figure, .ztext figure { max-width:100%; }
        #hydrogen-reading-footer { display:flex; gap:16px; justify-content:center; padding:28px 8px 60px; clear:both; }
        #hydrogen-reading-footer button { font:inherit; padding:10px 20px; background:transparent; color:inherit; border:1px solid currentColor; border-radius:8px; }
    `;
    document.head.appendChild(style);
    let anchor = null;
    let pending = false;
    const tracked = new WeakSet();
    const content = () => document.querySelector('.RichText, .ztext');
    function capture() {
        const root = content();
        if (!root) return;
        const rect = root.getBoundingClientRect();
        const x = Math.max(12, rect.left + 12);
        const y = Math.max(24, Math.min(innerHeight - 24, rect.top + 24));
        const range = document.caretRangeFromPoint && document.caretRangeFromPoint(x, y);
        if (range && range.startContainer.nodeType === 3 && root.contains(range.startContainer) && range.getBoundingClientRect().height > 0) {
            const box = range.getBoundingClientRect();
            anchor = { range, top:box.top };
        } else {
            const node = [...root.querySelectorAll('p, img, pre, li')].find(e => e.getBoundingClientRect().bottom > 24);
            anchor = node ? { node, top:node.getBoundingClientRect().top } : null;
        }
    }
    function restore() {
        if (!anchor) return;
        const node = anchor.node || anchor.range.startContainer;
        if (!node.isConnected) return;
        const rect = anchor.node ? anchor.node.getBoundingClientRect() : anchor.range.getBoundingClientRect();
        const delta = rect.top - anchor.top;
        if (Math.abs(delta) > 0.5) window.scrollBy({ top:delta, behavior:'instant' });
    }
    function refresh() {
        const root = content();
        if (!root) return;
        for (const img of root.querySelectorAll('img')) {
            if (tracked.has(img)) continue;
            tracked.add(img);
            const width = Number(img.getAttribute('data-rawwidth') || img.getAttribute('width'));
            const height = Number(img.getAttribute('data-rawheight') || img.getAttribute('height'));
            if (width > 0 && height > 0) img.style.aspectRatio = `${width}/${height}`;
            img.addEventListener('load', schedule);
            if (resize) resize.observe(img);
        }
        if (!document.getElementById('hydrogen-reading-footer')) {
            const footer = document.createElement('div');
            footer.id = 'hydrogen-reading-footer';
            for (const [label, action] of [['评论','readingComment'], ['收藏','readingCollect']]) {
                const button = document.createElement('button');
                button.textContent = label;
                button.onclick = () => HydrogenBridge.execute(JSON.stringify({action:'message',data:JSON.stringify({action})}));
                footer.appendChild(button);
            }
            root.appendChild(footer);
        }
        restore();
    }
    function schedule() {
        if (pending) return;
        pending = true;
        requestAnimationFrame(() => {
            refresh();
            pending = false;
            capture();
        });
    }
    const resize = window.ResizeObserver ? new ResizeObserver(schedule) : null;
    const observer = new MutationObserver(schedule);
    observer.observe(document.body, {childList:true, subtree:true});
    window.addEventListener('scroll', () => { if (!pending) capture(); }, {passive:true});
    window.addEventListener('resize', schedule);
    window.HydrogenEinkContent = { capture, refresh };
    refresh();
    capture();
})();
