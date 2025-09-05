package org.cafedi;

import org.cafedi.asm.BytecodeDumper;
import org.cafedi.asm.MemShellASMAnalyzer;
import org.cafedi.clean.CleanUp;


import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MemShellScannerAgent {
    private static Socket  eightNine_socket;
    private static PrintWriter writer;
    public static void agentmain(String args, Instrumentation inst) throws IOException {
        //创建一个socket连接，监听8899端口
        eightNine_socket = new Socket("127.0.0.1", 8899);
        writer = new PrintWriter(new OutputStreamWriter(eightNine_socket.getOutputStream(), StandardCharsets.UTF_8), true);
        //writer.println("Agent defaultCharset=" + java.nio.charset.Charset.defaultCharset());
        //启动命令监听线程
        startCommandListener(inst);
        try {
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
                    if (name.equals("dofilter") || name.equals("doGet") || name.equals("doPost") || name.equals("doPut") || name.equals("doDelete") || name.equals("doHead") || name.equals("doOptions") || name.equals("Trace")) {
                        suspicious = true;
                        //writer.println("[!] Loaded by suspicious Method: " + clazz.getName() + "#" + name);
                    }
                }
                if (suspicious) {
                    writer.println("[!]Loaded by suspicious Class: " + clazz.getName());
                    //获取字节码
                    writer.println("[*]Start dump classbytes");
                    Map<String,byte[]> classbyteMap = BytecodeDumper.dumpAllLoadClass(inst,clazz.getName(),writer);
                    writer.println("[*]End dump classbytes");
                    //使用ASM详细分析可疑代码
                    if (classbyteMap != null || classbyteMap.size() > 0) {
                        writer.println("[*]Start asm analyze");
                        MemShellASMAnalyzer.analyze(classbyteMap,writer);
                        writer.println("[*]End asm analyze");
                    }

                }
            }
            writer.println("[******]Scan Complete[******]");
            // 4. 可疑方法判断
        }catch (Throwable e) {
            e.printStackTrace();
        }
    }
    public static void premain(String agentArgs, Instrumentation inst) {}
    private static void startCommandListener(Instrumentation inst) {
        new Thread(()->{
            try (ServerSocket cmdServer = new ServerSocket(9900)){
                writer.println("[*]Start command listener 9900 Port");
                while (true){
                    Socket socket = cmdServer.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.contains("[clean]")) {
                            String target = line.substring(7, line.length());
                            writer.println("[*]Agent receive clean target :" + target.replaceAll("\\[[^\\]]*\\]",""));
                            CleanUp.clean(target,inst,writer);
                        }
                    }

                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "Agent-Command-Listener").start();
    }
    private static  boolean isPortAvailable() {
        try (ServerSocket testSocket = new ServerSocket(9900)) {
            testSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    public static void main(String[] args) {

    }

}
