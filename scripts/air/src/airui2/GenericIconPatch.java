package airui2;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/** Uses the Claude Code theme icon for Air's generic agent glyph when Claude Code is the only custom ACP agent. */
public final class GenericIconPatch {
    private static final String TARGET = "fleet/frontend/icons/IconKeys$Ai";
    private static ClassFileTransformer active;

    public static synchronized void apply(Instrumentation inst, boolean remove) {
        Class<?> target = null;
        for (Class<?> candidate : inst.getAllLoadedClasses()) {
            if (candidate.getName().equals(TARGET.replace('/', '.'))) target = candidate;
        }
        if (active != null) {
            inst.removeTransformer(active);
            active = null;
        }
        if (remove) {
            if (target != null) try { inst.retransformClasses(target); } catch (Throwable ignored) { }
            return;
        }
        active = new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> clazz,
                                              ProtectionDomain domain, byte[] bytes) {
                if (!TARGET.equals(name)) return null;
                try {
                    Module helper = Class.forName("airui2.IconFallback", true, null).getModule();
                    if (module != null && module.isNamed()) {
                        inst.redefineModule(module, Set.of(), Map.of("fleet.frontend.icons", Set.of(helper)),
                            Map.of(), Set.of(), Map.of());
                    }
                    ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofResourceParsing(loader)));
                    return cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("getAgent") &&
                            method.methodType().equalsString("()Lfleet/compose/theme/ThemeKey;"),
                        (builder, element) -> {
                            if (element instanceof Instruction instruction && instruction.opcode() == Opcode.ARETURN) {
                                builder.ldc(ClassDesc.of(TARGET.replace('/', '.')))
                                    .invokestatic(ClassDesc.of("airui2.GenericIconFallback"), "genericAgentIcon",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;"))
                                    .checkcast(ClassDesc.of("fleet.compose.theme.ThemeKey"))
                                    .with(element);
                            } else builder.with(element);
                        }));
                } catch (Throwable error) { return null; }
            }
        };
        inst.addTransformer(active, true);
        if (target != null) {
            try { inst.retransformClasses(target); } catch (Throwable ignored) { }
        }
    }
}
