package org.cafedi.asm;
/*
注册一个 ClassFileTransformer；

对 JVM 中所有已加载的类调用 retransformClasses；

在 transform 方法中拦截类的原始字节码（classfileBuffer）；

保存到 classByteMap 中，供后续分析（如使用 ASM 判断是否是内存马）
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BytecodeDumper {
    /**
     * 根据外部传入的类名，dump 对应类的原始字节码
     * @param targetClassName 目标类名（标准格式，如 org.example.MyClass）
     * @param inst Instrumentation 实例
     * @param writer 输出日志
     * @return 含指定类字节码的 Map
     */
    //声明一个hashmap用于保存类名和对应的字节码
    private static  final Map<String,byte[]> classByteMap = new HashMap<>();
    public static Map<String,byte[]> dumpAllLoadClass(Instrumentation inst, String targetClassName,PrintWriter writer) {
        // 每次清空旧数据
        classByteMap.clear();
        //创建transformer
        ClassFileTransformer transformer = new ClassFileTransformer() {
            //当类被重新加载（retransform）时，transform() 方法会被调用
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (className != null) {
                    //className.replace('/', '.') 将字节码路径风格类名（如 javax/servlet/FilterShell）转为 Java 标准格式；使用 clone() 防止被外部修改；
                    classByteMap.put(className.replace('/','.'), classfileBuffer.clone());
                }
                //不修改原始类,只是读取分析
                return null;
            }
        };
        //注册 transformer 并触发类重传,true 表示允许 retransformation（重新转换类字节码）
        inst.addTransformer(transformer, true);
        //查找目标类，触发重新转换
        for (Class<?> claszz : inst.getAllLoadedClasses()){
            try {
                if (claszz.getName().equals(targetClassName)){
                    //inst.isModifiableClass(cls) 判断该类是否可以被 retrans（有些核心类如 java.lang.String 是不可修改的）；
                    if (inst.isModifiableClass(claszz)){
                        //retransformClasses(cls) 会触发 transform() 方法，从而拦截类的原始字节码；
                        //跳过系统类以及动态Lambda类，防止死锁
                        //writer.println("[*] start class: " + claszz.getName());
                        if (claszz.getName().startsWith("java") || claszz.getName().startsWith("sun") || claszz.getName().startsWith("jdk") || claszz.getName().contains("LambdaForm$") || claszz.getName().contains("$$Lambda$")){
                            continue;
                        }
                        inst.retransformClasses(claszz);
                        //writer.println("[*dumpclass] Dumped  className = " + claszz.getName());
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
                writer.println(e);
            }
        }
        //清理 transformer 并返回结果
        // writer.println("[*] remove transformer");
        inst.removeTransformer(transformer);
        //导出字节码到class文件
        for (Map.Entry<String, byte[]> entry : classByteMap.entrySet()) {
            dumpClassToFile(entry.getKey(), entry.getValue(), writer);
        }
        return classByteMap;
    }

    private static void dumpClassToFile(String classname, byte[] classBytes, PrintWriter writer) {
        try {
            String baseDir = "/dumpClassFile";
            String filename = classname.replace('/','.') + ".class";
            File file = new File(baseDir +'/'+filename);
            File parent = file.getParentFile();
            //writer.println("[*]parent dir = " + parent.getAbsolutePath());
            if (!parent.exists()){
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(file)){
                fos.write(classBytes);
                fos.flush();
            }
        }catch (Exception e){
            writer.println(e);
        }
    }
}
