-- tests/reading_key_test.lua
-- 验证实体按键方向、设置优先级和异步翻页生命周期

package.path = "app/src/main/assets/?.lua;" .. package.path
local keys = { KEYCODE_VOLUME_UP = 24, KEYCODE_VOLUME_DOWN = 25, KEYCODE_PAGE_UP = 92, KEYCODE_PAGE_DOWN = 93 }
import = function() end
luajava = { bindClass = function(name)
  return name == "android.view.KeyEvent" and keys or {}
end }
View = { VISIBLE = 0 }
Constants = { SharedDataKeys = { EINK_KEY_PAGING = "enabled", EINK_REVERSE_KEYS = "reverse", EINK_OVERLAP_LINES = "lines" } }
local config = { enabled = true, reverse = false, lines = 2 }
Extensions = { Class = function() return {} end, Config = {
  getBool = function(key) return config[key] end,
  getNumber = function(key) return config[key] end,
} }
package.loaded["pages.base.base_fragment"] = {}
package.loaded["models.content.answer_model"] = {}
package.loaded["components.views.web_view_helper"] = {}
local turns = {}
package.loaded["helpers.reading_pager"] = { turn = function(view, direction, options)
  turns[#turns + 1] = { view = view, direction = direction, options = options }
  if options.onComplete then options.onComplete(1) end
end }

local Answer = dofile("app/src/main/assets/pages/fragment/answer/answer_fragment.lua")
local pending = {}
local view = { visibility = 0, post = function(callback) pending[#pending + 1] = callback end }
local page = setmetatable({ alive = true, currentPageIds = { webview = view }, views = {
  appbar = { setExpanded = function(expanded, animate) assert(not expanded and animate) end },
} }, { __index = Answer })
function page:isAlive() return self.alive end
function page:runIfAlive(callback)
  return function(...) if self.alive then callback(...) end end
end
local function flush()
  for _, callback in ipairs(pending) do callback() end
  pending = {}
end
local function expectKey(code, direction)
  assert(page:onReadingKeyDown(code))
  flush()
  assert(turns[#turns].direction == direction)
  assert(turns[#turns].options.overlapLines == 2)
end

expectKey(keys.KEYCODE_VOLUME_DOWN, "down")
expectKey(keys.KEYCODE_VOLUME_UP, "up")
expectKey(keys.KEYCODE_PAGE_DOWN, "down")
expectKey(keys.KEYCODE_PAGE_UP, "up")
config.reverse = true
expectKey(keys.KEYCODE_VOLUME_DOWN, "up")
expectKey(keys.KEYCODE_VOLUME_UP, "down")
expectKey(keys.KEYCODE_PAGE_DOWN, "down")
assert(not page:onReadingKeyDown(4))
config.enabled = false
assert(not page:onReadingKeyDown(keys.KEYCODE_VOLUME_DOWN))
assert(not page:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
config.enabled = true

page.alive = false
assert(not page:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
page.alive = true
view.visibility = 8
assert(not page:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
view.visibility = 0
page.isReadingTurnPending = true
local count = #turns
assert(page:onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
assert(#turns == count, "同一翻页未完成时不能重复提交")
page.isReadingTurnPending = false

package.loaded["init_app"] = true
package.loaded["pages.base.base_activity"] = {}
Helpers = { Resources = { app = { id = { tag_last_time = 1, tag_last_name = 2 } } } }
activity = { currentFocus = nil }
local Main = dofile("app/src/main/assets/pages/activity/main/main_activity.lua")
local host = { runIfAlive = function(_, callback) return callback end }
Main.setupVolumeController(host)
assert(not onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
view.visibility = 0
VolumeController.setActive(page)
assert(onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
flush()
assert(turns[#turns].direction == "down")
VolumeController.setActive(nil)
assert(not onReadingKeyDown(keys.KEYCODE_PAGE_DOWN))
print("按键方向、设置开关、宿主路由、重复按键与页面销毁契约通过")
