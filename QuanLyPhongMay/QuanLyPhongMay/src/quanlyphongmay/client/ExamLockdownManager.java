package quanlyphongmay.client;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ExamLockdownManager - Quản lý chế độ thi cử an toàn (Safe Exam & Lockdown Mode).
 * Khóa toàn bộ máy trạm, chặn phím tắt hệ thống (Alt+Tab, Win, Task Manager)
 * và cách ly truy cập Internet ngoại vi (chỉ cho phép mạng LAN nội bộ).
 */
public class ExamLockdownManager {

    private static JWindow examWindow = null;
    private static javax.swing.Timer examTimer = null;
    private static volatile boolean examActive = false;
    private static Thread processGuardThread = null;
    private static int remainingSeconds = 0;
    private static boolean internetBlocked = false;

    public static synchronized boolean isExamActive() {
        return examActive;
    }

    public static synchronized void enableExamMode(
            String examTitle,
            int durationMinutes,
            boolean blockInternet,
            String serverHost,
            String maMay,
            String tenMay) {

        SwingUtilities.invokeLater(() -> {
            if (examActive && examWindow != null && examWindow.isVisible()) {
                return;
            }

            examActive = true;
            remainingSeconds = Math.max(1, durationMinutes) * 60;
            internetBlocked = blockInternet;

            // 1. Áp dụng chính sách bảo mật Windows
            applyWindowsLockdownPolicies(true);

            // 2. Chặn Internet nếu được yêu cầu
            if (blockInternet) {
                setInternetBlock(true, serverHost);
            }

            // 3. Khởi tạo cửa sổ thi an toàn tràn màn hình
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();

            examWindow = new JWindow();
            examWindow.setAlwaysOnTop(true);
            examWindow.setBounds(screenBounds);

            // Chặn bàn phím và chuột
            AWTEventListener keyMouseBlocker = event -> {
                if (event instanceof KeyEvent ke) {
                    // Chặn các phím Alt, Windows, Tab, F4, Esc
                    int code = ke.getKeyCode();
                    if (code == KeyEvent.VK_ALT || code == KeyEvent.VK_WINDOWS
                            || code == KeyEvent.VK_TAB || code == KeyEvent.VK_ESCAPE
                            || code == KeyEvent.VK_F4 || code == KeyEvent.VK_CONTEXT_MENU) {
                        ke.consume();
                    }
                }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(keyMouseBlocker, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
            examWindow.getRootPane().putClientProperty("keyMouseBlocker", keyMouseBlocker);

            // Giao diện Safe Exam
            JPanel content = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(15, 23, 42, 252),
                            0, getHeight(), new Color(30, 41, 59, 254)
                    );
                    g2.setPaint(gp);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                }
            };
            content.setOpaque(false);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(30, 50, 30, 50));

            // Icon bài thi
            JLabel lblIcon = new JLabel("[Lockdown]", SwingConstants.CENTER);
            lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 64));
            lblIcon.setForeground(new Color(56, 189, 248));
            lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Tiêu đề thi cử
            JLabel lblHeader = new JLabel("CHẾ ĐỘ KHÓA BẢO MẬT HỆ THỐNG — SYSTEM LOCKDOWN", SwingConstants.CENTER);
            lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 26));
            lblHeader.setForeground(new Color(248, 250, 252));
            lblHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Tên bài thi
            JLabel lblExamName = new JLabel("Phiên bảo mật: " + examTitle, SwingConstants.CENTER);
            lblExamName.setFont(new Font("Segoe UI", Font.BOLD, 19));
            lblExamName.setForeground(new Color(251, 191, 36)); // Vàng nổi bật
            lblExamName.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Cảnh báo
            JLabel lblWarning = new JLabel(
                    "Mọi phím tắt hệ thống (Alt+Tab, Win) đã bị vô hiệu hóa."
                    + (blockInternet ? " Internet bên ngoài đã bị chặn." : ""),
                    SwingConstants.CENTER
            );
            lblWarning.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            lblWarning.setForeground(new Color(203, 213, 225));
            lblWarning.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Đồng hồ đếm ngược thời gian làm bài
            JLabel lblCountdown = new JLabel("Thời gian còn lại: 00:00", SwingConstants.CENTER);
            lblCountdown.setFont(new Font("Consolas", Font.BOLD, 36));
            lblCountdown.setForeground(new Color(52, 211, 153)); // Xanh lá
            lblCountdown.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Thông tin thí sinh
            JLabel lblCandidate = new JLabel("Thí sinh: " + maMay + " (" + tenMay + ")", SwingConstants.CENTER);
            lblCandidate.setFont(new Font("Segoe UI", Font.ITALIC, 15));
            lblCandidate.setForeground(new Color(148, 163, 184));
            lblCandidate.setAlignmentX(Component.CENTER_ALIGNMENT);

            card.add(lblIcon);
            card.add(Box.createVerticalStrut(15));
            card.add(lblHeader);
            card.add(Box.createVerticalStrut(10));
            card.add(lblExamName);
            card.add(Box.createVerticalStrut(12));
            card.add(lblWarning);
            card.add(Box.createVerticalStrut(25));
            card.add(lblCountdown);
            card.add(Box.createVerticalStrut(15));
            card.add(lblCandidate);

            content.add(card);
            examWindow.setContentPane(content);
            examWindow.setVisible(true);
            examWindow.toFront();
            examWindow.requestFocus();

            // Timer đếm ngược
            examTimer = new javax.swing.Timer(1000, e -> {
                if (remainingSeconds > 0) {
                    remainingSeconds--;
                    int m = remainingSeconds / 60;
                    int s = remainingSeconds % 60;
                    lblCountdown.setText(String.format("Thời gian còn lại: %02d:%02d", m, s));
                    if (remainingSeconds <= 300) { // Dưới 5 phút chuyển sang màu đỏ
                        lblCountdown.setForeground(new Color(248, 113, 113));
                    }
                } else {
                    lblCountdown.setText("HẾT GIỜ LÀM BÀI!");
                    lblCountdown.setForeground(new Color(248, 113, 113));
                }
                // Giữ cửa sổ luôn ở trên cùng và chiếm focus
                if (examWindow != null && examWindow.isVisible()) {
                    examWindow.toFront();
                }
            });
            examTimer.start();

            // Khởi động luồng giám sát Task Manager
            startTaskmgrGuard();
        });
    }

    public static synchronized void disableExamMode() {
        SwingUtilities.invokeLater(() -> {
            examActive = false;

            if (examTimer != null) {
                examTimer.stop();
                examTimer = null;
            }

            if (processGuardThread != null) {
                processGuardThread.interrupt();
                processGuardThread = null;
            }

            if (examWindow != null) {
                Object blocker = examWindow.getRootPane().getClientProperty("keyMouseBlocker");
                if (blocker instanceof AWTEventListener listener) {
                    Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
                }
                examWindow.setVisible(false);
                examWindow.dispose();
                examWindow = null;
            }

            // Gỡ bỏ chính sách khóa Windows & mở lại Internet
            applyWindowsLockdownPolicies(false);
            if (internetBlocked) {
                setInternetBlock(false, null);
                internetBlocked = false;
            }
        });
    }

    private static void startTaskmgrGuard() {
        processGuardThread = new Thread(() -> {
            while (examActive && !Thread.currentThread().isInterrupted()) {
                try {
                    // Nếu người dùng cố bật Task Manager, tắt ngay lập tức
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        new ProcessBuilder("taskkill", "/F", "/IM", "taskmgr.exe")
                                .redirectErrorStream(true)
                                .start();
                    }
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        }, "Exam-Taskmgr-Guard");
        processGuardThread.setDaemon(true);
        processGuardThread.start();
    }

    private static void applyWindowsLockdownPolicies(boolean lock) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }
        try {
            if (lock) {
                // Vô hiệu hóa Task Manager qua Windows Registry
                new ProcessBuilder("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                        "/v", "DisableTaskMgr", "/t", "REG_DWORD", "/d", "1", "/f").start();
            } else {
                // Bật lại Task Manager
                new ProcessBuilder("reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                        "/v", "DisableTaskMgr", "/f").start();
            }
        } catch (IOException ignored) {}
    }

    private static void setInternetBlock(boolean block, String serverHost) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }
        try {
            if (block) {
                // Cho phép kết nối tới Server phòng máy
                if (serverHost != null && !serverHost.isEmpty()) {
                    new ProcessBuilder("netsh", "advfirewall", "firewall", "add", "rule",
                            "name=LANPC_Exam_Allow_Server", "dir=out", "action=allow",
                            "remoteip=" + serverHost).start();
                }
                // Chặn kết nối Web ngoài (HTTP:80, HTTPS:443)
                new ProcessBuilder("netsh", "advfirewall", "firewall", "add", "rule",
                        "name=LANPC_Exam_Block_Web", "dir=out", "action=block",
                        "protocol=TCP", "remoteport=80,443").start();
            } else {
                // Gỡ bỏ luật tường lửa
                new ProcessBuilder("netsh", "advfirewall", "firewall", "delete", "rule",
                        "name=LANPC_Exam_Block_Web").start();
                new ProcessBuilder("netsh", "advfirewall", "firewall", "delete", "rule",
                        "name=LANPC_Exam_Allow_Server").start();
            }
        } catch (IOException ignored) {}
    }
}

