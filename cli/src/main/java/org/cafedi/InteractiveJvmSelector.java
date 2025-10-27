// language: java
package org.cafedi;

import java.io.Console;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * InteractiveJvmSelector: 在命令行展示 JVM 列表，允许用户选择并附加 agent。
 *
 * 使用示例（在 MainCli 中）：
 * List<Map.Entry<String,String>> jvms = getJVMlist();
 * InteractiveJvmSelector.selectAndAttach(jvms, out, err);
 */
public class InteractiveJvmSelector {
    /**
     * 列出 JVM 进程并交互选择，随后要求输入 agent 路径并调用 JvmAttacher.attach(...)
     *
     * @param entries 从 JvmProcessList.GetJVMProcessList() 得到的列表（每项 key=pid, value=display）
     * @param out 输出打印
     * @param err 错误打印
     * @return true 如果成功 attach 并加载 agent，false 否则（或用户取消）
     */
    public static boolean selectAndAttach(List<Map.Entry<String,String>> entries, PrintWriter out, PrintWriter err) {
        if (entries == null || entries.isEmpty()) {
            out.println("No JVM processes found.");
            return false;
        }

        out.println("Available JVM processes:");
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String,String> e = entries.get(i);
            out.printf("  [%d] pid=%s  %s%n", i + 1, e.getKey(), e.getValue());
        }
        out.println("  [0] Cancel");

        String input = readLine("Select a process number: ");
        if (input == null) {
            err.println("No console available and no input provided. Aborting.");
            return false;
        }

        int idx;
        try {
            idx = Integer.parseInt(input.trim());
        } catch (NumberFormatException nfe) {
            err.println("Invalid number.");
            return false;
        }
        if (idx == 0) {
            out.println("Cancelled by user.");
            return false;
        }
        if (idx < 1 || idx > entries.size()) {
            err.println("Selection out of range.");
            return false;
        }

        String pid = entries.get(idx - 1).getKey();
        out.printf("Selected pid=%s%n", pid);
        //todo 提供一个默认agent路径选项？
        String agentPath = readLine("Enter absolute path to agent.jar: ");
        if (agentPath == null || agentPath.trim().isEmpty()) {
            err.println("agent path is required. Aborting.");
            return false;
        }
        agentPath = agentPath.trim();
        out.printf("Attaching and loading agent (%s) ...%n", agentPath);
        boolean ok = JvmAttacher.attach(pid, agentPath, out, err);
        if (ok) out.println("Attach + load agent completed.");
        else err.println("Attach or agent load failed.");
        return ok;
    }

    private static String readLine(String prompt) {
        Console console = System.console();
        if (console != null) {
            return console.readLine(prompt);
        }
        // fallback to Scanner for IDE / redirected IO
        System.out.print(prompt);
        Scanner s = new Scanner(System.in);
        try {
            if (s.hasNextLine()) return s.nextLine();
            return null;
        } finally {
            // do not close System.in
        }
    }
}