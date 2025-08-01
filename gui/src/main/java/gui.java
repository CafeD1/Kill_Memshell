import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
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
    public gui() {
        initComponents();
        //设置JList标签
        FrameList.setListData(new String[]{"Tomcat", "SpringBoot"});
        // 设置单选模式
        FrameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public static void main(String[] args) {
        new gui().setVisible(true);
    }
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

    private void attach(ActionEvent e) throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
        //开始监听socket
        //面板列表
//        List<JTextArea> outList = Arrays.asList(statusTextArea,filterTextArea);
//        new SocketServer(outList).start();
        //new SocketServer(statusTextArea).start();
        // 1. 定义一个“路由”函数：接收每一行消息，并分发到相应的 JTextArea
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
            });
        };
        Thread srvThread = new Thread(new SocketServer(router), "SockSrv");
        srvThread.setDaemon(true);
        srvThread.start();
        //获取选中的行索引
        int selectedRow = jvmTable.getSelectedRow();
        if(selectedRow!=-1){
            // 获取第一列（索引为 0）的值,即PID
            Object value = jvmTable.getValueAt(selectedRow,0);
            //转为字符串参数
            String param = value.toString();
            //日志显示
            statusTextArea.append("开始Attach进程"+param+"\n");
            VirtualMachine vm = VirtualMachine.attach(param);
            Path agentpath = Paths.get("agent/target/MemShellScannerAgent-1.0-SNAPSHOT.jar");
            String path = "";
            if (Files.exists(agentpath)){
                path = agentpath.toAbsolutePath().toString();
            }else{
                statusTextArea.append("请检查agent文件是否存在");
                return;
            }
            vm.loadAgent(path,"agent");
            vm.detach();

        }else {
            JOptionPane.showMessageDialog(null,"请先选择表格中的一行");
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
        interceptor = new JPanel();
        scrollPane1 = new JScrollPane();
        statusTextArea = new JTextArea();
        panel2 = new JPanel();
        memClean = new JButton();

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

                        GroupLayout controllerLayout = new GroupLayout(controller);
                        controller.setLayout(controllerLayout);
                        controllerLayout.setHorizontalGroup(
                            controllerLayout.createParallelGroup()
                                .addGap(0, 358, Short.MAX_VALUE)
                        );
                        controllerLayout.setVerticalGroup(
                            controllerLayout.createParallelGroup()
                                .addGap(0, 253, Short.MAX_VALUE)
                        );
                    }
                    tabSpringBoot.addTab(bundle.getString("gui.controller.tab.title"), controller);

                    //======== interceptor ========
                    {

                        GroupLayout interceptorLayout = new GroupLayout(interceptor);
                        interceptor.setLayout(interceptorLayout);
                        interceptorLayout.setHorizontalGroup(
                            interceptorLayout.createParallelGroup()
                                .addGap(0, 358, Short.MAX_VALUE)
                        );
                        interceptorLayout.setVerticalGroup(
                            interceptorLayout.createParallelGroup()
                                .addGap(0, 253, Short.MAX_VALUE)
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

            GroupLayout panel2Layout = new GroupLayout(panel2);
            panel2.setLayout(panel2Layout);
            panel2Layout.setHorizontalGroup(
                panel2Layout.createParallelGroup()
                    .addGroup(GroupLayout.Alignment.TRAILING, panel2Layout.createSequentialGroup()
                        .addContainerGap(7, Short.MAX_VALUE)
                        .addComponent(memClean)
                        .addContainerGap())
            );
            panel2Layout.setVerticalGroup(
                panel2Layout.createParallelGroup()
                    .addGroup(panel2Layout.createSequentialGroup()
                        .addComponent(memClean)
                        .addGap(0, 0, Short.MAX_VALUE))
            );
        }

        GroupLayout contentPaneLayout = new GroupLayout(contentPane);
        contentPane.setLayout(contentPaneLayout);
        contentPaneLayout.setHorizontalGroup(
            contentPaneLayout.createParallelGroup()
                .addGroup(GroupLayout.Alignment.TRAILING, contentPaneLayout.createSequentialGroup()
                    .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(detail, GroupLayout.PREFERRED_SIZE, 386, GroupLayout.PREFERRED_SIZE)
                    .addGap(31, 31, 31)
                    .addComponent(panel2, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addGap(15, 15, 15))
                .addGroup(contentPaneLayout.createSequentialGroup()
                    .addGap(12, 12, 12)
                    .addComponent(scrollPane1, GroupLayout.PREFERRED_SIZE, 695, GroupLayout.PREFERRED_SIZE)
                    .addContainerGap())
        );
        contentPaneLayout.setVerticalGroup(
            contentPaneLayout.createParallelGroup()
                .addGroup(contentPaneLayout.createSequentialGroup()
                    .addGroup(contentPaneLayout.createParallelGroup()
                        .addComponent(panel1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(contentPaneLayout.createSequentialGroup()
                            .addContainerGap()
                            .addGroup(contentPaneLayout.createParallelGroup()
                                .addComponent(panel2, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(contentPaneLayout.createSequentialGroup()
                                    .addComponent(detail, GroupLayout.PREFERRED_SIZE, 295, GroupLayout.PREFERRED_SIZE)
                                    .addGap(0, 0, Short.MAX_VALUE)))))
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
    private JPanel interceptor;
    private JScrollPane scrollPane1;
    private JTextArea statusTextArea;
    private JPanel panel2;
    private JButton memClean;
    // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
