-- helpers/reading_pager.lua
-- 复用屏幕翻页动作并在翻页完成后通知页面

local M = {}
local pagingScript

--- 翻动当前正文
--- @param webview userdata 正文 WebView
--- @param direction string up 或 down
--- @param options table 重叠行数与完成回调
function M.turn(webview, direction, options)
  pagingScript = pagingScript or Helpers.Static.getJSContent("features/reading-pager")
  local lines = math.max(1, math.min(5, options.overlapLines or 2))
  local request = string.format("\nHydrogenReadingPager.turn(%d, %d);",
    direction == "up" and -1 or 1, lines)
  webview.evaluateJavascript(pagingScript .. request, {
    onReceiveValue = function(value)
      if options.onComplete then options.onComplete(tonumber(value) or 0) end
    end,
  })
end

return M
