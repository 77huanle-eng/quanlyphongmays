package quanlyphongmay;

import quanlyphongmay.server.ServerForm;
import quanlyphongmay.client.ClientForm;
import javax.swing.*;

/**
 * Điểm khởi chạy chính của dự án Quản Lý Phòng Máy
 */
public class QuanLyPhongMay {
    public static void main(String[] args) {
        // Áp dụng Design System Dark Theme toàn cục
        try {
            quanlyphongmay.common.UITheme.setupGlobalTheme();
        } catch (Exception ignored) {}

        // Nếu có tham số dòng lệnh "client" hoặc "--autostart" -> chạy ClientForm
        if (args.length > 0 && ("client".equalsIgnoreCase(args[0]) || "--autostart".equalsIgnoreCase(args[0]) || "-autostart".equalsIgnoreCase(args[0]))) {
            ClientForm.main(args);
            return;
        }

        // Mặc định khởi chạy ServerForm
        SwingUtilities.invokeLater(() -> {
            ServerForm server = new ServerForm();
            server.setLocationRelativeTo(null);
            server.setVisible(true);
        });
    }
}
