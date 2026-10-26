package airui2;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Bootstrap-visible text formatter. No app dependencies or credentials. */
public final class UsageText {
    private static final Path FILE = Path.of(System.getProperty("user.home"),
        ".local/share/air-claude-bridge/usage.properties");
    private static final DateTimeFormatter RESET = DateTimeFormatter.ofPattern("MM/dd HH:mm")
        .withZone(ZoneId.systemDefault());

    public static String decorate(String original) {
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Properties values = new Properties();
            values.load(reader);
            List<String> rows = new ArrayList<>();
            add(rows, values, "five_hour", "5h");
            add(rows, values, "seven_day", "1w");
            if (rows.isEmpty()) return original;
            return original + "\n\nSubscription usage\n" + String.join("\n", rows);
        } catch (Exception ignored) {
            return original;
        }
    }

    public static void render(Object context, Class<?> anchor) {
        String text = decorate("");
        if (text.isEmpty()) return;
        boolean entered = false;
        java.lang.reflect.Method exit = null;
        try {
            ClassLoader loader = anchor.getClassLoader();
            Properties values = new Properties();
            try (Reader reader = Files.newBufferedReader(FILE)) { values.load(reader); }
            Class<?> runtime = Class.forName("noria.impl.RuntimeKt", true, loader);
            Class<?> contextType = Class.forName("noria.NoriaContext", true, loader);
            exit = runtime.getMethod("RTexitScope", contextType);
            runtime.getMethod("RTenterScope", contextType, int.class).invoke(null, context, 904173);
            entered = true;
            Class<?> textClass = Class.forName("fleet.compose.theme.components.TextKt", true, loader);
            java.lang.reflect.Method uiText = java.util.Arrays.stream(textClass.getMethods())
                .filter(m -> m.getName().startsWith("UiText-") && m.getName().endsWith("$default") &&
                    m.getParameterCount() == 13 && m.getParameterTypes()[0] == String.class)
                .findFirst().orElseThrow();
            Class<?> progressClass = Class.forName("fleet.compose.theme.components.progressIndicators.ProgressBarKt", true, loader);
            java.lang.reflect.Method progress = null;
            for (java.lang.reflect.Method method : progressClass.getMethods()) {
                if (method.getName().equals("ProgressBarDeterminate$default") && method.getParameterCount() == 8) {
                    progress = method;
                    break;
                }
            }
            if (progress == null) throw new NoSuchMethodException("ProgressBarDeterminate$default");
            Class<?> styles = Class.forName("fleet.compose.theme.components.progressIndicators.ProgressBarStyle", true, loader);
            Class<?> modifierType = Class.forName("androidx.compose.ui.Modifier", true, loader);
            Object modifier = modifierType.getField("Companion").get(null);
            java.lang.reflect.Method height = Class.forName("androidx.compose.foundation.layout.SizeKt", true, loader)
                .getMethod("height-3ABfNKs", modifierType, float.class);
            java.lang.reflect.Method spacer = Class.forName("androidx.compose.foundation.layout.SpacerKt", true, loader)
                .getMethod("Spacer", modifierType, contextType);
            String[] keys = {"five_hour", "seven_day"};
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                String used = values.getProperty(key + ".used");
                String reset = values.getProperty(key + ".reset");
                if (used == null || reset == null) continue;
                float percentage = Float.parseFloat(used);
                Instant until = Instant.ofEpochSecond((long) Double.parseDouble(reset));
                if (!Float.isFinite(percentage) || percentage < 0 || percentage > 100 || !until.isAfter(Instant.now())) continue;
                runtime.getMethod("RTenterScope", contextType, int.class).invoke(null, context, 906174 + i);
                try { spacer.invoke(null, height.invoke(null, modifier, 6f), context); }
                finally { exit.invoke(null, context); }
                runtime.getMethod("RTenterScope", contextType, int.class).invoke(null, context, 904174 + i);
                try {
                    UsageRow.render(context, anchor, String.format(java.util.Locale.ROOT, "%.0f%%", percentage),
                        i == 0 ? " of 5h cycle" : " of 1w cycle", "resets " + RESET.format(until));
                } finally {
                    exit.invoke(null, context);
                }
                runtime.getMethod("RTenterScope", contextType, int.class).invoke(null, context, 907174 + i);
                try { spacer.invoke(null, height.invoke(null, modifier, 6f), context); }
                finally { exit.invoke(null, context); }
                String style = percentage >= 100 ? "Error" : percentage >= 80 ? "Warning" : "Default";
                runtime.getMethod("RTenterScope", contextType, int.class).invoke(null, context, 905174 + i);
                try {
                    progress.invoke(null, null, percentage, false, null, styles.getField(style).get(null), context, 13, null);
                } finally {
                    exit.invoke(null, context);
                }
            }
        } catch (Throwable error) {
            try {
                java.io.StringWriter trace = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(trace));
                Files.writeString(FILE.resolveSibling("ui-render-errors.log"), trace + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ignored) { }
        } finally {
            if (entered && exit != null) {
                try { exit.invoke(null, context); } catch (Exception ignored) { }
            }
        }
    }

    private static void add(List<String> rows, Properties values, String key, String label) {
        String used = values.getProperty(key + ".used");
        String reset = values.getProperty(key + ".reset");
        if (used == null || reset == null) return;
        double percentage = Double.parseDouble(used);
        Instant until = Instant.ofEpochSecond((long) Double.parseDouble(reset));
        if (!Double.isFinite(percentage) || percentage < 0 || percentage > 100 ||
            !until.isAfter(Instant.now())) return;
        rows.add(String.format(java.util.Locale.ROOT, "%s  %.0f%%   resets %s", label, percentage, RESET.format(until)));
    }
}
