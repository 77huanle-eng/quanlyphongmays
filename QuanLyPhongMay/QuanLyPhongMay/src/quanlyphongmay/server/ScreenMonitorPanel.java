package quanlyphongmay.server;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * ScreenMonitorPanel - Panel hien thi hinh anh man hinh truc tiep nhan tu may tram Client.
 * Ho tro tuong tac dieu khien tu xa (Interactive Remote Control):
 * - Bat su kien chuot va ban phim.
 * - Tinh toan ty le toa do X, Y chinh xac tuyet doi khong lech vi tri click.
 * - Dong goi ban tin socket: CONTROL|ACTION|x|y|button[|frameW|frameH].
 */
public class ScreenMonitorPanel extends JPanel {

    private BufferedImage currentFrame = null;
    private String statusMessage = "Đang chờ kết nối hình ảnh...";
    private int fps = 0;
    private int frameCounter = 0;
    private long lastFpsCalcTime = System.currentTimeMillis();
    private double currentKBs = 0.0;
    private long bytesInSecond = 0;

    // Dieu khien tu xa (Remote Control)
    private Consumer<String> commandSender;
    private boolean remoteControlEnabled = false;
    private long lastMouseMoveTime = 0;

    public ScreenMonitorPanel() {
        setBackground(new Color(15, 23, 42)); // Nen Slate 900
        setDoubleBuffered(true);
        setFocusable(true);

        initInputListeners();
    }

    public void setCommandSender(Consumer<String> commandSender) {
        this.commandSender = commandSender;
    }

    public void setRemoteControlEnabled(boolean enabled) {
        this.remoteControlEnabled = enabled;
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (enabled) {
            requestFocusInWindow();
        }
        repaint();
    }

    public boolean isRemoteControlEnabled() {
        return remoteControlEnabled;
    }

    public void setStatusMessage(String status) {
        this.statusMessage = status;
        repaint();
    }

    public synchronized void updateFrame(BufferedImage frame, int byteSize) {
        BufferedImage oldFrame = this.currentFrame;
        this.currentFrame = frame;
        if (oldFrame != null && oldFrame != frame) {
            oldFrame.flush();
        }
        this.statusMessage = null;

        // Tinh FPS va Data Rate
        frameCounter++;
        bytesInSecond += byteSize;

        long now = System.currentTimeMillis();
        long diff = now - lastFpsCalcTime;
        if (diff >= 1000) {
            fps = (int) Math.round((frameCounter * 1000.0) / diff);
            currentKBs = (bytesInSecond / 1024.0) / (diff / 1000.0);
            frameCounter = 0;
            bytesInSecond = 0;
            lastFpsCalcTime = now;
        }

        repaint();
    }

    public synchronized BufferedImage getCurrentFrame() {
        return currentFrame;
    }

