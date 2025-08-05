package org.cafedi.clean;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.PrintWriter;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

/*
内存马卸载类
 */
public class ClassUnloader {
    private  final  Instrumentation inst;
    private  final  PrintWriter writer;
    public ClassUnloader(Instrumentation inst, PrintWriter writer) {
        this.inst = inst;
        this.writer = writer;
    }
    /**
     * 将指定类替换成一个空实现。
     * @param targetClass 被清除的类
     */
    public void unloaderClass(Class<?> targetClass){
        try {
            //构造一个空实现
            byte[] emptyBytecode = generateEmptyClass(targetClass);
            //构造一个“类定义”对象，用于告诉 JVM “要把哪个类替换成哪段新的字节码
            ClassDefinition def = new ClassDefinition(targetClass, emptyBytecode);
            //将 JVM 中已加载的目标类热替换为新的字节码定义
            inst.redefineClasses(def);
            writer.println("[+] Unloaded class: " + targetClass.getName());
        }catch (Exception e){
            writer.println("[!] Failed to unload class: " + targetClass.getName());
            e.printStackTrace();
        }
    }

    private static byte[] generateEmptyClass(Class<?> targetClass) {
        /**
         * 生成与 targetClass 同名、但什么都不做的空类字节码。
         * 这里用 ASM 简单示例：class X { }。
         */
        // 1. 将类名从“点分隔”格式转换为 ASM 要求的“斜杠分隔”格式
        String name = targetClass.getName().replace(".", "/");
        // 2. 创建一个 ClassWriter，参数 0 表示不自动计算帧和最大栈本地变量（手动管理）
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        // 3. 开始定义类：JVM 版本、访问修饰符、内部名、签名、父类内部名、接口列表
        cw.visit(
                Opcodes.V1_8,                  // Java 8 版本号
                Opcodes.ACC_PUBLIC,            // public class
                name,                          // 类的内部名，比如 "com/example/MyClass"
                null,                          // 泛型签名（不需要时传 null）
                "java/lang/Object",            // 父类内部名
                null                           // 实现的接口列表
        );
        // 4. 定义一个 public 的无参构造器：<init>()V
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,            // public 方法
                "<init>",                      // 构造器名称必须写 "<init>"
                "()V",                         // 方法描述符：()V 表示无参返回 void
                null,                          // 泛型签名
                null                           // 抛出的异常列表
        );
        mv.visitCode();                    // 开始写入方法字节码

        // 5. 方法体：加载 this，然后调用父类构造器，再返回
        mv.visitVarInsn(Opcodes.ALOAD, 0);                 // 将 local 0 (this) 推到操作数栈
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,        // 调用超类方法（special invoke）
                "java/lang/Object",           // 父类内部名
                "<init>",                     // 父类构造器方法名
                "()V",                        // 方法描述符
                false                         // 如果是接口方法调用应为 true
        );
        mv.visitInsn(Opcodes.RETURN);                     // 返回 void

        // 6. 指定最大栈深度和本地变量表大小（因为我们用 CW 的 compute 参数为 0，需手工写）
        mv.visitMaxs(1, 1);                                // maxStack=1, maxLocals=1
        mv.visitEnd();                                     // 结束方法写入

        // 7. 结束类定义，返回完整字节码
        cw.visitEnd();
        return cw.toByteArray();
    }
}
