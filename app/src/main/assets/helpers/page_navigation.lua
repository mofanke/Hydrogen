-- helpers/page_navigation.lua
-- 统一阅读页面的按键、列表翻页、工具栏按钮和页面切换

local ContentPager = luajava.bindClass("com.hydrogen.ContentPager")
local KeyEvent = luajava.bindClass("android.view.KeyEvent")
local MenuItem = luajava.bindClass("android.view.MenuItem")
local Objects = luajava.bindClass("java.util.Objects")
local OnKeyListener = luajava.bindClass("android.content.DialogInterface$OnKeyListener")
local ReadingPager = require("helpers.reading_pager")
local M = {}
local pages = {}
local toolbarOwners = setmetatable({}, { __mode = "k" })
local PREVIOUS_ID, NEXT_ID = 614301, 614302

--- 将实体按键转换为阅读方向
--- @param code number Android 按键码
--- @return string|nil up 或 down
function M.direction(code)
  if not Extensions.Config.getBool(Constants.SharedDataKeys.EINK_KEY_PAGING) then return nil end
  if code == KeyEvent.KEYCODE_PAGE_UP then return "up" end
  if code == KeyEvent.KEYCODE_PAGE_DOWN then return "down" end
  if code ~= KeyEvent.KEYCODE_VOLUME_UP and code ~= KeyEvent.KEYCODE_VOLUME_DOWN then return nil end
  local isUp = code == KeyEvent.KEYCODE_VOLUME_UP
  if Extensions.Config.getBool(Constants.SharedDataKeys.EINK_REVERSE_KEYS) then isUp = not isUp end
  return isUp and "up" or "down"
end

--- 按当前前台页面与焦点选出阅读页面
--- @return table|nil 页面
function M.activePage()
  local focus = activity.currentFocus
  local newest
  for i = #pages, 1, -1 do
    local page = pages[i]
    if page:isAlive() and ContentPager.isVisible(page.root_view) then
      newest = newest or page
      if focus and ContentPager.contains(page.root_view, focus) then
        for j = #pages, 1, -1 do
          local front = pages[j]
          if front:isAlive() and ContentPager.isVisible(front.root_view)
            and ContentPager.sameContainer(front.root_view, page.root_view) then return front end
        end
        return page
      end
    end
  end
  return newest
end

--- 触发页面现有的虚拟翻页按钮
--- @param page table 页面
--- @param direction string up 或 down
--- @return boolean 是否消费翻页
function M.pressButton(page, direction)
  if not page or not page:isAlive() then return false end
  local toolbar = page.views and page.views.toolbar
  local id = direction == "up" and PREVIOUS_ID or NEXT_ID
  if toolbar and toolbar.menu.findItem(id) then
    toolbar.menu.performIdentifierAction(id, 0)
    return true
  end
  return page:turnReadingPage(direction)
end

--- 实体按键触发当前页面的虚拟翻页按钮
--- @param code number Android 按键码
--- @return boolean 是否消费按键
function M.dispatchKey(code)
  local direction = M.direction(code)
  if not direction then return false end
  local page = M.activePage() or (_G.VolumeController and _G.VolumeController.activeFragment)
  return M.pressButton(page, direction)
end

