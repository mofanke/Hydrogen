import org.luajvm.vm.LuaPlatform;

/** 在 APK 使用的 LuaJVM 引擎中执行按键与页面生命周期契约。 */
public class ReadingKeyTest {
    public static void main(String[] args) {
        var globals = LuaPlatform.standardGlobals();
        globals.execute("dofile(\"tests/reading_key_test.lua\")");
    }
}
