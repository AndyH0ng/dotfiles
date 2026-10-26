package airui2;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.lang.classfile.*;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public final class Patch {
    private static final String TARGET = "fleet/ai/frontend/view/contextWindow/ContextWindowPopupButtonKt$ContextWindowPopupButton$1$1";
    private static ClassFileTransformer active;
    private static final Path LOG = Path.of(System.getProperty("user.home"),
        ".local/share/air-claude-bridge/ui-patch.log");

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
            log("Removed; original UI restored");
            return;
        }
        Module helper = Class.forName("airui2.UsageText", true, null).getModule();
        if (target != null) inst.redefineModule(target.getModule(), Set.of(helper), Map.of(), Map.of(), Set.of(), Map.of());
        active = new ClassFileTransformer() {
            @Override
            public byte[] transform(Module module, ClassLoader loader, String name, Class<?> clazz,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!TARGET.equals(name)) return null;
                try {
                    inst.redefineModule(module, Set.of(helper), Map.of(), Map.of(), Set.of(), Map.of());
                    int[] calls = {0, 0};
                    ClassFile cf = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                        ClassHierarchyResolver.ofResourceParsing(loader)));
                    byte[] modified = cf.transformClass(cf.parse(bytes), ClassTransform.transformingMethodBodies(
                        method -> method.methodName().equalsString("invoke") &&
                            method.methodType().equalsString("(Landroidx/compose/foundation/layout/ColumnScope;Lnoria/NoriaContext;)V"),
                        (builder, element) -> {
                            if (element instanceof InvokeInstruction call &&
                                call.name().equalsString("render") &&
                                call.owner().asInternalName().equals("airui2/UsageText")) {
                                calls[0]++;
                                builder.with(element);
                            } else if (element instanceof Instruction instruction && instruction.opcode() == Opcode.RETURN) {
                                if (calls[0] == 0) {
                                builder.aload(2).ldc(ClassDesc.of(TARGET.replace('/', '.')))
                                    .invokestatic(ClassDesc.of("airui2.UsageText"), "render",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/Class;)V"));
                                calls[1]++;
                                }
                                builder.with(element);
                                calls[0]++;
                            } else {
                                builder.with(element);
                            }
                        }));
                    if (calls[1] > 1 || calls[0] == 0) throw new IllegalStateException("Unexpected Air bytecode; refusing patch");
                    log(calls[1] == 1 ? "Transformed ContextWindowPopupButton" : "ContextWindowPopupButton already patched");
                    return modified;
                } catch (Throwable error) {
                    try { log("Patch refused: " + error); } catch (Exception ignored) { }
                    return null;
                }
            }
        };
        inst.addTransformer(active, true);
        if (target != null) inst.retransformClasses(target);
        log("Attached runtime UI patch");
    }

    private static void log(String message) {
        try {
            Files.writeString(LOG, java.time.Instant.now() + " " + message + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }
}
