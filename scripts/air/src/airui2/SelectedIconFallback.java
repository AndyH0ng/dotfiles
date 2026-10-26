package airui2;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Supplies Claude's native gray mark for the closed model selector button. */
public final class SelectedIconFallback {
    private static final java.util.concurrent.atomic.AtomicInteger LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();
    private SelectedIconFallback() {}

    public static Object selectedIcon(Object current, Object tree, Class<?> caller) {
        try {
            Object selected = tree.getClass().getMethod("getSelected").invoke(tree);
            if (selected == null) return current;
            String id = null;
            for (Method method : selected.getClass().getMethods()) {
                if (method.getName().contains("getId") && method.getParameterCount() == 0) {
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
            log("selected icon error=" + error);
            return current;
        }
    }

    private static void log(String message) {
        try {
            Files.writeString(Path.of(System.getProperty("user.home"),
                    ".local/share/air-claude-bridge/ui2/icon-patch.log"),
                java.time.Instant.now() + " " + message + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) { }
    }
}
