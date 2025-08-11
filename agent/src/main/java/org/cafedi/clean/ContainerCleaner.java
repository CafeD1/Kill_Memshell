package org.cafedi.clean;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 通过 JMX 获取 StandardContext（managedResource）并移除指定 Filter 的实现（通过类名或类型匹配）。
 */
public class ContainerCleaner {

    /**
     * 从所有 Context MBean 中移除匹配的 Filter（按类名匹配或按类可分配性匹配）。
     *
     * @param filterClass 要移除的 Filter 的 Class（注意：可能与 webapp 中的类由不同的 classloader 加载，故做类名匹配）
     * @param writer      输出日志
     */
    //@SuppressWarnings("unchecked")
    public static void removeFilter(Class<?> filterClass, Instrumentation inst, PrintWriter writer) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            // 查询所有 type=Context 的 MBean（不同 Tomcat 版本 ObjectName 可能略有差异，用通配）
            Set<ObjectName> contextNames = mbs.queryNames(new ObjectName("*:type=Context,*"), null);
            if (contextNames == null || contextNames.isEmpty()) {
                writer.println("[!] 未找到 Context MBean（可能 Tomcat 未启用 JMX 或尚未加载 Context）");
                return;
            }
            boolean anyRemoved = false;
            for (ObjectName on : contextNames) {
                Object mbeanCtx;
                try {
                    mbeanCtx = mbs.getAttribute(on, "managedResource");
                } catch (Exception e) {
                    writer.println("[!] 读取 managedResource 失败: " + on + " -> " + e);
                    continue;
                }
                if (mbeanCtx == null) {
                    writer.println("[*] managedResource 为 null: " + on);
                    continue;
                }
                Class<?> ctxClass = mbeanCtx.getClass();
                writer.println("[*] Found context instance: class=" + ctxClass.getName() + " objectName=" + on);
                // 在类或其父类上查找 filterConfigs 字段
                Field configsField = findFieldInHierarchy(ctxClass, "filterConfigs");
                if (configsField == null) {
                    writer.println("[!] " + ctxClass.getName() + " 没有 filterConfigs 字段，跳过 " + on);
                    continue;
                }
                configsField.setAccessible(true);

                Map<String, Object> filterConfigs;
                try {
                    filterConfigs = (Map<String, Object>) configsField.get(mbeanCtx);
                } catch (Exception e) {
                    writer.println("[!] 读取 filterConfigs 失败: " + e);
                    continue;
                }
                if (filterConfigs == null || filterConfigs.isEmpty()) {
                    writer.println("[*] filterConfigs 为空: " + on);
                    continue;
                }
                // 遍历并移除匹配项
                Iterator<Map.Entry<String, Object>> it = filterConfigs.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Object> entry = it.next();
                    Object cfg = entry.getValue();
                    if (cfg == null) continue;

                    Field filterField = findFieldInHierarchy(cfg.getClass(), "filter");
                    if (filterField == null) {
                        writer.println("[!] ApplicationFilterConfig 或类似对象没有 filter 字段: " + cfg.getClass().getName());
                        continue;
                    }
                    filterField.setAccessible(true);
                    Object filterObj;
                    try {
                        filterObj = filterField.get(cfg);
                    } catch (Exception e) {
                        writer.println("[!] 读取 filter 字段失败: " + e);
                        continue;
                    }
                    if (filterObj == null) continue;
                    boolean match = false;
                    // 优先按类名匹配（跨 classloader 安全）
                    if (filterObj.getClass().getName().equals(filterClass.getName())) {
                        match = true;
                    } else {
                        // 如果在同一类加载器范围内，尝试按类型判断（可选）
                        try {
                            if (filterClass.isAssignableFrom(filterObj.getClass())) {
                                match = true;
                            }
                        } catch (Throwable ignored) {
                            // 不同 classloader 时可能抛异常或不可比，忽略
                        }
                    }
                    if (match) {
                        it.remove();
                        anyRemoved = true;
                        writer.println("[+][Filter Removed] context=" + on + " filterName=" + entry.getKey()
                                + " filterClass=" + filterObj.getClass().getName());
                    }
                } // end iterate filterConfigs
                // 可选：如果移除了 filter，需要同时更新 filter mappings / lifecycle（取决于 Tomcat 版本）。
                // 这里仅移除 filterConfigs 的条目；如需完全生效，可能需要额外清理 filterMappings 或触发 reload。
            } // end for each context
            if (!anyRemoved) {
                writer.println("[*] 未找到任何匹配的 Filter: " + filterClass.getName());
            } else {
                writer.println("[*] 完成移除动作（注意：某些更改在运行时可能需要 reload 或额外清理才能完全生效）");
            }
        } catch (Exception e) {
            writer.println("[!] removeFilter 失败: " + e);
            e.printStackTrace(writer);
        }
    }
    /**
     * 在类及其父类链中查找字段（返回第一个匹配到的 Field），找不到返回 null。
     */
    private static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(fieldName);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }
}
