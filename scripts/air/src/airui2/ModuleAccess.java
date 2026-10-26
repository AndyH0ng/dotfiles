package airui2;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;

/** Expose the public JetBrains Compose entry points to the bootstrap helper module. */
public final class ModuleAccess {
    private static ClassFileTransformer transformer;

    public static synchronized void install(Instrumentation inst, Module helper) {
        if (transformer != null) return;
        transformer = new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> type,
                                              ProtectionDomain domain, byte[] bytes) {
                String pkg = switch (name == null ? "" : name) {
                    case "fleet/compose/theme/components/TextKt" -> "fleet.compose.theme.components";
                    case "noria/impl/RuntimeKt" -> "noria.impl";
                    case "fleet/compose/theme/components/progressIndicators/ProgressBarKt" ->
                        "fleet.compose.theme.components.progressIndicators";
                    case "fleet/ai/frontend/view/input/selector/AgentSelectorIconProviderKt" ->
                        "fleet.ai.frontend.view.input.selector";
                    case "fleet/frontend/icons/IconKeys$Agents" -> "fleet.frontend.icons";
                    default -> null;
                };
                if (pkg != null && module != null && module.isNamed()) {
                    try {
                        inst.redefineModule(module, Set.of(), Map.of(pkg, Set.of(helper)),
                            Map.of(), Set.of(), Map.of());
                    } catch (RuntimeException ignored) { }
                }
                return null;
            }
        };
        inst.addTransformer(transformer, false);
    }
}
