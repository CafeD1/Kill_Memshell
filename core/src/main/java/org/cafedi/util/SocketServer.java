package org.cafedi.util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class SocketServer implements Runnable {
    private final Consumer<String> onMessage;
    public SocketServer(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(8899)) {
            onMessage.accept("[*] Listening on 8899");
            while (!server.isClosed()) {
                try (Socket sock = server.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        onMessage.accept(line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
