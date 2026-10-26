package airui2;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

/** Maps only the selected custom Claude ACP agent to Air's native Claude mark. */
public final class SelectedIconFallback2 {
    private static final AtomicInteger LOG_COUNT = new AtomicInteger();

    private SelectedIconFallback2() {}

    public static Object selectedIcon(Object current, Object tree, Class<?> caller) {
        try {
            Method getSelected = tree.getClass().getMethod("getSelected");
            getSelected.setAccessible(true);
            Object selected = getSelected.invoke(tree);
            if (selected == null) return current;
            String id = null;
            for (Method method : selected.getClass().getMethods()) {
                if (method.getName().startsWith("getId") && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    Object value = method.invoke(selected);
                    if (value instanceof String text) { id = text; break; }
                }
            }
            if (LOG_COUNT.getAndIncrement() < 20)
                log("selected icon read; type=" + selected.getClass().getName() + ", id=" + id + ", old=" + current);
            if (!"custom-acp/Claude Code".equals(id)) return current;
            Class<?> keys = Class.forName("fleet.frontend.icons.IconKeys$Agents", true, caller.getClassLoader());
            Object instance = keys.getField("INSTANCE").get(null);
            Object icon = keys.getMethod("getClaudeCode").invoke(instance);
            return icon == null ? current : icon;
        } catch (Throwable error) {
            if (LOG_COUNT.getAndIncrement() < 20) log("selected icon error=" + error);
            return current;
        }
    }

    public static void log(String message) {
        try {
            Files.writeString(Path.of(System.getProperty("user.home"),
                    ".local/share/air-claude-bridge/ui2/icon-patch.log"),
                java.time.Instant.now() + " " + message + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) { }
    }
}
