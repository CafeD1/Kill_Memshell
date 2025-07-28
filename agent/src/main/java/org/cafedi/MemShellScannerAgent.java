package org.cafedi;

import org.cafedi.asm.BytecodeDumper;
import org.cafedi.asm.MemShellASMAnalyzer;
import org.cafedi.asm.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MemShellScannerAgent {
    public static void agentmain(String args, Instrumentation inst) throws IOException {
        //创建一个socket连接，监听8899端口
        try (Socket socket = new Socket("127.0.0.1", 8899);PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)){
            //枚举所有已加载类
            writer.println("[******]Scan Start[*******]");
            Class[] classes = inst.getAllLoadedClasses();
            //黑名单
            List<String> black_keywords = Arrays.asList("shell", "proxy", "evil", "inject", "mem");
            //白名单
            List<String> white_keywords = Arrays.asList("org.springframework", "jakarta.servlet", "java.", "javax.");
            for (Class<?> clazz : classes) {
                String className = clazz.getName().toLowerCase();
                //白名单过滤
                if (white_keywords.stream().anyMatch(className::startsWith)) continue;
                Boolean suspicious = false;
                // 1. 类名关键字检测
                for (String keyword : black_keywords) {
                    if (className.contains(keyword)) {
                        suspicious = true;
                        //writer.println("Suspicious ClassName: " + className);
                        break;
                    }
                }
                // 2. 类加载器检测（不属于 AppClassLoader 可能是动态注入）
                ClassLoader cl = clazz.getClassLoader();
                if (cl != null && cl.getClass().getName().equals("AppClassLoader")) {
                    suspicious = true;
                    //writer.println("[!] Loaded by suspicious ClassLoader: " + cl.getClass().getName() + " for class: " + clazz.getName());
                }
                // 3. 接口/父类判断
                // 4. 可疑方法判断
                for (Method m : clazz.getDeclaredMethods()) {
                    String name = m.getName();
                    if (name.equals("dofilter")) {
                        suspicious = true;
                        //writer.println("[!] Loaded by suspicious Method: " + clazz.getName() + "#" + name);
                    }
                }
                if (suspicious) {
                    writer.println("[!]Loaded by suspicious Class: " + clazz.getName());
                    //获取字节码
                    writer.println("[*]Start dump classbytes");
                    Map<String,byte[]> classbyteMap = BytecodeDumper.dumpAllLoadClass(inst,clazz.getName(),writer);
                    //写入.class文件，用于反编译或分析
                    //todo
                    writer.println("[*]End dump classbytes");
                    //使用ASM详细分析可疑代码
                    writer.println("[*]Start asm analyze"+" Map Size: " + classbyteMap.size());
                    //Test.test(classbyteMap,writer);
                    MemShellASMAnalyzer.analyze(classbyteMap,writer);
                }
            }
            writer.println("[******]Scan Complete[******]");
            // 4. 可疑方法判断
        }catch (Throwable e) {
            e.printStackTrace();
        }
    }
    public static void premain(String agentArgs, Instrumentation inst) {}

    public static void main(String[] args) {

    }

}
