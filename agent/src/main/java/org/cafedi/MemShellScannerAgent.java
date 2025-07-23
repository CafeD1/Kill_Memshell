package org.cafedi;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.Socket;

public class MemShellScannerAgent {
    public static void agentmain(String args, Instrumentation inst) throws IOException {
        //创建一个socket连接，监听8899端口
        try (Socket socket = new Socket("127.0.0.1", 8899);PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)){
            //枚举所有已加载类
            writer.println("[*] Scan Start");
            Class[] classes = inst.getAllLoadedClasses();
            String[] keywords = {"shell", "proxy", "evil", "inject", "mem"};
            for (Class<?> clazz : classes) {
                String className = clazz.getName().toLowerCase();
                Boolean suspicious = false;
                // 1. 类名关键字检测
                for (String keyword : keywords) {
                    if (className.contains(keyword)) {
                        suspicious = true;
                        writer.println("Suspicious keyword: " + keyword);
                        break;
                    }
                }
                // 2. 类加载器检测（不属于 AppClassLoader 可能是动态注入）
                ClassLoader cl = clazz.getClassLoader();
                if (cl != null && cl.getClass().getName().equals("AppClassLoader")) {
                    suspicious = true;
                    writer.println("[!] Loaded by suspicious ClassLoader: " + cl.getClass().getName() + " for class: " + clazz.getName());
                    break;
                }
                // 3. 接口/父类判断
                // 4. 可疑方法判断
                for (Method m : clazz.getDeclaredMethods()) {
                    String name = m.getName();
                    if (name.equals("dofilter")) {
                        suspicious = true;
                        writer.println("[!] Loaded by suspicious Method: " + clazz.getName() + "#" + name);
                    }
                }
                if (suspicious) {
                    writer.println("[!] Loaded by suspicious Method: " + clazz.getName());
                }
            }
            writer.println("[*]Scan Complete");
            // 4. 可疑方法判断
        }catch (Throwable e) {
            e.printStackTrace();
        }
    }
    public static void premain(String agentArgs, Instrumentation inst) {}

    public static void main(String[] args) {

    }

}
