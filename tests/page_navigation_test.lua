-- tests/page_navigation_test.lua
-- 验证主页、日报、通用正文、返回栈、双栏和弹窗翻页

package.path = "app/src/main/assets/?.lua;" .. package.path
local keys = { KEYCODE_VOLUME_UP = 24, KEYCODE_VOLUME_DOWN = 25, KEYCODE_PAGE_UP = 92, KEYCODE_PAGE_DOWN = 93, ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MULTIPLE = 2 }
local calls, webCalls = {}, {}
local native = {
  isVisible = function(view) return view and view.visible end,
  isWebView = function(view) return view.kind == "web" end,
  isEditing = function(root) return root and root.editing or false end,
  sameContainer = function(a, b) return a.parent == b.parent end,
  findScrollable = function(root) return root and root.target end,
  contains = function(root, view)
    while type(view) == "table" do
      if view == root then return true end
      view = view.parent
    end
    return false
  end,
  turn = function(view, overlay, direction, lines)
    calls[#calls + 1] = { view = view, overlay = overlay, direction = direction, lines = lines }
    return true
  end,
}
import = function() end
View = { VISIBLE = 0 }
Gravity = { LEFT = 3 }
local menuClass = { SHOW_AS_ACTION_ALWAYS = 2, OnMenuItemClickListener = {} }
luajava = {
  bindClass = function(name)
    if name == "com.hydrogen.ContentPager" then return native end
    if name == "android.view.KeyEvent" then return keys end
    if name == "android.view.MenuItem" then return menuClass end
    if name == "java.util.Objects" then return { equals = function(a, b) return a == b end } end
    if name == "org.luajvm.android.host.LuaFragment" then return function(creator) return { callbacks = creator } end end
    return {}
  end,
  createProxy = function(_, callbacks) return callbacks end,
}
Constants = { SharedDataKeys = { EINK_KEY_PAGING = "enabled", EINK_REVERSE_KEYS = "reverse", EINK_OVERLAP_LINES = "lines" } }
local config = { enabled = true, reverse = false, lines = 2 }
Extensions = {
  Class = dofile("app/src/main/assets/extensions/class.lua"),
  Config = {
    getBool = function(key) return config[key] end,
    getNumber = function(key) return config[key] end,
  },
  Account = { getUserId = function() return nil end },
}
AppTheme = { colors = { primary = 1 } }
Helpers = { Static = { materialDrawable = function() return { setTint = function() end } end } }
Router = { resolveParams = function(data) return data end }
activity = {}
VolumeController = { setActive = function(page) VolumeController.activeFragment = page end }
package.loaded["pages.base.edge_to_edge_utils"] = { remove = function() end }
package.loaded["helpers.reading_pager"] = { turn = function(view, direction, options)
  webCalls[#webCalls + 1] = { view = view, direction = direction, complete = options.onComplete }
end }
for _, name in ipairs({ "recommend", "think", "hot", "follow", "daily", "follow_content" }) do
  package.loaded["models.feed." .. name .. "_model"] = {}
end
package.loaded["models.collection.collection_tab_model"] = {}
package.loaded["models.user.user_model"] = {}
local Navigation = require("helpers.page_navigation")
local BaseFragment = require("pages.base.base_fragment")
local Home = dofile("app/src/main/assets/pages/fragment/home/home_fragment.lua")
local home = Home()
home.root_view = { visible = true, parent = "left" }
home.views.drawer = { isDrawerOpen = function() return home.drawerOpen or false end }
home.homePageViews = { view_pager = { currentItem = 0 }, bottom_nav = {} }
home.pageModels.home = {}
home.displayTabs = {}
local function model()
  local view = { kind = "list", visible = true, adapter = { itemCount = 10 }, canScrollVertically = function() return false end }
  local result = { view = view, loads = 0, refreshes = 0 }
  function result:getCurrentRecyclerView() return self.view end
  function result:loadMore() self.loads = self.loads + 1 end
  function result:ensureLoaded() self.refreshes = self.refreshes + 1 end
  return result
end
home.currentPage = "主页"
for i, name in ipairs({ "推荐", "热榜", "想法", "关注" }) do
  home.displayTabs[i] = { name = name }
  home.pageModels.home[name] = model()
  home.homePageViews.view_pager.currentItem = i - 1
  assert(home:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
  assert(calls[#calls].view == home.pageModels.home[name].view, "必须翻当前主页栏目")
  assert(calls[#calls].overlay == home.homePageViews.bottom_nav)
end
for _, entry in ipairs({ { "日报", "daily" }, { "收藏", "collections" }, { "关注", "follow_content" } }) do
  home.currentPage = entry[1]
  home.pageModels[entry[2]] = model()
  assert(home:onReadingKeyDown(keys.KEYCODE_VOLUME_DOWN))
  assert(calls[#calls].view == home.pageModels[entry[2]].view, "抽屉页面必须翻自身列表")
end
local daily = home.pageModels.daily
home.currentPage = "日报"
daily.view.adapter.itemCount = 0
assert(home:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
assert(daily.refreshes == 1, "空列表应先请求首页")
daily.view.adapter.itemCount = 10
local before = daily.loads
assert(home:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
assert(daily.loads == before + 1, "到底时应接入模型原有加载更多")
home.drawerOpen = true
assert(not home:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
home.drawerOpen = false
config.reverse = true
assert(home:onReadingKeyDown(keys.KEYCODE_VOLUME_DOWN))
assert(calls[#calls].direction == -1)
config.enabled = false
assert(not home:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
config.enabled = true
config.reverse = false

Navigation.activate(home)
local Reader = Extensions.Class(BaseFragment)
function Reader:initLayout() self.root_view = { visible = true, parent = "left" } end
function Reader:onResume() self.resumes = (self.resumes or 0) + 1 end
local reader = Reader()
reader.views.webview = { visible = true, kind = "web" }
local fragment = reader:createFragment({})
fragment.callbacks.onCreateView()
fragment.callbacks.onResume()
assert(reader.resumes == 1)
activity.currentFocus = { parent = home.root_view }
assert(Navigation.activePage() == reader, "同栏新正文应优先于底层旧焦点")
assert(reader:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
assert(webCalls[#webCalls].view == reader.views.webview, "日报和通用正文走同一 WebView 分页")
local count = #webCalls
assert(reader:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
assert(#webCalls == count, "未完成时不能重复提交正文分页")
webCalls[#webCalls].complete(100)
fragment.callbacks.onPause()
assert(Navigation.activePage() == home, "返回时恢复仍可见的前页")
fragment.callbacks.onResume()
reader.root_view.parent = "right"
assert(Navigation.activePage() == home, "双栏应尊重左栏焦点")
activity.currentFocus = nil
assert(Navigation.activePage() == reader)
reader.root_view.editing = true
assert(not reader:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN), "文字输入不应触发翻页")
reader.root_view.editing = false
assert(reader:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
fragment.callbacks.onDestroy()
webCalls[#webCalls].complete(100)
assert(Navigation.activePage() == home, "销毁后不能占用按键")
assert(not reader:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))

local leftReader = Reader()
leftReader.views.webview = { visible = true, kind = "web" }
local leftFragment = leftReader:createFragment({})
leftFragment.callbacks.onCreateView()
leftFragment.callbacks.onResume()
local rightReader = Reader()
rightReader.views.webview = { visible = true, kind = "web" }
local rightFragment = rightReader:createFragment({})
rightFragment.callbacks.onCreateView()
rightReader.root_view.parent = "right"
rightFragment.callbacks.onResume()
activity.currentFocus = { parent = home.root_view }
assert(Navigation.activePage() == leftReader, "左栏旧焦点应归属于左栏最上层正文")
leftFragment.callbacks.onPause()
assert(Navigation.activePage() == home)
rightFragment.callbacks.onDestroy()
leftFragment.callbacks.onDestroy()
activity.currentFocus = nil

local menu = { items = {} }
menu.findItem = function(id) return menu.items[id] end
menu.performIdentifierAction = function(id) return menu.items[id].listener.onMenuItemClick() end
menu.add = function(_, id)
  local item = { setIcon = function() end, setShowAsAction = function() end }
  item.setOnMenuItemClickListener = function(callback) item.listener = callback end
  menu.items[id] = item
  return item
end
home.views.toolbar = { menu = menu }
Navigation.installButtons(home)
Navigation.installButtons(home)
local total = 0
for _ in pairs(menu.items) do total = total + 1 end
assert(total == 2, "重复初始化不能增加按钮")
menu.items = {}
Navigation.refreshToolbar(home.views.toolbar)
assert(menu.items[614302].listener.onMenuItemClick())
assert(calls[#calls].view == daily.view, "工具栏按钮应翻当前日报")
local buttonCalls = #calls
assert(Navigation.dispatchKey(keys.KEYCODE_PAGE_DOWN))
assert(#calls == buttonCalls + 1 and calls[#calls].view == daily.view, "按键必须调用当前虚拟按钮")
local originalLoad = daily.loadMore
function daily:loadMore() error("test failure") end
tip = function() end
assert(Navigation.dispatchKey(keys.KEYCODE_PAGE_DOWN), "翻页后的加载失败仍应消费按键")
daily.loadMore = originalLoad
local dialogRoot = { visible = true, target = daily.view }
local dialog = { window = { decorView = dialogRoot }, isShowing = function() return true end }
dialog.setOnKeyListener = function(listener) dialog.listener = listener end
Navigation.bindDialog(dialog, daily)
local callCount = #calls
assert(dialog.listener.onKey(dialog, keys.KEYCODE_PAGE_DOWN, { action = 0, repeatCount = 0 }))
assert(dialog.listener.onKey(dialog, keys.KEYCODE_PAGE_DOWN, { action = 0, repeatCount = 1 }))
assert(#calls == callCount + 1, "长按只翻一次弹窗")
assert(dialog.listener.onKey(dialog, keys.KEYCODE_PAGE_DOWN, { action = 1, repeatCount = 0 }))
dialogRoot.editing = true
assert(not dialog.listener.onKey(dialog, keys.KEYCODE_PAGE_DOWN, { action = 0, repeatCount = 0 }))
assert(not dialog.listener.onKey(dialog, 4, { action = 0, repeatCount = 0 }), "保留返回键")
Navigation.deactivate(home)
print("主页四栏目、日报/收藏/关注列表、正文、返回栈、双栏焦点、按钮重建及弹窗按键契约通过")
