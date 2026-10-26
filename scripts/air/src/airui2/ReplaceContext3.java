package airui2;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Replace the cached native text row with the same full width row used by subscription limits. */
public final class ReplaceContext3 {
    private static final String TARGET = "fleet/ai/frontend/view/contextWindow/ContextWindowPopupButtonKt$ContextWindowPopupButton$1$1";
    private static ClassFileTransformer active;

    public static synchronized void apply(Instrumentation inst, boolean remove) throws Exception {
        if (active != null) {
            inst.removeTransformer(active);
            active = null;
        }
        Class<?> target = null;
        for (Class<?> loaded : inst.getAllLoadedClasses()) if (loaded.getName().equals(TARGET.replace('/', '.'))) target = loaded;
        if (remove) {
            if (target != null) inst.retransformClasses(target);
            return;
        }
            active = new ClassFileTransformer() {
                @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> clazz,
                                                  ProtectionDomain domain, byte[] bytes) {
                    if (!TARGET.equals(name)) return null;
                    try {
                        int[] replacements = {0, 0};
                        ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                            ClassHierarchyResolver.ofResourceParsing(loader)));
                        byte[] result = cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                            method -> method.methodName().equalsString("invoke") && method.methodType().equalsString(
                                "(Landroidx/compose/foundation/layout/ColumnScope;Lnoria/NoriaContext;)V"),
                            (builder, element) -> {
                                if (element instanceof InvokeInstruction call &&
                                    call.name().equalsString("Row$default")) {
                                    for (int i = 0; i < 8; i++) builder.pop();
                                    ClassDesc self = ClassDesc.of(TARGET.replace('/', '.'));
                                    builder.aload(2).ldc(self).aload(0)
                                        .getfield(self, "$usedContextPercentageText", ClassDesc.of("java.lang.String"))
                                        .ldc(" of context used").aload(0)
                                        .getfield(self, "$proportionText", ClassDesc.of("java.lang.String"))
                                        .invokestatic(ClassDesc.of("airui2.UsageRow"), "render",
                                            MethodTypeDesc.ofDescriptor(
                                                "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"));
                                    replacements[0]++;
                                } else {
                                    if (element instanceof InvokeInstruction call &&
                                        call.name().equalsString("render") &&
                                        call.owner().asInternalName().equals("airui2/UsageRow")) replacements[1]++;
                                    builder.with(element);
                                }
                            }));
                        if (replacements[0] != 1 && replacements[1] != 1)
                            throw new IllegalStateException("Context row match=" + replacements[0] + ", replacement=" + replacements[1]);
                        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("user.home"),
                            ".local/share/air-claude-bridge/ui-patch.log"), "Final Context row replaced\n",
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        return result;
                    } catch (Throwable error) {
                        try { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("user.home"),
                            ".local/share/air-claude-bridge/ui-patch.log"), "Final row error: " + error + "\n",
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
