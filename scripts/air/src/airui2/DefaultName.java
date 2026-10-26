package airui2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the live ACP default model label from Air's workspace cache. */
public final class DefaultName {
    private static final Pattern MODEL = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"default\\\"\\s*,\\s*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static volatile String cached;
    private static volatile long checkedAt;

    public static boolean matches(String name) {
        if (name == null) return false;
        long now = System.currentTimeMillis();
        if (now - checkedAt > 5000) refresh(now);
        return name.equals(cached);
    }

    private static synchronized void refresh(long now) {
        if (now - checkedAt <= 5000) return;
        try {
            Path workspace = Path.of(System.getProperty("user.home"),
                "Library/Application Support/JetBrains/Air/localStorage/MULTIPROJECT/workspace");
            try (Stream<Path> files = Files.list(workspace)) {
                Path db = files.filter(path -> path.getFileName().toString().matches("db\\.[0-9.]+\\.json"))
                    .findFirst().orElseThrow();
                Matcher match = MODEL.matcher(Files.readString(db));
                cached = match.find() ? match.group(1) : null;
            }
        } catch (Exception ignored) {
            cached = null;
        }
        checkedAt = now;
    }
}
