package org.cafedi.clean;

import org.apache.catalina.core.StandardContext;
import javax.management.*;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ContainerCleaner {

    /**
     * 移除所有类型为 filterClass 的 Filter
     */
    @SuppressWarnings("unchecked")
    public static void removeFilter(Class<?> filterClass, Instrumentation inst, PrintWriter writer) {
        try {
            // 1. 找到 StandardContext 的 Class 对象和对应的 ClassLoader
            Class<?> standardContextClass = null;
            ClassLoader webappCl = null;
            for (Class<?> clz : inst.getAllLoadedClasses()) {
                if ("org.apache.catalina.core.StandardContext".equals(clz.getName())) {
                    standardContextClass = clz;
                    break;
                }
            }
            for (Class<?> load : inst.getAllLoadedClasses()) {
                writer.println("load: " + load.getClassLoader());
                if ("org.apache.catalina.loader.WebappClassLoaderBase".equals(load.getClassLoader())) {
                    webappCl = load.getClassLoader();
                    break;
                }
            }
            writer.println("standardContextClass=" + standardContextClass);
            writer.println("webappCl=" + webappCl);
            if (standardContextClass == null || webappCl == null) {
                throw new IllegalStateException("StandardContext 未加载或 Tomcat 版本不匹配");
            }
            // 2. 通过 WebappClassLoaderBase 的私有字段 'context' 拿到 StandardContext 实例
            Field ctxField = webappCl.getClass().getDeclaredField("context");
            ctxField.setAccessible(true);
            StandardContext ctx = (StandardContext) ctxField.get(webappCl);
            writer.println("[*] Obtained StandardContext: " + ctx.getName());

            // 3. 获取 filterConfigs 字段并遍历移除目标 Filter
            Field configsField = standardContextClass.getDeclaredField("filterConfigs");
            configsField.setAccessible(true);
            Map<String, Object> filterConfigs = (Map<String, Object>) configsField.get(ctx);

            Iterator<Map.Entry<String, Object>> it = filterConfigs.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                Object cfg = entry.getValue();
                Field filterField = cfg.getClass().getDeclaredField("filter");
                filterField.setAccessible(true);
                Object filterObj = filterField.get(cfg);

                if (filterClass.equals(filterObj.getClass())) {
                    it.remove();
                    writer.println("[+][FilterShell] Removed Filter: " + entry.getKey());
                }
            }

        } catch (Exception e) {
            writer.println("[!] filter 内存马清除失败: " + filterClass.getName());
            e.printStackTrace(writer);
        }
    }
}
