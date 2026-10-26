package airui2;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/** Patches the selected-icon value consumed by the closed model selector button. */
public final class SelectedIconPatch {
    private static final String TARGET = "fleet/ai/frontend/view/input/selector/ModelTreeContent";
    private static ClassFileTransformer active;

    public static synchronized void apply(Instrumentation inst, boolean remove) {
        Class<?> target = null;
        for (Class<?> candidate : inst.getAllLoadedClasses())
            if (candidate.getName().equals(TARGET.replace('/', '.'))) target = candidate;
        if (active != null) { inst.removeTransformer(active); active = null; }
        if (remove) {
            if (target != null) try { inst.retransformClasses(target); } catch (Throwable ignored) { }
            return;
        }
        active = new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> type,
                                              ProtectionDomain domain, byte[] bytes) {
                if (!TARGET.equals(name)) return null;
                try {
                    Module helper = Class.forName("airui2.SelectedIconFallback", true, null).getModule();
                    if (module != null && module.isNamed())
                        inst.redefineModule(module, Set.of(), Map.of(
                            "fleet.ai.frontend.view.input.selector", Set.of(helper)),
                            Map.of("fleet.ai.frontend.view.input.selector", Set.of(helper)),
                            Set.of(), Map.of());
                    ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofResourceParsing(loader)));
                    byte[] transformed = cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("getSelectedIcon") &&
                            method.methodType().equalsString("()Lfleet/compose/theme/ThemeKey;"),
                        (builder, element) -> {
                            if (element instanceof Instruction instruction && instruction.opcode() == Opcode.ARETURN) {
                                builder.aload(0)
                                    .ldc(ClassDesc.of(TARGET.replace('/', '.')))
                                    .invokestatic(ClassDesc.of("airui2.SelectedIconFallback2"), "selectedIcon",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;"))
                                    .checkcast(ClassDesc.of("fleet.compose.theme.ThemeKey"))
                                    .with(element);
                            } else builder.with(element);
                        }));
                    SelectedIconFallback2.log("selected icon getter transformed");
                    return transformed;
                } catch (Throwable error) { SelectedIconFallback2.log("selected icon transform error=" + error); return null; }
            }
        };
        inst.addTransformer(active, true);
        SelectedIconFallback2.log("selected icon patch installed; targetLoaded=" + (target != null));
        if (target != null) try { inst.retransformClasses(target); } catch (Throwable error) { SelectedIconFallback2.log("selected icon retransform error=" + error); }
    }
}
