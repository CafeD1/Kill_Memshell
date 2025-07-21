import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
//import com.jgoodies.forms.factories.*;
//import com.jgoodies.forms.layout.*;
//import net.miginfocom.swing.*;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.cafedi.util.JvmProcessList;
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
    }

    public static void main(String[] args) {
        new gui().setVisible(true);
    }
    private void setupCustomComponents(){
        //自定义表格模型
    }
    private void GetJVMProcess(ActionEvent e) {
        // TODO add your code here
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
        // TODO add your code here
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

    private void attach(ActionEvent e) throws IOException, AttachNotSupportedException {
        //获取选中的行索引
        int selectedRow = jvmTable.getSelectedRow();
        if(selectedRow!=-1){
            // 获取第一列（索引为 0）的值,即PID
            Object value = jvmTable.getValueAt(selectedRow,0);
            //转为字符串参数
            String param = value.toString();
            //日志显示
            statusTextArea.append("开始Attach进程"+param+"\n");
            VirtualMachine.attach(param);
        }else {
            JOptionPane.showMessageDialog(null,"请先选择表格中的一行");
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        ResourceBundle bundle = ResourceBundle.getBundle("config");
        panel1 = new JPanel();
        GetJVMProcess = new JButton();
        scrollPane1 = new JScrollPane();
        statusTextArea = new JTextArea();
        scrollPane2 = new JScrollPane();
        jvmTable = new JTable();
        attach = new JButton();

        //======== this ========
        setTitle(bundle.getString("gui.this.title"));
        Container contentPane = getContentPane();

        //======== panel1 ========
        {

            //---- GetJVMProcess ----
            GetJVMProcess.setText(bundle.getString("gui.GetJVMProcess.text"));
            GetJVMProcess.addActionListener(e -> GetJVMProcess(e));

            //======== scrollPane1 ========
            {
                scrollPane1.setViewportView(statusTextArea);
            }

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
            attach.addActionListener(e -> attach(e));

            GroupLayout panel1Layout = new GroupLayout(panel1);
            panel1.setLayout(panel1Layout);
            panel1Layout.setHorizontalGroup(
                panel1Layout.createParallelGroup()
                    .addGroup(panel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(panel1Layout.createParallelGroup(GroupLayout.Alignment.TRAILING, false)
                            .addComponent(scrollPane2, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE)
                            .addComponent(scrollPane1, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE)
                            .addComponent(GetJVMProcess, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE)
                            .addComponent(attach, GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE))
                        .addGap(0, 10, Short.MAX_VALUE))
            );
            panel1Layout.setVerticalGroup(
                panel1Layout.createParallelGroup()
                    .addGroup(panel1Layout.createSequentialGroup()
                        .addComponent(GetJVMProcess, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(attach, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scrollPane2, GroupLayout.PREFERRED_SIZE, 187, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scrollPane1, GroupLayout.DEFAULT_SIZE, 176, Short.MAX_VALUE)
                        .addContainerGap())
            );
        }

        GroupLayout contentPaneLayout = new GroupLayout(contentPane);
        contentPane.setLayout(contentPaneLayout);
        contentPaneLayout.setHorizontalGroup(
            contentPaneLayout.createParallelGroup()
                .addGroup(contentPaneLayout.createSequentialGroup()
                    .addComponent(panel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 416, Short.MAX_VALUE))
        );
        contentPaneLayout.setVerticalGroup(
            contentPaneLayout.createParallelGroup()
                .addComponent(panel1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JPanel panel1;
    private JButton GetJVMProcess;
    private JScrollPane scrollPane1;
    private JTextArea statusTextArea;
    private JScrollPane scrollPane2;
    private JTable jvmTable;
    private JButton attach;
    // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
