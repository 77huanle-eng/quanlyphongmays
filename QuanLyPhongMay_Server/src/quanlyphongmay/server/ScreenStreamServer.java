package quanlyphongmay.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/**
 * ScreenStreamServer - Máy chủ TCP tiếp nhận luồng hình ảnh màn hình trực tiếp từ các máy trạm Client
 * và chuyển tiếp khung hình tới giao diện ScreenMonitorPanel tương ứng.
 */
public class ScreenStreamServer implements Runnable {

    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    // Map chứa các ScreenMonitorPanel đang theo dõi theo tên máy (clientName -> Set<ScreenMonitorPanel>)
    // Hỗ trợ đồng thời cả chế độ xem đơn (MonitorForm) và chế độ xem nhiều máy (MultiScreenForm)!
    private final Map<String, java.util.Set<ScreenMonitorPanel>> activeMonitors = new ConcurrentHashMap<>();

    public ScreenStreamServer(int port) {
        this.port = port;
    }

    public void registerMonitor(String clientName, ScreenMonitorPanel panel) {
        if (clientName != null && panel != null) {
            activeMonitors.computeIfAbsent(clientName, k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())).add(panel);
        }
    }

    public void unregisterMonitor(String clientName, ScreenMonitorPanel panel) {
        if (clientName != null && panel != null) {
            java.util.Set<ScreenMonitorPanel> set = activeMonitors.get(clientName);
            if (set != null) {
                set.remove(panel);
                if (set.isEmpty()) {
                    activeMonitors.remove(clientName);
                }
            }
        }
    }

    public void unregisterMonitor(String clientName) {
        if (clientName != null) {
            activeMonitors.remove(clientName);
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        serverThread = new Thread(this, "ScreenStreamServer-Listener");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        clientPool.shutdownNow();
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        activeMonitors.clear();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            while (running && !Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                clientPool.submit(() -> handleStreamClient(clientSocket));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[ScreenStreamServer] Lỗi ServerSocket: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void handleStreamClient(Socket socket) {
        String clientName = "Unknown";
        try (
            Socket s = socket;
            DataInputStream dis = new DataInputStream(s.getInputStream())
        ) {
            // Đọc tên máy trạm
            int nameLen = dis.readInt();
            if (nameLen > 0 && nameLen < 1024) {
                byte[] nameBytes = new byte[nameLen];
                dis.readFully(nameBytes);
                clientName = new String(nameBytes, StandardCharsets.UTF_8).trim();
            }

            // Vòng lặp nhận từng frame ảnh JPEG
            while (running && !s.isClosed()) {
                int frameSize = dis.readInt();
                if (frameSize <= 0 || frameSize > 10 * 1024 * 1024) {
                    // Frame bất thường hoặc client đã ngắt kết nối
                    break;
                }

                byte[] frameBytes = new byte[frameSize];
                dis.readFully(frameBytes);

                BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameBytes));
                if (image != null) {
                    java.util.Set<ScreenMonitorPanel> panels = activeMonitors.get(clientName);
                    if (panels != null) {
                        for (ScreenMonitorPanel panel : panels) {
                            try {
                                panel.updateFrame(image, frameSize);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // Client đóng kết nối stream bình thường
        } finally {
            java.util.Set<ScreenMonitorPanel> panels = activeMonitors.get(clientName);
            if (panels != null) {
                for (ScreenMonitorPanel panel : panels) {
                    try {
                        panel.setStatusMessage("Máy trạm đã dừng truyền hình ảnh.");
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}

