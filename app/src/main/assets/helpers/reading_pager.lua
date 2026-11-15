-- helpers/reading_pager.lua
-- 按可读高度翻页，避让原生悬浮视图并保留重叠文字

local ReadingViewport = luajava.bindClass("com.hydrogen.ReadingViewport")
local M = {}
local pagingScript

--- 翻动 WebView 当前正文
--- @param webview userdata 正文 WebView
--- @param direction string up 或 down
--- @param options table 遮挡视图与重叠行数
function M.turn(webview, direction, options)
  pagingScript = pagingScript or Helpers.Static.getJSContent("features/reading-pager")
  local insets = ReadingViewport.getInsets(webview, options.topOverlay,
    options.bottomOverlay, options.buttonsOverlay)
  local lines = math.max(1, math.min(5, options.overlapLines or 2))
  local request = string.format("\nHydrogenReadingPager.turn(%d, %d, %d, %d, %d);",
    direction == "up" and -1 or 1, insets[0], insets[1], insets[2], lines)
  webview.evaluateJavascript(pagingScript .. request, nil)
end

return M
