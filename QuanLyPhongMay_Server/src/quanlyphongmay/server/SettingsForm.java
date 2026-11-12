package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * SettingsForm - Giao diện Cài đặt Hệ thống Quản Lý Phòng Máy
 */
public class SettingsForm extends JFrame {

    private final ServerForm serverForm;
    private final Preferences prefs = Preferences.userNodeForPackage(SettingsForm.class);

    // Tab 1: Server & Network
    private JTextField txtPort;
    private JSpinner spnMaxClients;
    private JComboBox<String> cboTimeout;
    private JTextField txtBroadcast;
    private JCheckBox chkAutoStart;
    private JCheckBox chkAllowMultiSubnet;

    // Tab 2: Giám sát & Đo khoảng cách
    private JComboBox<String> cboRefreshInterval;
    private JComboBox<String> cboDistanceInterval;
    private JCheckBox chkSoundConnect;
    private JCheckBox chkSoundDisconnect;
    private JCheckBox chkHeartbeat;

    // Tab 3: Nhật ký & Lưu trữ
    private JCheckBox chkAutoSaveLog;
    private JTextField txtLogPath;
    private JButton btnBrowseLog;
    private JComboBox<String> cboMaxLogLines;
    private JCheckBox chkAutoCleanLog;

    // Tab 4: Thông tin phòng máy & Giao diện
    private JTextField txtLabName;
    private JTextField txtAdminName;
    private JTextField txtLabLocation;
    private JComboBox<String> cboTheme;
    private JCheckBox chkPopupNotif;

    // Nút hành động
    private JButton btnSave;
    private JButton btnReset;
    private JButton btnClose;

    public SettingsForm() {
        this(null);
    }

    public SettingsForm(ServerForm serverForm) {
        this.serverForm = serverForm;
        initUI();
        loadSettings();
    }

    private void initUI() {
        setTitle("Cài Đặt Hệ Thống - Quản Lý Phòng Máy");
        setSize(780, 580);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(serverForm != null ? serverForm : null);
        setResizable(false);

        getContentPane().setBackground(UITheme.BG_DARK);
        setLayout(new BorderLayout(0, 0));

        // 1. Header banner
        add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Tabbed pane nội dung cài đặt
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_HEADER);
        tabbedPane.setBackground(UITheme.BG_SIDEBAR);
        tabbedPane.setForeground(UITheme.TEXT_PRIMARY);

        tabbedPane.addTab("[LAN] Cấu hình Server", createServerTab());
        tabbedPane.addTab("[PERF] Giám sát & Đo độ trễ", createMonitorTab());
        tabbedPane.addTab("[Exam] Nhật ký & Lưu trữ", createLogTab());
        tabbedPane.addTab("Thông tin phòng máy", createLabInfoTab());

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBackground(UITheme.BG_DARK);
        centerWrapper.setBorder(new EmptyBorder(12, 16, 12, 16));
        centerWrapper.add(tabbedPane, BorderLayout.CENTER);
        add(centerWrapper, BorderLayout.CENTER);

