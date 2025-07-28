package org.cafedi.asm;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;

public class Test {
    public static void test(Map<String, byte[]> map, PrintWriter writer) {
        writer.println("[*]Test");
        //遍历map获取类名和字节码
        for (Map.Entry<String, byte[]> entry : map.entrySet()) {
            String className = entry.getKey();
            byte[] classByte = entry.getValue();
            //writer.println("[*ClassByte]"+ Arrays.toString(classByte));
        }
    }
}
