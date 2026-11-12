package quanlyphongmay.client;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ClientControlForm - Giao diện điều khiển máy trạm Client (Pure Java Swing)
 * Tích hợp giám sát hiệu năng, khóa toàn màn hình quản trị viên (ScreenLockOverlay)
 * và truyền trực tiếp màn hình (ScreenSender).
 */
public class ClientControlForm extends javax.swing.JFrame {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String maMay = "";
    private String tenMay = "";
    private String serverHost = "127.0.0.1";

    private volatile boolean daKhoa = false;
    private volatile boolean dangDong = false;
    private volatile boolean isUserInitiatedDisconnect = false;
    private volatile boolean isReconnecting = false;
    private Runnable onUserDisconnect;

    public void setOnUserDisconnect(Runnable onUserDisconnect) {
        this.onUserDisconnect = onUserDisconnect;
    }

    private ScreenSender screenSender; // Luồng truyền màn hình trực tiếp về Server
    private SystemMonitor systemMonitor; // Giám sát hiệu năng máy
    private IncidentDetector incidentDetector;
    private ProcessMonitor processMonitor;
    private HardwareScanner hardwareScanner;
    private RemoteControlHandler remoteControlHandler; // Tự động phát hiện lỗi và vi phạm
    private SilentInstallHandler silentInstaller; // Xử lý cài đặt ngầm
    // Cooldown cảnh báo máy trạm (chỉ popup nhắc người dùng máy con mỗi 3 phút cho từng loại lỗi)
    private final java.util.concurrent.ConcurrentHashMap<String, Long> clientAlertCooldown
            = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CLIENT_ALERT_COOLDOWN_MS = 3 * 60 * 1000L; // 3 phút

    // ── File receive state (accumulated across readLine calls) ────────────────
    private String         fileRecvName   = null;   // tên file đang nhận
    private long           fileRecvSize   = 0;      // kích thước khai báo (bytes)
    private List<byte[]>   fileRecvChunks = null;   // danh sách chunk đã decode

    private static final java.util.logging.Logger logger
            = java.util.logging.Logger.getLogger(ClientControlForm.class.getName());

    public String getTenMay() {
        return tenMay;
    }

    public String getMaMay() {
        return maMay;
    }