        // 3. Bottom button panel
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    /** Tạo Header banner trên cùng */
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.ACCENT);
        header.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        JLabel lblIcon = new JLabel("[Cài đặt]");
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        lblIcon.setForeground(Color.WHITE);
        left.add(lblIcon);

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        textPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("CÀI ĐẶT HỆ THỐNG MÁY CHỦ");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);
        textPanel.add(lblTitle);

        JLabel lblSub = new JLabel("Tùy chỉnh thông số cổng, giám sát mạng LAN, lưu trữ nhật ký và phòng máy");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(224, 242, 254));
        textPanel.add(lblSub);

        left.add(textPanel);
        header.add(left, BorderLayout.WEST);

        return header;
    }

    /** Tab 1: Cấu hình Server & Mạng */
    private JPanel createServerTab() {
        JPanel tab = new JPanel();
        tab.setBackground(UITheme.BG_DARK);
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        tab.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel cardNetwork = UITheme.makeCardWithTitle("Thông Số Kết Nối Mạng", new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        // Cổng Server
        gbc.gridx = 0; gbc.gridy = 0;
        cardNetwork.add(createLabel("Cổng lắng nghe (Port):"), gbc);
        txtPort = new JTextField("5000", 12);
        UITheme.styleTextField(txtPort);
        gbc.gridx = 1;
        cardNetwork.add(txtPort, gbc);

        // Số kết nối tối đa
        gbc.gridx = 0; gbc.gridy = 1;
        cardNetwork.add(createLabel("Số lượng máy kết nối tối đa:"), gbc);
        spnMaxClients = new JSpinner(new SpinnerNumberModel(50, 1, 300, 5));
        spnMaxClients.setFont(UITheme.FONT_BODY);
        spnMaxClients.setPreferredSize(new Dimension(100, 30));
        gbc.gridx = 1;
        cardNetwork.add(spnMaxClients, gbc);

        // Timeout
        gbc.gridx = 0; gbc.gridy = 2;
        cardNetwork.add(createLabel("Thời gian chờ kết nối (Timeout):"), gbc);
        cboTimeout = new JComboBox<>(new String[]{"15 giây", "30 giây (Mặc định)", "60 giây", "Không giới hạn"});
        UITheme.styleComboBox(cboTimeout);
        gbc.gridx = 1;
        cardNetwork.add(cboTimeout, gbc);

        // Broadcast IP
        gbc.gridx = 0; gbc.gridy = 3;
        cardNetwork.add(createLabel("Địa chỉ Broadcast mạng LAN:"), gbc);
        txtBroadcast = new JTextField("255.255.255.255", 14);
        UITheme.styleTextField(txtBroadcast);
        gbc.gridx = 1;
        cardNetwork.add(txtBroadcast, gbc);

        tab.add(cardNetwork);
        tab.add(Box.createVerticalStrut(12));

        // Card tùy chọn khởi động
        JPanel cardOptions = UITheme.makeCardWithTitle("Tùy Chọn Hoạt Động", new GridLayout(2, 1, 8, 8));
        chkAutoStart = createCheckBox("Tự động kích hoạt Server ngay khi mở ứng dụng", false);
        chkAllowMultiSubnet = createCheckBox("Cho phép kết nối từ các máy thuộc Subnet / VLAN khác", true);
        cardOptions.add(chkAutoStart);
        cardOptions.add(chkAllowMultiSubnet);

        tab.add(cardOptions);
        tab.add(Box.createVerticalGlue());
        return tab;
    }

    /** Tab 2: Giám sát & Đo độ trễ */
    private JPanel createMonitorTab() {
        JPanel tab = new JPanel();
        tab.setBackground(UITheme.BG_DARK);
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        tab.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel cardInterval = UITheme.makeCardWithTitle("Tần Suất Giám Sát", new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        cardInterval.add(createLabel("Chu kỳ quét trạng thái Client:"), gbc);
        cboRefreshInterval = new JComboBox<>(new String[]{"1 giây", "2 giây (Khuyến nghị)", "5 giây", "10 giây"});
        UITheme.styleComboBox(cboRefreshInterval);
        gbc.gridx = 1;
        cardInterval.add(cboRefreshInterval, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        cardInterval.add(createLabel("Chu kỳ đo khoảng cách & RTT:"), gbc);
        cboDistanceInterval = new JComboBox<>(new String[]{"2 giây (Realtime)", "5 giây", "10 giây", "Chỉ khi bấm đo"});
        UITheme.styleComboBox(cboDistanceInterval);
        gbc.gridx = 1;
        cardInterval.add(cboDistanceInterval, gbc);

        tab.add(cardInterval);
        tab.add(Box.createVerticalStrut(12));

        JPanel cardAlerts = UITheme.makeCardWithTitle("Cảnh Báo & Âm Thanh", new GridLayout(3, 1, 8, 8));
        chkSoundConnect = createCheckBox("Phát âm thanh thông báo khi có Client mới kết nối", true);
        chkSoundDisconnect = createCheckBox("Phát âm thanh cảnh báo khi Client đột ngột ngắt kết nối", true);
        chkHeartbeat = createCheckBox("Gửi tín hiệu Ping Heartbeat định kỳ kiểm tra kết nối sống", true);
        cardAlerts.add(chkSoundConnect);
        cardAlerts.add(chkSoundDisconnect);
        cardAlerts.add(chkHeartbeat);

        tab.add(cardAlerts);
        tab.add(Box.createVerticalGlue());
        return tab;
    }

    /** Tab 3: Nhật ký & Lưu trữ */
    private JPanel createLogTab() {
        JPanel tab = new JPanel();
        tab.setBackground(UITheme.BG_DARK);
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        tab.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel cardLog = UITheme.makeCardWithTitle("Cấu Hình Lưu Trữ Nhật Ký", new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        chkAutoSaveLog = createCheckBox("Tự động ghi toàn bộ nhật ký Server ra file văn bản (.log)", true);
        cardLog.add(chkAutoSaveLog, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        cardLog.add(createLabel("Thư mục lưu nhật ký:"), gbc);

        JPanel pnlPath = new JPanel(new BorderLayout(6, 0));
        pnlPath.setOpaque(false);
        txtLogPath = new JTextField(System.getProperty("user.dir") + File.separator + "logs", 20);
        UITheme.styleTextField(txtLogPath);
        pnlPath.add(txtLogPath, BorderLayout.CENTER);

        btnBrowseLog = new JButton("Chọn...");
        UITheme.styleButtonGhost(btnBrowseLog);
        btnBrowseLog.addActionListener(e -> chooseLogFolder());
        pnlPath.add(btnBrowseLog, BorderLayout.EAST);

        gbc.gridx = 1;
        cardLog.add(pnlPath, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        cardLog.add(createLabel("Số dòng hiển thị tối đa:"), gbc);
        cboMaxLogLines = new JComboBox<>(new String[]{"500 dòng", "1000 dòng (Khuyến nghị)", "2000 dòng", "5000 dòng"});
        UITheme.styleComboBox(cboMaxLogLines);
        gbc.gridx = 1;
        cardLog.add(cboMaxLogLines, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        chkAutoCleanLog = createCheckBox("Tự động dọn dẹp các tệp nhật ký cũ hơn 30 ngày", false);
        cardLog.add(chkAutoCleanLog, gbc);

        tab.add(cardLog);
        tab.add(Box.createVerticalGlue());
        return tab;
    }

    /** Tab 4: Thông tin phòng máy & Giao diện */
    private JPanel createLabInfoTab() {
        JPanel tab = new JPanel();
        tab.setBackground(UITheme.BG_DARK);
        tab.setLayout(new BoxLayout(tab, BoxLayout.Y_AXIS));
        tab.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel cardLab = UITheme.makeCardWithTitle("Thông Tin Phòng Máy", new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        cardLab.add(createLabel("Tên phòng máy:"), gbc);
        txtLabName = new JTextField("Phòng Thực Hành Máy Tính 01", 18);
        UITheme.styleTextField(txtLabName);
        gbc.gridx = 1;
        cardLab.add(txtLabName, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        cardLab.add(createLabel("Quản trị viên phụ trách:"), gbc);
        txtAdminName = new JTextField("Quản Trị Viên / Giảng Viên", 18);
        UITheme.styleTextField(txtAdminName);
        gbc.gridx = 1;
        cardLab.add(txtAdminName, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        cardLab.add(createLabel("Vị trí / Địa điểm:"), gbc);
        txtLabLocation = new JTextField("Tầng 3 - Tòa nhà CNTT - Phòng 302", 18);
        UITheme.styleTextField(txtLabLocation);
        gbc.gridx = 1;
        cardLab.add(txtLabLocation, gbc);

        tab.add(cardLab);
        tab.add(Box.createVerticalStrut(12));

        JPanel cardTheme = UITheme.makeCardWithTitle("Tùy Chọn Hiển Thị", new GridBagLayout());
        gbc = createGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        cardTheme.add(createLabel("Chủ đề giao diện (Theme):"), gbc);
        cboTheme = new JComboBox<>(new String[]{"Tối Hiện Đại (Dark Modern)", "Sáng Thanh Lịch (Light Mode)", "Xanh Công Nghệ (Cyan Cyber)"});
        UITheme.styleComboBox(cboTheme);
        gbc.gridx = 1;
        cardTheme.add(cboTheme, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        chkPopupNotif = createCheckBox("Hiển thị cửa sổ Popup thông báo khi máy trạm gửi tin nhắn / cảnh báo", true);
        cardTheme.add(chkPopupNotif, gbc);

        tab.add(cardTheme);
        tab.add(Box.createVerticalGlue());
        return tab;
    }

    /** Tạo thanh nút bấm dưới cùng */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UITheme.BG_SIDEBAR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER),
            new EmptyBorder(12, 16, 12, 16)
        ));

        // Trạng thái server bên trái
        String statusText = (serverForm != null && serverForm.isServerRunning())
                ? "● Server đang hoạt động" : "[OFF] Server đang dừng";
        Color statusColor = (serverForm != null && serverForm.isServerRunning())
                ? UITheme.SUCCESS : UITheme.TEXT_MUTED;
        JLabel lblStatus = new JLabel(statusText);
        lblStatus.setFont(UITheme.FONT_BODY);
        lblStatus.setForeground(statusColor);
        panel.add(lblStatus, BorderLayout.WEST);

        // Các nút bấm bên phải
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        btnReset = new JButton("Khôi phục mặc định");
        UITheme.styleButtonGhost(btnReset);
        btnReset.addActionListener(e -> resetDefaults());
        right.add(btnReset);

        btnClose = new JButton("Đóng");
        UITheme.styleButtonGhost(btnClose);
        btnClose.addActionListener(e -> dispose());
        right.add(btnClose);

        btnSave = new JButton("[Disk] Lưu cài đặt");
        UITheme.styleButtonAccent(btnSave);
        btnSave.addActionListener(e -> saveSettings());
        right.add(btnSave);

        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    /** Chọn thư mục lưu log */
    private void chooseLogFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn thư mục lưu nhật ký hoạt động");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtLogPath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /** Nạp cấu hình từ Preferences / ServerForm */
    private void loadSettings() {
        String defaultPort = "5000";
        if (serverForm != null) {
            defaultPort = serverForm.getServerPort();
        }

        txtPort.setText(prefs.get("server_port", defaultPort));
        spnMaxClients.setValue(prefs.getInt("max_clients", 50));
        cboTimeout.setSelectedIndex(prefs.getInt("timeout_idx", 1));
        txtBroadcast.setText(prefs.get("broadcast_ip", "255.255.255.255"));
        chkAutoStart.setSelected(prefs.getBoolean("auto_start", false));
        chkAllowMultiSubnet.setSelected(prefs.getBoolean("allow_multi_subnet", true));

        cboRefreshInterval.setSelectedIndex(prefs.getInt("refresh_interval_idx", 1));
        cboDistanceInterval.setSelectedIndex(prefs.getInt("distance_interval_idx", 0));
        chkSoundConnect.setSelected(prefs.getBoolean("sound_connect", true));
        chkSoundDisconnect.setSelected(prefs.getBoolean("sound_disconnect", true));
        chkHeartbeat.setSelected(prefs.getBoolean("heartbeat", true));

        chkAutoSaveLog.setSelected(prefs.getBoolean("auto_save_log", true));
        txtLogPath.setText(prefs.get("log_path", System.getProperty("user.dir") + File.separator + "logs"));
        cboMaxLogLines.setSelectedIndex(prefs.getInt("max_log_lines_idx", 1));
        chkAutoCleanLog.setSelected(prefs.getBoolean("auto_clean_log", false));

        txtLabName.setText(prefs.get("lab_name", "Phòng Thực Hành Máy Tính 01"));
        txtAdminName.setText(prefs.get("admin_name", "Quản Trị Viên"));
        txtLabLocation.setText(prefs.get("lab_location", "Tầng 3 - Tòa nhà CNTT - Phòng 302"));
        cboTheme.setSelectedIndex(prefs.getInt("theme_idx", 0));
        chkPopupNotif.setSelected(prefs.getBoolean("popup_notif", true));
    }

    /** Lưu cấu hình */
    private void saveSettings() {
        String portStr = txtPort.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                JOptionPane.showMessageDialog(this,
                    "Cổng Server phải nằm trong khoảng từ 1024 đến 65535!",
                    "Cổng không hợp lệ",
                    JOptionPane.WARNING_MESSAGE);
                txtPort.requestFocus();
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "Vui lòng nhập số cổng hợp lệ (Ví dụ: 5000)!",
                "Lỗi định dạng",
                JOptionPane.ERROR_MESSAGE);
            txtPort.requestFocus();
            return;
        }

        // Lưu vào Preferences
        prefs.put("server_port", portStr);
        prefs.putInt("max_clients", (Integer) spnMaxClients.getValue());
        prefs.putInt("timeout_idx", cboTimeout.getSelectedIndex());
        prefs.put("broadcast_ip", txtBroadcast.getText().trim());
        prefs.putBoolean("auto_start", chkAutoStart.isSelected());
        prefs.putBoolean("allow_multi_subnet", chkAllowMultiSubnet.isSelected());

        prefs.putInt("refresh_interval_idx", cboRefreshInterval.getSelectedIndex());
        prefs.putInt("distance_interval_idx", cboDistanceInterval.getSelectedIndex());
        prefs.putBoolean("sound_connect", chkSoundConnect.isSelected());
        prefs.putBoolean("sound_disconnect", chkSoundDisconnect.isSelected());
        prefs.putBoolean("heartbeat", chkHeartbeat.isSelected());

        prefs.putBoolean("auto_save_log", chkAutoSaveLog.isSelected());
        prefs.put("log_path", txtLogPath.getText().trim());
        prefs.putInt("max_log_lines_idx", cboMaxLogLines.getSelectedIndex());
        prefs.putBoolean("auto_clean_log", chkAutoCleanLog.isSelected());

        String labName = txtLabName.getText().trim();
        prefs.put("lab_name", labName);
        prefs.put("admin_name", txtAdminName.getText().trim());
        prefs.put("lab_location", txtLabLocation.getText().trim());
        prefs.putInt("theme_idx", cboTheme.getSelectedIndex());
        prefs.putBoolean("popup_notif", chkPopupNotif.isSelected());

        // Đồng bộ sang ServerForm nếu có
        if (serverForm != null) {
            boolean isRunning = serverForm.isServerRunning();
            if (!isRunning) {
                serverForm.setServerPort(portStr);
            }
            serverForm.addLog("Đã lưu cài đặt hệ thống (Cổng: " + portStr + ", Phòng: " + labName + ")");
            ActivityLogForm.themNhatKy("SERVER", "Cài đặt", "Cập nhật cấu hình phòng máy: " + labName + ", Port: " + portStr);

            if (isRunning && !portStr.equals(serverForm.getServerPort())) {
                JOptionPane.showMessageDialog(this,
                    "Đã lưu cài đặt!\nLưu ý: Server đang chạy trên cổng cũ (" + serverForm.getServerPort()
                    + "). Cổng mới (" + portStr + ") sẽ áp dụng sau khi khởi động lại Server.",
                    "Lưu thành công",
                    JOptionPane.INFORMATION_MESSAGE);
                dispose();
                return;
            }
        }

        JOptionPane.showMessageDialog(this,
            "Đã lưu các thiết lập hệ thống thành công!",
            "Lưu cài đặt",
            JOptionPane.INFORMATION_MESSAGE);

        dispose();
    }

    /** Khôi phục cấu hình mặc định */
    private void resetDefaults() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Bạn có chắc chắn muốn khôi phục toàn bộ cài đặt về giá trị mặc định?",
            "Xác nhận khôi phục",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        txtPort.setText("5000");
        spnMaxClients.setValue(50);
        cboTimeout.setSelectedIndex(1);
        txtBroadcast.setText("255.255.255.255");
        chkAutoStart.setSelected(false);
        chkAllowMultiSubnet.setSelected(true);

        cboRefreshInterval.setSelectedIndex(1);
        cboDistanceInterval.setSelectedIndex(0);
        chkSoundConnect.setSelected(true);
        chkSoundDisconnect.setSelected(true);
        chkHeartbeat.setSelected(true);

        chkAutoSaveLog.setSelected(true);
        txtLogPath.setText(System.getProperty("user.dir") + File.separator + "logs");
        cboMaxLogLines.setSelectedIndex(1);
        chkAutoCleanLog.setSelected(false);

        txtLabName.setText("Phòng Thực Hành Máy Tính 01");
        txtAdminName.setText("Quản Trị Viên / Giảng Viên");
        txtLabLocation.setText("Tầng 3 - Tòa nhà CNTT - Phòng 302");
        cboTheme.setSelectedIndex(0);
        chkPopupNotif.setSelected(true);

        JOptionPane.showMessageDialog(this,
            "Đã khôi phục cài đặt mặc định. Nhấn 'Lưu cài đặt' để áp dụng.",
            "Khôi phục mặc định",
            JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Helper tạo thành phần giao diện ──────────────────────────────────────

    private JLabel createLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UITheme.FONT_BODY);
        lbl.setForeground(UITheme.TEXT_PRIMARY);
        return lbl;
    }

    private JCheckBox createCheckBox(String text, boolean selected) {
        JCheckBox chk = new JCheckBox(text, selected);
        chk.setFont(UITheme.FONT_BODY);
        chk.setForeground(UITheme.TEXT_PRIMARY);
        chk.setBackground(UITheme.BG_CARD);
        chk.setOpaque(false);
        chk.setFocusPainted(false);
        return chk;
    }

    private GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    public static void main(String[] args) {
        UITheme.applyGlobal();
        SwingUtilities.invokeLater(() -> new SettingsForm().setVisible(true));
    }
}
