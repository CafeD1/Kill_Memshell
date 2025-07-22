package org.cafedi;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

public class MemShellScannerAgent {
    public static void agentmain(String args, Instrumentation inst) {
        //枚举所有已加载类
        Class[] classes = inst.getAllLoadedClasses();
        String[] keywords = {"shell", "proxy", "evil", "inject","mem"};
        for (Class<?> clazz: classes) {
            String className = clazz.getName().toLowerCase();
            Boolean suspicious = false;
            // 1. 类名关键字检测
            for (String keyword: keywords) {
                if (className.contains(keyword)) {
                    suspicious = true;
                    System.out.println("Suspicious keyword: " + keyword);
                    break;
                }
            }
            // 2. 类加载器检测（不属于 AppClassLoader 可能是动态注入）
            ClassLoader cl = clazz.getClassLoader();
            if (cl !=null && cl.getClass().getName().equals("AppClassLoader")) {
                suspicious = true;
                System.out.println("[!] Loaded by suspicious ClassLoader: " + cl.getClass().getName() + " for class: " + clazz.getName());
                break;
            }
            // 3. 接口/父类判断
            // 4. 可疑方法判断
            for (Method m: clazz.getDeclaredMethods()) {
                String name = m.getName();
                if (name.equals("dofilter") || name.equals("service") || name.equals("equals")) {
                    suspicious = true;
                    System.out.println("[!] Loaded by suspicious Method: " + clazz.getName()+"#"+name);
                }
            }
            if (suspicious) {
                System.out.println("[!] Loaded by suspicious Method: " + clazz.getName());
            }
        }

        // 4. 可疑方法判断
    }
    public static void premain(String agentArgs, Instrumentation inst) {}

}
