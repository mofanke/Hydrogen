import org.luajvm.vm.LuaPlatform;

/** 使用应用的 LuaJVM 验证所有阅读页面的按键路由。 */
public class PageNavigationTest {
    public static void main(String[] args) {
        LuaPlatform.standardGlobals().execute("dofile(\"tests/page_navigation_test.lua\")");
    }
}
