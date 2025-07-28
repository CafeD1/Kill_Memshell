package org.cafedi.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MemShellASMAnalyzer {
    public static void analyze(Map<String, byte[]> map, PrintWriter writer){
        writer.println("[*internal]asm start");
//        try {
//            writer.println("[*]Start ASM Analyze");
//            //遍历map获取类名和字节码
//            for(Map.Entry<String,byte[]> entry:map.entrySet()){
//                String className = entry.getKey();
//                byte[] classByte = entry.getValue();
//                //ClassReader：ASM 的核心工具，用于读取并解析 .class 字节数组；
//                //accept(visitor, 0) 会遍历类结构（访问头部、字段、方法等）；
//                //使用匿名的 ClassVisitor 进行访问。
//                ClassReader cr = new ClassReader(classByte);
//                cr.accept(new ClassVisitor(Opcodes.ASM9){
//                    //初始化接口集合
//                    Set<String> interfaces = new HashSet<>();
//                    String superName;
//                    @Override
//                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
//                        if (interfaces != null) {
//                            for (String i : interfaces) {
//                                //添加接口到集合列表
//                                this.interfaces.add(i);
//                            }
//                        }
//                        this.superName = superName;
//                    }
//                    //遍历所有方法，分析方法调用指令
//                    @Override
//                    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
//                        return new MethodVisitor(Opcodes.ASM9) {
//                            @Override
//                            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
//                                detect(owner, methodName, className);
//                            }
//                        };
//                    }
//                    //识别可疑方法调用与内存马类型
//                    private void detect(String owner, String methodName, String className) {
//                        if (interfaces.contains("javax/servlet/Filter") || className.contains("Filter")) {
//                            if (owner.contains("ServletContext") && methodName.contains("addFilter")) {
//                                writer.println("[Filter Shell] " + className);
//                            }
//                        }
//                        if (owner.contains("ServletContext") && methodName.contains("addServlet")) {
//                            writer.println("[Servlet Shell] " + className);
//                        }
//                        if (owner.contains("ServletContext") && methodName.contains("addListener")) {
//                            writer.println("[Listener Shell] " + className);
//                        }
//                        if (owner.contains("springframework") && methodName.contains("register")) {
//                            writer.println("[Spring Controller Shell] " + className);
//                        }
//                        if (methodName.equals("defineClass") || methodName.equals("defineClass0")) {
//                            writer.println("[Dynamic Load Suspect] " + className + " uses " + owner + "." + methodName);
//                        }
//                    }
//                }, 0);
//            }
//    }catch (Exception e){
//            writer.println(e);
//        }
    }
}
