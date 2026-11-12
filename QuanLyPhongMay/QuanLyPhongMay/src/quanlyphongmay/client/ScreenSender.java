package quanlyphongmay.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * ScreenSender - Chụp màn hình máy trạm, nén ảnh JPEG và truyền luồng liên tục
 * về máy chủ Server qua Socket chuyên dụng.
 */
public class ScreenSender implements Runnable {

    private final String serverHost;
    private final int streamPort;
    private final String clientName;
    private volatile boolean running = false;
    private Socket streamSocket;
    private Thread thread;

    // Tần số khung hình mục tiêu: ~12-15 FPS (khoảng 70ms/frame)
    private static final int FRAME_DELAY_MS = 70;
    // Tỉ lệ nén JPEG (0.55 mang lại chất lượng xem rõ nét nhưng băng thông chỉ ~200-300KB/s)
    private static final float JPEG_QUALITY = 0.55f;
    // Chiều rộng tối đa khung hình để tránh quá tải mạng LAN
    private static final int MAX_WIDTH = 1280;

    public ScreenSender(String serverHost, int streamPort, String clientName) {
        this.serverHost = serverHost;
        this.streamPort = streamPort;
        this.clientName = clientName;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this, "Client-ScreenSender");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (streamSocket != null && !streamSocket.isClosed()) {
            try {
                streamSocket.close();
            } catch (IOException ignored) {}
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    @Override
    public void run() {
        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            System.err.println("[ScreenSender] Không thể khởi tạo java.awt.Robot: " + e.getMessage());
            return;
        }

        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        try {
            streamSocket = new Socket(serverHost, streamPort);
            streamSocket.setTcpNoDelay(true);

            DataOutputStream dataOut = new DataOutputStream(streamSocket.getOutputStream());

            // Gửi header nhận diện máy trạm: tên máy
            byte[] nameBytes = clientName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dataOut.writeInt(nameBytes.length);
            dataOut.write(nameBytes);
            dataOut.flush();

            // Khởi tạo JPEG Writer
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("Không tìm thấy JPG ImageWriter");
            }
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);

            ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);

            while (running && !Thread.currentThread().isInterrupted() && !streamSocket.isClosed()) {
                long frameStart = System.currentTimeMillis();

                // 1. Chụp màn hình
                BufferedImage screenImg = robot.createScreenCapture(screenRect);

                // 2. Tỉ lệ thu nhỏ nếu kích thước màn hình quá lớn
                BufferedImage targetImg = screenImg;
                if (screenRect.width > MAX_WIDTH) {
                    int targetWidth = MAX_WIDTH;
                    int targetHeight = (int) ((double) screenRect.height * MAX_WIDTH / screenRect.width);
                    targetImg = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = targetImg.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(screenImg, 0, 0, targetWidth, targetHeight, null);
                    g2.dispose();
                }

                // 3. Nén ảnh sang JPEG
                baos.reset();
                try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(mcios);
                    writer.write(null, new IIOImage(targetImg, null, null), param);
                }

                byte[] frameBytes = baos.toByteArray();

                // 4. Gửi độ dài (4 bytes int) và mảng byte dữ liệu frame
                dataOut.writeInt(frameBytes.length);
                dataOut.write(frameBytes);
                dataOut.flush();

                // 5. Điều tiết FPS ổn định
                long elapsed = System.currentTimeMillis() - frameStart;
                long sleepTime = FRAME_DELAY_MS - elapsed;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }

            writer.dispose();

        } catch (Exception e) {
            if (running) {
                System.err.println("[ScreenSender] Ngắt kết nối luồng hình ảnh: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }
}

