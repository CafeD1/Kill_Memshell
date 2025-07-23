package org.cafedi.util;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer {
    private JTextArea outputArea;
    public SocketServer(JTextArea outputArea) {
        this.outputArea = outputArea;
    }
    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8899)) {
                outputArea.append("[*]Listening on port 8899...\n");
                while (true) {
                    Socket client = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String msg = line;
                        SwingUtilities.invokeLater(()->outputArea.append(msg+"\n"));
                    }
                    client.close();
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }).start();
    }
    //判断监听端口是否被占用
    //public static boolean isPortAvailable(int port) {}
}
