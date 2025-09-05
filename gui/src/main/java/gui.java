import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Executable;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
//import com.jgoodies.forms.factories.*;
//import com.jgoodies.forms.layout.*;
//import net.miginfocom.swing.*;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.cafedi.util.JvmProcessList;
import org.cafedi.util.SocketServer;

import static java.util.List.*;
//import org.jdesktop.swingx.*;
/*
 * Created by JFormDesigner on Thu Jul 17 17:43:59 CST 2025
 */



/**
 * @author DZ
 */
public class gui extends JFrame {
    // 单例 SocketServer 线程和对象，防止重复启动
    private Thread srvThread;
    private SocketServer socketServer;
    private final int UI_PORT = 8899;   // UI 监听端口（agent 发消息到 UI）
    private final int CLEAN_PORT = 9900;
    // 线程池用于后台任务（attach / socket send / 列表刷新等）
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gui-worker-" + r.hashCode());
        t.setDaemon(true);
        return t;
    });
    public gui() {
        setTitle("Kill That Memshell By CafedDi");
        initComponents();
        //设置JList标签
        FrameList.setListData(new String[]{"Tomcat", "SpringBoot"});
        // 设置单选模式
        FrameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }
    public static void main(String[] args) {
        new gui().setVisible(true);
    }
    /* ========== JVM 列表 ========== */
    private void GetJVMProcess(ActionEvent e) {
        statusTextArea.append("正在获取JVM进程列表...\n");
        JvmProcessList jvm = new JvmProcessList();
        List<Map.Entry<String,String>> jvmentries = jvm.GetJVMProcessList();
        //处理jvm进程信息,创建表格数据,并在表格中显示
        //设置列名
        String[] columnNames = {"PID","进程名称"};
        //创建二维数组存放数据
        Object[][] rawdata = new Object[jvmentries.size()][2];
        int i = 0;
        for(Map.Entry<String,String> entry:jvmentries){
            String pid = entry.getKey();
            String pname = entry.getValue();
            //处理名字为空情况
            if(pname==null || pid.equals("")){
                pname = "未知进程";
            }
            //处理名字长情况
            if(pname.length()>200){
                pname = pname.substring(0,200)+"...";
            }
            rawdata[i][0] = pid;
            rawdata[i][1] = pname;
            i++;
        }
        // 应用模型到JTable
        DefaultTableModel model = new DefaultTableModel(rawdata,columnNames){
            @Override
            public boolean isCellEditable(int row, int column) {
                //禁止编辑单元格
                return false;
            }
        };
        jvmTable.setModel(model);
        statusTextArea.append("已加载"+jvmentries.size()+"个进程\n");

    }
    /* ========== 点击显示详情  ========== */
    private void jvmTableMouseClicked(MouseEvent e) {
        if(e.getClickCount()==2){
            int row = jvmTable.rowAtPoint(e.getPoint());
            String pid = jvmTable.getValueAt(row,0).toString();
            String pname = jvmTable.getValueAt(row,1).toString();
            JTextArea jta = new JTextArea(pname);
            jta.setEditable(false);
            jta.setLineWrap(true);
            jta.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(jta);
            scrollPane.setPreferredSize(new Dimension(400, 100));
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(jvmTable),
                    scrollPane,
                    "进程详情 - PID: " + pid,
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
    /* ========== Attach 逻辑  ========== */
    private void attach(ActionEvent e) throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
        // 先确保 SocketServer 启动（非阻塞）
        startSocketServerIfNeeded();
        int selectedRow = jvmTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "请先选择表格中的一行");
            return;
        }
        Object value = jvmTable.getValueAt(selectedRow, 0);
        String param = value.toString();
        // 在后台执行 attach 与 loadAgent（避免阻塞 UI）
        executor.execute(() -> {
            statusTextArea.append("开始 Attach 进程 " + param+"\n");
            try {
                VirtualMachine vm = VirtualMachine.attach(param);
                //Path agentpath = Paths.get("agent/target/MemShellScannerAgent-1.0-SNAPSHOT.jar");
                Path agentpath = Paths.get("MemShellScannerAgent-1.0-SNAPSHOT.jar");
                if (!Files.exists(agentpath)) {
                    statusTextArea.append("[!] agent 文件不存在: " + agentpath.toAbsolutePath()+"\n");
                    vm.detach();
                    return;
                }
                vm.loadAgent(agentpath.toAbsolutePath().toString(), "agent");
                vm.detach();
                statusTextArea.append("[*] Attach 并注入 Agent 完成: " + param+"\n");
            } catch (AttachNotSupportedException | AgentLoadException | AgentInitializationException ex) {
                statusTextArea.append("[!] Attach 失败: " + ex.getMessage()+"\n");
            } catch (IOException ex) {
                statusTextArea.append("[!] IO 错误: " + ex.getMessage()+"\n");
            }
        });
    }
    /* ========== SocketServer 启动（只启动一次） ========== */
    private  void startSocketServerIfNeeded(){
        // router 把消息分发到不同文本区
        Consumer<String> router = msg -> {
            // 2. 所有对 Swing 组件的修改都必须在 EDT（事件调度线程）里执行
            SwingUtilities.invokeLater(() -> {
                // 3. 先把原始消息追加到全局日志面板
                statusTextArea.append(msg+"\n");
                // 4. 如果消息里包含特定标记，就再把它追加到对应子面板
                if (msg.contains("[Filter Shell]")) {
                    filterTextArea.append(msg + "\n");
                }
                if (msg.contains("[Servlet Shell]")) {
                    servletTextArea.append(msg+"\n");
                }
                if (msg.contains("[Listener Shell]")) {
                    listenerTextArea.append(msg+"\n");
                }
                if (msg.contains("[Spring Controller Shell]")) {
                    controllerTextArea.append(msg+"\n");
                }
                if (msg.contains("[Intercepter Shell]")) {
                    interceptorTextArea.append(msg+"\n");
                }
            });
        };
        if (srvThread == null || !srvThread.isAlive()) {
            if (!isPortAvailable(UI_PORT)){
                statusTextArea.append("[!] 端口 " + UI_PORT + " 已被占用，跳过 SocketServer 启动（若是上次已启动的实例，请确保它正常运行）。\n");
            }
            else {
                socketServer = new SocketServer(router);
                srvThread = new Thread(socketServer, "SockSrv");
                srvThread.setDaemon(true);
                srvThread.start();
                statusTextArea.append("[*] SocketServer 已启动，监听端口 " + UI_PORT + "\n");
                }
                }
        else{
            statusTextArea.append("[*] SocketServer 已在运行。"+"\n");
        }
    }
    private void FrameListValueChanged(ListSelectionEvent e) {
        if(e.getValueIsAdjusting()){
            //获取选择
            String select = (String) FrameList.getSelectedValue();
            CardLayout cl = (CardLayout)(panelCards.getLayout());
            // 根据选择切换卡片视图
            switch (select){
                case "Tomcat":
                    cl.show(panelCards,"tomcat");
                    break;
                case "SpringBoot":
                    cl.show(panelCards,"springboot");
                    break;
            }
       }
    }
    /* ========== 清除内存马逻辑：验证输入、后台发送命令 ========== */
    private void memClean(ActionEvent e) {
        String targetClassName = cleanClassName.getText().trim();
        if (targetClassName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入要清除的内存马类名或文件名");
            return;
        }
        // 输入格式校验
        if (!targetClassName.startsWith("[")) {
            JOptionPane.showMessageDialog(this, "请按照格式 [filter/servlet/...]classname 输入要清除的内存马类名");
            return;
        }
        // 提交给后台发送清除命令
        sendCleanCommand(targetClassName);
    }
    private void sendCleanCommand(String target) {
        // 优先检测是否有 socket server （逻辑修正：isPortAvailable true 表示可用 => 没有其他进程监听）
        if (isPortAvailable(CLEAN_PORT)) {
            statusTextArea.append("[!] 未检测到清除端口 " + CLEAN_PORT + " 的服务，请确认目标进程是否已启动清理端口。"+"\n");
            // 仍可尝试发起连接（若你希望直接尝试可以移除 return）
            // return;
        }
        executor.execute(() -> {
            String payload = "[clean]" + target;
            statusTextArea.append("尝试发送清除命令: " + payload+"\n");
            try (Socket socket = new Socket("127.0.0.1", CLEAN_PORT);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println(payload);
                statusTextArea.append("[*] 清除命令已发送"+"\n");
            } catch (Exception ex) {
                statusTextArea.append("[!] 发送清除命令失败: " + ex.getMessage()+"\n");
            }
        });
    }
    /**
     * 简单端口检测：尝试绑定端口，能 bind 则说明可用（随后会自动 close）。
     */
    private static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        ResourceBundle bundle = ResourceBundle.getBundle("config");
        panel1 = new JPanel();
        GetJVMProcess = new JButton();
        scrollPane2 = new JScrollPane();
        jvmTable = new JTable();
        attach = new JButton();
        detail = new JSplitPane();
        scrollPane3 = new JScrollPane();
        FrameList = new JList();
        panelCards = new JPanel();
        tabTomcat = new JTabbedPane();
        listener = new JPanel();
        scrollPane6 = new JScrollPane();
        listenerTextArea = new JTextArea();
        filter = new JPanel();
        scrollPane4 = new JScrollPane();
        filterTextArea = new JTextArea();
        servlet = new JPanel();
        scrollPane5 = new JScrollPane();
        servletTextArea = new JTextArea();
        tabSpringBoot = new JTabbedPane();
        controller = new JPanel();
        scrollPane7 = new JScrollPane();
        controllerTextArea = new JTextArea();
        interceptor = new JPanel();
        scrollPane8 = new JScrollPane();
        interceptorTextArea = new JTextArea();
        scrollPane1 = new JScrollPane();
        statusTextArea = new JTextArea();
        panel2 = new JPanel();
        memClean = new JButton();
        cleanClassName = new JTextField();

        //======== this ========
        setTitle(bundle.getString("gui.this.title"));
        Container contentPane = getContentPane();

        //======== panel1 ========
        {

            //---- GetJVMProcess ----
            GetJVMProcess.setText(bundle.getString("gui.GetJVMProcess.text"));
            GetJVMProcess.addActionListener(e -> GetJVMProcess(e));

            //======== scrollPane2 ========
            {

                //---- jvmTable ----
                jvmTable.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        jvmTableMouseClicked(e);
                    }
                });
                scrollPane2.setViewportView(jvmTable);
            }

            //---- attach ----
            attach.setText(bundle.getString("gui.attach.text"));
            attach.addActionListener(e -> {try {
attach(e);} catch (IOException ex) {
    throw new RuntimeException(ex);
} catch (AttachNotSupportedException ex) {
    throw new RuntimeException(ex);
} catch (AgentLoadException ex) {
    throw new RuntimeException(ex);
} catch (AgentInitializationException ex) {
    throw new RuntimeException(ex);
}});

            GroupLayout panel1Layout = new GroupLayout(panel1);
            panel1.setLayout(panel1Layout);
            panel1Layout.setHorizontalGroup(
                panel1Layout.createParallelGroup()
                    .addGroup(panel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(panel1Layout.createParallelGroup(GroupLayout.Alignment.TRAILING, false)
                            .addComponent(GetJVMProcess, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE)
                            .addComponent(attach, GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE)
                            .addComponent(scrollPane2, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE))
                        .addGap(0, 10, Short.MAX_VALUE))
            );
            panel1Layout.setVerticalGroup(
                panel1Layout.createParallelGroup()
                    .addGroup(panel1Layout.createSequentialGroup()
                        .addComponent(GetJVMProcess, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(attach, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scrollPane2, GroupLayout.DEFAULT_SIZE, 0, Short.MAX_VALUE)
                        .addContainerGap())
            );
        }

        //======== detail ========
        {

            //======== scrollPane3 ========
            {

                //---- FrameList ----
                FrameList.setMinimumSize(new Dimension(38, 80));
                FrameList.addListSelectionListener(e -> FrameListValueChanged(e));
                scrollPane3.setViewportView(FrameList);
            }
            detail.setLeftComponent(scrollPane3);

            //======== panelCards ========
            {
                panelCards.setLayout(new CardLayout());

                //======== tabTomcat ========
                {

                    //======== listener ========
                    {

                        //======== scrollPane6 ========
                        {
                            scrollPane6.setViewportView(listenerTextArea);
                        }

                        GroupLayout listenerLayout = new GroupLayout(listener);
                        listener.setLayout(listenerLayout);
                        listenerLayout.setHorizontalGroup(
                            listenerLayout.createParallelGroup()
                                .addComponent(scrollPane6, GroupLayout.DEFAULT_SIZE, 358, Short.MAX_VALUE)
                        );
                        listenerLayout.setVerticalGroup(
                            listenerLayout.createParallelGroup()
                                .addGroup(listenerLayout.createSequentialGroup()
                                    .addComponent(scrollPane6, GroupLayout.DEFAULT_SIZE, 247, Short.MAX_VALUE)
                                    .addContainerGap())
                        );
                    }
                    tabTomcat.addTab(bundle.getString("gui.listener.tab.title"), listener);

                    //======== filter ========
                    {

                        //======== scrollPane4 ========
                        {
                            scrollPane4.setViewportView(filterTextArea);
                        }

                        GroupLayout filterLayout = new GroupLayout(filter);
                        filter.setLayout(filterLayout);
                        filterLayout.setHorizontalGroup(
                            filterLayout.createParallelGroup()
                                .addComponent(scrollPane4, GroupLayout.DEFAULT_SIZE, 358, Short.MAX_VALUE)
                        );
                        filterLayout.setVerticalGroup(
                            filterLayout.createParallelGroup()
                                .addComponent(scrollPane4, GroupLayout.DEFAULT_SIZE, 253, Short.MAX_VALUE)
                        );
                    }
                    tabTomcat.addTab(bundle.getString("gui.filter.tab.title"), filter);

                    //======== servlet ========
                    {

                        //======== scrollPane5 ========
                        {
                            scrollPane5.setViewportView(servletTextArea);
                        }

                        GroupLayout servletLayout = new GroupLayout(servlet);
                        servlet.setLayout(servletLayout);
                        servletLayout.setHorizontalGroup(
                            servletLayout.createParallelGroup()
                                .addComponent(scrollPane5, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 358, Short.MAX_VALUE)
                        );
                        servletLayout.setVerticalGroup(
                            servletLayout.createParallelGroup()
                                .addComponent(scrollPane5, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 253, Short.MAX_VALUE)
                        );
                    }
                    tabTomcat.addTab(bundle.getString("gui.servlet.tab.title"), servlet);
                }
                panelCards.add(tabTomcat, "tomcat");

                //======== tabSpringBoot ========
                {

                    //======== controller ========
                    {

                        //======== scrollPane7 ========
                        {
                            scrollPane7.setViewportView(controllerTextArea);
                        }

                        GroupLayout controllerLayout = new GroupLayout(controller);
                        controller.setLayout(controllerLayout);
                        controllerLayout.setHorizontalGroup(
                            controllerLayout.createParallelGroup()
                                .addGroup(GroupLayout.Alignment.TRAILING, controllerLayout.createSequentialGroup()
                                    .addComponent(scrollPane7, GroupLayout.DEFAULT_SIZE, 352, Short.MAX_VALUE)
                                    .addContainerGap())
                        );
                        controllerLayout.setVerticalGroup(
                            controllerLayout.createParallelGroup()
                                .addGroup(GroupLayout.Alignment.TRAILING, controllerLayout.createSequentialGroup()
                                    .addComponent(scrollPane7, GroupLayout.DEFAULT_SIZE, 247, Short.MAX_VALUE)
                                    .addContainerGap())
                        );
                    }
                    tabSpringBoot.addTab(bundle.getString("gui.controller.tab.title"), controller);

                    //======== interceptor ========
                    {

                        //======== scrollPane8 ========
                        {
                            scrollPane8.setViewportView(interceptorTextArea);
                        }

                        GroupLayout interceptorLayout = new GroupLayout(interceptor);
                        interceptor.setLayout(interceptorLayout);
                        interceptorLayout.setHorizontalGroup(
                            interceptorLayout.createParallelGroup()
                                .addGroup(GroupLayout.Alignment.TRAILING, interceptorLayout.createSequentialGroup()
                                    .addGap(0, 0, Short.MAX_VALUE)
                                    .addComponent(scrollPane8, GroupLayout.PREFERRED_SIZE, 368, GroupLayout.PREFERRED_SIZE))
                        );
                        interceptorLayout.setVerticalGroup(
                            interceptorLayout.createParallelGroup()
                                .addGroup(GroupLayout.Alignment.TRAILING, interceptorLayout.createSequentialGroup()
                                    .addComponent(scrollPane8, GroupLayout.DEFAULT_SIZE, 247, Short.MAX_VALUE)
                                    .addContainerGap())
                        );
                    }
                    tabSpringBoot.addTab(bundle.getString("gui.interceptor.tab.title"), interceptor);
                }
                panelCards.add(tabSpringBoot, "springboot");
            }
            detail.setRightComponent(panelCards);
        }

        //======== scrollPane1 ========
        {
            scrollPane1.setViewportView(statusTextArea);
        }

        //======== panel2 ========
        {

            //---- memClean ----
            memClean.setText("\u6e05\u9664\u5185\u5b58\u9a6c");
            memClean.addActionListener(e -> memClean(e));

            GroupLayout panel2Layout = new GroupLayout(panel2);
            panel2.setLayout(panel2Layout);
            panel2Layout.setHorizontalGroup(
                panel2Layout.createParallelGroup()
                    .addGroup(GroupLayout.Alignment.TRAILING, panel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(panel2Layout.createParallelGroup()
                            .addComponent(memClean, GroupLayout.DEFAULT_SIZE, 158, Short.MAX_VALUE)
                            .addComponent(cleanClassName, GroupLayout.DEFAULT_SIZE, 158, Short.MAX_VALUE))
                        .addContainerGap())
            );
            panel2Layout.setVerticalGroup(
                panel2Layout.createParallelGroup()
                    .addGroup(panel2Layout.createSequentialGroup()
                        .addComponent(memClean)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cleanClassName, GroupLayout.PREFERRED_SIZE, 42, GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
            );
        }

        GroupLayout contentPaneLayout = new GroupLayout(contentPane);
        contentPane.setLayout(contentPaneLayout);
        contentPaneLayout.setHorizontalGroup(
            contentPaneLayout.createParallelGroup()
                .addGroup(contentPaneLayout.createSequentialGroup()
                    .addGap(12, 12, 12)
                    .addGroup(contentPaneLayout.createParallelGroup()
                        .addComponent(scrollPane1, GroupLayout.DEFAULT_SIZE, 730, Short.MAX_VALUE)
                        .addGroup(contentPaneLayout.createSequentialGroup()
                            .addComponent(panel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(detail, GroupLayout.PREFERRED_SIZE, 386, GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(panel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addContainerGap())
        );
        contentPaneLayout.setVerticalGroup(
            contentPaneLayout.createParallelGroup()
                .addGroup(contentPaneLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(contentPaneLayout.createParallelGroup()
                        .addComponent(panel1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(contentPaneLayout.createSequentialGroup()
                            .addComponent(detail, GroupLayout.PREFERRED_SIZE, 295, GroupLayout.PREFERRED_SIZE)
                            .addGap(0, 0, Short.MAX_VALUE))
                        .addComponent(panel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(scrollPane1, GroupLayout.PREFERRED_SIZE, 137, GroupLayout.PREFERRED_SIZE)
                    .addGap(15, 15, 15))
        );
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JPanel panel1;
    private JButton GetJVMProcess;
    private JScrollPane scrollPane2;
    private JTable jvmTable;
    private JButton attach;
    private JSplitPane detail;
    private JScrollPane scrollPane3;
    private JList FrameList;
    private JPanel panelCards;
    private JTabbedPane tabTomcat;
    private JPanel listener;
    private JScrollPane scrollPane6;
    private JTextArea listenerTextArea;
    private JPanel filter;
    private JScrollPane scrollPane4;
    private JTextArea filterTextArea;
    private JPanel servlet;
    private JScrollPane scrollPane5;
    private JTextArea servletTextArea;
    private JTabbedPane tabSpringBoot;
    private JPanel controller;
    private JScrollPane scrollPane7;
    private JTextArea controllerTextArea;
    private JPanel interceptor;
    private JScrollPane scrollPane8;
    private JTextArea interceptorTextArea;
    private JScrollPane scrollPane1;
    private JTextArea statusTextArea;
    private JPanel panel2;
    private JButton memClean;
    private JTextField cleanClassName;
    // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
