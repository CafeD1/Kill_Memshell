package org.cafedi.clean;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

public class CleanUp implements ServletContextListener {
    public  static void  clean(String targetClass, Instrumentation inst, PrintWriter writer){
        //判断内存马类型
        boolean filterMem = false;
        if (targetClass.contains("filter")){
            filterMem = true;
            targetClass = targetClass.replaceAll("\\[[^\\]]*\\]","");
        }
        //找到所有类名中包含target的Class对象
        List<Class<?>> suspects = new ArrayList<>();
        for(Class<?> clazz : inst.getAllLoadedClasses()){
            if (clazz.getName().contains(targetClass)){
                suspects.add(clazz);
            }
        }
        //断开入口：如果是Filter，则移除
        for(Class<?> clazz : suspects){
            //判断cls所表示的类型，执行对应的清除方法。
            if (filterMem){
                writer.println("开始清除filter: " + clazz);
                ContainerCleaner.removeFilter(clazz,inst,writer);
            }
        }
        // 热替换字节码：清空原有实现
        ClassUnloader unloader = new ClassUnloader(inst,writer);
        for(Class<?> clazz : suspects){
            unloader.unloaderClass(clazz.getName());
        }
    }
}
