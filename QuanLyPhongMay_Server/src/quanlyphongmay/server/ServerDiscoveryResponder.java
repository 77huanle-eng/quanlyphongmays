package quanlyphongmay.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ServerDiscoveryResponder - Lắng nghe UDP Broadcast từ các máy trạm Client (cổng 5001 & 8888)
 * và phản hồi thông tin IP/Port của Server để Client tự động kết nối mà không cần nhập thủ công.
 */
public class ServerDiscoveryResponder {

    public static final int DISCOVERY_PORT_5001 = 5001;
    public static final int DISCOVERY_PORT_8888 = 8888;
    public static final String REQUEST_KEY = "DISCOVER_SERVER_REQUEST";
    public static final String RESPONSE_PREFIX = "DISCOVER_SERVER_RESPONSE";
    public static final String SEC_REQUEST_PREFIX = "DISCOVER_SEC";
    public static final String SEC_ANNOUNCE_PREFIX = "SERVER_SEC_ANNOUNCE";

    private final int serverTcpPort;
    private final String serverName;
    private final String serverUuid;
    private volatile boolean running = false;
    private final List<DatagramSocket> sockets = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();

    public ServerDiscoveryResponder(int serverTcpPort, String serverName) {
        this(serverTcpPort, serverName, loadOrCreateServerUuid());
    }

    public ServerDiscoveryResponder(int serverTcpPort, String serverName, String serverUuid) {
        this.serverTcpPort = serverTcpPort;
        this.serverName = (serverName == null || serverName.trim().isEmpty())
                ? "Server Quản Lý Phòng Máy" : serverName.trim();
        this.serverUuid = (serverUuid != null && !serverUuid.trim().isEmpty())
                ? serverUuid.trim() : loadOrCreateServerUuid();
    }

    public static synchronized String loadOrCreateServerUuid() {
        java.io.File f = new java.io.File("server_security.properties");
        java.util.Properties p = new java.util.Properties();
        if (f.exists()) {
            try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                p.load(in);
                String id = p.getProperty("serverUuid");
                if (id != null && !id.trim().isEmpty()) {
                    return id.trim();
                }
            } catch (Exception ignored) {}
        }
        String newUuid = java.util.UUID.randomUUID().toString();
        p.setProperty("serverUuid", newUuid);
        p.setProperty("createdAt", java.time.LocalDateTime.now().toString());
        try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
            p.store(out, "LANPC Server Cryptographic Identity");
        } catch (Exception ignored) {}
        return newUuid;
    }

    public static boolean isAnotherServerActive(int udpPort, int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(Math.max(400, timeoutMs));

            byte[] probe = (SEC_REQUEST_PREFIX + "|probe").getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName("255.255.255.255"), udpPort));

            byte[] buf = new byte[1024];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    socket.receive(recv);
                    String resp = new String(recv.getData(), recv.getOffset(), recv.getLength(), StandardCharsets.UTF_8).trim();
                    if (resp.startsWith(SEC_ANNOUNCE_PREFIX) || resp.startsWith(RESPONSE_PREFIX)) {
                        String senderIp = recv.getAddress().getHostAddress();
                        if (!"127.0.0.1".equals(senderIp)) {
                            return true;
                        }
                    }
                } catch (java.net.SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        int[] ports = new int[] { DISCOVERY_PORT_5001, DISCOVERY_PORT_8888 };
        for (int p : ports) {
            Thread t = new Thread(() -> listenOnPort(p), "Server-Discovery-Responder-" + p);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
    }

    public synchronized void stop() {
        running = false;
        synchronized (sockets) {
            for (DatagramSocket s : sockets) {
                try {
                    if (s != null && !s.isClosed()) {
                        s.close();
                    }
                } catch (Exception ignored) {}
            }
            sockets.clear();
        }
        for (Thread t : threads) {
            try {
                if (t != null) {
                    t.interrupt();
                }
            } catch (Exception ignored) {}
        }
        threads.clear();
    }

    private void listenOnPort(int port) {
        byte[] buffer = new byte[1024];
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket(port);
            sock.setBroadcast(true);
            synchronized (sockets) {
                sockets.add(sock);
            }

            while (running && !Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                sock.receive(packet);

                String message = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8
                ).trim();

                if (message.startsWith(SEC_REQUEST_PREFIX + "|") || message.equals(SEC_REQUEST_PREFIX)) {
                    String nonce = message.contains("|") ? message.substring(message.indexOf('|') + 1).trim() : "";
                    String localIp = getLanIp();
                    String hmac = quanlyphongmay.common.SecurityUtil.hmacSha256(
                            nonce + "|" + localIp + "|" + serverTcpPort + "|" + serverUuid,
                            quanlyphongmay.common.SecurityUtil.SHARED_HMAC_SECRET
                    );
                    String response = SEC_ANNOUNCE_PREFIX + "|" + localIp + "|" + serverTcpPort + "|" + serverUuid + "|" + hmac + "|" + serverName;
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket replyPacket = new DatagramPacket(
                            responseBytes,
                            responseBytes.length,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    sock.send(replyPacket);

                } else if (message.contains(REQUEST_KEY) || message.startsWith("DISCOVER_SERVER")) {
                    // Xác định IP LAN của Server
                    String localIp = getLanIp();

                    // Format: DISCOVER_SERVER_RESPONSE|<server_ip>|<server_port>|<server_name>
                    String response = RESPONSE_PREFIX + "|" + localIp + "|" + serverTcpPort + "|" + serverName;
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket replyPacket = new DatagramPacket(
                            responseBytes,
                            responseBytes.length,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    sock.send(replyPacket);

                    // Đồng thời gửi kèm gói tin SERVER_SEC_ANNOUNCE
                    String hmac = quanlyphongmay.common.SecurityUtil.hmacSha256(
                            localIp + "|" + serverTcpPort + "|" + serverUuid,
                            quanlyphongmay.common.SecurityUtil.SHARED_HMAC_SECRET
                    );
                    String secResp = SEC_ANNOUNCE_PREFIX + "|" + localIp + "|" + serverTcpPort + "|" + serverUuid + "|" + hmac + "|" + serverName;
                    byte[] secBytes = secResp.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket secReplyPacket = new DatagramPacket(secBytes, secBytes.length, packet.getAddress(), packet.getPort());
                    sock.send(secReplyPacket);
                }
            }
        } catch (IOException e) {
            if (running) {
                // Port có thể đã bị chiếm dụng hoặc socket đóng
            }
        } finally {
            if (sock != null && !sock.isClosed()) {
                sock.close();
            }
        }
    }

    private String getLanIp() {
        try (DatagramSocket testSocket = new DatagramSocket()) {
            testSocket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return testSocket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "127.0.0.1";
            }
        }
    }
}
