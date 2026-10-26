package airui2;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public final class CustomDefaultPatch3 {
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
        active = new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> type,
                                              ProtectionDomain domain, byte[] bytes) {
                if (!TARGET.equals(name)) return null;
                try {
                    ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofResourceParsing(loader)));
                    byte[] result = cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("isDefault") &&
                            method.methodType().equalsString("(Lfleet/ai/frontend/view/input/selector/AgentSelectorContent;Lfleet/ai/frontend/view/input/selector/AgentSelectorListItem$Selectable;)Z"),
                        (builder, element) -> {
                            if (element instanceof Instruction instruction && instruction.opcode() == Opcode.IRETURN) {
                                ClassDesc custom = ClassDesc.of("fleet.ai.frontend.view.input.selector.AgentSelectorListItem$Selectable$Model$Custom");
                                var other = builder.newLabel();
                                var done = builder.newLabel();
                                builder.aload(1).instanceOf(custom).ifeq(other)
                                    .ldc("default").aload(1).checkcast(custom)
                                    .invokevirtual(custom, "getModel-mH6qcRE", MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"))
                                    .invokevirtual(ClassDesc.of("java.lang.String"), "equals",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z"))
                                    .goto_(done)
                                    .labelBinding(other).iconst_0()
                                    .labelBinding(done).ior().with(element);
                            } else builder.with(element);
                        }));
                    System.err.println("CustomDefaultPatch3 transformed " + name);
                    return result;
                } catch (Throwable e) {
                    System.err.println("CustomDefaultPatch3 transform failed: " + e);
                    return null;
                }
            }
        };
        inst.addTransformer(active, true);
        if (target != null) inst.retransformClasses(target);
        System.err.println("CustomDefaultPatch3 loaded=" + (target != null));
    }
}
