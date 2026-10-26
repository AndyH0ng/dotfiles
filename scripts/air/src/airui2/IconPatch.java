package airui2;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/** Uses Air's built-in Claude Code icon for the custom Claude Code ACP agent. */
public final class IconPatch {
    private static final String TARGET = "fleet/ai/frontend/view/input/selector/AgentSelectorIconProviderKt";
    private static ClassFileTransformer active;

    public static synchronized void apply(Instrumentation inst, boolean remove) {
        if (active != null) {
            inst.removeTransformer(active);
            active = null;
        }
        if (remove) return;
        Class<?> target = null;
        for (Class<?> candidate : inst.getAllLoadedClasses()) {
            if (candidate.getName().equals(TARGET.replace('/', '.'))) target = candidate;
        }
        active = new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> clazz,
                                              ProtectionDomain domain, byte[] bytes) {
                if (!TARGET.equals(name)) return null;
                try {
                    Module helper = Class.forName("airui2.IconFallback", true, null).getModule();
                    if (module != null && module.isNamed()) {
                        inst.redefineModule(module, Set.of(), Map.of(
                            "fleet.ai.frontend.view.input.selector", Set.of(helper)), Map.of(), Set.of(), Map.of());
                    }
                    ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofResourceParsing(loader)));
                    byte[] transformed = cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                        method -> method.methodName().stringValue().startsWith("obtainAgentSelectorIconProvider-") &&
                            method.methodType().equalsString("(Ljava/lang/String;)Lfleet/ai/frontend/view/input/selector/AgentSelectorIconProvider;"),
                        (builder, element) -> {
                            if (element instanceof Instruction instruction && instruction.opcode() == Opcode.ARETURN) {
                                builder.aload(0)
                                    .ldc(ClassDesc.of(TARGET.replace('/', '.')))
                                    .invokestatic(ClassDesc.of("airui2.IconFallback"), "choose",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;"))
                                    .checkcast(ClassDesc.of("fleet.ai.frontend.view.input.selector.AgentSelectorIconProvider"))
                                    .with(element);
                            } else builder.with(element);
                        }));
                    log("transformed target=" + name);
                    return transformed;
                } catch (Throwable error) { log("transform error=" + error); return null; }
            }
        };
        inst.addTransformer(active, true);
        log("transformer installed; targetLoaded=" + (target != null));
        if (target != null) {
            try { inst.retransformClasses(target); } catch (Throwable error) { log("retransform error=" + error); }
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
