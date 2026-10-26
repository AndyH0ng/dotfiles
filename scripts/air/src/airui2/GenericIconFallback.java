package airui2;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces Air's generic robot glyph only when Claude Code is the sole custom ACP agent. */
public final class GenericIconFallback {
    private GenericIconFallback() {}

    public static Object genericAgentIcon(Object current, Class<?> caller) {
        try {
            Path config = Path.of(System.getProperty("user.home"),
                "Library/Application Support/JetBrains/Air/acp.json");
            String json = Files.readString(config);
            int start = json.indexOf("\"agent_servers\"");
            int end = start < 0 ? -1 : json.indexOf("\n  }", start);
            if (start < 0 || end < 0) return current;
            String servers = json.substring(start, end);
            Matcher matcher = Pattern.compile("^\\s{4}\"([^\"]*)\"\\s*:", Pattern.MULTILINE).matcher(servers);
            boolean claude = false;
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.isEmpty()) continue;
                if (!name.equalsIgnoreCase("Claude Code")) return current;
                claude = true;
            }
            if (!claude) return current;

            ClassLoader loader = caller.getClassLoader();
            Class<?> keys = Class.forName("fleet.frontend.icons.IconKeys$Agents", true, loader);
            Object instance = keys.getField("INSTANCE").get(null);
            Object icon = keys.getMethod("getClaudeCode").invoke(instance);
            return icon == null ? current : icon;
        } catch (Throwable error) {
            log("generic icon mapping error=" + error);
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
