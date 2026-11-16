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
        self.page.goto("about:blank")
        rows = ''.join(f'<p id="p{i}" data-row="{i}">第 {i} 行阅读测试</p>' for i in range(100))
        self.page.set_content(f'<style>body,p{{margin:0}}p{{font-size:{font}px;line-height:{font*1.5}px}}</style><main class="RichText">{extra}{rows}</main>')
        self.page.add_script_tag(path=str(ASSETS/'reading-layout.js'))
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
        self.page.add_script_tag(path=str(ASSETS/'reading-layout.js'))
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
        self.page.evaluate("HydrogenReadingLayout.init(true,2)")
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

    def test_last_page_starts_at_normal_step_and_keeps_blank_space(self):
        self.fixture()
        positions=[0]
        for _ in range(30):
            current=self.turn(1)
            if current==positions[-1]: break
            positions.append(current)
        self.assertEqual(positions,[0,580,1160,1740,2320,2900])
        self.assertEqual(self.page.locator('#p99').bounding_box()['y'],70)
        self.assertEqual(self.turn(-1),2320)
        self.assertEqual(self.turn(1),2900)
    def test_image_moves_as_a_whole_to_next_page(self):
        self.page.set_content('<style>body,figure{margin:0}</style><main class="RichText"><div style="height:500px">前文</div><figure><img id="photo" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22400%22 height=%22300%22/%3E"></figure><p>后文</p></main>')
        self.page.add_script_tag(path=str(ASSETS/'reading-layout.js'))
        self.page.add_script_tag(path=str(ASSETS/'reading-pager.js'))
        self.page.wait_for_function('photo.naturalWidth>0')
        self.page.evaluate('HydrogenReadingLayout.init(true,2)')
        self.assertGreaterEqual(self.page.locator('#photo').bounding_box()['y'],640)
        self.turn(1)
        r=self.page.locator('#photo').bounding_box()
        self.assertGreaterEqual(r['y'],0)
        self.assertLessEqual(r['y']+r['height'],640)
    def test_tall_image_preview_is_complete_and_does_not_chase_pages(self):
        self.page.set_content('<style>body,figure{margin:0}</style><main class="RichText"><div style="height:500px">前文</div><figure><img id="long" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22500%22 height=%225000%22/%3E"></figure><p id="after">后文</p></main>')
        self.page.add_script_tag(path=str(ASSETS/'reading-layout.js'))
        self.page.add_script_tag(path=str(ASSETS/'reading-pager.js'))
        self.page.wait_for_function('document.querySelector("#long").naturalWidth>0')
        self.page.evaluate('HydrogenReadingLayout.init(true,2)')
        self.assertLessEqual(self.page.locator('#long').bounding_box()['height'],608)
        self.turn(1)
        r=self.page.locator('#long').bounding_box()
        self.assertGreaterEqual(r['y'],0)
        self.assertLessEqual(r['y']+r['height'],640)
        self.turn(1)
        self.assertLessEqual(self.page.locator('#long').bounding_box()['y']+r['height'],0.5)
        self.assertGreaterEqual(self.page.locator('#after').bounding_box()['y'],-0.5)
    def test_reload_of_paging_script_keeps_reverse_page_history(self):
        self.fixture()
        first=self.turn(1)
        self.turn(1)
        self.page.add_script_tag(path=str(ASSETS/'reading-pager.js'))
        self.assertEqual(self.turn(-1),first)
    def test_multiple_images_and_captions_are_complete_and_text_is_not_skipped(self):
        self.fixture()
        self.page.evaluate('''() => {
            for (const [row,height] of [[15,300],[35,600],[60,100]]) {
                const figure=document.createElement('figure');
                figure.style.margin='16px 0';
                figure.innerHTML=`<img id="photo${row}" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22400%22 height=%22${height}%22/%3E"><figcaption>图片说明</figcaption>`;
                document.querySelector(`#p${row}`).after(figure);
            }
        }''')
        self.page.wait_for_function('[...document.querySelectorAll("img")].every(i=>i.naturalWidth>0)')
        self.page.evaluate('HydrogenReadingLayout.init(true,2)')
        rows=set()
        images=set()
        positions=[0]
        for _ in range(40):
            rows.update(self.rows())
            for r in self.page.locator('img').evaluate_all('imgs=>imgs.map(e=>{const r=e.getBoundingClientRect();return {top:r.top,bottom:r.bottom}})'):
                if r['bottom']>0.5 and r['top']<639.5:
                    self.assertGreaterEqual(r['top'],-0.5)
                    self.assertLessEqual(r['bottom'],640.5)
            images.update(self.page.evaluate('[...document.querySelectorAll("figure")].filter(e=>{const r=e.getBoundingClientRect();return r.top>=-0.5&&r.bottom<=640.5}).map(e=>e.querySelector("img").id)'))
            position=self.turn(1)
            if position==positions[-1]: break
            positions.append(position)
        self.assertEqual(rows,set(range(100)))
        self.assertEqual(images,{'photo15','photo35','photo60'})
        for previous in reversed(positions[:-1]):
            self.assertEqual(self.turn(-1),previous)
    def test_image_in_paragraph_is_complete(self):
        self.page.set_content('<style>body,p{margin:0}</style><main class="RichText"><div style="height:500px">前文</div><p><img id="photo" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22400%22 height=%22300%22/%3E"></p><p>后文</p></main>')
        self.page.add_script_tag(path=str(ASSETS/'reading-layout.js'))
        self.page.add_script_tag(path=str(ASSETS/'reading-pager.js'))
        self.page.wait_for_function('photo.naturalWidth>0')
        self.page.evaluate('HydrogenReadingLayout.init(true,2)')
        self.turn(1)
        r=self.page.locator('#photo').bounding_box()
        self.assertGreaterEqual(r['y'],0)
        self.assertLessEqual(r['y']+r['height'],640)
    def test_forced_reading_page_without_richtext_has_complete_image(self):
        self.page.set_content('<style>body,figure{margin:0}</style><div style="height:500px">前文</div><figure><img id="photo" class="pin-header-image" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22500%22 height=%225000%22/%3E"></figure><p>后文</p>')
        self.page.add_script_tag(path=str(ASSETS/'reading-layout.js'))
        self.page.add_script_tag(path=str(ASSETS/'reading-pager.js'))
        self.page.wait_for_function('photo.naturalWidth>0')
        self.page.evaluate('HydrogenReadingLayout.init(true,2)')
        self.turn(1)
        r=self.page.locator('#photo').bounding_box()
        self.assertGreaterEqual(r['y'],0)
        self.assertLessEqual(r['y']+r['height'],640)
    def test_narrow_preview_can_open_from_figure_but_links_keep_their_action(self):
        self.fixture(extra='<figure style="margin:0"><img src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22500%22 height=%225000%22/%3E"><figcaption><a href="#">图片来源</a><button>按钮</button></figcaption></figure>')
        self.page.evaluate('window.opened=[];window.HydrogenCore={getPageType:()=>"answer",api:{openImages:i=>opened.push(i)}}')
        self.page.add_script_tag(path=str(ASSETS/'image-viewer.js'))
        self.page.evaluate('ImageViewer.init();HydrogenReadingLayout.init(true,2)')
        self.page.locator('figure').click(position={'x':400,'y':200})
        self.assertEqual(self.page.evaluate('opened.length'),1)
        self.page.locator('a').click()
        self.page.locator('button').click()
        self.assertEqual(self.page.evaluate('opened.length'),1)
    def test_initial_layout_does_not_scroll_past_page_header(self):
        self.fixture()
        self.install_content()
        self.page.evaluate('document.body.style.paddingTop="100px";HydrogenReadingLayout.init(true,2)')
        self.assertEqual(self.page.evaluate('scrollY'),0)

    def test_manual_scroll_during_layout_queue_does_not_jump_back(self):
        self.fixture()
        self.turn(1)
        self.page.evaluate('scrollTo({top:0,behavior:"instant"})')
        self.page.evaluate('()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
        self.assertEqual(self.page.evaluate('scrollY'),0)

if __name__=='__main__': unittest.main(verbosity=2)
