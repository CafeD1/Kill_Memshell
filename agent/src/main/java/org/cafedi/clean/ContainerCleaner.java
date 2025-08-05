package org.cafedi.clean;

import com.sun.org.apache.xalan.internal.xsltc.dom.Filter;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

public class ContainerCleaner {
    /**
     * 移除所有类型为 filterClass 的 Filter
     */
    public static void removeFilter(Class<?> filterClass, PrintWriter writer) {
        try {
            //获取standcontext实例
            Class<?> contextClz = Class.forName("org.apache.catalina.core.StandardContext");
            // filterConfigs 字段对象
            Field configField = contextClz.getDeclaredField("filterConfigs");
            configField.setAccessible(true);
            // 获取当前 webapp 的 StandardContext 实例
            Object standcontext = getStandContext();
            //获取对象动态字段值，从拿到的 StandardContext 实例中，读取出filterConfigs 这个 Map,这个 Map 的 键 是 Filter 名称,值是ApplicationFilterConfig 对象
            Map<String,Object> filterConfigs = (Map<String,Object>)configField.get(standcontext);
            //反射拿到真正的 Filter 实例：ApplicationFilterConfig 包含一个私有字段 filter，即你自定义或内存马注册的 Filter 对象。
            Iterator<Map.Entry<String,Object>> iterator = filterConfigs.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String,Object> entry = iterator.next();
                Object config = entry.getValue();
                Field filterField = config.getClass().getDeclaredField("filter");
                filterField.setAccessible(true);
                Filter f = (Filter) filterField.get(config);
                // 判断此 Filter 是否为我们要清除的内存马
                if (f.getClass().equals(filterClass)) {
                    iterator.remove();
                    writer.println("[+][Filter Shell] Removed Filter: " + entry.getKey());
                }
            }
        }
        catch (Exception e) {
            writer.println("[!] Failed to remove filter: " + filterClass.getName());
            e.printStackTrace();
        }
    }
    //通过当前线程栈匹配获取 StandardContext 对象
    private static Object getStandContext() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            for (StackTraceElement elem : t.getStackTrace()) {
                if (elem.getClassName().contains("ApplicationFilterChain")) {
                    Class<?> chainClz = Class.forName(elem.getClassName());
                    Field contextField = chainClz.getDeclaredField("context");
                    contextField.setAccessible(true);
                    return contextField.get(null);
                }
            }
        }
        throw new IllegalStateException("StandardContext not found");
    }
}
