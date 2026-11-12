package quanlyphongmay.client;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ScreenLockOverlay - Cửa sổ khóa toàn màn hình máy trạm phía Client khi giáo viên phát lệnh LOCK.
 * Ngăn chặn thao tác bàn phím và chuột, hiển thị thông điệp thông báo quản trị viên đang khóa máy.
 */
public class ScreenLockOverlay {

    private static JWindow lockWindow = null;
    private static javax.swing.Timer clockTimer = null;
    private static volatile boolean locked = false;

    public static synchronized boolean isLocked() {
        return locked;
    }

    public static synchronized void lock(String maMay, String tenMay) {
        SwingUtilities.invokeLater(() -> {
            if (locked && lockWindow != null && lockWindow.isVisible()) {
                return;
            }

            locked = true;

            // Lấy toàn bộ vùng màn hình
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();

            lockWindow = new JWindow();
            lockWindow.setAlwaysOnTop(true);
            lockWindow.setBounds(screenBounds);

            // Chặn chuột và bàn phím
            AWTEventListener blocker = event -> {
                if (event instanceof InputEvent) {
                    ((InputEvent) event).consume();
                }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(blocker, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

            // Lưu blocker vào client property để gỡ khi unlock
            lockWindow.getRootPane().putClientProperty("blocker", blocker);

            // Giao diện khóa
            JPanel content = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Nền gradient tối sang trọng
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(15, 23, 42, 245),
                            0, getHeight(), new Color(30, 41, 59, 250)
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
            card.setBorder(new EmptyBorder(40, 60, 40, 60));

            // Icon khóa
            JLabel lblIcon = new JLabel("[Khóa]", SwingConstants.CENTER);
            lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 72));
            lblIcon.setForeground(new Color(239, 68, 68)); // Đỏ nổi bật
            lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Tiêu đề khóa
            JLabel lblTitle = new JLabel("MÁY TRẠM ĐANG BỊ KHÓA BỞI QUẢN TRỊ VIÊN", SwingConstants.CENTER);
            lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
            lblTitle.setForeground(new Color(248, 250, 252));
            lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Lời dặn dò
            JLabel lblSub = new JLabel("Vui lòng chú ý lắng nghe bài giảng và hướng dẫn trên bảng!", SwingConstants.CENTER);
            lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            lblSub.setForeground(new Color(203, 213, 225));
            lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Thông tin máy
            JLabel lblInfo = new JLabel("Thiết bị: " + maMay + " - " + tenMay, SwingConstants.CENTER);
            lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 15));
            lblInfo.setForeground(new Color(148, 163, 184));
            lblInfo.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Đồng hồ thời gian
            JLabel lblClock = new JLabel(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), SwingConstants.CENTER);
            lblClock.setFont(new Font("Segoe UI", Font.BOLD, 22));
            lblClock.setForeground(new Color(56, 189, 248)); // Xanh cyan
            lblClock.setAlignmentX(Component.CENTER_ALIGNMENT);

            clockTimer = new javax.swing.Timer(1000, e -> {
                lblClock.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            });
            clockTimer.start();

            card.add(lblIcon);
            card.add(Box.createVerticalStrut(20));
            card.add(lblTitle);
            card.add(Box.createVerticalStrut(12));
            card.add(lblSub);
            card.add(Box.createVerticalStrut(20));
            card.add(lblInfo);
            card.add(Box.createVerticalStrut(15));
            card.add(lblClock);

            content.add(card);
            lockWindow.setContentPane(content);
            lockWindow.setVisible(true);
            lockWindow.toFront();
            lockWindow.requestFocus();
        });
    }

    public static synchronized void unlock() {
        SwingUtilities.invokeLater(() -> {
            locked = false;
            if (clockTimer != null) {
                clockTimer.stop();
                clockTimer = null;
            }
            if (lockWindow != null) {
                Object blocker = lockWindow.getRootPane().getClientProperty("blocker");
                if (blocker instanceof AWTEventListener) {
                    Toolkit.getDefaultToolkit().removeAWTEventListener((AWTEventListener) blocker);
                }
                lockWindow.setVisible(false);
                lockWindow.dispose();
                lockWindow = null;
            }
        });
    }
}

