-- tests/reading_key_bridge_fixture.lua
-- 为真实 Java/LuaJVM 桥接提供虚拟按钮和当前页面

package.path = "app/src/main/assets/?.lua;" .. package.path
local keys = { KEYCODE_VOLUME_UP = 24, KEYCODE_VOLUME_DOWN = 25, KEYCODE_PAGE_UP = 92, KEYCODE_PAGE_DOWN = 93 }
luajava = {
  bindClass = function(name)
    if name == "android.view.KeyEvent" then return keys end
    return {}
  end,
}
package.loaded["helpers.reading_pager"] = {}
Constants = { SharedDataKeys = { EINK_KEY_PAGING = "enabled", EINK_REVERSE_KEYS = "reverse" } }
Extensions = { Config = { getBool = function(key) return key == "enabled" end } }
activity = {}
local Navigation = require("helpers.page_navigation")
local offset = 0
local page = { views = { toolbar = { menu = {} } } }
function page:isAlive() return true end
function page:turnReadingPage(direction)
  offset = offset + (direction == "down" and 37 or -37)
  return true
end
local callbacks = {
  [614301] = function() return page:turnReadingPage("up") end,
  [614302] = function() return page:turnReadingPage("down") end,
}
page.views.toolbar.menu.findItem = function(id) return callbacks[id] end
page.views.toolbar.menu.performIdentifierAction = function(id) return callbacks[id]() end
if ActualMenu then page.views.toolbar.menu = ActualMenu end
VolumeController = { activeFragment = page }
function hardware(code) return Navigation.dispatchKey(code) end
function virtual(direction) return callbacks[direction == "up" and 614301 or 614302]() end
function position() return offset end
function reset() offset = 0 end