--- 注册当前可见页面
--- @param page table 页面
function M.activate(page)
  M.deactivate(page)
  pages[#pages + 1] = page
  if _G.VolumeController then _G.VolumeController.setActive(page) end
  M.installButtons(page)
end

--- 移除暂停或销毁的页面，并恢复上一阅读页面
--- @param page table 页面
function M.deactivate(page)
  for i = #pages, 1, -1 do
    if pages[i] == page then table.remove(pages, i) end
  end
  if _G.VolumeController and _G.VolumeController.activeFragment == page then
    _G.VolumeController.setActive(M.activePage())
  end
end

--- 获取当前列表或正文，排除横向分页控件和隐藏栏目
--- @param page table 页面
--- @return userdata|nil 阅读视图
function M.readingView(page)
  local model = page:getReadingModel()
  if model and model.getCurrentRecyclerView then
    local view = model:getCurrentRecyclerView()
    if ContentPager.isVisible(view) then return view end
  end
  local view = page.webView or page.views.webview or page.views.recycler_view
  if ContentPager.isVisible(view) then return view end
  return ContentPager.findScrollable(page.root_view)
end

--- 使用当前阅读控件翻页，列表末尾按模型原有规则加载更多
--- @param page table 页面
--- @param direction string up 或 down
--- @return boolean 是否消费翻页
function M.turn(page, direction)
  if not page:isAlive() or ContentPager.isEditing(page.root_view) then return false end
  local view = page:getReadingView()
  if not view then return false end
  local lines = Extensions.Config.getNumber(Constants.SharedDataKeys.EINK_OVERLAP_LINES, 2)
  if ContentPager.isWebView(view) then
    if page.isReadingTurnPending then return true end
    page.isReadingTurnPending = true
    ReadingPager.turn(view, direction, {
      overlapLines = lines,
      onComplete = page:runIfAlive(function() page.isReadingTurnPending = false end),
    })
    return true
  end
  if not ContentPager.turn(view, page:getReadingBottomOverlay(), direction == "up" and -1 or 1, lines) then return false end
  local model = page:getReadingModel()
  if direction == "down" and not view.canScrollVertically(1) and model and model.loadMore
    and model.getCurrentRecyclerView and Objects.equals(model:getCurrentRecyclerView(), view) then
    local adapter = view.adapter
    local ok = pcall(function()
      if adapter and adapter.itemCount == 0 and model.ensureLoaded then model:ensureLoaded()
       else model:loadMore() end
    end)
    if not ok then tip("加载更多失败，请重试") end
  end
  return true
end

--- 为页面工具栏添加共用翻页按钮
--- @param page table 页面
function M.installButtons(page)
  local toolbar = page.views and page.views.toolbar
  if not toolbar then return end
  toolbarOwners[toolbar] = page
  local menu = toolbar.menu
  for _, item in ipairs({
    { id = PREVIOUS_ID, title = "上一屏", icon = "twotone_keyboard_arrow_up", direction = "up" },
    { id = NEXT_ID, title = "下一屏", icon = "twotone_keyboard_arrow_down", direction = "down" },
  }) do
    if not menu.findItem(item.id) then
      local button = menu.add(0, item.id, 0, item.title)
      local icon = Helpers.Static.materialDrawable(item.icon, 24)
      icon.setTint(AppTheme.colors.primary)
      button.setIcon(icon)
      button.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      button.setOnMenuItemClickListener(luajava.createProxy(MenuItem.OnMenuItemClickListener, {
        onMenuItemClick = page:runIfAlive(function() return page:turnReadingPage(item.direction) end),
      }))
    end
  end
end

--- 在工具栏菜单重建后保留翻页入口
--- @param toolbar userdata 页面工具栏
function M.refreshToolbar(toolbar)
  local page = toolbarOwners[toolbar]
  if page and page:isAlive() then M.installButtons(page) end
end

--- 为评论与内容弹窗绑定翻页，不拦截文字输入
--- @param dialog userdata 对话框
--- @param model table|nil 列表模型
function M.bindDialog(dialog, model)
  local page = { views = {} }
  function page:isAlive()
    return dialog.isShowing() and (not model or not model.isAlive or model:isAlive())
  end
  function page:runIfAlive(callback)
    return function(...) if self:isAlive() then return callback(...) end end
  end
  function page:getReadingView() return ContentPager.findScrollable(dialog.window.decorView) end
  function page:getReadingModel() return model end
  function page:getReadingBottomOverlay() end
  local held = {}
  dialog.setOnKeyListener(luajava.createProxy(OnKeyListener, {
    onKey = function(_, code, event)
      if event.action == KeyEvent.ACTION_UP and held[code] then held[code] = nil return true end
      local direction = M.direction(code)
      if not direction then return false end
      if held[code] then return true end
      if event.action ~= KeyEvent.ACTION_DOWN and event.action ~= KeyEvent.ACTION_MULTIPLE then return false end
      page.root_view = dialog.window.decorView
      if M.turn(page, direction) then
        if event.action == KeyEvent.ACTION_DOWN then held[code] = true end
        return true
      end
      return false
    end,
  }))
end

return M