    public ClientControlForm() {
        initComponents();

        setTitle("Quản lý phòng máy - Client");
        setLocationRelativeTo(null);

        txtTrangThai.setText("Chưa kết nối");
        txtMaMay.setText("");

        txtTrangThai.setEditable(false);
        txtMaMay.setEditable(false);
        txtThongBao.setEditable(false);
        txtChat.setEditable(false);

        btnGui.setEnabled(false);
        btnKhoa.setEnabled(false);
        btnNgatKetNoi.setEnabled(false);

        txtTinNhan.setEditable(false);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                isUserInitiatedDisconnect = true;
                isReconnecting = false;
                dangDong = true;
                if (onUserDisconnect != null) {
                    try { onUserDisconnect.run(); } catch (Exception ignored) {}
                }
                dungTruyenManHinh();
                ScreenLockOverlay.unlock();
                dongSocket();
            }
        });

        applyTheme();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (out != null && socket != null && !socket.isClosed()) {
                    out.println("DISCONNECT|" + maMay);
                    out.flush();
                    socket.close();
                }
            } catch (Exception ignored) {}
        }, "Client-ShutdownHook"));
    }

    public ClientControlForm(
            Socket socket,
            BufferedReader in,
            PrintWriter out,
            String maMay,
            String tenMay) {

        this();

        this.socket = socket;
        this.in = in;
        this.out = out;
        this.maMay = maMay;
        this.tenMay = tenMay;
        this.serverHost = socket.getInetAddress().getHostAddress();

        txtTrangThai.setText("Đã kết nối Server");
        txtMaMay.setText(maMay + " - " + tenMay);

        btnGui.setEnabled(true);
        btnKhoa.setEnabled(true);
        btnNgatKetNoi.setEnabled(true);
        txtTinNhan.setEditable(true);

        btnKhoa.setText("Khóa giao diện");
        lblKhoaGiaoDien.setText("Trạng thái khóa: Chưa khóa");
        lblKhoaGiaoDien.setForeground(UITheme.SUCCESS);

        ghiThongBao("Đã kết nối Server.");
        batDauNhanDuLieu();

        // Bắt đầu giám sát hiệu năng và gửi về server
        systemMonitor = new SystemMonitor(maMay, serverHost, this::guiLenhServer);
        systemMonitor.setAlertListener(this::handleClientAlert);
        systemMonitor.start();

        // Khởi động module phát hiện sự cố và hành vi vi phạm
        incidentDetector = new IncidentDetector(maMay, this::guiLenhServer);
        incidentDetector.start();

        // Khởi động module giám sát tiến trình và ứng dụng đang chạy
        processMonitor = new ProcessMonitor(maMay, this::guiLenhServer);
        processMonitor.start();

        // Khởi động module quét thông số phần cứng Real-time chuẩn DxDiag
        hardwareScanner = new HardwareScanner(maMay, serverHost, this::guiLenhServer);
        hardwareScanner.start();

        // Khởi động module điều khiển từ xa tương tác trực tiếp
        remoteControlHandler = new RemoteControlHandler();
    }

    private void handleClientAlert(String type, String value, String msg) {
        long now = System.currentTimeMillis();
        Long lastTime = clientAlertCooldown.get(type);

        if (lastTime == null || (now - lastTime) > CLIENT_ALERT_COOLDOWN_MS) {
            clientAlertCooldown.put(type, now);

            String message;
            switch (type) {
                case "RAM_FULL" ->
                    message = "[!] BỘ NHỚ RAM BỊ ĐẦY: " + (msg != null ? msg : value) + "\nHãy đóng ngay các tab trình duyệt hoặc ứng dụng nặng!";
                case "DISK_FULL" ->
                    message = "[!] Ổ ĐĨA C: SẮP HẾT DUNG LƯỢNG: " + (msg != null ? msg : value) + "\nHãy dọn dẹp các tệp tin tạm trên ổ C:!";
                case "CPU_HIGH" ->
                    message = "[!] CPU của máy đang vượt mức an toàn: " + value + "%\nHãy tắt bớt các phần mềm nặng!";
                case "RAM_HIGH" ->
                    message = "[!] Bộ nhớ RAM sắp đầy: " + value + "%\nHãy đóng bớt các tab trình duyệt hoặc ứng dụng không cần thiết!";
                case "DISK_HIGH" ->
                    message = "[!] Dung lượng ổ đĩa hệ thống đã sử dụng " + value + "%\nHãy dọn dẹp ổ đĩa của bạn!";
                default ->
                    message = "[!] Cảnh báo tài nguyên: " + type + " - " + (msg != null ? msg : value);
            }

            SwingUtilities.invokeLater(() -> {
                ghiThongBao("[PERF] [Giám sát tải] " + message + " (Đã báo cáo lên Server)");
                hienThiThongBaoNonBlocking(this, "Cảnh báo tài nguyên hệ thống", message, JOptionPane.WARNING_MESSAGE);
            });
        }
    }

    /**
     * Hiển thị thông báo Toast / Popup Non-Blocking trên giao diện Client.
     * Tự động biến mất sau 8 giây, tuyệt đối không làm treo luồng mạng hoặc giao diện.
     */
    public void hienThiThongBaoNonBlocking(Component parent, String title, String message, int messageType) {
        SwingUtilities.invokeLater(() -> {
            try {
                JDialog dialog = new JDialog();
                dialog.setTitle(title);
                dialog.setModal(false);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                JPanel panel = new JPanel(new BorderLayout(15, 12));
                panel.setBackground(UITheme.BG_CARD);
                panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.BORDER, 1),
                    BorderFactory.createEmptyBorder(12, 16, 12, 16)
                ));

                Color iconColor = (messageType == JOptionPane.ERROR_MESSAGE) ? UITheme.DANGER :
                                  (messageType == JOptionPane.WARNING_MESSAGE) ? UITheme.WARNING : UITheme.SUCCESS;
                String symbol = (messageType == JOptionPane.ERROR_MESSAGE) ? "✕" :
                                (messageType == JOptionPane.WARNING_MESSAGE) ? "⚠" : "✓";
                JLabel lblIcon = new JLabel(symbol, SwingConstants.CENTER);
                lblIcon.setFont(new Font("Segoe UI", Font.BOLD, 26));
                lblIcon.setForeground(iconColor);
                lblIcon.setPreferredSize(new Dimension(36, 36));
                panel.add(lblIcon, BorderLayout.WEST);

                JPanel msgPanel = new JPanel(new GridLayout(2, 1, 2, 2));
                msgPanel.setOpaque(false);
                JLabel lblTitle = new JLabel(title);
                lblTitle.setFont(UITheme.FONT_TITLE);
                lblTitle.setForeground(iconColor);
                JLabel lblMsg = new JLabel("<html><body style='width: 300px; color: #f1f5f9;'>" + message + "</body></html>");
                lblMsg.setFont(UITheme.FONT_BODY);
                msgPanel.add(lblTitle);
                msgPanel.add(lblMsg);
                panel.add(msgPanel, BorderLayout.CENTER);

                JButton btnClose = new JButton("Đóng (8s)");
                UITheme.styleButtonPrimary(btnClose);
                btnClose.addActionListener(e -> dialog.dispose());
                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                btnPanel.setOpaque(false);
                btnPanel.add(btnClose);
                panel.add(btnPanel, BorderLayout.SOUTH);

                dialog.setContentPane(panel);
                dialog.pack();
                if (parent != null && parent.isShowing()) {
                    dialog.setLocationRelativeTo(parent);
                } else {
                    Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
                    dialog.setLocation(scr.width - dialog.getWidth() - 30, scr.height - dialog.getHeight() - 60);
                }
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);

                Timer t = new Timer(8000, e -> {
                    if (dialog.isDisplayable()) {
                        dialog.dispose();
                    }
                });
                t.setRepeats(false);
                t.start();
            } catch (Exception ignored) {}
        });
    }

    private void applyTheme() {
        getContentPane().setBackground(UITheme.BG_DARK);

        // Labels
        UITheme.styleLabelBody(lblTrangThaiTitle);
        UITheme.styleLabelBody(lblMaMayTitle);
        UITheme.styleLabelBody(jLabel1);
        UITheme.styleLabelBody(lblChatTitle);
        UITheme.styleLabelBody(lblTinNhanTitle);
        UITheme.styleLabelBody(lblKhoaGiaoDien);

        // Text fields
        UITheme.styleTextField(txtTrangThai);
        UITheme.styleTextField(txtMaMay);
        UITheme.styleTextField(txtTinNhan);

        // Text areas
        UITheme.styleTextArea(txtChat);
        UITheme.styleTextArea(txtThongBao);

        // Buttons
        UITheme.styleButtonAccent(btnGui);
        UITheme.styleButtonSecondary(btnKhoa);
        UITheme.styleButtonDanger(btnNgatKetNoi);

        // Scroll panes
        UITheme.styleScrollPane(jScrollPane1);
        UITheme.styleScrollPane(jScrollPane2);
    }

    private void ghiThongBao(String noiDung) {
        SwingUtilities.invokeLater(() -> {
            txtThongBao.append(noiDung + "\n");
            txtThongBao.setCaretPosition(txtThongBao.getDocument().getLength());
        });
    }

        private void collectAndSendServices() {
        try {
            // Chay sc.exe truc tiep (native Win32 binary, < 20ms, 0% CPU Overhead)
            ProcessBuilder pb = new ProcessBuilder("sc.exe", "query", "state=", "all");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            String currentName = "";
            String currentDisplay = "";
            String currentStatus = "Stopped";

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("SERVICE_NAME:")) {
                        if (!currentName.isEmpty()) {
                            if (sb.length() > 0) sb.append("~~");
                            sb.append(currentName).append("::").append(currentStatus).append("::").append(currentDisplay.isEmpty() ? currentName : currentDisplay);
                        }
                        currentName = line.substring("SERVICE_NAME:".length()).trim();
                        currentDisplay = "";
                        currentStatus = "Stopped";
                    } else if (line.startsWith("DISPLAY_NAME:")) {
                        currentDisplay = line.substring("DISPLAY_NAME:".length()).trim();
                    } else if (line.startsWith("STATE")) {
                        if (line.contains("RUNNING")) {
                            currentStatus = "Running";
                        } else {
                            currentStatus = "Stopped";
                        }
                    }
                }
                if (!currentName.isEmpty()) {
                    if (sb.length() > 0) sb.append("~~");
                    sb.append(currentName).append("::").append(currentStatus).append("::").append(currentDisplay.isEmpty() ? currentName : currentDisplay);
                }
            }
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            String maMay = (txtMaMay != null) ? txtMaMay.getText().trim() : "CLIENT";
            guiLenhServer("STARTUP_SERVICES|" + maMay + "|" + sb.toString());
        } catch (Exception ex) {
            ghiThongBao("[Dịch vụ] Lỗi lấy danh sách services: " + ex.getMessage());
        }
    }

    public synchronized boolean guiLenhServer(String lenh) {
        if (socket == null || socket.isClosed() || out == null || lenh == null) {
            return false;
        }

        try {
            // Loại bỏ hoàn toàn \r và \n để không làm hỏng gói tin println qua Socket
            String clean = lenh.replace("\r", " ").replace("\n", " ");
            out.println(clean);
            out.flush();

            if (out.checkError()) {
                ghiThongBao("Gửi dữ liệu thất bại.");
                return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void batDauNhanDuLieu() {
        Thread thread = new Thread(() -> {
            try {
                String duLieu;

                while (!dangDong && !isUserInitiatedDisconnect && in != null && (duLieu = in.readLine()) != null) {
                    final String msg = duLieu;
                    SwingUtilities.invokeLater(() -> xuLyLenhServer(msg));
                }

                if (!dangDong && !isUserInitiatedDisconnect) {
                    SwingUtilities.invokeLater(() -> {
                        ghiThongBao("[Mất mạng] Server đã ngắt kết nối. Đang tự động kết nối lại...");
                        kichHoatTuDongKetNoiLai();
                    });
                }

            } catch (IOException e) {
                if (!dangDong && !isUserInitiatedDisconnect) {
                    SwingUtilities.invokeLater(() -> {
                        ghiThongBao("[Mất mạng] Kết nối tới Server gián đoạn. Đang tự động kết nối lại...");
                        kichHoatTuDongKetNoiLai();
                    });
                }
            }
        });

        thread.setName("Client-Receiver");
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void kichHoatTuDongKetNoiLai() {
        if (dangDong || isUserInitiatedDisconnect || isReconnecting) return;
        isReconnecting = true;

        txtTrangThai.setText("Mất kết nối Server. Đang tự động kết nối lại sau 3s...");
        txtTrangThai.setForeground(UITheme.DANGER);
        btnGui.setEnabled(false);
        btnKhoa.setEnabled(false);
        btnNgatKetNoi.setEnabled(true);

        dungTruyenManHinh();
        ScreenLockOverlay.unlock();

        if (hardwareScanner != null) {
            try { hardwareScanner.stop(); } catch (Exception ignored) {}
            hardwareScanner = null;
        }
        if (processMonitor != null) {
            try { processMonitor.stop(); } catch (Exception ignored) {}
            processMonitor = null;
        }
        if (systemMonitor != null) {
            try { systemMonitor.stop(); } catch (Exception ignored) {}
            systemMonitor = null;
        }
        if (incidentDetector != null) {
            try { incidentDetector.stop(); } catch (Exception ignored) {}
            incidentDetector = null;
        }

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        socket = null;
        in = null;
        out = null;

        Thread reconnectThread = new Thread(() -> {
            while (!dangDong && !isUserInitiatedDisconnect && isReconnecting) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
                if (dangDong || isUserInitiatedDisconnect) break;

                // 1. Dò tìm Server IP qua UDP Broadcast nếu Server đổi IP hoặc vừa khởi động lại
                String targetIp = serverHost;
                int targetPort = 5000;
                try {
                    ClientAutoDiscover.DiscoveredServer ds = ClientAutoDiscover.discover(1500);
                    if (ds != null && ds.ip != null) {
                        targetIp = ds.ip;
                        targetPort = ds.port;
                    }
                } catch (Exception ignored) {}

                final String curIp = targetIp;
                final int curPort = targetPort;
                SwingUtilities.invokeLater(() -> {
                    txtTrangThai.setText("Đang thử kết nối lại tới " + curIp + ":" + curPort + "...");
                    txtTrangThai.setForeground(UITheme.WARNING);
                });

                try {
                    Socket newSocket = new Socket();
                    newSocket.connect(new java.net.InetSocketAddress(curIp, curPort), 2500);
                    newSocket.setSoTimeout(5000);

                    BufferedReader newIn = new BufferedReader(new java.io.InputStreamReader(newSocket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    PrintWriter newOut = new PrintWriter(new java.io.OutputStreamWriter(newSocket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);

                    // Gửi REGISTER
                    newOut.println("REGISTER|" + maMay + "|" + tenMay);
                    String resp = newIn.readLine();
                    if (resp == null || !resp.startsWith("OK|")) {
                        newSocket.close();
                        continue;
                    }

                    // Gửi INFO (kèm MAC thực tế)
                    String thongTinMay = ComputerInfo.getAllInfo();
                    newOut.println("INFO|" + maMay + "|" + thongTinMay);
                    String infoReply = newIn.readLine();

                    newSocket.setSoTimeout(0);

                    // Khôi phục kết nối thành công!
                    this.socket = newSocket;
                    this.in = newIn;
                    this.out = newOut;
                    this.serverHost = curIp;
                    this.isReconnecting = false;

                    SwingUtilities.invokeLater(() -> {
                        txtTrangThai.setText("Đã kết nối Server");
                        txtTrangThai.setForeground(UITheme.SUCCESS);
                        btnGui.setEnabled(true);
                        btnKhoa.setEnabled(true);
                        ghiThongBao("Đã tự động kết nối lại Server (" + curIp + ") thành công!");
                    });

                    // Khởi động lại luồng nhận dữ liệu
                    batDauNhanDuLieu();

                    // Khởi động lại các module giám sát
                    systemMonitor = new SystemMonitor(maMay, serverHost, this::guiLenhServer);
                    systemMonitor.setAlertListener(this::handleClientAlert);
                    systemMonitor.start();

                    incidentDetector = new IncidentDetector(maMay, this::guiLenhServer);
                    incidentDetector.start();

                    processMonitor = new ProcessMonitor(maMay, this::guiLenhServer);
                    processMonitor.start();

                    hardwareScanner = new HardwareScanner(maMay, serverHost, this::guiLenhServer);
                    hardwareScanner.start();

                    break; // Thoát vòng lặp reconnect thành công
                } catch (Exception retryEx) {
                    // Chưa kết nối được, vòng lặp sẽ ngủ 3s và thử lại
                }
            }
        }, "Client-AutoReconnect-Daemon");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void xuLyLenhServer(String duLieu) {
        if ("LOCK".equalsIgnoreCase(duLieu)) {
            ghiThongBao("[!] [CẢNH BÁO TỪ SERVER] Máy trạm đã bị khóa bởi Server.");
            datTrangThaiKhoa(true);
            try {
                // Khóa phiên làm việc Windows thông qua Runtime.getRuntime().exec
                Runtime.getRuntime().exec(new String[]{"rundll32.exe", "user32.dll,LockWorkStation"});
            } catch (Exception ignored) {}
            guiLenhServer("ACTION_ACK|" + maMay + "|LOCK|SUCCESS|Đã khóa màn hình máy trạm thành công");
            hienThiThongBaoNonBlocking(this, "Khóa máy", "Máy trạm đã bị khóa theo lệnh từ Máy chủ", JOptionPane.WARNING_MESSAGE);

        } else if ("UNLOCK".equalsIgnoreCase(duLieu)) {
            datTrangThaiKhoa(false);
            guiLenhServer("ACTION_ACK|" + maMay + "|UNLOCK|SUCCESS|Đã mở khóa màn hình máy trạm");
            hienThiThongBaoNonBlocking(this, "Mở khóa", "Máy trạm đã được mở khóa", JOptionPane.INFORMATION_MESSAGE);

        } else if (duLieu.startsWith("EXAM_LOCK|")) {
            // EXAM_LOCK|ENABLE|title|durationMinutes|blockInternet
            // EXAM_LOCK|DISABLE
            String[] p = duLieu.split("\\|", 5);
            if (p.length >= 2) {
                if ("ENABLE".equalsIgnoreCase(p[1])) {
                    String examTitle = (p.length > 2) ? p[2] : "Kiểm tra phòng máy";
                    int duration = 45;
                    try { if (p.length > 3) duration = Integer.parseInt(p[3].trim()); } catch (Exception ignored) {}
                    boolean blockNet = (p.length > 4) && Boolean.parseBoolean(p[4].trim());
                    ExamLockdownManager.enableExamMode(examTitle, duration, blockNet, serverHost, maMay, tenMay);
                    ghiThongBao("[Exam] Server kích hoạt Chế độ thi cử an toàn: " + examTitle);
                    guiLenhServer("ACTION_ACK|" + maMay + "|EXAM_LOCK|SUCCESS|Đã kích hoạt chế độ thi cử: " + examTitle);
                    hienThiThongBaoNonBlocking(this, "Chế độ thi cử", "Đã kích hoạt chế độ thi: " + examTitle + " (" + duration + " phút)", JOptionPane.WARNING_MESSAGE);
                } else {
                    ExamLockdownManager.disableExamMode();
                    ghiThongBao("[Mở khóa] Server kết thúc Chế độ thi cử an toàn.");
                    guiLenhServer("ACTION_ACK|" + maMay + "|EXAM_LOCK|SUCCESS|Đã kết thúc chế độ thi cử");
                    hienThiThongBaoNonBlocking(this, "Chế độ thi cử", "Đã kết thúc chế độ thi cử. Các ứng dụng hoạt động bình thường.", JOptionPane.INFORMATION_MESSAGE);
                }
            }

        } else if (duLieu.startsWith("SILENT_INSTALL_START|")) {
            String[] p = duLieu.split("\\|", 4);
            if (p.length >= 3) {
                String fName = p[1];
                long fSize = 0;
                try { fSize = Long.parseLong(p[2].trim()); } catch (Exception ignored) {}
                String args = (p.length > 3) ? p[3] : "";
                if (silentInstaller == null) {
                    silentInstaller = new SilentInstallHandler(maMay, this::guiLenhServer);
                }
                silentInstaller.handleStart(fName, fSize, args);
                ghiThongBao("[Cài đặt] Bắt đầu nhận file cài đặt ngầm: " + fName);
            }

        } else if (duLieu.startsWith("SILENT_INSTALL_CHUNK|")) {
            String b64 = duLieu.substring("SILENT_INSTALL_CHUNK|".length());
            if (silentInstaller != null) {
                silentInstaller.handleChunk(b64);
            }

        } else if (duLieu.startsWith("SILENT_INSTALL_END|")) {
            String fName = duLieu.substring("SILENT_INSTALL_END|".length()).trim();
            if (silentInstaller != null) {
                silentInstaller.handleEnd(fName);
                ghiThongBao("[Deploy] Đang tiến hành cài đặt ngầm: " + fName + "...");
            }
            guiLenhServer("ACTION_ACK|" + maMay + "|SILENT_INSTALL|SUCCESS|Đang thực thi cài đặt ngầm: " + fName);
            hienThiThongBaoNonBlocking(this, "Cài đặt phần mềm", "Đang tiến hành cài đặt ngầm: " + fName, JOptionPane.INFORMATION_MESSAGE);

        } else if (duLieu.startsWith("CLEAN_SYSTEM|")) {
            boolean fullClean = duLieu.contains("FULL");
            ghiThongBao("[Dọn rác] Đang thực hiện dọn dẹp hệ thống (Deep Clean)...");
            SystemCleaner.cleanAsync(fullClean, report -> {
                String packet = "CLEAN_RESULT|" + maMay + "|" + String.format(java.util.Locale.US, "%.2f", report.freedMB)
                        + "|" + report.filesDeleted + "|" + report.status;
                guiLenhServer(packet);
                guiLenhServer("ACTION_ACK|" + maMay + "|CLEAN_SYSTEM|SUCCESS|Đã giải phóng " + String.format(java.util.Locale.US, "%.2f", report.freedMB) + " MB rác hệ thống (" + report.filesDeleted + " files)");
                ghiThongBao("[OK] " + report.toString());
                hienThiThongBaoNonBlocking(this, "Dọn dẹp hệ thống", "Đã dọn sạch " + String.format(java.util.Locale.US, "%.1f", report.freedMB) + " MB rác hệ thống (" + report.filesDeleted + " files)", JOptionPane.INFORMATION_MESSAGE);
            });

        } else if (duLieu.startsWith("START_STREAM|")) {
            int port = 5001;
            try {
                port = Integer.parseInt(duLieu.substring("START_STREAM|".length()).trim());
            } catch (NumberFormatException ignored) {}
            batDauTruyenManHinh(port);
            guiLenhServer("ACTION_ACK|" + maMay + "|START_STREAM|SUCCESS|Đã bắt đầu truyền phát màn hình cổng " + port);
            hienThiThongBaoNonBlocking(this, "Truyền phát", "Máy chủ đang giám sát màn hình thời gian thực (Cổng " + port + ")", JOptionPane.INFORMATION_MESSAGE);

        } else if ("STOP_STREAM".equalsIgnoreCase(duLieu)) {
            dungTruyenManHinh();
            guiLenhServer("ACTION_ACK|" + maMay + "|STOP_STREAM|SUCCESS|Đã dừng truyền phát màn hình");
            hienThiThongBaoNonBlocking(this, "Truyền phát", "Đã dừng chia sẻ màn hình", JOptionPane.INFORMATION_MESSAGE);

        } else if ("SHUTDOWN".equalsIgnoreCase(duLieu)) {
            ghiThongBao("[Hệ thống] Nhận lệnh TẮT MÁY (Shut down) từ Server. Máy sẽ tắt trong 5 giây...");
            guiLenhServer("ACTION_ACK|" + maMay + "|SHUTDOWN|SUCCESS|Máy trạm đang tiến hành tắt nguồn trong 5 giây");
            hienThiThongBaoNonBlocking(this, "Tắt máy", "Máy trạm sẽ tắt nguồn trong 5 giây theo lệnh từ Máy chủ!", JOptionPane.WARNING_MESSAGE);
            try {
                Runtime.getRuntime().exec(new String[]{"shutdown", "-s", "-t", "5", "-c", "Server quan tri yeu cau tat may phong may."});
            } catch (Exception ex) {
                ghiThongBao("[Lỗi] Không thể thực thi lệnh tắt máy: " + ex.getMessage());
            }

        } else if ("RESTART".equalsIgnoreCase(duLieu)) {
            ghiThongBao("[Hệ thống] Nhận lệnh KHỞI ĐỘNG LẠI (Restart) từ Server. Máy sẽ khởi động lại trong 5 giây...");
            guiLenhServer("ACTION_ACK|" + maMay + "|RESTART|SUCCESS|Máy trạm đang khởi động lại trong 5 giây");
            hienThiThongBaoNonBlocking(this, "Khởi động lại", "Máy trạm sẽ khởi động lại trong 5 giây theo lệnh từ Máy chủ!", JOptionPane.WARNING_MESSAGE);
            try {
                Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "-t", "5", "-c", "Server quan tri yeu cau khoi dong lai phong may."});
            } catch (Exception ex) {
                ghiThongBao("[Lỗi] Không thể thực thi lệnh khởi động lại: " + ex.getMessage());
            }

        } else if ("SLEEP".equalsIgnoreCase(duLieu)) {
            ghiThongBao("[Hệ thống] Nhận lệnh CHẾ ĐỘ NGỦ (Sleep) từ Server...");
            guiLenhServer("ACTION_ACK|" + maMay + "|SLEEP|SUCCESS|Máy trạm đang chuyển sang chế độ ngủ");
            try {
                Runtime.getRuntime().exec(new String[]{"rundll32.exe", "powrprof.dll,SetSuspendState", "0", "1", "0"});
            } catch (Exception ex) {
                ghiThongBao("[Lỗi] Không thể thực thi lệnh đưa máy vào chế độ ngủ: " + ex.getMessage());
            }
        } else if (duLieu.startsWith("CONTROL|")) {
            if (remoteControlHandler != null) {
                remoteControlHandler.handleCommand(duLieu);
            }
            guiLenhServer("ACTION_ACK|" + maMay + "|CONTROL|SUCCESS|Đã kích hoạt nhận điều khiển từ xa");

        } else if (duLieu.startsWith("KILL_PROCESS|")) {
            String payload = duLieu.substring("KILL_PROCESS|".length()).trim();
            String[] parts = payload.split("\\|", 2);
            String pidStr = parts[0].trim();
            String pName = (parts.length > 1) ? parts[1].trim() : "";
            try {
                long pid = Long.parseLong(pidStr);
                try {
                    ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                } catch (Throwable ignored) {}

                new ProcessBuilder("taskkill", "/F", "/T", "/PID", pidStr)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                ghiThongBao("[Tiến trình] Đã buộc dừng tiến trình PID: " + pidStr + (pName.isEmpty() ? "" : " (" + pName + ")"));
                guiLenhServer("ACTION_ACK|" + maMay + "|KILL_PROCESS|SUCCESS|Đã dừng tiến trình PID " + pidStr + (pName.isEmpty() ? "" : " (" + pName + ")"));
                hienThiThongBaoNonBlocking(this, "Đóng tiến trình", "Đã dừng tiến trình PID: " + pidStr + (pName.isEmpty() ? "" : " (" + pName + ")"), JOptionPane.INFORMATION_MESSAGE);

                if (processMonitor != null) {
                    processMonitor.scanAndSendNow();
                }
            } catch (Exception ex) {
                ghiThongBao("[Tiến trình] Lỗi khi dừng PID " + pidStr + ": " + ex.getMessage());
                guiLenhServer("ACTION_ACK|" + maMay + "|KILL_PROCESS|ERROR|Lỗi dừng PID " + pidStr + ": " + ex.getMessage());
            }

        } else if (duLieu.startsWith("PROC_KILL|")) {
            String payload = duLieu.contains("|") ? duLieu.substring(duLieu.indexOf('|') + 1).trim() : "";
            String[] parts = payload.split("\\|");
            String pidStr = (parts.length >= 2 && parts[0].equalsIgnoreCase(maMay)) ? parts[1].trim() : parts[0].trim();
            try {
                long pid = Long.parseLong(pidStr);
                try { ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly); } catch (Throwable ignored) {}
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", pidStr).redirectErrorStream(true).start().waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                ghiThongBao("[Tiến trình] Đã buộc dừng tiến trình PID: " + pidStr);
                guiLenhServer("ACTION_ACK|" + maMay + "|KILL_PROCESS|SUCCESS|Đã dừng tiến trình PID: " + pidStr);
                hienThiThongBaoNonBlocking(this, "Đóng tiến trình", "Đã dừng tiến trình PID: " + pidStr, JOptionPane.INFORMATION_MESSAGE);
                if (processMonitor != null) processMonitor.scanAndSendNow();
            } catch (Exception ex) {
                ghiThongBao("[Tiến trình] Lỗi khi dừng PID " + pidStr + ": " + ex.getMessage());
                guiLenhServer("ACTION_ACK|" + maMay + "|KILL_PROCESS|ERROR|Lỗi dừng PID " + pidStr + ": " + ex.getMessage());
            }

        } else if (duLieu.startsWith("PROC_KILL_NAME|")) {
            String pName = duLieu.substring("PROC_KILL_NAME|".length()).trim();
            if (!pName.isEmpty()) {
                try {
                    String exeName = pName.toLowerCase().endsWith(".exe") ? pName : (pName + ".exe");
                    new ProcessBuilder("taskkill", "/F", "/T", "/IM", exeName).redirectErrorStream(true).start().waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                    new ProcessBuilder("taskkill", "/F", "/T", "/IM", pName).redirectErrorStream(true).start().waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        ProcessHandle.allProcesses().forEach(ph -> {
                            String cmd = ph.info().command().orElse("");
                            if (cmd.toLowerCase().contains(pName.toLowerCase())) {
                                ph.destroyForcibly();
                            }
                        });
                    } catch (Throwable ignored) {}
                    ghiThongBao("[Tiến trình] Đã cưỡng bức tắt tiến trình: " + pName);
                    guiLenhServer("ACTION_ACK|" + maMay + "|KILL_PROCESS|SUCCESS|Đã cưỡng bức tắt tiến trình: " + pName);
                    hienThiThongBaoNonBlocking(this, "Đóng ứng dụng", "Đã tắt ứng dụng: " + pName, JOptionPane.INFORMATION_MESSAGE);
                    if (processMonitor != null) processMonitor.scanAndSendNow();
                } catch (Exception ex) {
                    ghiThongBao("[Tiến trình] Lỗi khi tắt tiến trình " + pName + ": " + ex.getMessage());
                    guiLenhServer("ACTION_ACK|" + maMay + "|KILL_PROCESS|ERROR|Lỗi tắt " + pName + ": " + ex.getMessage());
                }
            }

        } else if ("REQ_SNAPSHOT".equalsIgnoreCase(duLieu)) {
            new Thread(() -> {
                try {
                    java.awt.Robot robot = new java.awt.Robot();
                    java.awt.Rectangle screenRect = new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
                    java.awt.image.BufferedImage capture = robot.createScreenCapture(screenRect);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(capture, "jpg", baos);
                    byte[] bytes = baos.toByteArray();
                    String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                    guiLenhServer("SNAPSHOT_DATA|" + maMay + "|" + b64);
                    ghiThongBao("[Ảnh chụp] Đã gửi ảnh chụp màn hình về Server.");
                    guiLenhServer("ACTION_ACK|" + maMay + "|SNAPSHOT|SUCCESS|Đã gửi ảnh chụp màn hình về Server");
                } catch (Exception ex) {
                    ghiThongBao("[Ảnh chụp] Lỗi khi chụp màn hình: " + ex.getMessage());
                }
            }, "Client-Snapshot-Worker").start();

        } else if (duLieu.startsWith("PROC_KILL_TREE|")) {
            String payload = duLieu.substring("PROC_KILL_TREE|".length()).trim();
            String[] parts = payload.split("\\|");
            String pidStr = (parts.length >= 2 && parts[0].equalsIgnoreCase(maMay)) ? parts[1].trim() : parts[0].trim();
            try {
                long pid = Long.parseLong(pidStr);
                try { ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly); } catch (Throwable ignored) {}
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", pidStr).redirectErrorStream(true).start().waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                ghiThongBao("[Tiến trình] Đã buộc dừng toàn bộ nhánh cây PID: " + pidStr);
                guiLenhServer("ACTION_ACK|" + maMay + "|KILL_PROCESS|SUCCESS|Đã dừng toàn bộ cây tiến trình PID: " + pidStr);
                hienThiThongBaoNonBlocking(this, "Đóng tiến trình", "Đã dừng cây tiến trình PID: " + pidStr, JOptionPane.INFORMATION_MESSAGE);
                if (processMonitor != null) processMonitor.scanAndSendNow();
            } catch (Exception ex) {
                ghiThongBao("[Tiến trình] Lỗi khi dừng cây PID " + pidStr + ": " + ex.getMessage());
            }

        } else if (duLieu.startsWith("PROC_RUN|")) {
            String payload = duLieu.substring("PROC_RUN|".length()).trim();
            String[] parts = payload.split("\\|", 2);
            String cmd = (parts.length >= 2 && parts[0].equalsIgnoreCase(maMay)) ? parts[1].trim() : payload;
            try {
                new ProcessBuilder("cmd.exe", "/c", "start", "", cmd).redirectErrorStream(true).start();
                ghiThongBao("[Tiến trình] Đã chạy lệnh: " + cmd);
                guiLenhServer("ACTION_ACK|" + maMay + "|PROC_RUN|SUCCESS|Đã thực thi tác vụ: " + cmd);
                hienThiThongBaoNonBlocking(this, "Khởi chạy tác vụ", "Đang chạy tác vụ: " + cmd, JOptionPane.INFORMATION_MESSAGE);
                if (processMonitor != null) processMonitor.scanAndSendNow();
            } catch (Exception ex) {
                ghiThongBao("[Tiến trình] Lỗi chạy tác vụ " + cmd + ": " + ex.getMessage());
            }

        } else if ("REQ_PROC_LIST".equalsIgnoreCase(duLieu) || "GET_PROCESSES".equalsIgnoreCase(duLieu)) {
            if (processMonitor != null) {
                processMonitor.scanAndSendNow();
            }

        } else if ("REQ_STARTUP_SERVICES".equalsIgnoreCase(duLieu)) {
            new Thread(this::collectAndSendServices, "StartupServicesCollector").start();

        } else if (duLieu.startsWith("SERVICE_CONTROL|")) {
            String[] parts = duLieu.split("\\|", 3);
            if (parts.length >= 3) {
                String action = parts[1].trim(); // start, stop, restart
                String svcName = parts[2].trim();
                new Thread(() -> {
                    try {
                        new ProcessBuilder("cmd.exe", "/c", "net", action, svcName).redirectErrorStream(true).start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                        collectAndSendServices();
                        guiLenhServer("ACTION_ACK|" + maMay + "|SERVICE_CONTROL|SUCCESS|Dịch vụ " + svcName + " -> " + action);
                    } catch (Exception ignored) {}
                }).start();
            }

        } else if ("REQ_FULL_SPECS".equalsIgnoreCase(duLieu) || "GET_HARDWARE_DETAIL".equalsIgnoreCase(duLieu)) {
            if (systemMonitor != null) {
                systemMonitor.sendHardwareDetail(true);
                systemMonitor.sendDxDiag();
            }
            if (hardwareScanner == null) {
                hardwareScanner = new HardwareScanner(maMay, serverHost, this::guiLenhServer);
                hardwareScanner.start();
            } else {
                hardwareScanner.scanAndSendNow();
            }
            guiLenhServer("ACTION_ACK|" + maMay + "|HARDWARE_DETAIL|SUCCESS|Đã cập nhật thông số phần cứng thời gian thực");

        } else if (duLieu.startsWith("RESTRICT_APPS|")) {
            String apps = duLieu.substring("RESTRICT_APPS|".length()).trim();
            ghiThongBao("[Chính sách] Server áp dụng danh sách chặn ứng dụng: " + apps);
            guiLenhServer("ACTION_ACK|" + maMay + "|RESTRICT_APPS|SUCCESS|Đã áp dụng danh sách chặn: " + apps);
            hienThiThongBaoNonBlocking(this, "Chính sách kiểm soát", "Máy chủ đã cập nhật danh sách ứng dụng bị hạn chế", JOptionPane.WARNING_MESSAGE);

        } else if ("RESTRICT_APPS_CLEAR".equalsIgnoreCase(duLieu)) {
            ghiThongBao("[Chính sách] Server gỡ bỏ toàn bộ danh sách hạn chế ứng dụng.");
            guiLenhServer("ACTION_ACK|" + maMay + "|RESTRICT_APPS|SUCCESS|Đã gỡ bỏ toàn bộ giới hạn ứng dụng");
            hienThiThongBaoNonBlocking(this, "Chính sách kiểm soát", "Đã gỡ bỏ toàn bộ hạn chế ứng dụng", JOptionPane.INFORMATION_MESSAGE);

        } else if (duLieu.startsWith("PING|")) {
            guiLenhServer("PONG|" + duLieu.substring(5));

        } else if ("PING".equalsIgnoreCase(duLieu)) {
            guiLenhServer("PONG");

        } else if (duLieu.startsWith("NOTICE|")) {
            String raw = duLieu.substring("NOTICE|".length()).trim();
            final String tieuDeHopThoai = "CẢNH BÁO TỪ SERVER";
            final String noiDungHienThi;

            String cleanRaw = raw.replace("[!]", "[!]").trim();
            if (cleanRaw.startsWith("[!] CẢNH BÁO TỪ SERVER") || cleanRaw.startsWith("CẢNH BÁO TỪ SERVER")) {
                noiDungHienThi = cleanRaw;
            } else {
                noiDungHienThi = "[CẢNH BÁO TỪ SERVER] " + cleanRaw;
            }

            ghiThongBao(noiDungHienThi);
            try {
                java.awt.Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {}

            guiLenhServer("ACTION_ACK|" + maMay + "|NOTICE|SUCCESS|Đã hiển thị thông báo tới màn hình người dùng");
            hienThiThongBaoNonBlocking(this, tieuDeHopThoai, noiDungHienThi, JOptionPane.WARNING_MESSAGE);

        } else if (duLieu.startsWith("CHAT|")) {
            String[] parts = duLieu.split("\\|", 3);
            String time = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

            String sender = "Máy chủ";
            if (parts.length == 3) {
                sender = parts[1];
                String content = parts[2];
                txtChat.append("[" + time + "] " + sender + ": " + content + "\n");
            } else {
                txtChat.append("[" + time + "] " + duLieu.substring(5) + "\n");
            }
            txtChat.setCaretPosition(txtChat.getDocument().getLength());
            guiLenhServer("ACTION_ACK|" + maMay + "|CHAT|SUCCESS|Đã nhận tin nhắn từ " + sender);

        } else if (duLieu.startsWith("OK|")) {
            ghiThongBao("[OK] " + duLieu.substring(3));

        } else if ("OK|BYE".equals(duLieu)) {
            ghiThongBao("Server xác nhận ngắt kết nối.");

        } else if (duLieu.startsWith("ERROR|")) {
            ghiThongBao("Lỗi từ Server: " + duLieu);

        } else if (duLieu.startsWith("FILESEND_START|")) {
            String[] p = duLieu.split("\\|", 3);
            if (p.length == 3) {
                fileRecvName = p[1];
                try { fileRecvSize = Long.parseLong(p[2].trim()); }
                catch (NumberFormatException ex) { fileRecvSize = 0; }
                fileRecvChunks = new ArrayList<>();
                ghiThongBao("[Nhận file] Đang nhận file: " + fileRecvName
                        + " (" + formatSize(fileRecvSize) + ")");
            }

        } else if (duLieu.startsWith("FILESEND_CHUNK|")) {
            if (fileRecvChunks != null) {
                String b64 = duLieu.substring("FILESEND_CHUNK|".length());
                try {
                    byte[] chunk = Base64.getDecoder().decode(b64);
                    fileRecvChunks.add(chunk);
                } catch (IllegalArgumentException ex) {
                    ghiThongBao("[!] Lỗi giải mã chunk: " + ex.getMessage());
                }
            }

        } else if (duLieu.startsWith("FILESEND_END|")) {
            if (fileRecvChunks != null && fileRecvName != null) {
                String finName = duLieu.substring("FILESEND_END|".length()).trim();
                saveReceivedFile(finName.isEmpty() ? fileRecvName : finName);
            }
            fileRecvName   = null;
            fileRecvSize   = 0;
            fileRecvChunks = null;

        } else {
            ghiThongBao("Server: " + duLieu);
        }
    }

    private void datTrangThaiKhoa(boolean khoa) {
        daKhoa = khoa;

        if (khoa) {
            ScreenLockOverlay.lock(maMay, tenMay);
        } else {
            ScreenLockOverlay.unlock();
        }

        txtTinNhan.setEditable(!khoa);

        btnGui.setEnabled(
                !khoa
                && socket != null
                && !socket.isClosed());

        btnKhoa.setText(
                khoa ? "Mở khóa giao diện" : "Khóa giao diện");

        lblKhoaGiaoDien.setText(
                khoa
                ? "Trạng thái khóa: Đã khóa toàn màn hình"
                : "Trạng thái khóa: Chưa khóa");

        ghiThongBao(
                khoa
                ? "[Khóa] Server đã kích hoạt khóa toàn màn hình máy trạm."
                : "[Mở khóa] Server đã mở khóa toàn màn hình máy trạm.");
    }

    private synchronized void batDauTruyenManHinh(int port) {
        dungTruyenManHinh();
        screenSender = new ScreenSender(serverHost, port, maMay);
        screenSender.start();
        ghiThongBao("[Stream] Bắt đầu truyền hình ảnh màn hình về Server (Port " + port + ").");
    }

    private synchronized void dungTruyenManHinh() {
        if (screenSender != null) {
            screenSender.stop();
            screenSender = null;
            ghiThongBao("[Dừng] Đã dừng truyền hình ảnh màn hình.");
        }
    }

    private void dongSocket() {
        dangDong = true;
        isReconnecting = false;

        dungTruyenManHinh();
        ScreenLockOverlay.unlock();

        if (hardwareScanner != null) {
            try { hardwareScanner.stop(); } catch (Exception ignored) {}
            hardwareScanner = null;
        }

        if (processMonitor != null) {
            try { processMonitor.stop(); } catch (Exception ignored) {}
            processMonitor = null;
        }

        if (systemMonitor != null) {
            try { systemMonitor.stop(); } catch (Exception ignored) {}
            systemMonitor = null;
        }

        try {
            if (out != null && socket != null && !socket.isClosed()) {
                out.println("DISCONNECT|" + maMay);
                out.println("QUIT");
                out.flush();
            }
        } catch (Exception ignored) {}

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            ghiThongBao("Lỗi đóng kết nối: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            txtTrangThai.setText("Đã ngắt kết nối");
            btnGui.setEnabled(false);
            btnKhoa.setEnabled(false);
            btnNgatKetNoi.setEnabled(false);
            txtTinNhan.setEditable(false);
        });
    }

    private void saveReceivedFile(String suggestedName) {
        if (fileRecvChunks == null || fileRecvChunks.isEmpty()) {
            ghiThongBao("[!] Không có dữ liệu file để lưu.");
            return;
        }

        int totalBytes = 0;
        for (byte[] chunk : fileRecvChunks) totalBytes += chunk.length;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Lưu file nhận từ Server");
        chooser.setSelectedFile(new File(
                System.getProperty("user.home") + File.separator + suggestedName));
        int opt = chooser.showSaveDialog(this);
        if (opt != JFileChooser.APPROVE_OPTION) {
            ghiThongBao("Đã huỷ lưu file: " + suggestedName);
            return;
        }

        File dest = chooser.getSelectedFile();

        final int fTotal = totalBytes;
        final List<byte[]> chunks = new ArrayList<>(fileRecvChunks);
        new Thread(() -> {
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                for (byte[] chunk : chunks) fos.write(chunk);
                SwingUtilities.invokeLater(() -> {
                    ghiThongBao("[OK] Đã lưu file: " + dest.getName() + " (" + formatSize(fTotal) + ")");
                    guiLenhServer("ACTION_ACK|" + maMay + "|FILE_RECEIVE|SUCCESS|Đã lưu tệp vào " + dest.getName() + " (" + formatSize(fTotal) + ")");
                    hienThiThongBaoNonBlocking(this, "Nhận tệp hoàn tất", "Đã lưu tệp " + dest.getName() + " (" + formatSize(fTotal) + ") vào máy", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    ghiThongBao("[Lỗi] Lỗi ghi file: " + ex.getMessage());
                    guiLenhServer("ACTION_ACK|" + maMay + "|FILE_RECEIVE|ERROR|Lỗi ghi tệp " + dest.getName() + ": " + ex.getMessage());
                });
            }
        }, "FileSave-" + suggestedName).start();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1_048_576)  return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1_073_741_824) return String.format("%.2f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }

    private void initComponents() {
        lblTrangThaiTitle = new javax.swing.JLabel();
        txtTrangThai = new javax.swing.JTextField();
        lblMaMayTitle = new javax.swing.JLabel();
        txtMaMay = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        lblChatTitle = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtChat = new javax.swing.JTextArea();
        lblTinNhanTitle = new javax.swing.JLabel();
        txtTinNhan = new javax.swing.JTextField();
        btnGui = new javax.swing.JButton();
        btnKhoa = new javax.swing.JButton();
        btnNgatKetNoi = new javax.swing.JButton();
        lblKhoaGiaoDien = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtThongBao = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        lblTrangThaiTitle.setText("Trạng thái kết nối:");
        lblMaMayTitle.setText("Thông tin máy: ");
        jLabel1.setText("Thông báo và nhật ký: ");
        lblChatTitle.setText("Nội dung chat: ");

        txtChat.setColumns(20);
        txtChat.setRows(5);
        jScrollPane1.setViewportView(txtChat);

        lblTinNhanTitle.setText("Nhập tin nhắn: ");

        btnGui.setText("Gửi tin nhắn");
        btnGui.addActionListener(this::btnGuiActionPerformed);

        btnKhoa.setText("Khoá");
        btnKhoa.addActionListener(this::btnKhoaActionPerformed);

        btnNgatKetNoi.setText("Ngắt kết nối");
        btnNgatKetNoi.addActionListener(this::btnNgatKetNoiActionPerformed);

        lblKhoaGiaoDien.setText("Trạng thái khóa: Chưa khóa");

        txtThongBao.setColumns(20);
        txtThongBao.setRows(5);
        jScrollPane2.setViewportView(txtThongBao);

        // Bố cục Pure Java Swing chuẩn
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBackground(UITheme.BG_DARK);
        mainPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        // Center Panel chứa Form inputs và 2 Text Areas
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);

        // Dòng 0: Trạng thái kết nối
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0; gbc.weighty = 0.0;
        centerPanel.add(lblTrangThaiTitle, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtTrangThai, gbc);

        // Dòng 1: Thông tin máy
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblMaMayTitle, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtMaMay, gbc);

        // Dòng 2: Thông báo và nhật ký
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(jLabel1, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.4;
        jScrollPane2.setPreferredSize(new Dimension(380, 100));
        centerPanel.add(jScrollPane2, gbc);

        // Dòng 3: Nội dung chat
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblChatTitle, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 0.6;
        jScrollPane1.setPreferredSize(new Dimension(380, 120));
        centerPanel.add(jScrollPane1, gbc);

        // Dòng 4: Nhập tin nhắn
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0; gbc.weighty = 0.0;
        centerPanel.add(lblTinNhanTitle, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtTinNhan, gbc);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // South: Nút bấm và Trạng thái khóa
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setOpaque(false);

        // Hàng nút 1: Gửi tin nhắn & Ngắt kết nối
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 8));
        row1.setOpaque(false);
        row1.add(btnGui);
        row1.add(btnNgatKetNoi);
        southPanel.add(row1);

        // Hàng nút 2: Khóa giao diện & Trạng thái khóa
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 6));
        row2.setOpaque(false);
        row2.add(btnKhoa);
        row2.add(lblKhoaGiaoDien);
        southPanel.add(row2);

        mainPanel.add(southPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setPreferredSize(new Dimension(640, 620));
        pack();
    }

    private void btnGuiActionPerformed(java.awt.event.ActionEvent evt) {
        String tinNhan = txtTinNhan.getText().trim();

        if (tinNhan.isEmpty()) {
            return;
        }

        if (daKhoa) {
            JOptionPane.showMessageDialog(
                    this,
                    "Giao diện đang bị khóa!",
                    "Thông báo",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Gửi theo định dạng Server: CHAT|mãMáy|nộiDung
        if (guiLenhServer("CHAT|" + maMay + "|" + tinNhan)) {
            String time = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtChat.append("[" + time + "] Bạn → Server: " + tinNhan + "\n");
            txtChat.setCaretPosition(txtChat.getDocument().getLength());
            txtTinNhan.setText("");
        }
    }

    private void btnKhoaActionPerformed(java.awt.event.ActionEvent evt) {
        if (socket == null || socket.isClosed()) {
            JOptionPane.showMessageDialog(this, "Client chưa kết nối Server!");
            return;
        }

        String lenh = daKhoa ? "UNLOCK_REQUEST" : "LOCK_REQUEST";

        if (guiLenhServer(lenh)) {
            ghiThongBao(
                    daKhoa
                    ? "Đã gửi yêu cầu mở khóa lên Server."
                    : "Đã gửi yêu cầu khóa lên Server.");
        }
    }

    private void btnNgatKetNoiActionPerformed(java.awt.event.ActionEvent evt) {
        isUserInitiatedDisconnect = true;
        isReconnecting = false;
        dangDong = true;

        if (onUserDisconnect != null) {
            try { onUserDisconnect.run(); } catch (Exception ignored) {}
        }

        if (out != null) {
            guiLenhServer("QUIT");
        }

        ghiThongBao("Đã chủ động ngắt kết nối Server.");
        dongSocket();
        dispose();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info
                    : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> {
            ClientControlForm form = new ClientControlForm();
            form.setVisible(true);
        });
    }

    // Khai báo các thành phần giao diện (giữ nguyên tên biến 100%)
    private javax.swing.JButton btnGui;
    private javax.swing.JButton btnKhoa;
    private javax.swing.JButton btnNgatKetNoi;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lblChatTitle;
    private javax.swing.JLabel lblKhoaGiaoDien;
    private javax.swing.JLabel lblMaMayTitle;
    private javax.swing.JLabel lblTinNhanTitle;
    private javax.swing.JLabel lblTrangThaiTitle;
    private javax.swing.JTextArea txtChat;
    private javax.swing.JTextField txtMaMay;
    private javax.swing.JTextArea txtThongBao;
    private javax.swing.JTextField txtTinNhan;
    private javax.swing.JTextField txtTrangThai;
}
