package quanlyphongmay.client;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * ClientAutoDiscover - Gửi gói tin UDP Broadcast tìm Server trong mạng LAN
 * và nhận phản hồi cấu hình tự động.
 */
public class ClientAutoDiscover {

    public static final int DISCOVERY_PORT = 5001;
    public static final int LEGACY_PORT = 8888;
    public static final String REQUEST_KEY = "DISCOVER_SERVER_REQUEST";
    public static final String RESPONSE_PREFIX = "DISCOVER_SERVER_RESPONSE";
    public static final String SEC_REQUEST_PREFIX = "DISCOVER_SEC";
    public static final String SEC_ANNOUNCE_PREFIX = "SERVER_SEC_ANNOUNCE";

    public static String getPinnedServerUuid() {
        java.io.File secFile = new java.io.File("client_security.properties");
        if (!secFile.exists()) return "";
        try (java.io.InputStream in = new java.io.FileInputStream(secFile)) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            return p.getProperty("pinnedServerUuid", "").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void pinServer(String uuid, String ip) {
        java.io.File secFile = new java.io.File("client_security.properties");
        java.util.Properties p = new java.util.Properties();
        if (secFile.exists()) {
            try (java.io.InputStream in = new java.io.FileInputStream(secFile)) {
                p.load(in);
            } catch (Exception ignored) {}
        }
        p.setProperty("pinnedServerUuid", uuid != null ? uuid : "");
        p.setProperty("pinnedServerIp", ip != null ? ip : "");
        p.setProperty("pinnedAt", java.time.LocalDateTime.now().toString());
        try (java.io.OutputStream out = new java.io.FileOutputStream(secFile)) {
            p.store(out, "LANPC Client Cryptographic Security & Server Pinning");
        } catch (Exception ignored) {}
    }

    public static class DiscoveredServer {
        public final String ip;
        public final int port;
        public final String name;

        public DiscoveredServer(String ip, int port, String name) {
            this.ip = ip;
            this.port = port;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + " (" + ip + ":" + port + ")";
        }
    }

    /**
     * Quét tìm Server trong mạng LAN (chạy đồng bộ với thời gian timeout)
     * @param timeoutMs Thời gian chờ tối đa (ms)
     * @return DiscoveredServer nếu tìm thấy, hoặc null nếu không phản hồi
     */
    public static DiscoveredServer discover(int timeoutMs) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(Math.max(1000, timeoutMs));

            String nonce = quanlyphongmay.common.SecurityUtil.generateNonce(16);
            byte[] secSendData = (SEC_REQUEST_PREFIX + "|" + nonce).getBytes(StandardCharsets.UTF_8);
            byte[] legSendData = REQUEST_KEY.getBytes(StandardCharsets.UTF_8);
            int[] targetPorts = new int[] { DISCOVERY_PORT, LEGACY_PORT };

            for (int p : targetPorts) {
                // 1. Gửi tới địa chỉ broadcast chung 255.255.255.255
                try {
                    socket.send(new DatagramPacket(secSendData, secSendData.length, InetAddress.getByName("255.255.255.255"), p));
                    socket.send(new DatagramPacket(legSendData, legSendData.length, InetAddress.getByName("255.255.255.255"), p));
                } catch (Exception ignored) {}

                // 2. Gửi tới tất cả subnet broadcast addresses của card mạng
                for (InetAddress bcast : listAllBroadcastAddresses()) {
                    try {
                        socket.send(new DatagramPacket(secSendData, secSendData.length, bcast, p));
                        socket.send(new DatagramPacket(legSendData, legSendData.length, bcast, p));
                    } catch (Exception ignored) {}
                }
            }

            // 3. Đợi phản hồi
            byte[] recvBuf = new byte[2048];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(recvPacket);
                    String resp = new String(
                            recvPacket.getData(),
                            recvPacket.getOffset(),
                            recvPacket.getLength(),
                            StandardCharsets.UTF_8
                    ).trim();

                    // Ưu tiên xác thực gói tin bảo mật SERVER_SEC_ANNOUNCE
                    if (resp.startsWith(SEC_ANNOUNCE_PREFIX)) {
                        String[] parts = resp.split("\\|", 6);
                        // Format: SERVER_SEC_ANNOUNCE|<ip>|<port>|<uuid>|<hmac>|<name>
                        if (parts.length >= 5) {
                            String serverIp = parts[1].trim();
                            if (serverIp.equals("127.0.0.1") || serverIp.equals("0.0.0.0")) {
                                serverIp = recvPacket.getAddress().getHostAddress();
                            }
                            int port = Integer.parseInt(parts[2].trim());
                            String uuid = parts[3].trim();
                            String hmac = parts[4].trim();
                            String name = (parts.length >= 6) ? parts[5].trim() : "Server Phòng Máy";

                            // Xác thực HMAC
                            boolean ok = quanlyphongmay.common.SecurityUtil.verifyHmac(
                                    nonce + "|" + serverIp + "|" + port + "|" + uuid,
                                    quanlyphongmay.common.SecurityUtil.SHARED_HMAC_SECRET,
                                    hmac
                            );
                            if (!ok) {
                                ok = quanlyphongmay.common.SecurityUtil.verifyHmac(
                                        serverIp + "|" + port + "|" + uuid,
                                        quanlyphongmay.common.SecurityUtil.SHARED_HMAC_SECRET,
                                        hmac
                                );
                            }

                            if (ok) {
                                // Kiểm tra ghim Server duy nhất (Single Server Pinning)
                                String pinnedUuid = getPinnedServerUuid();
                                if (pinnedUuid.isEmpty()) {
                                    pinServer(uuid, serverIp);
                                    return new DiscoveredServer(serverIp, port, name);
                                } else if (pinnedUuid.equalsIgnoreCase(uuid)) {
                                    return new DiscoveredServer(serverIp, port, name);
                                } else {
                                    System.err.println("[AutoDiscover] Chặn máy chủ lạ: UUID " + uuid + " không khớp máy chủ đã ghim " + pinnedUuid);
                                }
                            }
                        }
                    } else if (resp.startsWith(RESPONSE_PREFIX)) {
                        String[] parts = resp.split("\\|", 4);
                        if (parts.length >= 3) {
                            String serverIp = parts[1].trim();
                            if (serverIp.equals("127.0.0.1") || serverIp.equals("0.0.0.0")) {
                                serverIp = recvPacket.getAddress().getHostAddress();
                            }
                            int port = Integer.parseInt(parts[2].trim());
                            String name = (parts.length >= 4) ? parts[3].trim() : "Server Phòng Máy";
                            return new DiscoveredServer(serverIp, port, name);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[AutoDiscover] Lỗi quét server: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        return null;
    }

    /**
     * Quét tìm Server trong luồng nền (asynchronous), trả kết quả qua callback trên Swing EDT.
     */
    public static void discoverAsync(int timeoutMs, Consumer<DiscoveredServer> callback) {
        Thread t = new Thread(() -> {
            DiscoveredServer server = discover(timeoutMs);
            SwingUtilities.invokeLater(() -> callback.accept(server));
        }, "Client-AutoDiscover-Thread");
        t.setDaemon(true);
        t.start();
    }

    private static List<InetAddress> listAllBroadcastAddresses() {
        List<InetAddress> broadcastList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        broadcastList.add(broadcast);
                    }
                }
            }
        } catch (SocketException ignored) {}
        return broadcastList;
    }
}

