package org.cafedi;

import org.cafedi.util.SocketServer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
/**
 * AgentComm: 在 CLI 侧启动 8899 输出监听（使用 org.cafedi.util.SocketServer）
 * 并提供发送命令到 agent（默认 agent 命令端口 9900）的工具方法。
 */
public class AgentComm {
    private Thread serverThread;
    private static final String agentHost = "127.0.0.1";
    private static final int agentPort = 9900;
    private int connectTimeoutMs = 5000;
    /**
     * 启动一个后台守护线程，运行 org.cafedi.util.SocketServer，
     * 将接收到的每行输出传到 out。
     *
     * 注意：org.cafedi.util.SocketServer 内部使用固定端口 8899（与 agent 相配合）。
     */
    public void startOutputListener(PrintWriter out, PrintWriter err) {
        if (out == null) throw new IllegalArgumentException("out is required");
        Consumer<String> consumer = line -> {
            // 直接回显 agent 发来的每一行
            out.println(line);
        };
        SocketServer server = new SocketServer(consumer);
        serverThread = new Thread(server, "Agent-Output-Listener");
        serverThread.setDaemon(true);
        serverThread.start();
        out.println("[*] agent output listener started (port 8899)");
    }
    /**
     * 单向发送清除命令到 agent（agent 的命令监听端口通常是 9900）。
     *
     * 命令格式： [clean]target
     *
     * @return true 表示发送成功（写入并 flush 成功），false 否则
     */
    public void sendClean(String target,PrintWriter out, PrintWriter err) {
        String cmd = "[clean]" + target;
        if (out != null) out.printf("[*] sending clean command to %s:%d -> %s%n", agentHost, agentPort, cmd);
        sendRawOneWay(agentHost, agentPort, cmd, connectTimeoutMs, out, err);
    }
    /**
     * 发送任意单行命令到 agent，并立即返回（不等待响应）。
     */
    public void sendRawOneWay(String host, int port, String cmd,
                                 int connectTimeoutMs, PrintWriter out, PrintWriter err) {
        if (cmd == null) {
            if (err != null) err.println("cmd is null");
            System.exit(0);
        }
        try (Socket sock = new Socket(host,port)) {
            PrintWriter writer = new PrintWriter(sock.getOutputStream(),true);
            writer.println(cmd);
            out.println("[*] command sent");
//            sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
//            try (BufferedWriter w = new BufferedWriter(
//                    new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8))) {
//                w.write(line);
//                w.write("\n");
//                w.flush();
//                if (out != null) out.println("[*] command sent");
//                return true;
//            }
        } catch (IOException e) {
            if (err != null) err.println("failed to send command: " + e.getMessage());
        }
    }
}