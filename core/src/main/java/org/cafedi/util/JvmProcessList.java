package org.cafedi.util;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.util.*;

public class JvmProcessList {
    public static List<Map.Entry<String,String>> GetJVMProcessList() {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        //获取进程列表
        try {
            List<VirtualMachineDescriptor> vmList = VirtualMachine.list();
            for (VirtualMachineDescriptor vmd : vmList) {
               entries.add(new AbstractMap.SimpleEntry<>(vmd.id(), vmd.displayName()));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return entries;
    }

    public static void main(String[] args) {
        JvmProcessList jvm = new JvmProcessList();
        List<Map.Entry<String,String>> entries = jvm.GetJVMProcessList();
        System.out.println(entries);
    }
}

