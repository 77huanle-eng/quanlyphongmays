package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/**
 * MonitorForm – Windows 11 Task Manager Command Center cho máy trạm LAN.
 * Tái tạo 100% giao diện và tính năng của Windows 11 Task Manager:
 * 1. Thanh điều hướng dọc ngoài cùng bên trái (Processes, Performance, Services, Live Stream, Alerts).
 * 2. Màn hình "Processes" (Tiến trình) đầy đủ: Heatmap resource coloring, Run task, End task, End tree, Popup menu, Sorter.
 * 3. Màn hình "Performance" (Hiệu năng): Master-Detail 7 linh kiện phần cứng (CPU, RAM, Disk, Ethernet, Wi-Fi, GPU 0, GPU 1).
 * 4. Màn hình "Services" (Dịch vụ Windows): Danh sách Services, Start/Stop service.
 * 5. Màn hình "Live Stream" (Quan sát & Điều khiển từ xa tương tác).
 * 6. Màn hình "Alerts" (Nhật ký Cảnh báo hệ thống).
 */
public class MonitorForm extends JFrame {

    private final String clientName;
    private final String clientIP;
    private final Consumer<String> commandSender;
    private final ScreenStreamServer streamServer;

    // ── Vùng trung tâm: CardLayout chuyển đổi giữa các màn hình ───────────────
    private final CardLayout mainCardLayout = new CardLayout();
    private final JPanel mainDeck = new JPanel(mainCardLayout);

    // ── Thanh Menu Điều Hướng Dọc Bên Trái (Left Navigation Rail) ─────────────
    private final JPanel navSidebar = new JPanel();
    private final JButton btnNavProcesses       = new JButton("Tiến trình (Processes)");
    private final JButton btnNavPerformance     = new JButton("Hiệu năng (Performance)");
    private final JButton btnNavStartupServices = new JButton("Dịch vụ (Services)");
    private final JButton btnNavLiveStream      = new JButton("Màn hình trực tiếp (Live)");
    private final JButton btnNavAlerts          = new JButton("Cảnh báo (Alerts)");
    private final List<JButton> navButtons = new ArrayList<>();
    private String currentNavKey = "processes";

    // ── Footer thông tin máy trạm trên Sidebar ────────────────────────────────
    private final JLabel lblClientName  = new JLabel("—");
    private final JLabel lblClientIP    = new JLabel("—");
    private final JLabel lblPing        = new JLabel("— ms");
    private final JLabel lblLastUpdate  = new JLabel("—");
    private volatile double sysCpuPct = 0.0;
    private volatile double sysRamPct = 0.0;
    private volatile double sysDiskPct = 0.0;

