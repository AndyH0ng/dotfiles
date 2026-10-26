package airui2;

import java.lang.reflect.Method;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Maps the locally configured Claude Code ACP entry to Air's bundled Claude icon provider. */
public final class IconFallback {
    private static final String CLAUDE_ID = "claude-code";

    private IconFallback() {}

    public static Object choose(Object current, String id, Class<?> caller) {
        if (id == null || !id.toLowerCase(Locale.ROOT).contains("claude")) return current;
        if (CLAUDE_ID.equals(id)) return current;
        try {
            ClassLoader loader = caller.getClassLoader();
            Class<?> icons = Class.forName(
                "fleet.ai.frontend.view.input.selector.AgentSelectorIconProviderKt", true, loader);
            for (Method method : icons.getMethods()) {
                if (method.getName().startsWith("obtainAgentSelectorIconProvider-") &&
                    method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class) {
                    Object bundled = method.invoke(null, CLAUDE_ID);
                    return bundled == null ? current : bundled;
                }
            }
        } catch (Throwable error) { log("lookup error=" + error); }
        return current;
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
