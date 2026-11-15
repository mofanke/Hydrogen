"""用 Chromium 检查分页是否完整覆盖文字及页面边界。"""

from pathlib import Path
import os
import unittest

from playwright.sync_api import sync_playwright

SCRIPT = Path(__file__).resolve().parents[1] / "app/src/main/assets/static/js/features/reading-pager.js"


class ReadingPagerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch(executable_path=os.environ.get("PAGER_TEST_CHROMIUM"))

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.page = self.browser.new_page(viewport={"width": 480, "height": 640})

    def tearDown(self):
        self.page.close()

    def fixture(self, font=20, smooth=False, image=False):
        rows = []
        for i in range(100):
            if image and i == 35:
                rows.append('<div style="height:850px;background:#dce8ef">彩色长图</div>')
            height = font * (1.8 if i % 7 == 0 else 1.5)
            rows.append(f'<p data-row="{i}" style="line-height:{height}px;font-size:{font}px">第 {i} 行：墨水屏阅读测试</p>')
        behavior = "smooth" if smooth else "auto"
        self.page.set_content(f'<style>html{{scroll-behavior:{behavior}}}body,p{{margin:0}}.RichText{{padding-top:100px}}</style>'
                              '<main class="RichText">' + "".join(rows) + '</main><input>')
        self.page.add_script_tag(path=str(SCRIPT))
        self.page.evaluate("window.scrollTo({top:0,behavior:'instant'})")

    def read_rows(self, top, bottom):
        return self.page.evaluate("""([top,bottom]) => [...document.querySelectorAll('[data-row]')]
            .filter(p => { const r = p.getBoundingClientRect(); return r.top >= top - 0.01 && r.bottom <= innerHeight - bottom + 0.01; })
            .map(p => Number(p.dataset.row))""", [top, bottom])

    def turn(self, direction, top=0, bottom=100, native_height=640, lines=2):
        return self.page.evaluate("([d,t,b,h,l]) => HydrogenReadingPager.turn(d,t,b,h,l)",
                                  [direction, top, bottom, native_height, lines])

    def walk(self, use_original=False, bottom=100):
        seen = set()
        for _ in range(200):
            before = self.page.evaluate("scrollY")
            top = max(0, 100 - before)
            seen.update(self.read_rows(top, bottom))
            if use_original:
                self.page.evaluate("window.scrollBy(0, innerHeight)")
            else:
                self.turn(1, top=top, bottom=bottom)
            after = self.page.evaluate("scrollY")
            if after == before:
                break
        return seen

    def test_original_full_viewport_scroll_loses_lines(self):
        self.fixture()
        self.assertLess(len(self.walk(use_original=True)), 100)

    def test_all_lines_readable_with_overlay_and_large_fonts(self):
        for font in [16, 20, 32, 48]:
            with self.subTest(font=font):
                self.fixture(font=font)
                self.assertEqual(self.walk(), set(range(100)))

    def test_floating_buttons_and_long_color_image(self):
        self.fixture(image=True)
        self.assertEqual(self.walk(bottom=200), set(range(100)))

    def test_instant_scroll_even_when_page_requests_smooth(self):
        self.fixture(smooth=True)
        self.turn(1)
        self.assertGreater(self.page.evaluate("scrollY"), 0)
        self.assertEqual(self.page.evaluate("document.documentElement.style.scrollBehavior"), "")

    def test_reverse_and_start_end_boundaries(self):
        self.fixture()
        self.assertTrue(self.turn(-1))
        self.assertEqual(self.page.evaluate("scrollY"), 0)
        self.turn(1)
        self.turn(-1)
        self.assertEqual(self.page.evaluate("scrollY"), 0)
        self.walk()
        end = self.page.evaluate("scrollY")
        self.turn(1)
        self.assertEqual(self.page.evaluate("scrollY"), end)
        self.assertIn(99, self.read_rows(0, 100))

    def test_native_css_pixel_conversion(self):
        self.fixture()
        self.turn(1, bottom=200, native_height=1280)
        self.assertEqual(self.page.evaluate("scrollY"), 468)

    def test_overlap_setting_changes_step(self):
        self.fixture()
        self.turn(1, lines=1)
        one_line = self.page.evaluate("scrollY")
        self.page.evaluate("window.scrollTo(0,0)")
        self.turn(1, lines=5)
        self.assertLess(self.page.evaluate("scrollY"), one_line)

    def test_input_and_unlaid_out_view_do_not_scroll(self):
        self.fixture()
        self.page.locator("input").evaluate("e => e.focus({preventScroll:true})")
        self.assertFalse(self.turn(1))
        self.page.locator("input").evaluate("e => e.blur()")
        self.assertFalse(self.turn(1, native_height=0))
        self.assertEqual(self.page.evaluate("scrollY"), 0)

    def test_short_content_stays_at_start(self):
        self.page.set_content('<p class="RichText">短回答</p>')
        self.page.add_script_tag(path=str(SCRIPT))
        self.turn(1)
        self.assertEqual(self.page.evaluate("scrollY"), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
