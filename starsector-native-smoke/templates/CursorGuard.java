import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

// test-only agent: stops LWJGL moving the real cursor; the game centres it at battle start even in a background window
// grabbed mode (centerCursor) stays as is: LWJGL only grabs a focused window
// needs --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED
public final class CursorGuard implements ClassFileTransformer {

    static final String TARGET = "org/lwjgl/opengl/WindowsDisplay";

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new CursorGuard());
    }

    // asm types stay inside Patch, so a jvm without the export logs "off" instead of failing to start
    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (!TARGET.equals(name)) return null;
        try {
            byte[] out = Patch.apply(bytes);
            System.err.println(out == null ? "CURSOR_GUARD off: no nSetCursorPosition call in " + name + ".setCursorPosition"
                    : "CURSOR_GUARD on: " + name + ".setCursorPosition no longer moves the real cursor");
            return out;
        } catch (Throwable t) {
            System.err.println("CURSOR_GUARD off: " + t);
            return null;
        }
    }

    static final class Patch {

        static byte[] apply(byte[] bytes) {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            boolean[] hit = new boolean[1];
            reader.accept(new ClassVisitor(Opcodes.ASM7, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, method, desc, signature, exceptions);
                    if (!"setCursorPosition".equals(method) || !"(II)V".equals(desc)) return mv;
                    return new MethodVisitor(Opcodes.ASM7, mv) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String called, String calledDesc, boolean itf) {
                            if (op == Opcodes.INVOKESTATIC && TARGET.equals(owner) && "nSetCursorPosition".equals(called) && "(II)V".equals(calledDesc)) {
                                // pop2 has the call's stack effect, so the stack map frames stay valid
                                super.visitInsn(Opcodes.POP2);
                                hit[0] = true;
                            } else {
                                super.visitMethodInsn(op, owner, called, calledDesc, itf);
                            }
                        }
                    };
                }
            }, 0);
            return hit[0] ? writer.toByteArray() : null;
        }
    }
}
