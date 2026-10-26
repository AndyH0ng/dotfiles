package airui2;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Marks an ACP model named "default" as the default row in Air's model picker. */
public final class DefaultBadgePatch {
    private static final String TARGET = "fleet/ai/frontend/view/input/selector/PresentableAgent$Model";
    private static ClassFileTransformer active;

    public static synchronized void apply(Instrumentation inst, boolean remove) {
        if (active != null) { inst.removeTransformer(active); active = null; }
        Class<?> target = null;
        for (Class<?> type : inst.getAllLoadedClasses())
            if (TARGET.replace('/', '.').equals(type.getName())) target = type;
        if (remove) {
            if (target != null) try { inst.retransformClasses(target); } catch (Throwable ignored) { }
            return;
        }
        active = new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> type,
                                              ProtectionDomain domain, byte[] bytes) {
                if (!TARGET.equals(name)) return null;
                try {
                    ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofResourceParsing(loader)));
                    ClassDesc model = ClassDesc.of(TARGET.replace('/', '.'));
                    return cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("isDefault") &&
                            method.methodType().equalsString("()Z"),
                        (builder, element) -> {
                            if (element instanceof Instruction instruction && instruction.opcode() == Opcode.IRETURN) {
                                builder.ldc("default").aload(0)
                                    .getfield(model, "id", ClassDesc.of("java.lang.String"))
                                    .invokevirtual(ClassDesc.of("java.lang.String"), "equals",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z"))
                                    .ior().with(element);
                            } else builder.with(element);
                        }));
                } catch (Throwable ignored) { return null; }
            }
        };
        inst.addTransformer(active, true);
        if (target != null) try { inst.retransformClasses(target); } catch (Throwable ignored) { }
    }
}
