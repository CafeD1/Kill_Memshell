package org.cafedi.clean;

import org.objectweb.asm.*;

import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;

public class ClassUnloader {
    private final Instrumentation inst;
    private final PrintWriter writer;

    public ClassUnloader(Instrumentation inst, PrintWriter writer) {
        this.inst = inst;
        this.writer = writer;
    }
    /**
     * 尝试“中和”指定类（将方法体替换成空实现或返回默认值）。
     * @param targetClassName 完整类名，例如 "org.apache.jsp.filter_005fshell2_jsp$ShellFilter"
     */
    public void unloaderClass(String targetClassName) {
        try {
            // 1. 在 JVM 已加载类中找到目标 Class 对象
            Class<?> target = null;
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (c.getName().equals(targetClassName)) {
                    target = c;
                    break;
                }
            }
            if (target == null) {
                writer.println("[!] target class not found in JVM: " + targetClassName);
                return;
            }
            writer.println("[*] Found target class: " + target + " loader=" + target.getClassLoader());
            // 2. 检查 retransformation 支持
            if (!inst.isRetransformClassesSupported()) {
                writer.println("[!] Instrumentation does not support retransformation");
                return;
            }
            // 3. 创建 transformer：当重转换目标类时，返回一个把非构造器方法体替换掉的字节码
            ClassFileTransformer transformer = new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                        ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                    // className 使用斜杠分隔，例如 org/apache/jsp/...
                    if (classBeingRedefined == null) return null;
                    if (!classBeingRedefined.getName().equals(targetClassName)) return null;
                    try {
                        ClassReader cr = new ClassReader(classfileBuffer);
                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                                // 不修改构造器和静态初始化块（这两者处理比较敏感）
                                if (name.equals("<init>") || name.equals("<clinit>")) {
                                    return mv;
                                }
                                // 对其它方法，返回一个替代的 MethodVisitor，在 visitCode 时写入空实现或默认返回值
                                return new MethodVisitor(Opcodes.ASM9, mv) {
                                    @Override
                                    public void visitCode() {
                                        super.visitCode();
                                        // 根据返回类型插入默认返回
                                        Type returnType = Type.getReturnType(descriptor);
                                        switch (returnType.getSort()) {
                                            case Type.VOID:
                                                mv.visitInsn(Opcodes.RETURN);
                                                break;
                                            case Type.BOOLEAN:
                                            case Type.CHAR:
                                            case Type.BYTE:
                                            case Type.SHORT:
                                            case Type.INT:
                                                mv.visitInsn(Opcodes.ICONST_0);
                                                mv.visitInsn(Opcodes.IRETURN);
                                                break;
                                            case Type.LONG:
                                                mv.visitInsn(Opcodes.LCONST_0);
                                                mv.visitInsn(Opcodes.LRETURN);
                                                break;
                                            case Type.FLOAT:
                                                mv.visitInsn(Opcodes.FCONST_0);
                                                mv.visitInsn(Opcodes.FRETURN);
                                                break;
                                            case Type.DOUBLE:
                                                mv.visitInsn(Opcodes.DCONST_0);
                                                mv.visitInsn(Opcodes.DRETURN);
                                                break;
                                            case Type.ARRAY:
                                            case Type.OBJECT:
                                                mv.visitInsn(Opcodes.ACONST_NULL);
                                                mv.visitInsn(Opcodes.ARETURN);
                                                break;
                                            default:
                                                // 兜底：返回 void（不常见）
                                                mv.visitInsn(Opcodes.RETURN);
                                                break;
                                        }
                                        // 直接结束方法（后面不再写其他字节码）
                                        // 必须调用 visitMaxs/visitEnd，ASM 的 ClassWriter(COMPUTE_MAXS) 会计算
                                        mv.visitMaxs(0, 0);
                                        mv.visitEnd();
                                    }
                                    // 避免写入原来的字节码：覆盖其它访问点为空实现即可
                                };
                            }
                        };
                        cr.accept(cv, ClassReader.EXPAND_FRAMES);
                        return cw.toByteArray();
                    } catch (Throwable t) {
                        writer.println("[!] transform error for " + targetClassName + " : " + t);
                        t.printStackTrace(writer);
                        return null;
                    }
                }
            };
            // 4. 注册 transformer 并触发 retransformation
            inst.addTransformer(transformer, true);
            try {
                inst.retransformClasses(target); // 只会触发 transformer 对目标类的 transform
                writer.println("[+]Clean Success!!!: " + targetClassName);
            } finally {
                // 一定要移除 transformer，避免影响其它类
                inst.removeTransformer(transformer);
            }
        } catch (Throwable e) {
            writer.println("[!]Cleam Fail: " + targetClassName + " -> " + e);
            e.printStackTrace(writer);
        }
    }
}
