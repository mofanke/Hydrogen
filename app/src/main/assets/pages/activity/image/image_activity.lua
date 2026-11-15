-- pages/activity/image/image_activity.lua
-- 将图片列表交给按区域解码的墨水屏查看器

require("init_app")

local Intent = luajava.bindClass("android.content.Intent")
local ArrayList = luajava.bindClass("java.util.ArrayList")
local FrameLayout = luajava.bindClass("android.widget.FrameLayout")
local EinkImageActivity = luajava.bindClass("com.hydrogen.EinkImageActivity")
local BaseActivity = require("pages.base.base_activity")
local ImageActivity = Extensions.Class(BaseActivity)

function ImageActivity:onCreate(params)
  local urls = ArrayList()
  for _, url in ipairs(params.data or {}) do
    local source = url:gsub("_qhd", "_r"):gsub("_fhd", "_r"):gsub("_720w", "_r")
    urls.add(source)
  end
  if urls.isEmpty() then
    tip("没有可显示的图片")
    activity.finish()
    return
  end
  local intent = Intent(activity, EinkImageActivity)
  intent.putStringArrayListExtra("images", urls)
  intent.putExtra("index", math.floor(tonumber(params.index) or 0))
  intent.putExtra("reverse", Extensions.Config.getBool(Constants.SharedDataKeys.EINK_REVERSE_KEYS))
  activity.startActivity(intent)
  activity.finish()
end

function ImageActivity:initLayout()
  self.root_view = FrameLayout(activity)
end

return ImageActivity
