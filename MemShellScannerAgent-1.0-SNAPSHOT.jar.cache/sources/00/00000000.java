package agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

/* loaded from: MemShellScannerAgent-1.0-SNAPSHOT.jar:agent/MemShellScannerAgent.class */
public class MemShellScannerAgent {
    public static void agentmain(String args, Instrumentation inst) {
        Method[] declaredMethods;
        Class<?>[] classes = inst.getAllLoadedClasses();
        String[] keywords = {"shell", "proxy", "evil", "inject", "mem"};
        for (Class<?> clazz : classes) {
            String className = clazz.getName().toLowerCase();
            Boolean suspicious = false;
            int length = keywords.length;
            int i = 0;
            while (true) {
                if (i >= length) {
                    break;
                }
                String keyword = keywords[i];
                if (!className.contains(keyword)) {
                    i++;
                } else {
                    suspicious = true;
                    System.out.println("Suspicious keyword: " + keyword);
                    break;
                }
            }
            ClassLoader cl = clazz.getClassLoader();
            if (cl != null && cl.getClass().getName().equals("AppClassLoader")) {
                System.out.println("[!] Loaded by suspicious ClassLoader: " + cl.getClass().getName() + " for class: " + clazz.getName());
                return;
            }
            for (Method m : clazz.getDeclaredMethods()) {
                String name = m.getName();
                if (name.equals("dofilter") || name.equals("service") || name.equals("equals")) {
                    suspicious = true;
                    System.out.println("[!] Loaded by suspicious Method: " + clazz.getName() + "#" + name);
                }
            }
            if (suspicious.booleanValue()) {
                System.out.println("[!] Loaded by suspicious Method: " + clazz.getName());
            }
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) {
    }
}