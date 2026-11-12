package quanlyphongmay.client;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RemoteControlHandler - Tiep nhan va thuc thi cac lenh dieu khien tu xa
 * tu Server (chuot, ban phim) gia lap qua java.awt.Robot.
 * Dam bao an toan luong va xu ly ngoai le triet de.
 */
public class RemoteControlHandler {

    private static final Logger logger = Logger.getLogger(RemoteControlHandler.class.getName());

    private Robot robot;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Client-RemoteControl-Worker");
        t.setDaemon(true);
        return t;
    });

    public RemoteControlHandler() {
        try {
            this.robot = new Robot();
            this.robot.setAutoWaitForIdle(false);
            this.robot.setAutoDelay(0);
        } catch (AWTException e) {
            logger.log(Level.SEVERE, "[RemoteControl] Khong the khoi tao Robot: " + e.getMessage(), e);
            this.robot = null;
        }
    }

    public void stop() {
        if (!executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    /**
     * Boc tach va xu ly goi tin dieu khien tu Server:
     * Dinh dang: CONTROL|ACTION|x|y|button[|frameW|frameH]
     */
    public void handleCommand(String packet) {
        if (robot == null || packet == null || !packet.startsWith("CONTROL|")) {
            return;
        }

        executor.submit(() -> {
            try {
                String[] parts = packet.split("\\|");
                if (parts.length < 5) return;

                String action = parts[1].trim().toUpperCase();
                int x = Integer.parseInt(parts[2].trim());
                int y = Integer.parseInt(parts[3].trim());
                int button = Integer.parseInt(parts[4].trim());

                int frameW = (parts.length > 5) ? Integer.parseInt(parts[5].trim()) : 0;
                int frameH = (parts.length > 6) ? Integer.parseInt(parts[6].trim()) : 0;

                // Quy doi toa do theo do phan giai thuc te cua man hinh Client
                Point actualPoint = translateCoordinates(x, y, frameW, frameH);

                executeAction(action, actualPoint.x, actualPoint.y, button, x);

            } catch (Exception e) {
                logger.log(Level.WARNING, "[RemoteControl] Loi xu ly lenh: " + packet + " -> " + e.getMessage());
            }
        });
    }

    /**
     * Quy doi toa do tu khung hinh truyen ve Server sang toa do man hinh thuc te cua may tram Client.
     */
    private Point translateCoordinates(int x, int y, int frameW, int frameH) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        if (frameW <= 0 || frameH <= 0) {
            if (screenSize.width > 1280) {
                frameW = 1280;
                frameH = (int) ((double) screenSize.height * 1280 / screenSize.width);
            } else {
                frameW = screenSize.width;
                frameH = screenSize.height;
            }
        }

        int targetX = (int) Math.round((double) x * screenSize.width / frameW);
        int targetY = (int) Math.round((double) y * screenSize.height / frameH);

        targetX = Math.max(0, Math.min(screenSize.width - 1, targetX));
        targetY = Math.max(0, Math.min(screenSize.height - 1, targetY));

        return new Point(targetX, targetY);
    }

    private void executeAction(String action, int x, int y, int button, int rawParam1) {
        if (robot == null) return;

        switch (action) {
            case "MOVE" -> {
                robot.mouseMove(x, y);
            }
            case "PRESS" -> {
                robot.mouseMove(x, y);
                int mask = getButtonMask(button);
                robot.mousePress(mask);
            }
            case "RELEASE" -> {
                robot.mouseMove(x, y);
                int mask = getButtonMask(button);
                robot.mouseRelease(mask);
            }
            case "CLICK" -> {
                robot.mouseMove(x, y);
                int mask = getButtonMask(button);
                robot.mousePress(mask);
                robot.delay(15);
                robot.mouseRelease(mask);
            }
            case "DOUBLE_CLICK" -> {
                robot.mouseMove(x, y);
                int mask = getButtonMask(button);
                robot.mousePress(mask);
                robot.delay(15);
                robot.mouseRelease(mask);
                robot.delay(60);
                robot.mousePress(mask);
                robot.delay(15);
                robot.mouseRelease(mask);
            }
            case "WHEEL" -> {
                robot.mouseWheel(button); // button luu wheelRotation
            }
            case "KEY_PRESS", "KEY_DOWN" -> {
                int keyCode = rawParam1;
                try {
                    robot.keyPress(keyCode);
                } catch (IllegalArgumentException ignored) {}
            }
            case "KEY_RELEASE", "KEY_UP" -> {
                int keyCode = rawParam1;
                try {
                    robot.keyRelease(keyCode);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private int getButtonMask(int button) {
        return switch (button) {
            case 2 -> InputEvent.BUTTON2_DOWN_MASK; // Chuot giua
            case 3 -> InputEvent.BUTTON3_DOWN_MASK; // Chuot phai
            default -> InputEvent.BUTTON1_DOWN_MASK; // Chuot trai
        };
    }
}

