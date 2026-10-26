package airui2;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Draws one subscription limit using Air's own context usage row components. */
public final class UsageRow {
    public static void render(Object context, Class<?> anchor, String amount, String description, String reset) throws Exception {
        ClassLoader loader = anchor.getClassLoader();
        Class<?> function = Class.forName("kotlin.jvm.functions.Function2", true, loader);
        Object content = Proxy.newProxyInstance(loader, new Class<?>[] {function}, (proxy, method, args) -> {
            if (!method.getName().equals("invoke")) return null;
            Object rowScope = args[0];
            Object rowContext = args[1];
            Class<?> contextType = Class.forName("noria.NoriaContext", true, loader);
            Class<?> runtime = Class.forName("noria.impl.RuntimeKt", true, loader);
            Method enter = runtime.getMethod("RTenterScope", contextType, int.class);
            Method exit = runtime.getMethod("RTexitScope", contextType);
            Class<?> textClass = Class.forName("fleet.compose.theme.components.TextKt", true, loader);
            Method uiText = null;
            for (Method candidate : textClass.getMethods()) {
                if (candidate.getName().startsWith("UiText-") && candidate.getName().endsWith("$default") &&
                    candidate.getParameterCount() == 13 && candidate.getParameterTypes()[0] == String.class) {
                    uiText = candidate;
                    break;
                }
            }
            if (uiText == null) throw new NoSuchMethodException("UiText String default");
            Class<?> styles = Class.forName("fleet.compose.theme.keys.TextStyleKeys", true, loader);
            Object styleKeys = styles.getField("INSTANCE").get(null);
            Object bold = styles.getMethod("getDefaultSemiBold").invoke(styleKeys);
            Object medium = styles.getMethod("getMedium").invoke(styleKeys);
            Class<?> locals = Class.forName("fleet.compose.theme.CompositionLocalsKt", true, loader);
            Object theme = locals.getMethod("getTheme", contextType).invoke(null, rowContext);
            Class<?> keys = Class.forName("fleet.compose.theme.keys.ThemeKeys", true, loader);
            Object themeKeys = keys.getField("INSTANCE").get(null);
            Object primaryKey = keys.getMethod("getTextPrimary").invoke(themeKeys);
            Object secondaryKey = keys.getMethod("getTextSecondary").invoke(themeKeys);
            Class<?> themeKeyType = Class.forName("fleet.compose.theme.ThemeKey", true, loader);
            Method getColor = theme.getClass().getMethod("get", themeKeyType);
            Method unbox = Class.forName("androidx.compose.ui.graphics.Color", true, loader).getMethod("unbox-impl");
            long primary = (long) unbox.invoke(getColor.invoke(theme, primaryKey));
            long secondary = (long) unbox.invoke(getColor.invoke(theme, secondaryKey));
            String[] values = {amount, description, reset};
            Object[] textStyles = {bold, medium, medium};
            for (int i = 0; i < 3; i++) {
                enter.invoke(null, rowContext, 903101 + i);
                try {
                    Object[] textArgs = new Object[13];
                    textArgs[0] = values[i];
                    textArgs[1] = i == 0 ? primary : secondary;
                    textArgs[2] = textStyles[i];
                    textArgs[9] = Class.forName("androidx.compose.ui.Modifier", true, loader).getField("Companion").get(null);
                    textArgs[10] = rowContext;
                    textArgs[11] = 504; // Match the native Context used row.
                    uiText.invoke(null, textArgs);
                } finally {
                    exit.invoke(null, rowContext);
                }
                if (i == 1) {
                    Class<?> modifierType = Class.forName("androidx.compose.ui.Modifier", true, loader);
                    Object modifier = modifierType.getField("Companion").get(null);
                    Class<?> rowScopeType = Class.forName("androidx.compose.foundation.layout.RowScope", true, loader);
                    Method weight = rowScopeType.getMethod("weight$default", rowScopeType, modifierType,
                        float.class, boolean.class, int.class, Object.class);
                    Object weighted = weight.invoke(null, rowScope, modifier, 1f, false, 2, null);
                    Class<?> spacer = Class.forName("androidx.compose.foundation.layout.SpacerKt", true, loader);
                    enter.invoke(null, rowContext, 903110);
                    try { spacer.getMethod("Spacer", modifierType, contextType).invoke(null, weighted, rowContext); }
                    finally { exit.invoke(null, rowContext); }
                }
            }
            return Class.forName("kotlin.Unit", true, loader).getField("INSTANCE").get(null);
        });
        Class<?> row = Class.forName("androidx.compose.foundation.layout.RowKt", true, loader);
        Class<?> modifierType = Class.forName("androidx.compose.ui.Modifier", true, loader);
        Object modifier = modifierType.getField("Companion").get(null);
        Object fullWidth = Class.forName("androidx.compose.foundation.layout.SizeKt", true, loader)
            .getMethod("fillMaxWidth", modifierType, float.class).invoke(null, modifier, 1f);
        for (Method method : row.getMethods()) {
            if (method.getName().equals("Row$default") && method.getParameterCount() == 8) {
                method.invoke(null, fullWidth, null, null, false, content, context, 14, null);
                return;
            }
        }
        throw new NoSuchMethodException("Row$default");
    }
}
