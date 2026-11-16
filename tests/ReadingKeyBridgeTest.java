import com.hydrogen.ReadingKeyDispatcher;
import org.luajvm.bind.JavaCall;
import org.luajvm.bind.Coercion;
import org.luajvm.core.Globals;
import org.luajvm.vm.LuaPlatform;

/** 联合真实 Java 按键分发、LuaJVM 桥接与虚拟按钮回调检查一次按键只翻一次。 */
public class ReadingKeyBridgeTest {
    public static final class NativeMenu {
        private final Globals globals;
        public NativeMenu(Globals globals) { this.globals = globals; }
        public boolean findItem(int id) { return id == 614301 || id == 614302; }
        public boolean performIdentifierAction(int id, int flags) {
            return Boolean.TRUE.equals(JavaCall.call(globals.get("virtual"), id == 614301 ? "up" : "down"));
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var globals = LuaPlatform.standardGlobals();
        globals.set("ActualMenu", Coercion.toLua(new NativeMenu(globals)));
        globals.execute("dofile(\"tests/reading_key_bridge_fixture.lua\")");
        ReadingKeyDispatcher dispatcher = new ReadingKeyDispatcher();
        var down = globals.get("hardware");
        int[] nativeDefaults = {0};
        java.util.function.BooleanSupplier invokeDown = () -> Boolean.TRUE.equals(JavaCall.call(down, 93));
        java.util.function.BooleanSupplier invokeUp = () -> Boolean.TRUE.equals(JavaCall.call(down, 92));
        java.util.function.IntSupplier position = () -> ((Number) JavaCall.call(globals.get("position"))).intValue();
        JavaCall.call(globals.get("virtual"), "down");
        int buttonDistance = position.getAsInt();
        require(buttonDistance == 37, "虚拟按钮基准");
        JavaCall.call(globals.get("reset"));
        require(dispatcher.dispatch(93, 0, 0, false, true, invokeDown), "非编辑网页应接管 PageDown");
        require(position.getAsInt() == buttonDistance, "PageDown 必须触发同一个虚拟按钮回调");
        require(dispatcher.dispatch(93, 0, 1, false, true, invokeDown), "消费长按重复");
        require(dispatcher.dispatch(93, 2, 5, false, true, invokeDown), "消费已接管序列中的 MULTIPLE");
        require(position.getAsInt() == buttonDistance, "重复事件不能多翻");
        require(dispatcher.dispatch(93, 1, 0, false, true, invokeDown), "消费 KeyUp");
        require(dispatcher.dispatch(92, 0, 0, false, true, invokeUp), "接管 PageUp");
        require(dispatcher.dispatch(92, 1, 0, false, true, invokeUp), "消费 PageUp KeyUp");
        require(position.getAsInt() == 0, "反向翻页与虚拟按钮一致");
        require(!dispatcher.dispatch(93, 0, 0, true, true, invokeDown), "文字输入保留原生按键");
        require(position.getAsInt() == 0, "输入状态不翻页");
        require(!dispatcher.dispatch(4, 0, 0, false, true, invokeDown), "保留返回键");
        // 首次观测到的事件带 repeatCount 时，也只通过应用按钮翻一屏。
        require(dispatcher.dispatch(93, 0, 3, false, true, invokeDown), "兼容缺失首个 DOWN 的设备事件");
        require(position.getAsInt() == buttonDistance, "设备 repeat 事件不能走原生步长");
        dispatcher.dispatch(93, 1, 0, false, true, invokeDown);
        JavaCall.call(globals.get("reset"));
        require(dispatcher.dispatch(93, 2, 4, false, true, invokeDown), "独立 MULTIPLE 只翻一屏");
        require(position.getAsInt() == buttonDistance, "MULTIPLE 不按 repeatCount 翻多屏");
        // 应用回调已有副作用但返回失败时，仍隔离控件的第二次翻页。
        dispatcher.clear();
        boolean consumed = dispatcher.dispatch(93, 0, 0, false, true, () -> {
            JavaCall.call(globals.get("virtual"), "down");
            return false;
        });
        if (!consumed) nativeDefaults[0]++;
        require(consumed && nativeDefaults[0] == 0, "失败返回不能造成原生二次翻页");
        dispatcher.dispatch(93, 1, 0, false, true, invokeDown);
        require(!dispatcher.dispatch(93, 0, 0, false, false, () -> false), "关闭接管时保留默认行为");
        require(!dispatcher.dispatch(25, 0, 1, false, false, () -> false), "未接管的音量长按保留系统行为");
        System.out.println("Java/LuaJVM/虚拟按钮联合回归通过：方向、UP、repeat、MULTIPLE、输入与原生二次翻页隔离");
    }
}
