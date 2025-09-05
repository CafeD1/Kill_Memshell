package org.cafedi.detect;

//import org.springframework.context.ApplicationContext;
//import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

/**
 * 反射读取 RequestMappingHandlerMapping 的 mappingRegistry 示例
 */
//public class HandlerMappingHelper {
//    public static void inspect(ApplicationContext ctx) {
//        try {
//            // 1. 通过 context 拿到 RequestMappingHandlerMapping
//            RequestMappingHandlerMapping mapping = ctx.getBean(RequestMappingHandlerMapping.class);
//            System.out.println("Found RequestMappingHandlerMapping: " + mapping.getClass().getName());
//            // 2. 向上遍历类层级，查找名为 mappingRegistry 的字段
//            Field registryField = findFieldInHierarchy(mapping.getClass(), "mappingRegistry");
//            if (registryField == null) {
//                System.out.println("mappingRegistry 字段未找到。可能是 Spring 版本不同或字段改名。建议直接使用 getHandlerMethods()。");
//                return;
//            }
//            // 尝试访问私有字段（注意：在 Java9+ 需要 --add-opens 或等价权限）
//            registryField.setAccessible(true);
//            Object mappingRegistry = registryField.get(mapping);
//            if (mappingRegistry == null) {
//                System.out.println("mappingRegistry 为 null（尚未初始化或没有 handler）。");
//                return;
//            }
//            System.out.println("mappingRegistry 类型: " + mappingRegistry.getClass().getName());
//            // 3. mappingRegistry 内部通常有若干 Map 字段，尝试打印这些 Map 的内容（容错处理）
//            Field[] fields = mappingRegistry.getClass().getDeclaredFields();
//            for (Field f : fields) {
//                f.setAccessible(true);
//                Object value = f.get(mappingRegistry);
//                if (value instanceof Map) {
//                    System.out.println("Found Map field: " + f.getName() + " -> size=" + ((Map<?, ?>) value).size());
//                    // 打印部分 key/value（别全部打印）
//                    int printed = 0;
//                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
//                        if (printed++ > 20) break; // 避免海量输出
//                        System.out.println("  key: " + entry.getKey() + " -> val: " + entry.getValue());
//                    }
//                } else if (value instanceof Collection) {
//                    System.out.println("Found Collection field: " + f.getName() + " -> size=" + ((Collection<?>) value).size());
//                } else {
//                    // 打印小类型或 null
//                    if (!Modifier.isStatic(f.getModifiers())) {
//                        System.out.println("Other field: " + f.getName() + " -> " + String.valueOf(value));
//                    }
//                }
//            }
//            // 额外：尝试调用 getHandlerMethods() 作为对比
//            // mapping.getHandlerMethods() 是公开 API，可以直接使用
//            mapping.getHandlerMethods().forEach((k, v) -> {
//                System.out.println("pattern: " + k + " -> handler: " + v);
//            });
//        } catch (NoSuchFieldException nsfe) {
//            System.err.println("反射时找不到字段: " + nsfe.getMessage());
//        } catch (Exception e) {
//            System.err.println("inspect 发生异常: " + e.getClass().getName() + " - " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//    // 在类及其父类中查找字段名（兼容继承链）
//    private static Field findFieldInHierarchy(Class<?> cls, String fieldName) throws NoSuchFieldException {
//        Class<?> cur = cls;
//        while (cur != null && cur != Object.class) {
//            try {
//                Field f = cur.getDeclaredField(fieldName);
//                return f;
//            } catch (NoSuchFieldException e) {
//                cur = cur.getSuperclass();
//            }
//        }
//        // 若没有找到，抛出或返回 null（这里返回 null，调用方处理）
//        return null;
//    }
//}