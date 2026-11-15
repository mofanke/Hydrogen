"""验证原按钮滚动基准、可视视口、图片排版稳定与文末操作。"""
from pathlib import Path
import os
import unittest
from playwright.sync_api import sync_playwright

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets/static/js/features'

class ReadingPagerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.driver = sync_playwright().start()
        cls.browser = cls.driver.chromium.launch(executable_path=os.environ.get('PAGER_TEST_CHROMIUM'))
    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.driver.stop()
    def setUp(self):
        self.page = self.browser.new_page(viewport={'width':480,'height':640})
    def tearDown(self):
        self.page.close()
    def fixture(self, font=20, extra=''):
        rows = ''.join(f'<p id="p{i}" data-row="{i}">第 {i} 行阅读测试</p>' for i in range(100))
        self.page.set_content(f'<style>body,p{{margin:0}}p{{font-size:{font}px;line-height:{font*1.5}px}}</style><main class="RichText">{extra}{rows}</main>')
        self.page.add_script_tag(path=str(ASSETS/'reading-pager.js'))
        self.page.evaluate("scrollTo({top:0,behavior:'instant'})")
    def turn(self, direction, lines=2):
        return self.page.evaluate('([d,l])=>HydrogenReadingPager.turn(d,l)',[direction,lines])
    def rows(self):
        return self.page.evaluate("[...document.querySelectorAll('[data-row]')].filter(p=>{const r=p.getBoundingClientRect();return r.top>=0&&r.bottom<=visualViewport.height}).map(p=>Number(p.dataset.row))")
    def test_all_lines_visible_at_multiple_font_sizes(self):
        for font in [16,20,32,48]:
            with self.subTest(font=font):
                self.fixture(font)
                seen=set()
                for _ in range(200):
                    seen.update(self.rows())
                    before=self.page.evaluate('scrollY')
                    if self.turn(1)==before: break
                self.assertEqual(seen,set(range(100)))
    def test_same_scrollby_baseline_with_small_overlap(self):
        self.fixture()
        self.assertEqual(self.turn(1),640-60)
        self.turn(-1)
        self.assertEqual(self.page.evaluate('scrollY'),0)
    def test_layout_viewport_larger_than_visible_screen(self):
        self.fixture()
        self.page.evaluate("Object.defineProperty(window,'innerHeight',{value:1600,configurable:true})")
        self.assertLessEqual(self.turn(1),640-60)
    def test_real_page_zoom_uses_visual_viewport(self):
        self.fixture()
        session=self.page.context.new_cdp_session(self.page)
        session.send('Emulation.setPageScaleFactor',{'pageScaleFactor':2})
        visible=self.page.evaluate('visualViewport.height')
        self.assertLess(visible,640)
        self.assertLessEqual(self.turn(1),visible-60)
    def test_short_and_end_boundaries(self):
        self.page.set_content('<p class="RichText">短回答</p>')
        self.page.add_script_tag(path=str(ASSETS/'reading-pager.js'))
        self.assertEqual(self.turn(1),0)
        self.fixture()
        for _ in range(30): self.turn(1)
        end=self.page.evaluate('scrollY')
        self.assertEqual(self.turn(1),end)
        self.assertIn(99,self.rows())
    def test_smooth_style_does_not_animate(self):
        self.fixture()
        self.page.evaluate("document.documentElement.style.scrollBehavior='smooth'")
        self.assertGreater(self.turn(1),0)
    def test_input_does_not_turn(self):
        self.fixture(extra='<input>')
        self.page.locator('input').evaluate('e=>e.focus({preventScroll:true})')
        self.assertEqual(self.turn(1),0)
    def test_overlap_setting(self):
        self.fixture()
        first=self.turn(1,1)
        self.page.evaluate('scrollTo(0,0)')
        self.assertLess(self.turn(1,5),first)
    def install_content(self):
        self.page.evaluate("window.messages=[];window.HydrogenBridge={execute:s=>messages.push(JSON.parse(s))}")
        self.page.add_script_tag(path=str(ASSETS/'eink-content.js'))
    def test_footer_is_at_end_and_comment_dispatches(self):
        self.fixture()
        self.install_content()
        footer=self.page.locator('#hydrogen-reading-footer')
        self.assertEqual(footer.count(),1)
        self.assertGreater(footer.bounding_box()['y'],2500)
        self.assertEqual(footer.evaluate('e=>getComputedStyle(e).position'),'static')
        footer.get_by_text('评论',exact=True).click()
        self.assertEqual(self.page.evaluate('JSON.parse(messages[0].data).action'),'readingComment')
    def test_images_keep_ratio_and_do_not_stretch(self):
        self.fixture(extra='<img id="test-image" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%221000%22 height=%222000%22/%3E">')
        self.install_content()
        self.page.wait_for_function('document.querySelector("img").naturalWidth>0')
        size=self.page.locator('img').bounding_box()
        self.assertLessEqual(size['width'],480)
        self.assertAlmostEqual(size['height']/size['width'],2,places=2)
    def test_late_image_above_keeps_reading_position(self):
        self.fixture(extra='<img id="late">')
        self.install_content()
        self.page.evaluate('scrollTo(0,900)')
        self.page.evaluate('()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
        old=self.page.locator('#p35').bounding_box()['y']
        self.page.evaluate("document.querySelector('#late').src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%221000%22 height=%221400%22/%3E'")
        self.page.wait_for_function('document.querySelector("#late").naturalWidth>0')
        self.page.evaluate('()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
        self.assertAlmostEqual(self.page.locator('#p35').bounding_box()['y'],old,delta=2)

if __name__=='__main__': unittest.main(verbosity=2)
