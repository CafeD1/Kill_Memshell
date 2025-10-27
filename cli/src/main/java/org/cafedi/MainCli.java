package org.cafedi;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.cafedi.util.JvmProcessList;

import javax.swing.*;

/**
 * Main CLI: 将检测（detect）和清除（clean）逻辑分离，支持单独检测。
 */
public class MainCli {
    private static final AgentComm AGENT_COMM = new AgentComm();
    // 线程池用于后台任务（attach / socket send / 列表刷新等）
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r,"MainCli-Worker"+r.hashCode());
        t.setDaemon(true);
        return t;
    });
    enum TargetType {
        FILTER, SERVLET, LISTENER, CONTROLLER, INTERCEPTOR;
        //静态工厂方法，用于从字符串（如命令行参数）解析出对应的枚举值
        static TargetType parse(String s) {
            if (s == null) throw new IllegalArgumentException("type is null");
            switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "filter": return FILTER;
                case "servlet": return SERVLET;
                case "listener": return LISTENER;
                case "controller": return CONTROLLER;
                case "interceptor": return INTERCEPTOR;
                default: throw new IllegalArgumentException("unknown type: " + s);
            }
        }
        static String allowed() {
            return "filter|servlet|listener|controller|interceptor";
        }
    }
    enum Mode {
        DETECT, CLEAN;
        static Mode parse(String s) {
            if (s == null) throw new IllegalArgumentException("mode is null");
            switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "detect": return DETECT;
                case "clean": return CLEAN;
                default: throw new IllegalArgumentException("unknown mode: " + s);
            }
        }
    }
    public static void main(String[] args) {
        String target = null;
        TargetType type = TargetType.FILTER;
        Mode mode = Mode.CLEAN; // 默认执行清除
        boolean verbose = false;
        PrintWriter out = new PrintWriter(System.out, true);
        PrintWriter err = new PrintWriter(System.err, true);
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];

                if (a.equals("-h") || a.equals("--help")) {
                    printUsage(out);
                    return;
                } else if (a.equals("--detect-only")) {
                    mode = Mode.DETECT;
                } else if (a.equals("--verbose")) {
                    verbose = true;
                } else if (a.startsWith("--mode=")) {
                    mode = Mode.parse(a.substring("--mode=".length()));
                } else if (a.equals("--mode")) {
                    if (i + 1 < args.length) {
                        mode = Mode.parse(args[++i]);
                    } else {
                        err.println("missing value for " + a);
                        printUsage(err);
                        System.exit(1);
                        return;
                    }
                } else if (a.startsWith("--target=")) {
                    target = a.substring("--target=".length());
                } else if (a.equals("-t") || a.equals("--target")) {
                    if (i + 1 < args.length) {
                        target = args[++i];
                    } else {
                        err.println("missing value for " + a);
                        printUsage(err);
                        System.exit(1);
                        return;
                    }
                } else if (a.startsWith("-t") && a.length() > 2) { // -tVALUE
                    target = a.substring(2);
                } else if (a.startsWith("--type=")) {
                    type = TargetType.parse(a.substring("--type=".length()));
                } else if (a.equals("-T") || a.equals("--type")) {
                    if (i + 1 < args.length) {
                        type = TargetType.parse(args[++i]);
                    } else {
                        err.println("missing value for " + a);
                        printUsage(err);
                        System.exit(1);
                        return;
                    }
                } else if (a.startsWith("-T") && a.length() > 2) { // -TVALUE
                    type = TargetType.parse(a.substring(2));
                } else {
                    err.println("unknown option: " + a);
                    printUsage(err);
                    System.exit(1);
                    return;
                }
            }
            // 仅在 CLEAN 模式下强制要求 target；DETECT 模式允许不指定 target（进行更宽泛或交互式检测）
            if (target == null || target.trim().isEmpty()) {
                if (mode == Mode.CLEAN) {
                    err.println("target is required in clean mode");
                    printUsage(err);
                    System.exit(1);
                    return;
                }
            } else {
                target = target.trim();
            }
            CleanerService service = new CleanerService(verbose, out, err);
            if (mode == Mode.DETECT) {
                List<CleanerService.Finding> findings = service.detect(target, type);
                if (findings.isEmpty()) {
                    //out.println("No findings.");
                    System.exit(0);
                } else {
                    out.printf("Found %d issue(s):%n", findings.size());
                    for (CleanerService.Finding f : findings) {
                        out.printf(" - %s: %s%n", f.getLocation(), f.getMessage());
                    }
                    // 约定：检测模式当发现问题时返回非 0，用于脚本检测
                    System.exit(10);
                }
            } else { // CLEAN
                int cleaned = service.clean(target, type);
                out.printf("Clean completed. %d item(s) cleaned.%n", cleaned);
                System.exit(0);
            }
        } catch (IllegalArgumentException iae) {
            err.println("argument error: " + iae.getMessage());
            printUsage(err);
            System.exit(2);
        } catch (Exception e) {
            err.println("unexpected error: " + e.getMessage());
            if (verbose) e.printStackTrace(err);
            System.exit(3);
        }
    }

    private static void printUsage(PrintWriter out) {
        out.println("Usage: java -jar cafedi-cli.jar -t <target> [-T <type>] [--mode detect|clean] [--detect-only] [--verbose]");
        out.println("  -t, --target     target class name or pattern (required in clean mode; optional in detect mode)");
        out.println("  -T, --type       target type: " + TargetType.allowed() + " (default: filter)");
        out.println("  --mode           'detect' or 'clean' (default: clean)");
        out.println("  --detect-only    explicit alias for detect mode");
        out.println("  --verbose        print detailed information");
        out.println("  -h, --help       show this help");
        out.println();
        out.println("Notes:");
        out.println("  In detect mode the program returns exit code 0 if nothing found, 10 if findings exist.");
    }

    /**
     * CleanerService: 抽象出检测与清除逻辑
     *
     * 在真实工程中，把 detect(...) / clean(...) 的实现替换为对你现有 CleanerRunner 的调用。
     */
    static class CleanerService {
        private final boolean verbose;
        private final PrintWriter out;
        private final PrintWriter err;

        CleanerService(boolean verbose, PrintWriter out, PrintWriter err) {
            this.verbose = verbose;
            this.out = out;
            this.err = err;
        }

        /**
         * 检测目标并返回发现项列表（不修改任何内容）。
         * TODO: 用实际的检测实现替换当前模拟实现。
         * 目标 target 在 detect 模式下可以为 null，表示更宽泛的检测或交互式选择。
         */
        List<Finding> detect(String target, TargetType type) {
            if (verbose) out.printf("Detecting target=%s type=%s%n", target, type);
            // ==== 检测开始 ====
            //获取JVM进程列表
            List<Map.Entry<String,String>> jvms = MainCli.getJVMlist();
            InteractiveJvmSelector.selectAndAttach(jvms, out, err);
            List<Finding> list = new ArrayList<>();
            String t = target == null ? "" : target;
            if (t.toLowerCase(Locale.ROOT).contains("vuln")) {
                list.add(new Finding(t.isEmpty() ? "<all>" : t, "Suspicious registration detected"));
            }
            // ==== 检测结束 ====
            //out.printf("Detection completed. %d finding(s) found.%nFindings: %s%n", list.size(), list);
            return list;
        }

        /**
         * 执行清除逻辑：通常先检测再对每个发现执行清除动作，返回成功清除的数量。
         * TODO: 用实际的清除实现替换当前模拟实现。
         */
        int clean(String target, TargetType type) {
            if (verbose) out.printf("Cleaning target=%s type=%s%n", target, type);
            if (target == null || target.trim().isEmpty()) {
                err.println("clean requires a non-empty target");
                return 0;
            }
            int cleaned = 0;
            // ==== 清除开始 ====
            target = target.trim();
            // 输入格式校验
            if (!target.startsWith("[")) {
                err.println("请按照格式 [filter/servlet/...]classname 输入要清除的内存马类名");
                return 0;
            }
            sendCleanToAgent(target,out, err);
            // ==== 清除结束 ====

            return cleaned;
        }

        /**
         * 检测结果对象
         */
        static class Finding {
            private final String location;
            private final String message;

            Finding(String location, String message) {
                this.location = location;
                this.message = message;
            }

            public String getLocation() {
                return location;
            }

            public String getMessage() {
                return message;
            }
        }
    }
    public static List<Map.Entry<String,String>> getJVMlist() {
        //获取JVM进程列表
        JvmProcessList jvm = new JvmProcessList();
        List<Map.Entry<String,String>> entries = jvm.GetJVMProcessList();
        return entries;
    }
    /**
     * 向 agent 发送清除命令（agent 端的命令监听在 9900）。
     * @param out 正常输出
     * @param err 错误输出
     * @return true 如果发送成功
     */
    public static void sendCleanToAgent(String target,PrintWriter out, PrintWriter err) {
        // AGENT_COMM 会在内部打印发送状态到 out/err
        AGENT_COMM.sendClean(target,out, err);
    }
}