    // ── Tab 1: Màn hình Tiến trình (Processes) ────────────────────────────────
    private final DefaultTableModel modelProcess = new DefaultTableModel(
        new Object[]{"Tên tiến trình", "PID", "Trạng thái", "CPU (%)", "RAM (MB)", "Ổ đĩa (MB/s)", "Mạng (Mbps)", "Tiêu đề cửa sổ"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable tblProcess = new JTable(modelProcess);
    private final TableRowSorter<DefaultTableModel> procSorter = new TableRowSorter<>(modelProcess);
    private final JTextField txtFilterProc = new JTextField(16);
    private final JLabel lblProcCount = new JLabel("Tổng số tiến trình: 0");
    private final JButton btnRunTask = new JButton("[+] Chạy tác vụ mới");
    private final JButton btnEndTask = new JButton("[-] Dừng tiến trình (End task)");
    private final JButton btnEndTree = new JButton("[x] Tắt cả nhánh (End tree)");
    private final JButton btnRefreshProc = new JButton("Làm mới");
    private final JCheckBox chkAutoRefresh = new JCheckBox("Tự động (3s)", false);
    private javax.swing.Timer autoRefreshTimer;

    // ── Tab 2: Màn hình Hiệu năng (Performance - Windows 11 Task Manager) ────
    private final TaskMgrPerformancePanel perfPanel;

    // ── Tab 3: Màn hình Dịch vụ Windows (Services & Startup) ──────────────────
    private final DefaultTableModel modelServices = new DefaultTableModel(
        new Object[]{"Tên dịch vụ (Name)", "Trạng thái (Status)", "Mô tả dịch vụ (Display Name)"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable tblServices = new JTable(modelServices);
    private final TableRowSorter<DefaultTableModel> svcSorter = new TableRowSorter<>(modelServices);
    private final JTextField txtFilterSvc = new JTextField(16);
    private final JLabel lblSvcCount = new JLabel("Tổng số dịch vụ: 0");
    private final JButton btnStartSvc = new JButton("[>] Khởi động dịch vụ");
    private final JButton btnStopSvc = new JButton("[x] Dừng dịch vụ");
    private final JButton btnRefreshSvc = new JButton("Làm mới");

    // ── Tab 4: Màn hình Live Stream & Điều khiển ──────────────────────────────
    private final ScreenMonitorPanel screenPanel = new ScreenMonitorPanel();
    private final JButton btnStartStream = new JButton("Bắt đầu xem");
    private final JButton btnStopStream  = new JButton("Dừng xem");
    private final JButton btnSnapshot    = new JButton("Chụp ảnh (Snapshot)");
    private final JButton btnFullscreen  = new JButton("Toàn màn hình");
    private final JButton btnMultiScreen = new JButton("Chế độ nhiều máy");
    private final JToggleButton btnRemoteControl = new JToggleButton("[Điều khiển] Bật điều khiển từ xa");
    private Runnable onOpenMultiScreen;
    public void setOnOpenMultiScreen(Runnable r) { this.onOpenMultiScreen = r; }
    private boolean isStreaming = false;

    // ── Tab 5: Màn hình Nhật ký Cảnh báo (Alerts) ──────────────────────────────
    private final JTextArea txtAlert = new JTextArea();
    private int alertCount = 0;
    private final Map<String, Long> lastAlertTimeMap = new java.util.concurrent.ConcurrentHashMap<>();


    public MonitorForm(String clientName, String clientIP) {
        this(clientName, clientIP, null, null);
    }

    public MonitorForm(String clientName, String clientIP, Consumer<String> commandSender, ScreenStreamServer streamServer) {
        super("Task Manager — " + clientName + " (" + clientIP + ")");
        this.clientName = clientName;
        this.clientIP = clientIP;
        this.commandSender = commandSender;
        this.streamServer = streamServer;

        lblClientName.setText(clientName);
        lblClientIP.setText(clientIP);
        screenPanel.setCommandSender(commandSender);

        // Khởi tạo Performance Panel chuẩn Windows 11
        this.perfPanel = new TaskMgrPerformancePanel(txtAlert, () -> {
            txtAlert.setText("");
            alertCount = 0;
            updateAlertBadge();
        });

        buildUI();
        initAutoRefresh();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1260, 820);
        setMinimumSize(new Dimension(1020, 680));
        setLocationRelativeTo(null);

        // Dừng stream và timer khi đóng cửa sổ
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopLiveStream();
                if (autoRefreshTimer != null) autoRefreshTimer.stop();
            }
        });

        // Chủ động lấy dữ liệu tiến trình ban đầu
        if (commandSender != null) {
            commandSender.accept("REQ_PROC_LIST");
            commandSender.accept("GET_HARDWARE_DETAIL");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Build UI Tổng Thể: Left Navigation Sidebar + Central CardDeck
    // ─────────────────────────────────────────────────────────────────────────
    private void buildUI() {
        getContentPane().setBackground(UITheme.BG_DARK);
        setLayout(new BorderLayout(0, 0));

        // 1. Cột Menu Dọc bên trái
        add(buildNavSidebar(), BorderLayout.WEST);

        // 2. Vùng trung tâm (CardLayout)
        mainDeck.setBackground(UITheme.BG_DARK);
        mainDeck.add(buildProcessesTab(),      "processes");
        mainDeck.add(perfPanel,                "performance");
        mainDeck.add(buildStartupServicesTab(),"startupservices");
        mainDeck.add(buildLiveStreamTab(),     "livestream");
        mainDeck.add(buildAlertsTab(),         "alerts");

        add(mainDeck, BorderLayout.CENTER);

        // Mặc định chọn tab Processes
        selectNav("processes");
    }

    private JComponent buildNavSidebar() {
        navSidebar.setLayout(new BorderLayout(0, 0));
        navSidebar.setBackground(new Color(17, 24, 39)); // Slate 950 / #111827
        navSidebar.setBorder(new MatteBorder(0, 0, 0, 1, UITheme.BORDER));
        navSidebar.setPreferredSize(new Dimension(185, 0));

        // Top Branding
        JPanel topBrand = new JPanel(new GridLayout(2, 1, 0, 2));
        topBrand.setOpaque(false);
        topBrand.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel lblBrandTitle = new JLabel("TASK MANAGER");
        lblBrandTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblBrandTitle.setForeground(UITheme.ACCENT);

        JLabel lblBrandSub = new JLabel("Windows 11 Console");
        lblBrandSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblBrandSub.setForeground(UITheme.TEXT_MUTED);

        topBrand.add(lblBrandTitle);
        topBrand.add(lblBrandSub);
        navSidebar.add(topBrand, BorderLayout.NORTH);

        // Menu Buttons
        JPanel navMenu = new JPanel();
        navMenu.setLayout(new BoxLayout(navMenu, BoxLayout.Y_AXIS));
        navMenu.setOpaque(false);
        navMenu.setBorder(new EmptyBorder(4, 6, 4, 6));

        setupNavButton(btnNavProcesses,       "processes");
        setupNavButton(btnNavPerformance,     "performance");
        setupNavButton(btnNavStartupServices, "startupservices");
        setupNavButton(btnNavLiveStream,      "livestream");
        setupNavButton(btnNavAlerts,          "alerts");

        navMenu.add(btnNavProcesses);
        navMenu.add(Box.createVerticalStrut(4));
        navMenu.add(btnNavPerformance);
        navMenu.add(Box.createVerticalStrut(4));
        navMenu.add(btnNavStartupServices);
        navMenu.add(Box.createVerticalStrut(4));
        navMenu.add(btnNavLiveStream);
        navMenu.add(Box.createVerticalStrut(4));
        navMenu.add(btnNavAlerts);
        navMenu.add(Box.createVerticalGlue());

        navSidebar.add(navMenu, BorderLayout.CENTER);

        // Bottom Client Status Card
        JPanel bottomCard = new JPanel();
        bottomCard.setLayout(new BoxLayout(bottomCard, BoxLayout.Y_AXIS));
        bottomCard.setBackground(new Color(15, 20, 32));
        bottomCard.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, UITheme.BORDER),
            new EmptyBorder(10, 12, 12, 12)
        ));

        lblClientName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblClientName.setForeground(Color.WHITE);

        lblClientIP.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblClientIP.setForeground(UITheme.TEXT_MUTED);

        lblPing.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblPing.setForeground(UITheme.SUCCESS);

        lblLastUpdate.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblLastUpdate.setForeground(UITheme.TEXT_MUTED);

        bottomCard.add(lblClientName);
        bottomCard.add(Box.createVerticalStrut(2));
        bottomCard.add(lblClientIP);
        bottomCard.add(Box.createVerticalStrut(3));
        bottomCard.add(lblPing);
        bottomCard.add(Box.createVerticalStrut(2));
        bottomCard.add(lblLastUpdate);

