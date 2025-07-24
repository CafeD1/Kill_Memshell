package org.cafedi.asm;
/*
注册一个 ClassFileTransformer；

对 JVM 中所有已加载的类调用 retransformClasses；

在 transform 方法中拦截类的原始字节码（classfileBuffer）；

保存到 classByteMap 中，供后续分析（如使用 ASM 判断是否是内存马）
 */

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

public class BytecodeDumper {
    //声明一个hashmap用于保存类名和对应的字节码
    private static  final Map<String,byte[]> classByteMap = new HashMap<>();
    public static Map<String,byte[]> dumpAllLoadClass(Instrumentation inst) {
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
        //遍历所有已加载类，触发重新转换
        for (Class<?> claszz : inst.getAllLoadedClasses()){
            try {
                //inst.isModifiableClass(cls) 判断该类是否可以被 retrans（有些核心类如 java.lang.String 是不可修改的）；
                if (inst.isModifiableClass(claszz)){
                    //retransformClasses(cls) 会触发 transform() 方法，从而拦截类的原始字节码；
                    inst.retransformClasses(claszz);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        //清理 transformer 并返回结果
        inst.removeTransformer(transformer);
        return classByteMap;
    }
}
