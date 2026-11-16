// 虚拟按钮与实体键共用滚动起点，图片整体落页，末页保持留白。
(() => {
    if (window.HydrogenReadingPager) return;
    let context = null, history = [], index = 0, expected = 0, layoutRevision = -1;
    window.HydrogenReadingPager = {
        turn(direction, overlapLines = 2) {
            const active = document.activeElement;
            if (active && (active.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))) return scrollY;
            const layout = window.HydrogenReadingLayout;
            layout.init(true,overlapLines);
            const content = document.querySelector('.RichText, .ztext, .content-wrap') || document.body;
            let current = scrollY;
            const rev = layout.revision();
            if (content !== context || Math.abs(current - expected) > 2 || rev !== layoutRevision) {
                context = content; history = [current]; index = 0;
            }
            const page = layout.prepare(current);
            const end = layout.contentEnd();
            let target;
            if (direction > 0) {
                if (current + page.height >= end - 0.5) { expected = current; layoutRevision = rev; return current; }
                target = index + 1 < history.length ? history[index + 1] : current + page.step;
                const large = page.pending.find(p => p.height > page.height - page.overlap);
                if (large) target = Math.max(target,Math.floor(large.top));
            } else {
                target = index > 0 ? history[index - 1] : Math.max(0,current - page.step);
            }
            // 起点不能停在图片中间；向前时只跳过本页已完整显示的图片。
            for (const block of layout.pictures()) {
                const r = block.getBoundingClientRect();
                const top = r.top + current, bottom = r.bottom + current;
                if (target > top + 0.5 && target < bottom - 0.5) {
                    if (direction < 0) target = Math.max(0,Math.floor(top));
                    else if (top >= current - 0.5 && bottom <= current + page.height + 0.5) target = Math.ceil(bottom);
                }
            }
            target = Math.max(0,target);
            layout.prepare(target);
            window.scrollBy({top:target-current,left:0,behavior:'instant'});
            const result = scrollY;
            if (Math.abs(result - current) > 0.5) {
                if (direction < 0 && index > 0) index--;
                else if (direction > 0 && index + 1 < history.length) index++;
                else if (direction > 0) { history = history.slice(0,index+1);history.push(result);index++; }
            }
            expected = result; layoutRevision = layout.revision();
            layout.capture();
            return result;
        }
    };
})();