        navSidebar.add(bottomCard, BorderLayout.SOUTH);
        return navSidebar;
    }

    private void setupNavButton(JButton btn, String key) {
        navButtons.add(btn);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setForeground(new Color(156, 163, 175)); // Gray 400
        btn.setBackground(new Color(17, 24, 39));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setPreferredSize(new Dimension(170, 38));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 12, 8, 10));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!key.equals(currentNavKey)) {
                    btn.setBackground(new Color(31, 41, 55));
                    btn.setForeground(Color.WHITE);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!key.equals(currentNavKey)) {
                    btn.setBackground(new Color(17, 24, 39));
                    btn.setForeground(new Color(156, 163, 175));
                }
            }
        });

        btn.addActionListener(e -> selectNav(key));
    }

    private void selectNav(String key) {
        this.currentNavKey = key;
        for (JButton b : navButtons) {
            b.setBackground(new Color(17, 24, 39));
            b.setForeground(new Color(156, 163, 175));
            b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            b.setBorder(new EmptyBorder(8, 12, 8, 10));
        }

        JButton activeBtn = switch (key) {
            case "processes"       -> btnNavProcesses;
            case "performance"     -> btnNavPerformance;
            case "startupservices" -> btnNavStartupServices;
            case "livestream"      -> btnNavLiveStream;
            case "alerts"          -> btnNavAlerts;
            default                -> btnNavProcesses;
        };

        activeBtn.setBackground(new Color(30, 41, 59)); // Slate 800 active
        activeBtn.setForeground(Color.WHITE);
        activeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        activeBtn.setBorder(new CompoundBorder(
            new MatteBorder(0, 4, 0, 0, UITheme.ACCENT), // Vạch màu Cyan 4px ở mép trái
            new EmptyBorder(8, 8, 8, 10)
        ));

        mainCardLayout.show(mainDeck, key);

        // Kích hoạt nạp dữ liệu khi mở tab
        if ("processes".equals(key) && commandSender != null) {
            commandSender.accept("REQ_PROC_LIST");
        } else if ("startupservices".equals(key) && commandSender != null) {
            commandSender.accept("REQ_STARTUP_SERVICES");
        }
    }

    private void updateAlertBadge() {
        if (alertCount > 0) {
            btnNavAlerts.setText("[!] Cảnh báo (" + alertCount + ")");
            btnNavAlerts.setForeground(UITheme.DANGER);
        } else {
            btnNavAlerts.setText("[!] Cảnh báo (Alerts)");
            btnNavAlerts.setForeground(new Color(156, 163, 175));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Màn hình "Processes" (Tab Tiến trình chuẩn Task Manager)
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel buildProcessesTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(10, 14, 12, 14));

        // Top Action Bar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(UITheme.BG_CARD);
        toolbar.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel lblSearch = new JLabel("Lọc:");
        lblSearch.setFont(UITheme.FONT_BODY);
        lblSearch.setForeground(UITheme.TEXT_MUTED);

        UITheme.styleTextField(txtFilterProc);
        txtFilterProc.setToolTipText("Lọc tiến trình theo Tên hoặc PID");
        txtFilterProc.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String text = txtFilterProc.getText().trim();
                if (text.isEmpty()) {
                    procSorter.setRowFilter(null);
                } else {
                    procSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        // Nút Run new task
        UITheme.styleButtonAccent(btnRunTask);
        btnRunTask.addActionListener(e -> runNewTaskDialog());

        // Nút End task
        UITheme.styleButtonDanger(btnEndTask);
        btnEndTask.addActionListener(e -> killSelectedProcess(false));

        // Nút End tree
        UITheme.styleButtonDanger(btnEndTree);
        btnEndTree.setText("[x] Tắt cả nhánh");
        btnEndTree.setToolTipText("Tắt toàn bộ nhánh cây của tiến trình (taskkill /F /T)");
        btnEndTree.addActionListener(e -> killSelectedProcess(true));

        // Nút Làm mới
        UITheme.styleButtonSecondary(btnRefreshProc);
        btnRefreshProc.addActionListener(e -> {
            if (commandSender != null) {
                commandSender.accept("REQ_PROC_LIST");
                lblProcCount.setText("Đang tải danh sách tiến trình...");
            }
        });

        // Checkbox tự động làm mới
        chkAutoRefresh.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkAutoRefresh.setForeground(UITheme.TEXT_MUTED);
        chkAutoRefresh.setOpaque(false);
        chkAutoRefresh.addActionListener(e -> {
            if (chkAutoRefresh.isSelected()) {
                autoRefreshTimer.start();
            } else {
                autoRefreshTimer.stop();
            }
        });

        lblProcCount.setFont(UITheme.FONT_HEADER);
        lblProcCount.setForeground(UITheme.ACCENT);

        toolbar.add(lblSearch);
        toolbar.add(txtFilterProc);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(btnRunTask);
        toolbar.add(btnEndTask);
        toolbar.add(btnEndTree);
        toolbar.add(btnRefreshProc);
        toolbar.add(chkAutoRefresh);
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(lblProcCount);

        // Bảng danh sách tiến trình
        tblProcess.setRowSorter(procSorter);
        UITheme.styleTable(tblProcess);
        tblProcess.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Comparator sắp xếp số cho các cột
        procSorter.setComparator(1, (Comparator<Object>) this::compareLong);   // PID
        procSorter.setComparator(3, (Comparator<Object>) this::compareDouble); // CPU %
        procSorter.setComparator(4, (Comparator<Object>) this::compareDouble); // RAM MB
        procSorter.setComparator(5, (Comparator<Object>) this::compareDouble); // Disk MB/s
        procSorter.setComparator(6, (Comparator<Object>) this::compareDouble); // Net Mbps

        // Mặc định sắp xếp theo RAM giảm dần
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(4, SortOrder.DESCENDING));
        procSorter.setSortKeys(sortKeys);

        // Áp dụng Heatmap Resource Cell Renderer
        tblProcess.setDefaultRenderer(Object.class, new ProcessHeatmapRenderer());

        // Độ rộng cột
        tblProcess.getColumnModel().getColumn(0).setPreferredWidth(160); // Tên
        tblProcess.getColumnModel().getColumn(1).setPreferredWidth(70);  // PID
        tblProcess.getColumnModel().getColumn(1).setMaxWidth(90);
        tblProcess.getColumnModel().getColumn(2).setPreferredWidth(85);  // Trạng thái
        tblProcess.getColumnModel().getColumn(2).setMaxWidth(100);
        tblProcess.getColumnModel().getColumn(3).setPreferredWidth(75);  // CPU %
        tblProcess.getColumnModel().getColumn(3).setMaxWidth(90);
        tblProcess.getColumnModel().getColumn(4).setPreferredWidth(95);  // RAM MB
        tblProcess.getColumnModel().getColumn(4).setMaxWidth(120);
        tblProcess.getColumnModel().getColumn(5).setPreferredWidth(80);  // Disk
        tblProcess.getColumnModel().getColumn(5).setMaxWidth(100);
        tblProcess.getColumnModel().getColumn(6).setPreferredWidth(80);  // Net
        tblProcess.getColumnModel().getColumn(6).setMaxWidth(100);
        tblProcess.getColumnModel().getColumn(7).setPreferredWidth(320); // Tiêu đề cửa sổ

        // Menu Chuột Phải (Popup Context Menu)
        initProcessPopupMenu();

        JScrollPane scrollPane = new JScrollPane(tblProcess);
        UITheme.styleScrollPane(scrollPane);

        p.add(toolbar, BorderLayout.NORTH);
        p.add(scrollPane, BorderLayout.CENTER);
        return p;
    }

    private void initAutoRefresh() {
        autoRefreshTimer = new javax.swing.Timer(3000, e -> {
            if ("processes".equals(currentNavKey) && isShowing() && commandSender != null) {
                commandSender.accept("REQ_PROC_LIST");
            }
        });
    }

    private void runNewTaskDialog() {
        String cmd = JOptionPane.showInputDialog(
            this,
            "Nhập tên chương trình, tiến trình hoặc lệnh cần chạy trên máy " + clientName + ":\n(Ví dụ: notepad, cmd, calc, chrome, taskmgr, services.msc)",
            "Chạy tác vụ mới (Run new task)",
            JOptionPane.PLAIN_MESSAGE
        );

        if (cmd != null && !cmd.trim().isEmpty()) {
            if (commandSender != null) {
                commandSender.accept("PROC_RUN|" + clientName + "|" + cmd.trim());
                addAlert("PROC_RUN", "Đã gửi lệnh chạy tác vụ: " + cmd.trim());
            }
        }
    }

    private void killSelectedProcess(boolean killTree) {
        int viewRow = tblProcess.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tiến trình trong danh sách để dừng!", "Chưa chọn tiến trình", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = tblProcess.convertRowIndexToModel(viewRow);
        String procName = String.valueOf(modelProcess.getValueAt(modelRow, 0));
        String pid      = String.valueOf(modelProcess.getValueAt(modelRow, 1));
        String title    = String.valueOf(modelProcess.getValueAt(modelRow, 7));

        String confirmMsg = (killTree ? "Bạn có chắc chắn muốn TẮT TOÀN BỘ NHÁNH CÂY của tiến trình:\n" : "Bạn có chắc chắn muốn DỪNG tiến trình:\n")
                + "• Tên tiến trình: " + procName + "\n"
                + "• PID: " + pid + "\n"
                + (title.isEmpty() ? "" : ("• Tiêu đề: " + title + "\n"))
                + (killTree ? "\n(Lưu ý: Thao tác này sẽ đóng tất cả tiến trình con liên quan!)" : "");

        int cf = JOptionPane.showConfirmDialog(
            this, confirmMsg,
            killTree ? "Xác nhận tắt cả nhánh (End process tree)" : "Xác nhận dừng tiến trình (End task)",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        );

        if (cf == JOptionPane.YES_OPTION && commandSender != null) {
            String cmd;
            String cleanName = procName.replaceAll("\\s*\\(\\d+\\)$", "").trim();
            if (procName.contains("(") || killTree) {
                cmd = "PROC_KILL_NAME|" + cleanName;
                commandSender.accept(cmd);
                if (pid != null && !pid.isEmpty() && !"0".equals(pid)) {
                    commandSender.accept("PROC_KILL_TREE|" + pid);
                }
            } else {
                cmd = "PROC_KILL|" + clientName + "|" + pid;
                commandSender.accept(cmd);
            }
            addAlert("PROC_KILL", (killTree ? "Đã gửi lệnh tắt cả nhóm/nhánh: " : "Đã gửi lệnh dừng tiến trình: ") + procName + " (PID: " + pid + ")");
        }
    }

    private void initProcessPopupMenu() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(UITheme.BG_CARD);

        JMenuItem miEndTask = new JMenuItem("[-] Dừng tiến trình (End task)");
        JMenuItem miEndTree = new JMenuItem("[x] Dừng toàn bộ nhánh cây (End process tree)");
        JMenuItem miRunNew  = new JMenuItem("[+] Chạy tác vụ mới (Run task)...");
        JMenuItem miProps   = new JMenuItem("[i] Xem thuộc tính / Thông tin chi tiết");
        JMenuItem miRefresh = new JMenuItem("Làm mới danh sách");

        miEndTask.addActionListener(e -> killSelectedProcess(false));
        miEndTree.addActionListener(e -> killSelectedProcess(true));
        miRunNew.addActionListener(e -> runNewTaskDialog());
        miRefresh.addActionListener(e -> {
            if (commandSender != null) commandSender.accept("REQ_PROC_LIST");
        });

        miProps.addActionListener(e -> {
            int sel = tblProcess.getSelectedRow();
            if (sel >= 0) {
                int mr = tblProcess.convertRowIndexToModel(sel);
                String name   = String.valueOf(modelProcess.getValueAt(mr, 0));
                String pid    = String.valueOf(modelProcess.getValueAt(mr, 1));
                String status = String.valueOf(modelProcess.getValueAt(mr, 2));
                String cpu    = String.valueOf(modelProcess.getValueAt(mr, 3));
                String ram    = String.valueOf(modelProcess.getValueAt(mr, 4));
                String title  = String.valueOf(modelProcess.getValueAt(mr, 7));

                JOptionPane.showMessageDialog(this,
                    "THÔNG TIN TIẾN TRÌNH TRÊN MÁY: " + clientName + "\n\n" +
                    "• Tên tiến trình: " + name + "\n" +
                    "• PID: " + pid + "\n" +
                    "• Trạng thái: " + status + "\n" +
                    "• Mức chiếm dụng CPU: " + cpu + "%\n" +
                    "• Bộ nhớ RAM đang dùng: " + ram + " MB\n" +
                    "• Tiêu đề ứng dụng: " + (title.isEmpty() ? "Không có cửa sổ chính" : title),
                    "Chi tiết tiến trình - " + name, JOptionPane.INFORMATION_MESSAGE);
            }
        });

        popup.add(miEndTask);
        popup.add(miEndTree);
        popup.addSeparator();
        popup.add(miRunNew);
        popup.add(miProps);
        popup.addSeparator();
        popup.add(miRefresh);

        tblProcess.setComponentPopupMenu(popup);

        // Click đúp vào dòng để dừng tiến trình
        tblProcess.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblProcess.getSelectedRow() >= 0) {
                    killSelectedProcess(false);
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                int r = tblProcess.rowAtPoint(e.getPoint());
                if (r >= 0 && r < tblProcess.getRowCount()) {
                    tblProcess.setRowSelectionInterval(r, r);
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Màn hình "Services" (Dịch vụ Windows & Khởi động)
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel buildStartupServicesTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(10, 14, 12, 14));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(UITheme.BG_CARD);
        toolbar.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel lblSearch = new JLabel("Lọc dịch vụ:");
        lblSearch.setFont(UITheme.FONT_BODY);
        lblSearch.setForeground(UITheme.TEXT_MUTED);

        UITheme.styleTextField(txtFilterSvc);
        txtFilterSvc.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String text = txtFilterSvc.getText().trim();
                if (text.isEmpty()) {
                    svcSorter.setRowFilter(null);
                } else {
                    svcSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        UITheme.styleButtonSuccess(btnStartSvc);
        btnStartSvc.addActionListener(e -> controlSelectedService("start"));

        UITheme.styleButtonDanger(btnStopSvc);
        btnStopSvc.addActionListener(e -> controlSelectedService("stop"));

        UITheme.styleButtonSecondary(btnRefreshSvc);
        btnRefreshSvc.addActionListener(e -> {
            if (commandSender != null) commandSender.accept("REQ_STARTUP_SERVICES");
        });

        lblSvcCount.setFont(UITheme.FONT_HEADER);
        lblSvcCount.setForeground(UITheme.ACCENT);

        toolbar.add(lblSearch);
        toolbar.add(txtFilterSvc);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(btnStartSvc);
        toolbar.add(btnStopSvc);
        toolbar.add(btnRefreshSvc);
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(lblSvcCount);

        tblServices.setRowSorter(svcSorter);
        UITheme.styleTable(tblServices);
        tblServices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        tblServices.getColumnModel().getColumn(0).setPreferredWidth(180);
        tblServices.getColumnModel().getColumn(1).setPreferredWidth(100);
        tblServices.getColumnModel().getColumn(1).setMaxWidth(130);
        tblServices.getColumnModel().getColumn(2).setPreferredWidth(450);

        // Renderer cho Service: Running (Xanh lá), Stopped (Xám mờ)
        tblServices.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (isSelected) {
                    c.setBackground(new Color(30, 58, 138));
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(row % 2 == 0 ? UITheme.BG_CARD : UITheme.BG_DARK);
                    c.setForeground(UITheme.TEXT_PRIMARY);
                    if (column == 1) {
                        String st = String.valueOf(value);
                        setHorizontalAlignment(SwingConstants.CENTER);
                        if ("Running".equalsIgnoreCase(st)) {
                            c.setForeground(UITheme.SUCCESS);
                        } else {
                            c.setForeground(UITheme.TEXT_MUTED);
                        }
                    } else {
                        setHorizontalAlignment(SwingConstants.LEFT);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tblServices);
        UITheme.styleScrollPane(scrollPane);

        p.add(toolbar, BorderLayout.NORTH);
        p.add(scrollPane, BorderLayout.CENTER);
        return p;
    }

    private void controlSelectedService(String action) {
        int sel = tblServices.getSelectedRow();
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một dịch vụ trong bảng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int mr = tblServices.convertRowIndexToModel(sel);
        String svcName = String.valueOf(modelServices.getValueAt(mr, 0));
        String desc    = String.valueOf(modelServices.getValueAt(mr, 2));

        int cf = JOptionPane.showConfirmDialog(
            this,
            "Bạn có chắc muốn " + ("start".equals(action) ? "KHỞI ĐỘNG" : "DỪNG") + " dịch vụ:\n• " + svcName + " (" + desc + ") trên máy " + clientName + "?",
            "Xác nhận điều khiển dịch vụ",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        );

        if (cf == JOptionPane.YES_OPTION && commandSender != null) {
            commandSender.accept("SERVICE_CONTROL|" + action + "|" + svcName);
            addAlert("SERVICE", "Đã gửi lệnh " + action + " tới dịch vụ " + svcName);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Màn hình "Live Stream" (Quan sát & Điều khiển từ xa tương tác)
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel buildLiveStreamTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(10, 14, 12, 14));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        toolbar.setBackground(UITheme.BG_CARD);
        toolbar.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        UITheme.styleButtonAccent(btnStartStream);
        UITheme.styleButtonDanger(btnStopStream);
        UITheme.styleButtonSecondary(btnSnapshot);
        UITheme.styleButtonSecondary(btnFullscreen);

        btnStopStream.setEnabled(false);

        btnStartStream.addActionListener(e -> startLiveStream());
        btnStopStream.addActionListener(e -> stopLiveStream());
        btnSnapshot.addActionListener(e -> takeSnapshot());
        btnFullscreen.addActionListener(e -> showFullscreen());

        btnRemoteControl.setFont(UITheme.FONT_BTN);
        btnRemoteControl.setFocusPainted(false);
        btnRemoteControl.setOpaque(true);
        btnRemoteControl.setBackground(UITheme.BG_CARD);
        btnRemoteControl.setForeground(UITheme.ACCENT);
        btnRemoteControl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRemoteControl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.ACCENT, 1, true),
            BorderFactory.createEmptyBorder(7, 14, 7, 14)
        ));
        btnRemoteControl.addActionListener(e -> {
            boolean active = btnRemoteControl.isSelected();
            screenPanel.setRemoteControlEnabled(active);
            if (active) {
                btnRemoteControl.setText("[Điều khiển] Đang điều khiển từ xa");
                btnRemoteControl.setBackground(UITheme.DANGER);
                btnRemoteControl.setForeground(Color.WHITE);
                screenPanel.requestFocusInWindow();
            } else {
                btnRemoteControl.setText("[Điều khiển] Bật điều khiển từ xa");
                btnRemoteControl.setBackground(UITheme.BG_CARD);
                btnRemoteControl.setForeground(UITheme.ACCENT);
            }
        });

        toolbar.add(btnStartStream);
        toolbar.add(btnStopStream);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(btnSnapshot);
        toolbar.add(btnFullscreen);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(btnRemoteControl);
        toolbar.add(Box.createHorizontalStrut(8));
        UITheme.styleButtonAccent(btnMultiScreen);
        btnMultiScreen.addActionListener(e -> {
            if (onOpenMultiScreen != null) {
                onOpenMultiScreen.run();
            }
        });
        toolbar.add(btnMultiScreen);

        p.add(toolbar, BorderLayout.NORTH);
        p.add(screenPanel, BorderLayout.CENTER);
        return p;
    }

    public void switchToLiveStream(boolean enableControl) {
        SwingUtilities.invokeLater(() -> {
            selectNav("livestream");
            startLiveStream();
            if (enableControl) {
                btnRemoteControl.setSelected(true);
                btnRemoteControl.setText("[Điều khiển] Đang điều khiển từ xa");
                btnRemoteControl.setBackground(UITheme.DANGER);
                btnRemoteControl.setForeground(Color.WHITE);
                screenPanel.setRemoteControlEnabled(true);
                screenPanel.requestFocusInWindow();
            }
        });
    }

    private void startLiveStream() {
        if (isStreaming) return;
        isStreaming = true;

        if (streamServer != null) {
            streamServer.registerMonitor(clientName, screenPanel);
        }
        if (commandSender != null) {
            commandSender.accept("START_STREAM|5001");
        }

        screenPanel.setStatusMessage("Đang gửi yêu cầu và kết nối luồng màn hình...");
        btnStartStream.setEnabled(false);
        btnStopStream.setEnabled(true);
    }

    private void stopLiveStream() {
        if (!isStreaming) return;
        isStreaming = false;

        if (commandSender != null) {
            commandSender.accept("STOP_STREAM");
        }
        if (streamServer != null) {
            streamServer.unregisterMonitor(clientName);
        }

        btnRemoteControl.setSelected(false);
        btnRemoteControl.setText("[Điều khiển] Bật điều khiển từ xa");
        btnRemoteControl.setBackground(UITheme.BG_CARD);
        btnRemoteControl.setForeground(UITheme.ACCENT);
        screenPanel.setRemoteControlEnabled(false);
        screenPanel.setStatusMessage("Đã dừng quan sát màn hình.");
        btnStartStream.setEnabled(true);
        btnStopStream.setEnabled(false);
    }

    private void takeSnapshot() {
        BufferedImage frame = screenPanel.getCurrentFrame();
        if (frame == null) {
            JOptionPane.showMessageDialog(this, "Chưa có khung hình màn hình để chụp!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        String defaultName = "Snapshot_" + clientName + "_" + System.currentTimeMillis() + ".png";
        chooser.setSelectedFile(new File(defaultName));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }
            if (screenPanel.saveSnapshot(file)) {
                JOptionPane.showMessageDialog(this, "Đã lưu ảnh màn hình thành công tại:\n" + file.getAbsolutePath(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Lỗi lưu file ảnh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showFullscreen() {
        BufferedImage frame = screenPanel.getCurrentFrame();
        if (frame == null) {
            JOptionPane.showMessageDialog(this, "Chưa có khung hình nào để xem toàn màn hình!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog fsDialog = new JDialog(this, "Toàn màn hình - " + clientName, true);
        fsDialog.setUndecorated(true);
        fsDialog.setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds());

        ScreenMonitorPanel fsPanel = new ScreenMonitorPanel();
        fsPanel.updateFrame(frame, 0);

        if (isStreaming && streamServer != null) {
            streamServer.registerMonitor(clientName, fsPanel);
        }

        fsDialog.setContentPane(fsPanel);

        fsDialog.getRootPane().registerKeyboardAction(e -> {
            if (isStreaming && streamServer != null) {
                streamServer.registerMonitor(clientName, screenPanel);
            }
            fsDialog.dispose();
        }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        fsDialog.setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Màn hình "Alerts" (Nhật ký Cảnh báo hệ thống)
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel buildAlertsTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(UITheme.BG_CARD);
        header.setBorder(new CompoundBorder(
            new LineBorder(UITheme.BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel title = new JLabel("NHẬT KÝ CẢNH BÁO & SỰ CỐ HỆ THỐNG MÁY " + clientName);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(new Color(245, 158, 11)); // Amber

        JButton btnClear = new JButton("Xóa toàn bộ log");
        UITheme.styleButtonDanger(btnClear);
        btnClear.addActionListener(e -> {
            txtAlert.setText("");
            alertCount = 0;
            updateAlertBadge();
        });

        header.add(title, BorderLayout.WEST);
        header.add(btnClear, BorderLayout.EAST);

        txtAlert.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtAlert.setBackground(new Color(8, 12, 20));
        txtAlert.setForeground(new Color(254, 202, 202));
        txtAlert.setEditable(false);
        txtAlert.setLineWrap(true);
        txtAlert.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(txtAlert);
        UITheme.styleScrollPane(sp);

        p.add(header, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. API Gọi từ ServerForm (Cập nhật dữ liệu thời gian thực an toàn EDT)
    // ─────────────────────────────────────────────────────────────────────────

    public void updatePerf(double cpu, double ramPct, String ramLabel,
                           double disk, double netUp, double netDown,
                           double gpu, long ping) {
        updatePerf(null, cpu, ramPct, ramLabel, disk, netUp, netDown, gpu, ping);
    }

    public void updatePerf(Map<String, String> kv,
                           double cpu, double ramPct, String ramLabel,
                           double disk, double netUp, double netDown,
                           double gpu, long ping) {
        sysCpuPct = cpu;
        sysRamPct = ramPct;
        sysDiskPct = disk;
        SwingUtilities.invokeLater(() -> {
            perfPanel.updatePerfMetrics(kv, cpu, ramPct, ramLabel, disk, netUp, netDown, gpu, ping);

            if (ping >= 0) {
                lblPing.setText("Ping: " + ping + " ms");
                lblPing.setForeground(ping < 50 ? UITheme.SUCCESS
                        : ping < 150 ? UITheme.WARNING : UITheme.DANGER);
            }

            lblLastUpdate.setText("Cập nhật: " + LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        });
    }

    public void setClientOffline() {
        SwingUtilities.invokeLater(() -> {
            lblPing.setText("Ngoại tuyến (Offline)");
            lblPing.setForeground(UITheme.DANGER);
            lblLastUpdate.setText("Mất kết nối lúc: " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            screenPanel.setStatusMessage("Máy khách đã ngắt kết nối mạng LAN.");
        });
    }

    public void updateHardwareDetail(Map<String, String> kv) {
        perfPanel.updateHardwareDetail(kv);
    }

    public void updateDxDiag(String os, String cpu, String ram, String gpu,
                             String dx, String bios, String model,
                             String maker, String name) {
        Map<String, String> kv = new HashMap<>();
        if (cpu != null && !cpu.isBlank())   kv.put("cpuName", cpu);
        if (ram != null && !ram.isBlank())   kv.put("ramTotal", ram);
        if (gpu != null && !gpu.isBlank())   kv.put("gpu0Name", gpu);
        if (model != null && !model.isBlank()) kv.put("diskName", model);
        updateHardwareDetail(kv);
    }

    public void addAlert(String type, String value) {
        long now = System.currentTimeMillis();
        Long last = lastAlertTimeMap.get(type);
        if (last != null && (now - last) < 30000L) {
            return;
        }
        lastAlertTimeMap.put(type, now);

        SwingUtilities.invokeLater(() -> {
            alertCount++;
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String msg = switch (type) {
                case "RAM_FULL"  -> "[CẢNH BÁO] BỘ NHỚ RAM ĐẦY: " + value;
                case "DISK_FULL" -> "[CẢNH BÁO] Ổ ĐĨA C: SẮP HẾT DUNG LƯỢNG: " + value;
                case "CPU_HIGH"  -> "[CẢNH BÁO] CPU QUÁ TẢI: " + value;
                case "RAM_HIGH"  -> "[CẢNH BÁO] RAM QUÁ TẢI: " + value;
                case "DISK_HIGH" -> "[CẢNH BÁO] Ổ ĐĨA GẦN ĐẦY: " + value;
                default          -> "[CẢNH BÁO] " + type + ": " + value;
            };
            txtAlert.append("[" + time + "] " + msg + "\n");
            txtAlert.setCaretPosition(txtAlert.getDocument().getLength());
            perfPanel.updateAlertBadge(alertCount);
            updateAlertBadge();
        });
    }

    public void updateProcessList(String data) {
        SwingUtilities.invokeLater(() -> {
            if (data == null || data.trim().isEmpty() || "NONE".equals(data.trim())) {
                modelProcess.setRowCount(0);
                lblProcCount.setText("Tổng số tiến trình: 0");
                return;
            }

            String selectedPid = null;
            int sel = tblProcess.getSelectedRow();
            if (sel >= 0) {
                int mr = tblProcess.convertRowIndexToModel(sel);
                if (mr < modelProcess.getRowCount()) {
                    selectedPid = String.valueOf(modelProcess.getValueAt(mr, 1));
                }
            }

            String[] entries = data.split("~~");
            modelProcess.setRowCount(0);
            int restoredRow = -1;
            int runningCount = 0;

            double totalCpu = 0.0;
            double totalRam = 0.0;
            double totalDisk = 0.0;

            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                String[] fields = entry.split("::", 8);

                String name   = fields.length > 0 ? fields[0] : "";
                String pid    = fields.length > 1 ? fields[1] : "";
                String status = "Running";
                String cpu    = "0";
                String ram    = "0";
                String disk   = "0.0";
                String net    = "0.0";
                String title  = "";

                if (fields.length >= 8) {
                    status = fields[2];
                    cpu    = fields[3];
                    ram    = fields[4];
                    disk   = fields[5];
                    net    = fields[6];
                    title  = fields[7];
                } else if (fields.length == 5) {
                    cpu   = fields[2];
                    ram   = fields[3];
                    title = fields[4];
                }

                if ("Running".equalsIgnoreCase(status)) runningCount++;

                try { totalCpu += Double.parseDouble(cpu.replace(',', '.')); } catch (Exception ignored) {}
                try { totalRam += Double.parseDouble(ram.replace(',', '.')); } catch (Exception ignored) {}
                try { totalDisk += Double.parseDouble(disk.replace(',', '.')); } catch (Exception ignored) {}

                modelProcess.addRow(new Object[]{
                    name,
                    pid,
                    status,
                    cpu.isEmpty() ? "0" : cpu,
                    ram.isEmpty() ? "0" : ram,
                    disk,
                    net,
                    title
                });

                if (selectedPid != null && selectedPid.equals(pid)) {
                    restoredRow = modelProcess.getRowCount() - 1;
                }
            }

            double dispCpu = sysCpuPct > 0 ? sysCpuPct : totalCpu;
            double dispRam = sysRamPct > 0 ? sysRamPct : (totalRam > 0 ? (totalRam / 16384.0 * 100.0) : 0);
            double dispDisk = sysDiskPct > 0 ? sysDiskPct : totalDisk;
            try {
                tblProcess.getColumnModel().getColumn(3).setHeaderValue(String.format(java.util.Locale.US, "CPU (%%) - Tổng: %.1f%%", Math.min(100.0, dispCpu)));
                tblProcess.getColumnModel().getColumn(4).setHeaderValue(String.format(java.util.Locale.US, "RAM (MB) - Tổng: %.1f%%", Math.min(100.0, dispRam)));
                tblProcess.getColumnModel().getColumn(5).setHeaderValue(String.format(java.util.Locale.US, "Ổ đĩa (MB/s) - %.1f%%", Math.min(100.0, dispDisk)));
                tblProcess.getTableHeader().repaint();
            } catch (Exception ignored) {}

            lblProcCount.setText("Tổng số: " + modelProcess.getRowCount() + " (Đang chạy: " + runningCount + ")");

            if (restoredRow >= 0 && restoredRow < tblProcess.getRowCount()) {
                try {
                    int viewIdx = tblProcess.convertRowIndexToView(restoredRow);
                    if (viewIdx >= 0) {
                        tblProcess.setRowSelectionInterval(viewIdx, viewIdx);
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public void updateServicesList(String data) {
        SwingUtilities.invokeLater(() -> {
            if (data == null || data.trim().isEmpty()) {
                modelServices.setRowCount(0);
                lblSvcCount.setText("Tổng số dịch vụ: 0");
                return;
            }

            String[] entries = data.split("~~");
            modelServices.setRowCount(0);

            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                String[] parts = entry.split("::", 3);
                if (parts.length >= 2) {
                    String name   = parts[0].trim();
                    String status = parts[1].trim();
                    String desc   = (parts.length >= 3) ? parts[2].trim() : name;
                    modelServices.addRow(new Object[]{name, status, desc});
                }
            }
            lblSvcCount.setText("Tổng số dịch vụ: " + modelServices.getRowCount());
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Heatmap Resource Cell Renderer (Tô màu cảnh báo mức chiếm CPU/RAM)
    // ─────────────────────────────────────────────────────────────────────────
    private static class ProcessHeatmapRenderer extends DefaultTableCellRenderer {
        private final java.text.DecimalFormat dfRam = new java.text.DecimalFormat("#,##0.0");
        private final java.text.DecimalFormat dfCpu = new java.text.DecimalFormat("0.0");

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String valStr = (value != null) ? value.toString().trim() : "";

            if (isSelected) {
                c.setBackground(new Color(30, 58, 138)); // Windows 11 Blue Selection
                c.setForeground(Color.WHITE);
                return c;
            }

            // Nền xen kẽ Dark Theme
            c.setBackground(row % 2 == 0 ? UITheme.BG_CARD : UITheme.BG_DARK);
            c.setForeground(UITheme.TEXT_PRIMARY);

            if (column == 0) { // Tên tiến trình
                setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(new EmptyBorder(0, 10, 0, 4));
            } else if (column == 1) { // PID
                setHorizontalAlignment(SwingConstants.CENTER);
                c.setForeground(UITheme.TEXT_MUTED);
            } else if (column == 2) { // Trạng thái
                setHorizontalAlignment(SwingConstants.CENTER);
                if ("Running".equalsIgnoreCase(valStr)) {
                    c.setForeground(new Color(16, 185, 129)); // Xanh lá
                } else if ("Suspended".equalsIgnoreCase(valStr)) {
                    c.setForeground(new Color(245, 158, 11)); // Cam
                } else {
                    c.setForeground(UITheme.TEXT_MUTED);
                }
            } else if (column == 3) { // CPU % Heatmap chuẩn Windows 11 Blue
                setHorizontalAlignment(SwingConstants.RIGHT);
                try {
                    double cpu = Double.parseDouble(valStr.replace("%", "").trim());
                    setText(dfCpu.format(cpu) + "%");
                    if (cpu > 0.1) {
                        float ratio = (float) Math.min(1.0, cpu / 35.0);
                        int r = (int) (18 + ratio * 20);
                        int g = (int) (48 + ratio * 60);
                        int b = (int) (90 + ratio * 115);
                        c.setBackground(new Color(r, g, b));
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setForeground(UITheme.TEXT_MUTED);
                    }
                } catch (Exception ignored) {}
            } else if (column == 4) { // RAM MB Heatmap chuẩn Windows 11 Blue
                setHorizontalAlignment(SwingConstants.RIGHT);
                try {
                    double ram = Double.parseDouble(valStr.replace("MB", "").replace(",", ".").trim());
                    setText(dfRam.format(ram) + " MB");
                    if (ram > 20.0) {
                        float ratio = (float) Math.min(1.0, ram / 1200.0);
                        int r = (int) (16 + ratio * 22);
                        int g = (int) (42 + ratio * 58);
                        int b = (int) (80 + ratio * 115);
                        c.setBackground(new Color(r, g, b));
                        c.setForeground(Color.WHITE);
                    }
                } catch (Exception ignored) {}
            } else if (column == 5) { // Ổ đĩa
                setHorizontalAlignment(SwingConstants.RIGHT);
                c.setForeground("0.0".equals(valStr) || "0".equals(valStr) ? UITheme.TEXT_MUTED : UITheme.TEXT_PRIMARY);
                if (!valStr.isEmpty() && !valStr.endsWith("MB/s")) setText(valStr + " MB/s");
            } else if (column == 6) { // Mạng
                setHorizontalAlignment(SwingConstants.RIGHT);
                c.setForeground("0.0".equals(valStr) || "0".equals(valStr) ? UITheme.TEXT_MUTED : UITheme.TEXT_PRIMARY);
                if (!valStr.isEmpty() && !valStr.endsWith("Mbps")) setText(valStr + " Mbps");
            } else if (column == 7) { // Tiêu đề cửa sổ
                setHorizontalAlignment(SwingConstants.LEFT);
                c.setForeground(valStr.isEmpty() ? UITheme.TEXT_MUTED : UITheme.TEXT_PRIMARY);
            }

            return c;
        }
    }

    private int compareLong(Object o1, Object o2) {
        try {
            long p1 = Long.parseLong(String.valueOf(o1).trim());
            long p2 = Long.parseLong(String.valueOf(o2).trim());
            return Long.compare(p1, p2);
        } catch (Exception e) {
            return String.valueOf(o1).compareTo(String.valueOf(o2));
        }
    }

    private int compareDouble(Object o1, Object o2) {
        try {
            double d1 = Double.parseDouble(String.valueOf(o1).replace("MB", "").replace("%", "").trim());
            double d2 = Double.parseDouble(String.valueOf(o2).replace("MB", "").replace("%", "").trim());
            return Double.compare(d1, d2);
        } catch (Exception e) {
            return String.valueOf(o1).compareTo(String.valueOf(o2));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tương thích ngược: Các lớp cũ nếu có module khác tham chiếu
    // ─────────────────────────────────────────────────────────────────────────
    public static class MetricBar extends JPanel {
        public MetricBar(String t, Color c, String u) {}
        public void setValue(double v) {}
        public void setSubLabel(String s) {}
        public void setDanger(boolean d) {}
    }

    public static class NetGraph extends JPanel {
        public void addPoint(float u, float d) {}
    }
}
