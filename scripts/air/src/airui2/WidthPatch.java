package airui2;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Give the native Context row enough room for its right-aligned token count. */
public final class WidthPatch {
    private static final String TARGET = "fleet/ai/frontend/view/contextWindow/ContextWindowPopupButtonKt$ContextWindowPopupButton$1";
    private static ClassFileTransformer active;

    public static synchronized void apply(Instrumentation inst, boolean remove) throws Exception {
        if (active != null) {
            inst.removeTransformer(active);
            active = null;
        }
        Class<?> target = null;
        for (Class<?> candidate : inst.getAllLoadedClasses()) {
            if (candidate.getName().equals(TARGET.replace('/', '.'))) target = candidate;
        }
        if (remove) {
            if (target != null) inst.retransformClasses(target);
            return;
        }
            active = new ClassFileTransformer() {
                @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> clazz,
                                                  ProtectionDomain domain, byte[] bytes) {
                    if (!TARGET.equals(name)) return null;
                    try {
                        int[] changes = {0};
                        ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                            ClassHierarchyResolver.ofResourceParsing(loader)));
                        byte[] result = cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                            method -> method.methodName().equalsString("invoke") &&
                                method.methodType().equalsString("(Lnoria/NoriaContext;)V"),
                            (builder, element) -> {
                                if (element instanceof ConstantInstruction constant &&
                                    constant.opcode() == Opcode.SIPUSH && constant.constantValue().equals(276)) {
                                    builder.sipush(340);
                                    changes[0]++;
                                } else if (element instanceof ConstantInstruction constant &&
                                           constant.opcode() == Opcode.SIPUSH && constant.constantValue().equals(340)) {
                                    builder.with(element);
                                    changes[0]++;
                                } else {
                                    builder.with(element);
                                }
                            }));
                        if (changes[0] != 1) throw new IllegalStateException("Air popup width bytecode changed");
                        return result;
                    } catch (Throwable error) {
                        try { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("user.home"),
                            ".local/share/air-claude-bridge/ui-patch.log"), error + "\n",
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
                        catch (Exception ignored) { }
                        return null;
                    }
                }
            };
            inst.addTransformer(active, true);
        if (target != null) inst.retransformClasses(target);
    }
}
