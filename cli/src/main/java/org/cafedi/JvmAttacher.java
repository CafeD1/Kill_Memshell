package org.cafedi;

import com.sun.tools.attach.VirtualMachine;

import java.io.PrintWriter;

/**
 * JvmAttacher: 使用 Attach API 将 agent 加载到目标 JVM。
 * 集成 AgentComm：在 attach 前启动本地 8899 输出监听，便于接收 agent 回显。
 */
public class JvmAttacher {
    // 复用同一个 AgentComm 实例并保证监听只启动一次
    private static final AgentComm AGENT_COMM = new AgentComm();
    private static volatile boolean listenerStarted = false;
    /**
     * @param pid 目标 JVM 进程 id
     * @param agentPath agent jar 的绝对路径
     * @param out 输出流
     * @param err 错误流
     * @return true 如果加载成功，false 否则
     */
    public static boolean attach(String pid, String agentPath, PrintWriter out, PrintWriter err) {
        if (pid == null || pid.isEmpty()) {
            err.println("pid is empty");
            return false;
        }
        if (agentPath == null || agentPath.isEmpty()) {
            err.println("agentPath is required");
            return false;
        }
        // 在 attach 前启动 agent 输出监听（8899），确保 agent 连接回 CLI 时可以被接收
        startAgentOutputListenerOnce(out, err);

        VirtualMachine vm = null;
        try {
            out.printf("Attaching to pid=%s ...%n", pid);
            vm = VirtualMachine.attach(pid);
            out.printf("Attached. Loading agent: %s%n", agentPath);
            vm.loadAgent(agentPath, "agent");
            out.println("Agent loaded successfully.");
            // agent 在 agentmain 中会连接回 8899 并开始输出，AgentComm 的监听线程会回显这些行
            return true;
        } catch (Throwable t) {
            err.printf("Failed to attach/load agent: %s%n", t.getMessage());
            if (!out.checkError()) t.printStackTrace(err);
            return false;
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                    out.println("Detached from target JVM.");
                } catch (Throwable t) {
                    // ignore detach error but report
                    err.printf("Warning: failed to detach: %s%n", t.getMessage());
                }
            }
        }
    }
    public static synchronized void startAgentOutputListenerOnce(PrintWriter out, PrintWriter err) {
        if (listenerStarted) return;
        try {
            AGENT_COMM.startOutputListener(out, err);
            listenerStarted = true;
        } catch (Throwable t) {
            // 不中断 attach 流程，只记录错误
            if (err != null) err.printf("Warning: failed to start agent output listener: %s%n", t.getMessage());
        }
    }
}