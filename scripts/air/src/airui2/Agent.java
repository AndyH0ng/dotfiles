package airui2;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public final class Agent {
    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        Path jar = Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar.resolveSibling("usage-helper2.jar").toFile()));
        Module helper = Class.forName("airui2.UsageText", true, null).getModule();
        ModuleAccess.install(instrumentation, helper);
        IconPatch.apply(instrumentation, false);
        SelectedIconPatch.apply(instrumentation, false);
        DefaultBadgePatch.apply(instrumentation, false);
        CustomDefaultPatch3.apply(instrumentation, false);
        RenderDefaultPatch.apply(instrumentation, false);
        Patch.apply(instrumentation, false);
        WidthPatch.apply(instrumentation, false);
        ReplaceContext3.apply(instrumentation, false);
    }

    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        try {
            Class.forName("airui.Patch").getMethod("apply", Instrumentation.class, boolean.class)
                .invoke(null, instrumentation, true);
        } catch (ClassNotFoundException ignored) { }
        Path jar = Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Class<?> installer = Class.forName("airui2.Patch");
        if (!"remove".equals(args)) {
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar.resolveSibling("usage-helper2.jar").toFile()));
        }
        boolean remove = "remove".equals(args);
        if (remove) {
            IconPatch.apply(instrumentation, true);
            GenericIconPatch.apply(instrumentation, true);
            SelectedIconPatch.apply(instrumentation, true);
            DefaultBadgePatch.apply(instrumentation, true);
            CustomDefaultPatch3.apply(instrumentation, true);
            RenderDefaultPatch.apply(instrumentation, true);
            ReplaceContext3.apply(instrumentation, true);
            WidthPatch.apply(instrumentation, true);
        }
        installer.getMethod("apply", Instrumentation.class, boolean.class)
            .invoke(null, instrumentation, remove);
        if (!remove) {
            Module helper = Class.forName("airui2.UsageText", true, null).getModule();
            ModuleAccess.install(instrumentation, helper);
            IconPatch.apply(instrumentation, false);
            SelectedIconPatch.apply(instrumentation, false);
            DefaultBadgePatch.apply(instrumentation, false);
            CustomDefaultPatch3.apply(instrumentation, false);
            RenderDefaultPatch.apply(instrumentation, false);
            WidthPatch.apply(instrumentation, false);
            ReplaceContext3.apply(instrumentation, false);
        }
    }
}
