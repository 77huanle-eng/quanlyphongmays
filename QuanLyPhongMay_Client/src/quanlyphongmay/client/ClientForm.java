package quanlyphongmay.client;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ClientForm - Giao diện kết nối phòng máy Client (Pure Java Swing)
 * Tích hợp tính năng:
 * - Tự động ghi nhớ cấu hình mã máy, server IP (client_config.properties)
 * - Tự động đăng ký Windows Startup (HKCU Run & Startup folder)
 * - Tự động quét tìm Server và kết nối ngay lập tức (Zero-Click Auto-Connect)
 * - Tự động lặp lại kết nối khi vừa bật máy hoặc khởi động lại
 */
public class ClientForm extends javax.swing.JFrame {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ClientForm.class.getName());

    private volatile boolean userCancelledAutoConnect = false;
    private volatile boolean isConnecting = false;
    private boolean isAutoStart = false;
    private Thread autoConnectThread = null;

    public ClientForm() {
        this(false);
    }

    public ClientForm(boolean isAutoStart) {
        this.isAutoStart = isAutoStart;
        initComponents();
        setTitle("Hệ thống quản lý phòng máy - Client");
        setLocationRelativeTo(null);

        applyTheme();
        loadConfig();
        registerWindowsAutoStart();

        lblTrangThai.setText("Đang khởi động kết nối tự động...");
        lblTrangThai.setForeground(UITheme.WARNING);
        btnNgatKetNoi.setEnabled(false);

        startAutoConnectWorker(isAutoStart);
    }

    public static File getConfigFile() {
        try {
            File jarDir = new File(ClientForm.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            if (jarDir != null && jarDir.isDirectory()) {
                return new File(jarDir, "client_config.properties");
            }
        } catch (Exception ignored) {}
        return new File("client_config.properties");
    }

    private void loadConfig() {
        File cfg = getConfigFile();
        Properties props = new Properties();
        if (cfg.exists()) {
            try (InputStream inStream = new FileInputStream(cfg)) {
                props.load(new InputStreamReader(inStream, StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.warning("Không thể đọc client_config.properties: " + e.getMessage());
            }
        }

        String compName = ComputerInfo.getComputerName();
        if (compName == null || compName.trim().isEmpty() || "Unknown".equalsIgnoreCase(compName)) {
            compName = System.getenv("COMPUTERNAME");
        }
        if (compName == null || compName.trim().isEmpty()) {
            compName = "PC01";
        }

        String secureNodeId = ComputerInfo.generateSecureNodeId();
        String savedIp = props.getProperty("server_ip", "127.0.0.1").trim();
        String savedPort = props.getProperty("server_port", "5000").trim();
        String savedTen = props.getProperty("ten_may", compName).trim();

        txtServerIP.setText(savedIp.isEmpty() ? "127.0.0.1" : savedIp);
        txtPort.setText(savedPort.isEmpty() ? "5000" : savedPort);
        txtMaMay.setText(secureNodeId);
        txtMaMay.setEditable(false);
        txtMaMay.setFocusable(false);
        txtMaMay.setToolTipText("Mã bảo mật định danh độc nhất (Cryptographic Node ID)");
        txtTenMay.setText(savedTen.isEmpty() ? compName : savedTen);
    }

    public static void saveConfig(String serverIp, int port, String maMay, String tenMay) {
        File cfg = getConfigFile();
        Properties props = new Properties();
        props.setProperty("server_ip", serverIp);
        props.setProperty("server_port", String.valueOf(port));
        props.setProperty("ma_may", ComputerInfo.generateSecureNodeId());
        props.setProperty("ten_may", tenMay);
        props.setProperty("last_connected", java.time.LocalDateTime.now().toString());
        try (OutputStream outStream = new FileOutputStream(cfg)) {
            props.store(new OutputStreamWriter(outStream, StandardCharsets.UTF_8), "LANPC Client Configuration");
        } catch (Exception e) {
            logger.warning("Không thể lưu client_config.properties: " + e.getMessage());
        }
    }

    public static void registerWindowsAutoStart() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("win")) return;

            File jarFile = null;
            try {
                jarFile = new File(ClientForm.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            } catch (Exception ignored) {}

            String jarPath = (jarFile != null) ? jarFile.getAbsolutePath() : "";
            if (!jarPath.toLowerCase().endsWith(".jar")) {
                File[] candidates = new File[] {
                    new File("QuanLyPhongMay_Client/dist/QuanLyPhongMay_Client.jar"),
                    new File("dist/QuanLyPhongMay_Client.jar"),
                    new File("../QuanLyPhongMay_Client/dist/QuanLyPhongMay_Client.jar")
                };
                for (File f : candidates) {
                    if (f.exists()) {
                        jarPath = f.getAbsolutePath();
                        break;
                    }
                }
            }

            if (jarPath.toLowerCase().endsWith(".jar")) {
                String javaw = System.getProperty("java.home") + "\\bin\\javaw.exe";
                if (!new File(javaw).exists()) {
                    javaw = "javaw.exe";
                }
                String cmd = "\"" + javaw + "\" -jar \"" + jarPath + "\" --autostart";

                // 1. Đăng ký Registry HKCU Run
                ProcessBuilder pb = new ProcessBuilder(
                    "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", "LANPC_Client", "/t", "REG_SZ", "/d", cmd, "/f"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                // 2. Dự phòng: Tạo file bat trong thư mục Startup
                String appData = System.getenv("APPDATA");
                if (appData != null) {
                    File startupDir = new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup");
                    if (startupDir.exists() && startupDir.isDirectory()) {
                        File batFile = new File(startupDir, "LANPC_Client.bat");
                        String script = "@echo off\r\nstart \"\" " + cmd + "\r\n";
                        java.nio.file.Files.writeString(batFile.toPath(), script, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[AutoStart] Lỗi đăng ký Windows Startup: " + e.getMessage());
        }
    }

    public void onUserCancelledAutoConnect() {
        this.userCancelledAutoConnect = true;
        this.lblTrangThai.setText("Đã ngắt kết nối (Tạm dừng tự động)");
        this.lblTrangThai.setForeground(UITheme.WARNING);
        this.btnKetNoi.setEnabled(true);
        this.btnNgatKetNoi.setEnabled(false);
    }

    private void startAutoConnectWorker(boolean isAutoStart) {
        autoConnectThread = new Thread(() -> {
            try {
                // Đợi 1 giây để card mạng và hệ điều hành ổn định sau boot
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                return;
            }

            while (!userCancelledAutoConnect && (socket == null || socket.isClosed())) {
                if (isConnecting) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                    continue;
                }

                // 1. Thử quét tìm Server trong mạng LAN qua UDP Broadcast
                try {
                    ClientAutoDiscover.DiscoveredServer ds = ClientAutoDiscover.discover(1500);
                    if (ds != null && ds.ip != null) {
                        SwingUtilities.invokeLater(() -> {
                            txtServerIP.setText(ds.ip);
                            txtPort.setText(String.valueOf(ds.port));
                            lblTrangThai.setText("Tìm thấy: " + ds.name + " (" + ds.ip + ")");
                            lblTrangThai.setForeground(UITheme.SUCCESS);
                        });
                    }
                } catch (Exception ignored) {}

                // 2. Thử kết nối TCP
                final boolean[] connected = new boolean[1];
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        connected[0] = thucHienKetNoi(true);
                    });
                } catch (Exception e) {
                    break;
                }

                if (connected[0]) {
                    // Kết nối thành công, thoát worker
                    break;
                }

                // 3. Nếu chưa thành công, chờ 3 giây trước khi thử lại
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Client-AutoConnect-Worker");
        autoConnectThread.setDaemon(true);
        autoConnectThread.start();
    }

    private void applyTheme() {
        getContentPane().setBackground(UITheme.BG_DARK);

        // Labels
        jLabel1.setFont(UITheme.FONT_TITLE);
        jLabel1.setForeground(UITheme.ACCENT);
        UITheme.styleLabelBody(jLabel2);
        UITheme.styleLabelBody(jLabel3);
        UITheme.styleLabelBody(jLabel4);
        UITheme.styleLabelBody(jLabel5);

        // Trạng thái
        lblTrangThai.setFont(UITheme.FONT_HEADER);
        lblTrangThai.setForeground(UITheme.WARNING);

        // Text fields
        UITheme.styleTextField(txtServerIP);
        UITheme.styleTextField(txtPort);
        UITheme.styleTextField(txtMaMay);
        UITheme.styleTextField(txtTenMay);

        // Buttons
        UITheme.styleButtonAccent(btnKetNoi);
        UITheme.styleButtonDanger(btnNgatKetNoi);
        UITheme.styleButtonSecondary(btnAutoDiscover);
    }

    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        txtServerIP = new javax.swing.JTextField();
        btnAutoDiscover = new javax.swing.JButton("[Tìm] Tìm Server");
        jLabel3 = new javax.swing.JLabel();
        txtPort = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        txtMaMay = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        txtTenMay = new javax.swing.JTextField();
        btnKetNoi = new javax.swing.JButton();
        btnNgatKetNoi = new javax.swing.JButton();
        lblTrangThai = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setFont(new java.awt.Font("Segoe UI", Font.BOLD, 22));
        jLabel1.setText("KẾT NỐI PHÒNG MÁY");
        jLabel1.setHorizontalAlignment(SwingConstants.CENTER);

        jLabel2.setText("Địa chỉ IP Server: ");
        txtServerIP.setText("127.0.0.1");

        btnAutoDiscover.setText("[Tìm] Tìm Server");
        btnAutoDiscover.setToolTipText("Quét tự động tìm IP của Server trong mạng LAN");
        btnAutoDiscover.addActionListener(e -> thucHienTimServer());

        jLabel3.setText("Port: ");
        txtPort.setText("5000");

        jLabel4.setText("Mã máy: ");
        txtMaMay.setText(ComputerInfo.generateSecureNodeId());
        txtMaMay.setEditable(false);
        txtMaMay.setFocusable(false);
        txtMaMay.setToolTipText("Mã bảo mật định danh độc nhất (Cryptographic Node ID)");

        jLabel5.setText("Tên máy: ");
        txtTenMay.setText("Máy tính 01");

        btnKetNoi.setText("Kết nối");
        btnKetNoi.addActionListener(this::btnKetNoiActionPerformed);

        btnNgatKetNoi.setText("Ngắt kết nối");
        btnNgatKetNoi.addActionListener(this::btnNgatKetNoiActionPerformed);

        lblTrangThai.setFont(new java.awt.Font("Segoe UI", Font.PLAIN, 16));
        lblTrangThai.setText("Chưa kết nối");
        lblTrangThai.setHorizontalAlignment(SwingConstants.CENTER);

        // Bố cục Pure Java Swing chuẩn
        JPanel mainPanel = new JPanel(new BorderLayout(0, 18));
        mainPanel.setBackground(UITheme.BG_DARK);
        mainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));

        // North: Title
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setOpaque(false);
        titlePanel.add(jLabel1);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // Center: Form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        // Row 0: IP + Nút Tìm Server
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        formPanel.add(jLabel2, gbc);

        JPanel ipPanel = new JPanel(new BorderLayout(8, 0));
        ipPanel.setOpaque(false);
        ipPanel.add(txtServerIP, BorderLayout.CENTER);
        ipPanel.add(btnAutoDiscover, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(ipPanel, gbc);

        // Row 1: Port
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        formPanel.add(jLabel3, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtPort, gbc);

        // Row 2: Ma may
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        formPanel.add(jLabel4, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtMaMay, gbc);

        // Row 3: Ten may
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        formPanel.add(jLabel5, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtTenMay, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // South: Buttons and Status
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setOpaque(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 8));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnKetNoi);
        buttonPanel.add(btnNgatKetNoi);
        southPanel.add(buttonPanel);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        statusPanel.setOpaque(false);
        statusPanel.add(lblTrangThai);
        southPanel.add(statusPanel);

        mainPanel.add(southPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setPreferredSize(new Dimension(540, 480));
        pack();
    }

    private void thucHienTimServer() {
        btnAutoDiscover.setEnabled(false);
        lblTrangThai.setText("Đang quét tìm Server trong mạng LAN...");
        lblTrangThai.setForeground(UITheme.WARNING);

        ClientAutoDiscover.discoverAsync(2500, server -> {
            btnAutoDiscover.setEnabled(true);
            if (server != null) {
                txtServerIP.setText(server.ip);
                txtPort.setText(String.valueOf(server.port));
                lblTrangThai.setText("Tìm thấy: " + server.name + " (" + server.ip + ")");
                lblTrangThai.setForeground(UITheme.SUCCESS);
                JOptionPane.showMessageDialog(
                        this,
                        "Đã tự động tìm thấy Server phòng máy!\n"
                        + "Tên Server: " + server.name + "\n"
                        + "Địa chỉ IP: " + server.ip + "\n"
                        + "Cổng kết nối: " + server.port,
                        "Nhận diện Server thành công",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                lblTrangThai.setText("Không tìm thấy Server tự động");
                lblTrangThai.setForeground(UITheme.DANGER);
                JOptionPane.showMessageDialog(
                        this,
                        "Không tìm thấy Server nào đang phát tín hiệu trong mạng LAN.\n"
                        + "Vui lòng nhập IP thủ công hoặc đảm bảo Server đã được bật.",
                        "Không tìm thấy Server",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        });
    }

    private void btnKetNoiActionPerformed(java.awt.event.ActionEvent evt) {
        thucHienKetNoi(false);
    }

    public synchronized boolean thucHienKetNoi(boolean isAuto) {
        if (isConnecting) return false;
        isConnecting = true;
        try {
            String ip = txtServerIP.getText().trim();
            String maMay = txtMaMay.getText().trim();
            String tenMay = txtTenMay.getText().trim();

            int port;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException e) {
                if (!isAuto) {
                    JOptionPane.showMessageDialog(this, "Port phải là số nguyên.", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }

            if (ip.isEmpty() || maMay.isEmpty() || tenMay.isEmpty()) {
                if (!isAuto) {
                    JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ IP Server, Port, Mã máy và Tên máy.", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                }
                return false;
            }

            if (port < 1 || port > 65535) {
                if (!isAuto) {
                    JOptionPane.showMessageDialog(this, "Port phải nằm trong khoảng 1 đến 65535.", "Port không hợp lệ", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }

            try {
                // Kết nối đến Server
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 3000);
                socket.setSoTimeout(5000);

                // Khởi tạo luồng nhận và gửi dữ liệu chuẩn UTF-8
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // 1. Gửi yêu cầu đăng ký máy kèm chữ ký HMAC bảo mật
                String macAddress = ComputerInfo.getMAC();
                String authToken = quanlyphongmay.common.SecurityUtil.hmacSha256(
                        maMay + "|" + tenMay + "|" + macAddress,
                        quanlyphongmay.common.SecurityUtil.SHARED_HMAC_SECRET
                );
                out.println("REGISTER|" + maMay + "|" + tenMay + "|" + macAddress + "|" + authToken);
                out.flush();

                // 2. Nhận phản hồi đăng ký từ Server
                String phanHoi = in.readLine();

                if (phanHoi != null && phanHoi.startsWith("REJECTED|AUTH_FAILED")) {
                    try { socket.close(); } catch (IOException ignored) {}
                    socket = null; in = null; out = null;
                    lblTrangThai.setText("Bị từ chối: Xác thực bảo mật thất bại");
                    if (!isAuto) {
                        JOptionPane.showMessageDialog(
                            this,
                            "[Bảo mật] Xác thực định danh với Máy chủ thất bại!\nKhóa bảo mật hoặc gói tin không hợp lệ.",
                            "Lỗi xác thực Server",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                    btnKetNoi.setEnabled(true);
                    btnNgatKetNoi.setEnabled(false);
                    return false;
                }

                if (phanHoi != null && phanHoi.startsWith("REJECTED|ALREADY_LOGGED_IN")) {
                    try { socket.close(); } catch (IOException ignored) {}
                    socket = null; in = null; out = null;
                    lblTrangThai.setText("Bị từ chối: Trùng lặp máy");
                    if (!isAuto) {
                        JOptionPane.showMessageDialog(
                            this,
                            "[Chặn] Mã máy \"" + maMay + "\" đang được đăng nhập ở nơi khác!\nVui lòng đăng xuất máy cũ trước khi đăng nhập lại.",
                            "Trùng lặp đăng nhập",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                    btnKetNoi.setEnabled(true);
                    btnNgatKetNoi.setEnabled(false);
                    return false;
                }

                if (phanHoi != null && phanHoi.startsWith("REJECTED|DUPLICATE_IP")) {
                    try { socket.close(); } catch (IOException ignored) {}
                    socket = null; in = null; out = null;
                    lblTrangThai.setText("Bị từ chối: Trùng IP");
                    if (!isAuto) {
                        JOptionPane.showMessageDialog(
                            this,
                            "[Chặn] Địa chỉ IP của máy đang bị trùng với một máy trạm khác đang kết nối vào Server!\nVui lòng kiểm tra lại cấu hình mạng.",
                            "Trùng lặp IP",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                    btnKetNoi.setEnabled(true);
                    btnNgatKetNoi.setEnabled(false);
                    return false;
                }

                if (phanHoi == null || !phanHoi.startsWith("OK|")) {
                    throw new IOException("Server từ chối đăng ký: " + phanHoi);
                }

                // 3. Lấy thông tin máy tính (kèm MAC address)
                String thongTinMay = ComputerInfo.getAllInfo();

                // 4. Gửi thông tin máy lên Server
                out.println("INFO|" + maMay + "|" + thongTinMay);
                out.flush();

                // 4b. Đọc phản hồi OK|INFO từ Server để tránh buffering lẫn sang ClientControlForm
                String infoReply = in.readLine();
                if (infoReply == null) {
                    throw new java.io.IOException("Server ngắt kết nối sau khi nhận INFO.");
                }

                socket.setSoTimeout(0);

                // 5. Cập nhật trạng thái giao diện
                lblTrangThai.setText("Đã kết nối Server");
                btnKetNoi.setEnabled(false);
                btnNgatKetNoi.setEnabled(true);

                if (!isAuto) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Kết nối Server thành công!\n"
                        + "Mã máy: " + maMay + "\n"
                        + "Tên máy: " + tenMay + "\n"
                        + "Server: " + ip + ":" + port + "\n\n"
                        + "Đã gửi thông tin phần cứng và MAC lên Server.",
                        "Thành công",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                }

                // 6. Mở giao diện điều khiển Client
                ClientControlForm form = new ClientControlForm(socket, in, out, maMay, tenMay);

                form.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        ClientForm.this.setVisible(true);
                        lblTrangThai.setText("Chưa kết nối");
                        btnKetNoi.setEnabled(true);
                        btnNgatKetNoi.setEnabled(false);
                    }
                });

                form.setLocationRelativeTo(null);
                form.setVisible(true);
                setVisible(false);
                return true;

            } catch (IOException e) {
                lblTrangThai.setText("Kết nối thất bại");
                if (!isAuto) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Không thể kết nối Server.\nIP: " + ip + "\nPort: " + port + "\nLỗi: " + e.getMessage(),
                        "Lỗi kết nối",
                        JOptionPane.ERROR_MESSAGE
                    );
                }

                try {
                    if (socket != null) socket.close();
                } catch (IOException ignored) {}

                socket = null;
                in = null;
                out = null;
                btnKetNoi.setEnabled(true);
                btnNgatKetNoi.setEnabled(false);
                return false;
            }
        } finally {
            isConnecting = false;
        }
    }

    private void btnNgatKetNoiActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            if (out != null && socket != null && !socket.isClosed()) {
                out.println("DISCONNECT|" + txtMaMay.getText().trim());
                out.flush();
                out.println("QUIT");
                out.flush();
                socket.close();
            }

            socket = null;
            in = null;
            out = null;

            lblTrangThai.setText("Đã ngắt kết nối");
            btnKetNoi.setEnabled(true);
            btnNgatKetNoi.setEnabled(false);

            setVisible(true);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this, "Lỗi ngắt kết nối: " + e.getMessage()
            );
        }
    }

    public static void main(String args[]) {
        try {
            quanlyphongmay.common.UITheme.setupGlobalTheme();
        } catch (Exception ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> {
            new ClientForm().setVisible(true);
        });
    }

    private javax.swing.JButton btnAutoDiscover;
    private javax.swing.JButton btnKetNoi;
    private javax.swing.JButton btnNgatKetNoi;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel lblTrangThai;
    private javax.swing.JTextField txtMaMay;
    private javax.swing.JTextField txtPort;
    private javax.swing.JTextField txtServerIP;
    private javax.swing.JTextField txtTenMay;
}
