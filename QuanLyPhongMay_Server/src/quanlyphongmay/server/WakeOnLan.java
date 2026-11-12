package quanlyphongmay.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * WakeOnLan - Tiện ích gửi gói tin Magic Packet khởi động máy trạm từ xa qua mạng LAN
 */
public class WakeOnLan {

    private static final int DEFAULT_PORT = 9;

    /**
     * Gửi Magic Packet đến địa chỉ MAC chỉ định
     * @param macStr Chuỗi MAC (hỗ trợ các định dạng 00:11:22:33:44:55 hoặc 00-11-22-33-44-55)
     * @return true nếu gửi thành công gói tin UDP Broadcast
     */
    public static boolean sendMagicPacket(String macStr) {
        if (macStr == null || macStr.trim().isEmpty()) {
            return false;
        }

        try {
            byte[] macBytes = parseMac(macStr);
            if (macBytes.length != 6) {
                return false;
            }

            // Tạo Magic Packet: 6 bytes 0xFF theo sau bởi 16 lần lặp của MAC (Tổng: 102 bytes)
            byte[] bytes = new byte[6 + 16 * macBytes.length];
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) 0xFF;
            }
            for (int i = 6; i < bytes.length; i += macBytes.length) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
            }

            InetAddress address = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, DEFAULT_PORT);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.send(packet);
            }

            // Gửi thêm trên cổng 7 để tăng tỷ lệ nhận diện của card mạng
            DatagramPacket packetPort7 = new DatagramPacket(bytes, bytes.length, address, 7);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.send(packetPort7);
            }

            return true;
        } catch (Exception e) {
            System.err.println("Lỗi gửi Wake-on-LAN: " + e.getMessage());
            return false;
        }
    }

    private static byte[] parseMac(String macStr) throws IllegalArgumentException {
        String cleanMac = macStr.replaceAll("[:\\-]", "").trim();
        if (cleanMac.length() != 12) {
            throw new IllegalArgumentException("Địa chỉ MAC không đúng định dạng 12 ký tự hex: " + macStr);
        }

        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) Integer.parseInt(cleanMac.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}

