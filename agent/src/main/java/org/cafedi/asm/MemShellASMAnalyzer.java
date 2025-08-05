package org.cafedi.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


import java.io.PrintWriter;
import java.io.Writer;
import java.util.*;
import java.util.regex.Pattern;

public class MemShellASMAnalyzer {
    // 定义一组可配置的检测规则：ownerPattern, methodPattern, label
    private static final List<Rule> RULES = Arrays.asList(
            //filter
            new Rule(".*Context.*", "addFilterMapBefore", "[Filter Shell]"),
            new Rule(".*javax/servlet/filterchain.*", "dofilter", "[Filter Shell]"),
            //servlet
            new Rule(".*Context.*", "addChild", "[Servlet Shell]"),
            new Rule(".*javax/servlet/HttpServletRequest.*", "service", "[Servlet Shell]"),
            new Rule(".*javax/servlet/HttpServletResponse.*", "service", "[Servlet Shell]"),
            new Rule(".*javax/servlet/http/HttpServletRequest.*", "doGet", "[Servlet Shell]"),
            new Rule(".*javax/servlet/http/HttpServletRequest.*", "doPost", "[Servlet Shell]"),
            new Rule(".*javax/servlet/http/HttpServletRequest.*", "doDelete", "[Servlet Shell]"),
            new Rule(".*javax/servlet/http/HttpServletRequest.*", "doHead", "[Servlet Shell]"),
            new Rule(".*javax/servlet/http/HttpServletRequest.*", "doOptions", "[Servlet Shell]"),
            new Rule(".*javax/servlet/http/HttpServletRequest.*", "doPut", "[Servlet Shell]"),
            new Rule(".*javax/servlet/http/HttpServletRequest.*", "Trace", "[Servlet Shell]"),
            //listener
            new Rule(".*Context.*", "addApplicationEventListener", "[Listener Shell]"),
            new Rule(".*javax/servlet/ServletRequestEvent.*", "requestInitialized", "[Listener Shell]"),
            new Rule(".*javax/servlet/ServletRequestEvent.*", "requestDestroyed", "[Listener Shell]"),
            //spring controller
            new Rule(".*springframework.*", "register.*", "[Spring Controller Shell]"),
            //proxy
            new Rule("java/lang/reflect/Proxy", "invoke", "[Proxy Shell]"),
            new Rule(".*ClassLoader.*", "defineClass.*", "[Dynamic Load Suspect]"),
            // dynamic load
            new Rule("sun/misc/Unsafe", "defineClass.*", "[Unsafe Dynamic Load]")
            // … 如需更多规则可继续在此添加
    );
    private static final Pattern JSP_TAG_PATTERN = Pattern.compile("<%.*%>|\\.jsp", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_KEYWORD_PATTERN = Pattern.compile("cmd=|shell|exec", Pattern.CASE_INSENSITIVE);

    public static void analyze(Map<String, byte[]> map, PrintWriter writer){
        try {
           //遍历map获取类名和字节码
           for(Map.Entry<String, byte[]> entry : map.entrySet()){
               String className = entry.getKey();
               byte[] classBytes = entry.getValue();
               //ClassReader：ASM 的核心工具，用于读取并解析 .class 字节数组；
               //accept(visitor, 0) 会遍历类结构（访问头部、字段、方法等）；
               //使用匿名的 ClassVisitor 进行访问。
               ClassReader cr = new ClassReader(classBytes);
               // 先在外层解析接口和 superName，用于后续规则判断
               ClassMetadata meta = new ClassMetadata();
               cr.accept(new ClassVisitor(Opcodes.ASM9) {
//                 String superName;
                   @Override
                   public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                       meta.className = name;
                       if (interfaces != null && interfaces.length > 0) {
                           //添加接口到集合列表
                           meta.interfaces.addAll(Arrays.asList(interfaces));
                           //writer.println("interfaces:"+meta.interfaces+"   classname:"+meta.className);
                       }
                   }
               }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
               // 再完整遍历，含方法调用和常量池检测
               cr.accept(new ClassVisitor(Opcodes.ASM9) {
                   private boolean isFilterLike = meta.interfaces.contains("javax/servlet/Filter")  || meta.className.toLowerCase().contains("filter");
                   private boolean isServletLike = meta.interfaces.contains("javax/servlet/Servlet")  || meta.className.toLowerCase().contains("servlet");
                   //可补充servlet等内存马特征
                   @Override
                   public  MethodVisitor visitMethod(int access, String mName, String desc, String signature, String[] exceptions){
                       return new MethodVisitor(Opcodes.ASM9) {
                           @Override
                           public void visitMethodInsn(int opcode, String owner, String methodName, String desc, boolean itf) {
                               String o = owner, m = methodName;
                               RULES.forEach(r -> {
                                   if (r.matches(o, m)) {
                                       //writer.printf("[%s] %s → %s#%s()%n", r.label, meta.className, owner, methodName);
                                       writer.printf("[%s] %s → %s()%n", r.label, meta.className.replace("/","."),methodName);
                                   }
                               });
                           }
                           @Override
                           public void visitLdcInsn(Object cst) {
                               if (cst instanceof String) {
                                   String s = (String) cst;
                                   if (JSP_TAG_PATTERN.matcher(s).find() || SHELL_KEYWORD_PATTERN.matcher(s).find()) {
                                       writer.printf("[常量检测] %s 含可疑脚本标记：%s%n", meta.className, s);
                                       if (meta.interfaces.contains("javax/servlet/Filter") || s.toLowerCase().contains("filter")) {
                                           writer.printf("[常量检测][Filter Shell] %s 含可疑脚本标记：%s%n", meta.className, s);
                                       }
                                       if (meta.interfaces.contains("javax/servlet/http/HttpServlet") || s.toLowerCase().contains("servlet")) {
                                           writer.printf("[常量检测][Servlet Shell] %s 含可疑脚本标记：%s%n", meta.className, s);
                                       }
                                       if (meta.interfaces.contains("javax/servlet/ServletRequestListener") || s.toLowerCase().contains("listener")) {
                                           writer.printf("[常量检测][Listener Shell] %s 含可疑脚本标记：%s%n", meta.className, s);
                                       }
                                   }
                               }
                           }
                       };
                   }
               },0);
           }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    //简单封装
    private  static class  Rule{
        final Pattern ownerPat, methodPat;
        final String label;
        Rule(String ownerRegex, String methodRegex, String label) {
            this.ownerPat = Pattern.compile(ownerRegex);
            this.methodPat = Pattern.compile(methodRegex);
            this.label = label;
        }
        boolean matches(String owner, String method) {
            return ownerPat.matcher(owner).find() && methodPat.matcher(method).find();
        }
    }
    // 临时存储类的元信息
    private static class ClassMetadata {
        String className;
        Set<String> interfaces = new HashSet<>();
    }
}