    public boolean saveSnapshot(File targetFile) {
        BufferedImage frame = currentFrame;
        if (frame == null) {
            return false;
        }
        try {
            return ImageIO.write(frame, "PNG", targetFile);
        } catch (IOException e) {
            System.err.println("Loi luu snapshot: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tinh toan ty le toa do tren Panel sang toa do thuc te cua frame Client
    // ─────────────────────────────────────────────────────────────────────────
    private Point toClientCoords(Point p) {
        BufferedImage frame = currentFrame;
        if (frame == null) return null;

        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int imgWidth = frame.getWidth();
        int imgHeight = frame.getHeight();

        if (panelWidth <= 0 || panelHeight <= 0 || imgWidth <= 0 || imgHeight <= 0) return null;
        double scale = Math.min((double) panelWidth / imgWidth, (double) panelHeight / imgHeight);
        if (scale <= 0.00001) return null;
        int drawWidth = (int) (imgWidth * scale);
        int drawHeight = (int) (imgHeight * scale);
        int x = (panelWidth - drawWidth) / 2;
        int y = (panelHeight - drawHeight) / 2;

        if (p.x < x || p.x >= x + drawWidth || p.y < y || p.y >= y + drawHeight) {
            return null; // Chuot nam ngoai vung man hinh hien thi
        }

        int clientX = (int) Math.round((p.x - x) / scale);
        int clientY = (int) Math.round((p.y - y) / scale);

        clientX = Math.max(0, Math.min(imgWidth - 1, clientX));
        clientY = Math.max(0, Math.min(imgHeight - 1, clientY));

        return new Point(clientX, clientY);
    }

    private void sendControl(String action, int x, int y, int button) {
        if (commandSender == null || !remoteControlEnabled) return;
        BufferedImage frame = currentFrame;
        int frameW = (frame != null) ? frame.getWidth() : 1280;
        int frameH = (frame != null) ? frame.getHeight() : 720;

        // Dinh dang ban tin socket chuan: CONTROL|ACTION|x|y|button|frameW|frameH
        String packet = "CONTROL|" + action + "|" + x + "|" + y + "|" + button + "|" + frameW + "|" + frameH;
        commandSender.accept(packet);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dang ky cac su kien chuot va ban phim cho Interactive Remote Control
    // ─────────────────────────────────────────────────────────────────────────
    private void initInputListeners() {
        // Su kien Chuot
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!remoteControlEnabled) return;
                requestFocusInWindow();
                Point p = toClientCoords(e.getPoint());
                if (p != null) {
                    sendControl("PRESS", p.x, p.y, e.getButton());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!remoteControlEnabled) return;
                Point p = toClientCoords(e.getPoint());
                if (p != null) {
                    sendControl("RELEASE", p.x, p.y, e.getButton());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!remoteControlEnabled) return;
                Point p = toClientCoords(e.getPoint());
                if (p != null) {
                    if (e.getClickCount() == 2) {
                        sendControl("DOUBLE_CLICK", p.x, p.y, e.getButton());
                    } else {
                        sendControl("CLICK", p.x, p.y, e.getButton());
                    }
                }
            }
        });

        // Su kien Di chuyen Chuot (kem throttle ~35ms tranh spam mang)
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMove(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseMove(e);
            }

            private void handleMouseMove(MouseEvent e) {
                if (!remoteControlEnabled) return;
                long now = System.currentTimeMillis();
                if (now - lastMouseMoveTime < 35) return;
                lastMouseMoveTime = now;

                Point p = toClientCoords(e.getPoint());
                if (p != null) {
                    sendControl("MOVE", p.x, p.y, 0);
                }
            }
        });

        // Su kien Cuon Chuot (Mouse Wheel)
        addMouseWheelListener(e -> {
            if (!remoteControlEnabled) return;
            Point p = toClientCoords(e.getPoint());
            int x = (p != null) ? p.x : 0;
            int y = (p != null) ? p.y : 0;
            sendControl("WHEEL", x, y, e.getWheelRotation());
        });

        // Su kien Ban phim
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!remoteControlEnabled) return;
                sendControl("KEY_PRESS", e.getKeyCode(), 0, 0);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (!remoteControlEnabled) return;
                sendControl("KEY_RELEASE", e.getKeyCode(), 0, 0);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int panelWidth = getWidth();
        int panelHeight = getHeight();

        BufferedImage frame = currentFrame;
        if (frame != null) {
            int imgWidth = frame.getWidth();
            int imgHeight = frame.getHeight();

            // Tinh ti le co gian bao toan khung hinh (Letterbox)
            double scale = Math.min((double) panelWidth / imgWidth, (double) panelHeight / imgHeight);
            int drawWidth = (int) (imgWidth * scale);
            int drawHeight = (int) (imgHeight * scale);
            int x = (panelWidth - drawWidth) / 2;
            int y = (panelHeight - drawHeight) / 2;

            g2.drawImage(frame, x, y, drawWidth, drawHeight, null);

            // Vien man hinh: Mau do/cam ruc ro neu dang Remote Control, mau Slate mac dinh neu chi xem
            if (remoteControlEnabled) {
                g2.setColor(new Color(239, 68, 68)); // Do ruc ro
                g2.setStroke(new BasicStroke(2.0f));
            } else {
                g2.setColor(new Color(51, 65, 85));
                g2.setStroke(new BasicStroke(1.0f));
            }
            g2.drawRect(x - 1, y - 1, drawWidth + 2, drawHeight + 2);

            // Overlay HUD thong so o goc tren ben trai
            drawOverlayHUD(g2, imgWidth, imgHeight);

        } else {
            // Chua co hinh anh -> Hien thi man hinh cho
            g2.setColor(new Color(148, 163, 184));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            FontMetrics fm = g2.getFontMetrics();

            String msg = (statusMessage != null) ? statusMessage : "Không có tín hiệu màn hình";
            int textWidth = fm.stringWidth(msg);
            int textX = (panelWidth - textWidth) / 2;
            int textY = panelHeight / 2;

            // Ve bieu tuong man hinh vector sac net
            int iconW = 64, iconH = 40;
            int ix = (panelWidth - iconW) / 2;
            int iy = textY - 55;
            g2.setColor(new Color(30, 41, 59));
            g2.fillRoundRect(ix, iy, iconW, iconH, 6, 6);
            g2.setColor(new Color(6, 182, 212));
            g2.drawRoundRect(ix, iy, iconW, iconH, 6, 6);
            // Chan de man hinh
            g2.setColor(new Color(148, 163, 184));
            g2.fillRect(ix + iconW / 2 - 3, iy + iconH, 6, 8);
            g2.fillRect(ix + iconW / 2 - 14, iy + iconH + 8, 28, 3);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            g2.setColor(new Color(226, 232, 240));
            g2.drawString(msg, textX, textY + 30);
        }

        g2.dispose();
    }

    private void drawOverlayHUD(Graphics2D g2, int imgW, int imgH) {
        String hudText = String.format("FPS: %d | Độ phân giải: %dx%d | Băng thông: %.1f KB/s%s",
                fps, imgW, imgH, currentKBs,
                remoteControlEnabled ? " | [ĐIỀU KHIỂN TỪ XA: ĐANG BẬT]" : "");

        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(hudText);
        int pad = 6;

        // Nen mo HUD
        g2.setColor(new Color(15, 23, 42, 195));
        g2.fillRoundRect(12, 12, textWidth + pad * 2, fm.getHeight() + pad, 6, 6);

        // Cham tron trang thai: Xanh LIVE hoac Do REMOTE
        if (remoteControlEnabled) {
            g2.setColor(new Color(239, 68, 68)); // Do
        } else {
            g2.setColor(new Color(34, 197, 94)); // Xanh
        }
        g2.fillOval(18, 12 + (fm.getHeight() + pad - 8) / 2, 8, 8);

        // Chu HUD
        if (remoteControlEnabled) {
            g2.setColor(new Color(254, 202, 202));
        } else {
            g2.setColor(new Color(241, 245, 249));
        }
        g2.drawString(hudText, 32, 12 + pad + fm.getAscent() - 2);
    }
}
