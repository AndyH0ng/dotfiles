package airui2;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;

/** Adds Air's ACP default marker at the final model-row rendering step. */
public final class RenderDefaultPatch {
    private static final String TARGET = "fleet/ai/frontend/view/input/selector/AgentSelectorDropdownKt";
    private static ClassFileTransformer active;

    public static synchronized void apply(Instrumentation inst, boolean remove) throws Exception {
        if (active != null) { inst.removeTransformer(active); active = null; }
        Class<?> target = null;
        for (Class<?> type : inst.getAllLoadedClasses())
            if (TARGET.replace('/', '.').equals(type.getName())) target = type;
        if (remove) {
            if (target != null) inst.retransformClasses(target);
            return;
        }
        Module helper = Class.forName("airui2.DefaultName", true, null).getModule();
        if (target != null) inst.redefineModule(target.getModule(), Set.of(helper), Map.of(), Map.of(), Set.of(), Map.of());
        active = new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> type,
                                              ProtectionDomain domain, byte[] bytes) {
                if (!TARGET.equals(name)) return null;
                try {
                    if (module != null && module.isNamed())
                        inst.redefineModule(module, Set.of(helper), Map.of(), Map.of(), Set.of(), Map.of());
                    ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofResourceParsing(loader)));
                    return cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("ModelListItem") &&
                            method.methodType().equalsString("(Landroidx/compose/foundation/layout/RowScope;Ljava/lang/String;Ljava/lang/String;ZLfleet/compose/theme/components/ItemCellColors;Lfleet/compose/theme/text/TextMatching$Matcher;Lnoria/NoriaContext;)V"),
                        (builder, element) -> {
                            if (element instanceof Instruction instruction && instruction.opcode() == Opcode.ILOAD_3) {
                                builder.with(element).aload(1)
                                    .invokestatic(ClassDesc.of("airui2.DefaultName"), "matches",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z"))
                                    .ior();
                            } else builder.with(element);
                        }));
                } catch (Throwable error) {
                    System.err.println("RenderDefaultPatch failed: " + error);
                    return null;
                }
            }
        };
        inst.addTransformer(active, true);
        if (target != null) inst.retransformClasses(target);
    }
}
