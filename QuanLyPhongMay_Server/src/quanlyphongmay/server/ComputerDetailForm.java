package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/**
 * ComputerDetailForm - Giao diện Chi tiết máy Client chuẩn DirectX Diagnostic Tool (DxDiag)
 * Tái tạo 100% giao diện DxDiag theo Dark Theme của QuanLyPhongMay.
 * Hỗ trợ cập nhật Real-time thông số tĩnh và động mỗi 2 giây.
 * 5 Tabs:
 *   1. Hệ thống chung (System Information)
 *   2. Đồ họa & Màn hình (Display - Hỗ trợ Multi-GPU: Card tích hợp & Card rời)
 *   3. Âm thanh (Sound Devices)
 *   4. Thiết bị Ngoại vi (Input - Chuột, Bàn phím USB, Giám sát chống rút trộm thiết bị)
 *   5. Ổ đĩa & Lưu trữ (Physical Disks & Partitions)
 */
public class ComputerDetailForm extends JFrame {

    private Consumer<String> commandSender;
    private String clientName = "Chưa có dữ liệu";
    private String clientIP = "Chưa có dữ liệu";
    private String clientStatus = "Chưa xác định";
    private String cachedMac = "";
    private final Map<String, String> cumulativeSpecs = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Header Components ───────────────────────────────────────────────────
    private final JLabel lblHeaderTitle = new JLabel("CHI TIẾT MÁY CLIENT — DXDIAG SYSTEM INFORMATION");
    private final JLabel lblHeaderSubtitle = new JLabel("—");
    private final JLabel lblStatusBadge = new JLabel("[Trực tuyến] Chưa xác định") {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = getBackground();
            if (bg == null) bg = new Color(6, 78, 59);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(new Color(16, 185, 129));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    };

    // ── Tab 1: System Information Labels ────────────────────────────────────
    private final JLabel lblValDateTime     = new JLabel("—");
    private final JLabel lblValCompName     = new JLabel("—");
    private final JLabel lblValOS           = new JLabel("—");
    private final JLabel lblValLanguage     = new JLabel("—");
    private final JLabel lblValManufacturer = new JLabel("—");
    private final JLabel lblValModel        = new JLabel("—");
    private final JLabel lblValBIOS         = new JLabel("—");
    private final JLabel lblValProcessor    = new JLabel("—");
    private final JLabel lblValMemory       = new JLabel("—");
    private final JLabel lblValPageFile     = new JLabel("—");
    private final JLabel lblValDirectX      = new JLabel("DirectX 12");
    private final JLabel lblValNetInfo      = new JLabel("—");

    // ── Tab 2: Display (Multi-GPU Sub-tabs) ──────────────────────────────────
    private final JTabbedPane tabDisplaySub = new JTabbedPane();
    private final List<GpuDisplayPanel> gpuPanels = new ArrayList<>();

    // ── Tab 3: Sound Devices ────────────────────────────────────────────────
    private final DefaultListModel<String> soundListModel = new DefaultListModel<>();
    private final JList<String> lstSound = new JList<>(soundListModel);
    private final Map<Integer, Map<String, String>> soundDataMap = new HashMap<>();
    private final JLabel lblSoundName     = new JLabel("—");
    private final JLabel lblSoundHwId     = new JLabel("—");
    private final JLabel lblSoundDefault  = new JLabel("—");
    private final JLabel lblSoundDriver   = new JLabel("—");
    private final JLabel lblSoundVersion  = new JLabel("—");
    private final JLabel lblSoundDate     = new JLabel("—");
    private final JLabel lblSoundProvider = new JLabel("—");

    // ── Tab 4: Input Devices (Mouse, Keyboard, Anti-Theft) ───────────────────
    private final JLabel lblGearBanner = new JLabel("[AN TOÀN] Đang kết nối thiết bị ngoại vi USB/HID hợp lệ.");
    private final DefaultTableModel modelInput = new DefaultTableModel(
        new Object[]{"STT", "Tên thiết bị (Device Name)", "Loại (Type)", "Trạng thái", "Vendor ID (VID)", "Product ID (PID)", "Chuẩn kết nối (Device ID)"}, 0
    ) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tblInput = new JTable(modelInput);

    // ── Tab 5: Disks & Partitions ───────────────────────────────────────────
    private final DefaultTableModel modelPhysDisk = new DefaultTableModel(
        new Object[]{"Ổ vật lý", "Model ổ cứng (SSD / NVMe)", "Dung lượng", "Phân loại", "Chuẩn giao tiếp"}, 0
    ) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tblPhysDisk = new JTable(modelPhysDisk);

    private final DefaultTableModel modelPart = new DefaultTableModel(
        new Object[]{"Phân vùng", "Định dạng (FS)", "Tổng dung lượng", "Đã dùng", "Còn trống", "Mức sử dụng (%)"}, 0
    ) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tblPart = new JTable(modelPart);

    // ── Bottom Action Buttons ───────────────────────────────────────────────
    private final JButton btnNotification = new JButton("Gửi thông báo");
    private final JButton btnChat         = new JButton("Nhắn tin (Chat)");
    private final JButton btnLock         = new JButton("Khóa máy");
    private final JButton btnUnlock       = new JButton("Mở khóa");
    private final JButton btnWakeOnLan    = new JButton("[WoL] Bật nguồn");
    private final JButton btnRefresh      = new JButton("Làm mới (Refresh)");
    private final JButton btnClose        = new JButton("Đóng");

    // Tương thích ngược tên biến cũ (để không lỗi nếu có mã cũ truy cập)
    private JLabel lblComputerName = lblValCompName;
    private JLabel lblIP           = new JLabel("—");
    private JLabel lblStatus       = lblStatusBadge;
    private JLabel lblConnectTime  = new JLabel("—");
    private JLabel lblOS           = lblValOS;
    private JLabel lblCPU          = lblValProcessor;
    private JLabel lblRAM          = lblValMemory;
    private JLabel lblDisk         = new JLabel("—");

    public ComputerDetailForm() {
        this("Chưa có dữ liệu", "Chưa có dữ liệu", "Chưa xác định");
    }

    public ComputerDetailForm(String name, String ip, String status) {
        super("Chi tiết máy Client — " + name);
        this.clientName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Chưa có dữ liệu";
        this.clientIP = (ip != null && !ip.trim().isEmpty()) ? ip.trim() : "Chưa có dữ liệu";
        this.clientStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : "Chưa xác định";

        buildUI();
        updateHeaderInfo();

        setMinimumSize(new Dimension(880, 640));
        setSize(980, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    public void setCommandSender(Consumer<String> sender) {
        this.commandSender = sender;
    }

    public void setComputerInfo(String name, String ip, String status, String connectedAt, String details) {
        if (name != null && !name.isEmpty() && !"Chưa có dữ liệu".equalsIgnoreCase(name)) this.clientName = name;
        if (ip != null && !ip.isEmpty() && !"Chưa có dữ liệu".equalsIgnoreCase(ip)) this.clientIP = ip;
        if (status != null && !status.isEmpty()) this.clientStatus = status;
        if (connectedAt != null && !connectedAt.isEmpty()) this.lblConnectTime.setText(connectedAt);

        setTitle("Chi tiết máy Client — " + this.clientName);
        lblValCompName.setText(this.clientName);
        lblValNetInfo.setText("IP: " + this.clientIP + (cachedMac.isEmpty() ? "" : "  |  MAC: " + cachedMac));
        updateHeaderInfo();

        if (details != null && !details.trim().isEmpty() && !"Chưa gửi thông tin phần cứng".equalsIgnoreCase(details)) {
            updateRealtimeSpecs(details);
        }
    }

    private void updateHeaderInfo() {
        lblHeaderSubtitle.setText("Máy: " + clientName + "  |  Địa chỉ IP: " + clientIP + (cachedMac.isEmpty() ? "" : "  |  MAC: " + cachedMac));
        lblStatusBadge.setText("[Trực tuyến] " + ("Online".equalsIgnoreCase(clientStatus) ? "Đang trực tuyến" : clientStatus));
        lblStatusBadge.setForeground("Online".equalsIgnoreCase(clientStatus) ? new Color(16, 185, 129) : UITheme.TEXT_MUTED);
    }

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(UITheme.BG_DARK);

        // 1. Header Panel
        content.add(buildHeaderPanel(), BorderLayout.NORTH);

        // 2. Center TabbedPane (DxDiag 5 Tabs)
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setBackground(UITheme.BG_SIDEBAR);
        mainTabs.setForeground(UITheme.TEXT_PRIMARY);
        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        mainTabs.addTab("Hệ thống (System)", buildSystemTab());
        mainTabs.addTab("Màn hình & Đồ họa (Display)", buildDisplayTab());
        mainTabs.addTab("Âm thanh (Sound)", buildSoundTab());
        mainTabs.addTab("Thiết bị Ngoại vi (Input)", buildInputTab());
        mainTabs.addTab("Ổ đĩa & Lưu trữ (Disks)", buildDisksTab());

        content.add(mainTabs, BorderLayout.CENTER);

        // 3. Bottom Action Bar
        content.add(buildBottomActionBar(), BorderLayout.SOUTH);

        setContentPane(content);
    }

    // ── 1. Header Panel ───────────────────────────────────────────────────────
    private JPanel buildHeaderPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(new Color(17, 24, 39)); // Slate 900
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(31, 41, 55)),
            BorderFactory.createEmptyBorder(12, 18, 12, 18)
        ));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 4));
        left.setOpaque(false);
        lblHeaderTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblHeaderTitle.setForeground(UITheme.ACCENT);
        lblHeaderSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblHeaderSubtitle.setForeground(UITheme.TEXT_MUTED);
        left.add(lblHeaderTitle);
        left.add(lblHeaderSubtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        right.setOpaque(false);
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusBadge.setForeground(new Color(16, 185, 129));
        lblStatusBadge.setBackground(new Color(6, 78, 59));
        lblStatusBadge.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        lblStatusBadge.setOpaque(false);
        right.add(lblStatusBadge);

        p.add(left, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ── Tab 1: System Information (Chuẩn Tab System của DxDiag) ───────────────
    private JPanel buildSystemTab() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(15, 18, 15, 18));

        JPanel card = createTitledCard("Thông tin Hệ thống (System Information)");
        card.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 12, 6, 12);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addSystemRow(card, g, row++, "Current Date/Time:", lblValDateTime);
        addSystemRow(card, g, row++, "Computer Name:", lblValCompName);
        addSystemRow(card, g, row++, "Operating System:", lblValOS);
        addSystemRow(card, g, row++, "Language:", lblValLanguage);
        addSystemRow(card, g, row++, "System Manufacturer:", lblValManufacturer);
        addSystemRow(card, g, row++, "System Model:", lblValModel);
        addSystemRow(card, g, row++, "BIOS:", lblValBIOS);
        addSystemRow(card, g, row++, "Processor:", lblValProcessor);
        addSystemRow(card, g, row++, "Memory:", lblValMemory);
        addSystemRow(card, g, row++, "Page file:", lblValPageFile);
        addSystemRow(card, g, row++, "DirectX Version:", lblValDirectX);
        addSystemRow(card, g, row++, "Network (IP / MAC):", lblValNetInfo);

        JScrollPane sp = new JScrollPane(card);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private void addSystemRow(JPanel parent, GridBagConstraints g, int row, String label, JLabel valLabel) {
        g.gridx = 0; g.gridy = row; g.weightx = 0.0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(new Color(156, 163, 175)); // Muted
        lbl.setPreferredSize(new Dimension(170, 22));
        parent.add(lbl, g);

        g.gridx = 1; g.weightx = 1.0;
        valLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        valLabel.setForeground(UITheme.TEXT_PRIMARY);
        parent.add(valLabel, g);
    }

    // ── Tab 2: Display & Multi-GPU ───────────────────────────────────────────
    private JPanel buildDisplayTab() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        tabDisplaySub.setBackground(UITheme.BG_CARD);
        tabDisplaySub.setForeground(UITheme.TEXT_PRIMARY);
        tabDisplaySub.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Khởi tạo sẵn GPU 0 và GPU 1
        GpuDisplayPanel gpu0 = new GpuDisplayPanel("Display 1");
        GpuDisplayPanel gpu1 = new GpuDisplayPanel("Display 2");
        gpuPanels.add(gpu0);
        gpuPanels.add(gpu1);
        tabDisplaySub.addTab("Display 1", gpu0);

        p.add(tabDisplaySub, BorderLayout.CENTER);
        return p;
    }

    // Class đại diện cho một màn hình GPU trong Tab Display
    private static class GpuDisplayPanel extends JPanel {
        final JLabel lblName = new JLabel("—");
        final JLabel lblMfg  = new JLabel("—");
        final JLabel lblChip = new JLabel("—");
        final JLabel lblDAC  = new JLabel("n/a");
        final JLabel lblDevType = new JLabel("Full Display Device");
        final JLabel lblTotalMem = new JLabel("—");
        final JLabel lblVram = new JLabel("—");
        final JLabel lblSharedMem = new JLabel("—");

        final JLabel lblDriver = new JLabel("—");
        final JLabel lblVersion = new JLabel("—");
        final JLabel lblDate = new JLabel("—");
        final JLabel lblWHQL = new JLabel("Yes");
        final JLabel lblDDI = new JLabel("12");
        final JLabel lblFeatureLevels = new JLabel("11_0, 10_1, 10_0, 9_3, 9_2, 9_1");
        final JLabel lblDriverModel = new JLabel("WDDM 1.2");

        final JLabel lblDDraw = new JLabel("Enabled");
        final JLabel lblD3D = new JLabel("Enabled");
        final JLabel lblAGP = new JLabel("Enabled");
        final JLabel lblDx12Ult = new JLabel("Not Supported");

        GpuDisplayPanel(String title) {
            setLayout(new GridLayout(1, 3, 10, 10));
            setBackground(UITheme.BG_DARK);
            setBorder(new EmptyBorder(8, 8, 8, 8));

            // Card 1: Device
            JPanel pDevice = createCard("Thiết bị (Device)");
            pDevice.setLayout(new GridLayout(8, 2, 6, 6));
            addProp(pDevice, "Name:", lblName);
            addProp(pDevice, "Manufacturer:", lblMfg);
            addProp(pDevice, "Chip Type:", lblChip);
            addProp(pDevice, "DAC Type:", lblDAC);
            addProp(pDevice, "Device Type:", lblDevType);
            addProp(pDevice, "Approx. Total Memory:", lblTotalMem);
            addProp(pDevice, "Display Memory (VRAM):", lblVram);
            addProp(pDevice, "Shared Memory:", lblSharedMem);
            add(pDevice);

            // Card 2: Drivers
            JPanel pDrivers = createCard("Trình điều khiển (Drivers)");
            pDrivers.setLayout(new GridLayout(7, 2, 6, 6));
            addProp(pDrivers, "Main Driver:", lblDriver);
            addProp(pDrivers, "Version:", lblVersion);
            addProp(pDrivers, "Date:", lblDate);
            addProp(pDrivers, "WHQL Logo'd:", lblWHQL);
            addProp(pDrivers, "Direct3D DDI:", lblDDI);
            addProp(pDrivers, "Feature Levels:", lblFeatureLevels);
            addProp(pDrivers, "Driver Model:", lblDriverModel);
            add(pDrivers);

            // Card 3: DirectX Features
            JPanel pFeatures = createCard("Tính năng DirectX (DirectX Features)");
            pFeatures.setLayout(new GridLayout(4, 2, 6, 6));
            lblDDraw.setForeground(new Color(16, 185, 129));
            lblD3D.setForeground(new Color(16, 185, 129));
            lblAGP.setForeground(new Color(16, 185, 129));
            lblDx12Ult.setForeground(new Color(16, 185, 129));
            addProp(pFeatures, "DirectDraw Acceleration:", lblDDraw);
            addProp(pFeatures, "Direct3D Acceleration:", lblD3D);
            addProp(pFeatures, "AGP Texture Acceleration:", lblAGP);
            addProp(pFeatures, "DirectX 12 Ultimate:", lblDx12Ult);
            add(pFeatures);
        }

        private static void addProp(JPanel p, String label, JLabel val) {
            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.BOLD, 11));
            l.setForeground(UITheme.TEXT_MUTED);
            val.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            val.setForeground(UITheme.TEXT_PRIMARY);
            p.add(l);
            p.add(val);
        }

        private static JPanel createCard(String title) {
            JPanel p = new JPanel();
            p.setBackground(UITheme.BG_CARD);
            p.setBorder(BorderFactory.createCompoundBorder(
                new TitledBorder(new LineBorder(UITheme.BORDER, 1, true), title, TitledBorder.LEFT, TitledBorder.TOP,
                    new Font("Segoe UI", Font.BOLD, 12), UITheme.ACCENT),
                new EmptyBorder(8, 10, 8, 10)
            ));
            return p;
        }
    }

    // ── Tab 3: Sound Devices ────────────────────────────────────────────────
    private JPanel buildSoundTab() {
        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(12, 15, 12, 15));

        // Cột trái: Danh sách thiết bị âm thanh
        lstSound.setBackground(UITheme.BG_CARD);
        lstSound.setForeground(UITheme.TEXT_PRIMARY);
        lstSound.setSelectionBackground(new Color(30, 58, 138));
        lstSound.setSelectionForeground(Color.WHITE);
        lstSound.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lstSound.setFixedCellHeight(30);

        JScrollPane spList = new JScrollPane(lstSound);
        spList.setPreferredSize(new Dimension(320, 0));
        spList.setBorder(BorderFactory.createCompoundBorder(
            new TitledBorder(new LineBorder(UITheme.BORDER, 1, true), "Danh sách Thiết bị Âm thanh", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), UITheme.ACCENT),
            new EmptyBorder(4, 4, 4, 4)
        ));

        // Cột phải: Chi tiết thiết bị & Driver
        JPanel pDetail = new JPanel(new GridLayout(2, 1, 10, 10));
        pDetail.setOpaque(false);

        JPanel pDevice = createTitledCard("Thiết bị âm thanh (Device)");
        pDevice.setLayout(new GridLayout(3, 2, 8, 8));
        addPropRow(pDevice, "Name:", lblSoundName);
        addPropRow(pDevice, "Hardware ID:", lblSoundHwId);
        addPropRow(pDevice, "Default Device:", lblSoundDefault);
        pDetail.add(pDevice);

        JPanel pDriver = createTitledCard("Trình điều khiển âm thanh (Drivers)");
        pDriver.setLayout(new GridLayout(4, 2, 8, 8));
        addPropRow(pDriver, "Driver Name:", lblSoundDriver);
        addPropRow(pDriver, "Version:", lblSoundVersion);
        addPropRow(pDriver, "Date:", lblSoundDate);
        addPropRow(pDriver, "Provider:", lblSoundProvider);
        pDetail.add(pDriver);

        lstSound.addListSelectionListener(e -> {
            int sel = lstSound.getSelectedIndex();
            if (sel >= 0 && soundDataMap.containsKey(sel)) {
                Map<String, String> d = soundDataMap.get(sel);
                lblSoundName.setText(d.getOrDefault("name", "—"));
                lblSoundHwId.setText(d.getOrDefault("hwid", "—"));
                lblSoundDefault.setText(d.getOrDefault("default", "No"));
                lblSoundDriver.setText(d.getOrDefault("driver", "—"));
                lblSoundVersion.setText(d.getOrDefault("version", "—"));
                lblSoundDate.setText(d.getOrDefault("date", "—"));
                lblSoundProvider.setText(d.getOrDefault("mfg", "—"));
            }
        });

        p.add(spList, BorderLayout.WEST);
        p.add(pDetail, BorderLayout.CENTER);
        return p;
    }

    // ── Tab 4: Input Devices (Chuột, Bàn phím USB, Chống rút trộm) ───────────
    private JPanel buildInputTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(12, 15, 12, 15));

        // Banner chống rút thiết bị
        lblGearBanner.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblGearBanner.setForeground(new Color(52, 211, 153));
        lblGearBanner.setBackground(new Color(6, 78, 59));
        lblGearBanner.setOpaque(true);
        lblGearBanner.setBorder(new EmptyBorder(8, 12, 8, 12));
        p.add(lblGearBanner, BorderLayout.NORTH);

        // Bảng thiết bị DirectInput
        UITheme.styleTable(tblInput);
        tblInput.getColumnModel().getColumn(0).setPreferredWidth(45);
        tblInput.getColumnModel().getColumn(0).setMaxWidth(60);
        tblInput.getColumnModel().getColumn(1).setPreferredWidth(200);
        tblInput.getColumnModel().getColumn(2).setPreferredWidth(120);
        tblInput.getColumnModel().getColumn(3).setPreferredWidth(90);
        tblInput.getColumnModel().getColumn(4).setPreferredWidth(100);
        tblInput.getColumnModel().getColumn(5).setPreferredWidth(100);
        tblInput.getColumnModel().getColumn(6).setPreferredWidth(260);

        tblInput.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String val = (value != null) ? value.toString() : "";
                if (col == 0) setHorizontalAlignment(CENTER);
                else if (col == 3) {
                    setHorizontalAlignment(CENTER);
                    if ("Attached".equalsIgnoreCase(val)) {
                        c.setForeground(new Color(16, 185, 129)); // Xanh lá
                    } else {
                        c.setForeground(new Color(239, 68, 68)); // Đỏ cảnh báo
                    }
                } else if (col == 4 || col == 5) {
                    setHorizontalAlignment(CENTER);
                    c.setForeground(UITheme.ACCENT);
                } else {
                    setHorizontalAlignment(LEFT);
                    c.setForeground(UITheme.TEXT_PRIMARY);
                }
                return c;
            }
        });

        JScrollPane sp = new JScrollPane(tblInput);
        UITheme.styleScrollPane(sp);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    // ── Tab 5: Disks & Partitions ───────────────────────────────────────────
    private JPanel buildDisksTab() {
        JPanel p = new JPanel(new GridLayout(2, 1, 10, 10));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(12, 15, 12, 15));

        // 1. Physical Disks
        JPanel pPhys = createTitledCard("Ổ cứng Vật lý (Physical Disks - SSD / NVMe / HDD)");
        pPhys.setLayout(new BorderLayout());
        UITheme.styleTable(tblPhysDisk);
        JScrollPane spPhys = new JScrollPane(tblPhysDisk);
        UITheme.styleScrollPane(spPhys);
        pPhys.add(spPhys, BorderLayout.CENTER);
        p.add(pPhys);

        // 2. Partitions
        JPanel pPart = createTitledCard("Phân vùng Logic & Dung lượng Trống Real-time (Logical Partitions)");
        pPart.setLayout(new BorderLayout());
        UITheme.styleTable(tblPart);
        tblPart.getColumnModel().getColumn(5).setCellRenderer(new PartitionUsageBarRenderer());
        JScrollPane spPart = new JScrollPane(tblPart);
        UITheme.styleScrollPane(spPart);
        pPart.add(spPart, BorderLayout.CENTER);
        p.add(pPart);

        return p;
    }

    // Renderer vẽ thanh mức dung lượng ổ đĩa (%)
    private static class PartitionUsageBarRenderer extends JProgressBar implements TableCellRenderer {
        PartitionUsageBarRenderer() {
            super(0, 100);
            setStringPainted(true);
            setFont(new Font("Segoe UI", Font.BOLD, 11));
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int pct = 0;
            try {
                pct = (int) Math.round(Double.parseDouble(String.valueOf(value).replace("%", "").trim()));
                pct = Math.max(0, Math.min(100, pct));
            } catch (Exception ignored) {}
            setValue(pct);
            setString(pct + "%");
            if (pct >= 90) setForeground(new Color(239, 68, 68)); // Đỏ
            else if (pct >= 75) setForeground(new Color(245, 158, 11)); // Vàng cam
            else setForeground(new Color(16, 185, 129)); // Xanh lá
            setBackground(UITheme.BG_CARD);
            return this;
        }
    }

    // ── 3. Bottom Action Bar ────────────────────────────────────────────────
    private JPanel buildBottomActionBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        p.setBackground(new Color(17, 24, 39));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER));

        UITheme.styleButtonWarning(btnNotification);
        UITheme.styleButtonPrimary(btnChat);
        UITheme.styleButtonDanger(btnLock);
        UITheme.styleButtonSuccess(btnUnlock);
        UITheme.styleButtonSuccess(btnWakeOnLan);
        UITheme.styleButtonAccent(btnRefresh);
        UITheme.styleButtonSecondary(btnClose);

        btnNotification.addActionListener(e -> sendNotificationDialog());
        btnChat.addActionListener(e -> sendChatDialog());
        btnLock.addActionListener(e -> confirmLock());
        btnUnlock.addActionListener(e -> confirmUnlock());
        btnWakeOnLan.addActionListener(e -> sendWakeOnLan());
        btnRefresh.addActionListener(e -> {
            if (commandSender != null) {
                commandSender.accept("REQ_FULL_SPECS");
                commandSender.accept("GET_HARDWARE_DETAIL");
                lblStatusBadge.setText("Đang yêu cầu làm mới dữ liệu...");
            }
        });
        btnClose.addActionListener(e -> dispose());

        p.add(btnNotification);
        p.add(btnChat);
        p.add(btnLock);
        p.add(btnUnlock);
        p.add(btnWakeOnLan);
        p.add(btnRefresh);
        p.add(btnClose);
        return p;
    }

        public void setClientOffline() {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText("[Ngoại tuyến] Đã ngắt kết nối");
            lblStatusBadge.setForeground(new java.awt.Color(239, 68, 68));
            lblStatusBadge.repaint();
        });
    }

    // ── NẠP VÀ CẬP NHẬT REAL-TIME SPECS ─────────────────────────────────────
    public void updateRealtimeSpecs(String payload) {
        if (payload == null || payload.trim().isEmpty()) return;

        String delimiter = payload.contains("##SEP##") ? "##SEP##" : (payload.contains(";;") ? ";;" : "\\|");
        String[] pairs = payload.split(delimiter);
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                String k = parts[0].trim();
                String v = parts[1].trim();
                if (!v.isEmpty()) {
                    cumulativeSpecs.put(k, v);
                    String kLower = k.toLowerCase();
                    cumulativeSpecs.put(kLower, v);
                    if (kLower.equals("os")) cumulativeSpecs.put("os_name", v);
                    if (kLower.equals("cpu")) cumulativeSpecs.put("cpu_name", v);
                    if (kLower.equals("ram")) cumulativeSpecs.put("ram_total_mb", v);
                    if (kLower.equals("disk")) cumulativeSpecs.put("disk0_model", v);
                    if (kLower.equals("mac")) cumulativeSpecs.put("mac_address", v);
                }
            }
        }

        SwingUtilities.invokeLater(() -> updateUIFromMap(cumulativeSpecs));
    }

    private static String getVal(Map<String, String> d, String defaultVal, String... keys) {
        if (d == null) return defaultVal;
        for (String k : keys) {
            String v = d.get(k);
            if (v != null && !v.trim().isEmpty() && !v.equalsIgnoreCase("N/A") && !v.equals("—")) {
                return v.trim();
            }
            v = d.get(k.toLowerCase());
            if (v != null && !v.trim().isEmpty() && !v.equalsIgnoreCase("N/A") && !v.equals("—")) {
                return v.trim();
            }
        }
        return defaultVal;
    }

    private void updateUIFromMap(Map<String, String> d) {
        String nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lblStatusBadge.setText("[Trực tuyến] Cập nhật: " + nowTime);
        lblStatusBadge.setForeground(new Color(16, 185, 129));

        if (d.containsKey("mac_address")) setCachedMac(d.get("mac_address"));
        if (d.containsKey("mac")) setCachedMac(d.get("mac"));
        if (d.containsKey("computer_name")) clientName = d.get("computer_name");
        updateHeaderInfo();

        // 1. Tab Hệ thống (DxDiag System Specs)
        String defaultDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, h:mm:ss a", Locale.US));
        lblValDateTime.setText(getVal(d, defaultDateTime, "date_time", "datetime", "date"));
        lblValCompName.setText(getVal(d, clientName, "computer_name", "computername", "name"));

        String rawOs = getVal(d, "", "os_name", "os", "operatingsystem");
        if (rawOs.isEmpty()) {
            rawOs = System.getProperty("os.name", "Windows 11") + " 64-bit";
        }
        if (rawOs.startsWith("Microsoft ")) {
            rawOs = rawOs.substring("Microsoft ".length()).trim();
        }
        String build = getVal(d, "", "os_build", "build");
        String osVer = getVal(d, "10.0", "os_version", "version");
        if (!rawOs.contains("Build") && !build.isEmpty()) {
            rawOs += " (" + osVer + ", Build " + build + ")";
        }
        lblValOS.setText(rawOs);

        String curLang = lblValLanguage.getText();
        if (curLang == null || curLang.isEmpty() || curLang.equals("—")) curLang = "Đang thu thập...";
        lblValLanguage.setText(getVal(d, curLang, "language", "lang", "os_language"));

        String curMfg = lblValManufacturer.getText();
        if (curMfg == null || curMfg.isEmpty() || curMfg.equals("—")) curMfg = "Đang thu thập...";
        lblValManufacturer.setText(getVal(d, curMfg, "manufacturer", "system_manufacturer", "maker", "systemmanufacturer"));

        String curModel = lblValModel.getText();
        if (curModel == null || curModel.isEmpty() || curModel.equals("—")) curModel = "Đang thu thập...";
        lblValModel.setText(getVal(d, curModel, "model", "system_model", "systemmodel"));

        String curBios = lblValBIOS.getText();
        if (curBios == null || curBios.isEmpty() || curBios.equals("—")) curBios = "Đang thu thập...";
        lblValBIOS.setText(getVal(d, curBios, "bios_version", "bios", "smbios_version", "smbiosbiosversion"));

        String cpu = getVal(d, "Đang thu thập...", "processor", "cpu_name", "cpuName", "cpu");
        String cpuLogical = getVal(d, "", "cpu_logical", "logical", "cores");
        String cpuSpeed = getVal(d, "", "cpu_speed", "baseSpeed", "speed");
        String cpuStr = cpu;
        if (!"Đang thu thập...".equals(cpu)) {
            if (!cpuStr.contains("CPUs") && !cpuLogical.isEmpty()) {
                cpuStr += " (" + cpuLogical + " CPUs)";
            }
            if (!cpuStr.contains("~") && !cpuSpeed.isEmpty()) {
                if (!cpuSpeed.toLowerCase().contains("ghz") && !cpuSpeed.toLowerCase().contains("mhz")) cpuSpeed += " GHz";
                cpuStr += ", ~" + cpuSpeed;
            }
        }
        lblValProcessor.setText(cpuStr);

        String ramTotal = getVal(d, "", "memory", "ram", "ram_total_mb", "ramTotal");
        if (ramTotal.isEmpty() || "—".equals(ramTotal)) {
            if (lblValMemory.getText().isEmpty() || "—".equals(lblValMemory.getText())) {
                lblValMemory.setText("Đang thu thập...");
            }
        } else if (!ramTotal.toLowerCase().contains("ram") && !ramTotal.toLowerCase().contains("gb")) {
            lblValMemory.setText(ramTotal + "MB RAM");
        } else {
            lblValMemory.setText(ramTotal);
        }

        String curPage = lblValPageFile.getText();
        if (curPage == null || curPage.isEmpty() || curPage.equals("—")) curPage = "Đang thu thập...";
        String pVal = getVal(d, curPage, "pagefile", "pageFile");
        if ("Đang thu thập...".equals(pVal)) {
            String pUsed = getVal(d, "", "pagefile_used_mb");
            String pAvail = getVal(d, "", "pagefile_avail_mb");
            if (!pUsed.isEmpty() && !pAvail.isEmpty()) {
                pVal = pUsed + "MB used, " + pAvail + "MB available";
            }
        }
        lblValPageFile.setText(pVal);

        lblValDirectX.setText(getVal(d, "DirectX 12", "directx_ver", "directx"));
        lblValNetInfo.setText("IP: " + clientIP + (cachedMac.isEmpty() ? "" : "  |  MAC: " + cachedMac));

        // 2. Tab Đồ họa (DxDiag Display Tab)
        int gpuCount = 1;
        String gcStr = getVal(d, "", "gpu_count", "gpuCount");
        if (!gcStr.isEmpty()) {
            try { gpuCount = Integer.parseInt(gcStr); } catch (Exception ignored) {}
        }
        String g0Check = getVal(d, "", "gpu0_name", "gpu0Name", "gpu0");
        String g1Check = getVal(d, "", "gpu1_name", "gpu1Name", "gpu1");
        if (gpuCount <= 0) {
            if (!g0Check.isEmpty() && !"N/A".equalsIgnoreCase(g0Check)) gpuCount = 1;
            if (!g1Check.isEmpty() && !"N/A".equalsIgnoreCase(g1Check)) gpuCount = 2;
        }
        if (gpuCount < 1) gpuCount = 1;

        while (gpuPanels.size() < gpuCount) {
            gpuPanels.add(new GpuDisplayPanel("Display " + (gpuPanels.size() + 1)));
        }
        while (tabDisplaySub.getTabCount() > gpuCount) {
            tabDisplaySub.removeTabAt(tabDisplaySub.getTabCount() - 1);
        }
        while (tabDisplaySub.getTabCount() < gpuCount) {
            int idx = tabDisplaySub.getTabCount();
            tabDisplaySub.addTab("Display " + (idx + 1), gpuPanels.get(idx));
        }

        for (int i = 0; i < gpuCount; i++) {
            GpuDisplayPanel gp = gpuPanels.get(i);
            String prefix = "gpu" + i + "_";
            String pCamel = "gpu" + i;

            String name = getVal(d, "Display Adapter", prefix + "name", pCamel + "Name", "gpu" + i);
            tabDisplaySub.setTitleAt(i, (gpuCount > 1 ? ("Display " + (i + 1) + ": ") : "") + name);
            gp.lblName.setText(name);

            gp.lblMfg.setText(getVal(d, "—", prefix + "mfg", pCamel + "Mfg", prefix + "maker", pCamel + "Maker"));
            gp.lblChip.setText(getVal(d, name, prefix + "chip", pCamel + "Chip"));
            gp.lblDAC.setText(getVal(d, "Internal", prefix + "dac", pCamel + "Dac"));
            gp.lblDevType.setText("Full Display Device");

            String vram = getVal(d, "0", prefix + "vram", pCamel + "Vram");
            String shared = getVal(d, "0", prefix + "shared", pCamel + "Shared");
            String total = getVal(d, "0", prefix + "total", pCamel + "Total");
            gp.lblVram.setText(vram + " MB");
            gp.lblSharedMem.setText(shared + " MB");
            gp.lblTotalMem.setText(total + " MB");

            gp.lblDriver.setText(getVal(d, "DisplayDriver.dll", prefix + "driver", pCamel + "Driver"));
            gp.lblVersion.setText(getVal(d, "—", prefix + "version", pCamel + "Version"));
            gp.lblDate.setText(getVal(d, "—", prefix + "date", pCamel + "Date"));
            gp.lblWHQL.setText("Yes");
            gp.lblDDI.setText(getVal(d, "12", prefix + "ddi", pCamel + "Ddi", pCamel + "DirectX", "directx_ver"));
            gp.lblFeatureLevels.setText(getVal(d, "12_1, 12_0, 11_1, 11_0", prefix + "feature_levels", pCamel + "FeatureLevels"));
            gp.lblDriverModel.setText(getVal(d, "WDDM 3.1", prefix + "driver_model", pCamel + "DriverModel"));
            gp.lblDDraw.setText("Enabled");
            gp.lblD3D.setText("Enabled");
            gp.lblAGP.setText("Enabled");
            gp.lblDx12Ult.setText(getVal(d, "Không hỗ trợ (Not Supported)", prefix + "dx12_ult", pCamel + "Dx12Ult"));
        }

        // 3. Tab Âm thanh (DxDiag Sound Tab)
        int soundCount = 0;
        try { soundCount = Integer.parseInt(d.getOrDefault("sound_count", "0")); } catch (Exception ignored) {}
        if (soundCount == 0) soundCount = 1;
        soundListModel.clear();
        soundDataMap.clear();
        for (int i = 0; i < soundCount; i++) {
            String prefix = "sound" + i + "_";
            String name = d.getOrDefault(prefix + "name", "Audio Device");
            if (!name.startsWith("Speakers") && !name.contains("(")) {
                name = "Speakers (" + name + ")";
            }
            soundListModel.addElement((i + 1) + ". " + name);
            Map<String, String> item = new HashMap<>();
            item.put("name", name);
            item.put("hwid", d.getOrDefault(prefix + "hwid", "N/A"));
            item.put("default", d.getOrDefault(prefix + "default", "Yes"));
            item.put("driver", d.getOrDefault(prefix + "driver", "—"));
            item.put("version", d.getOrDefault(prefix + "version", "—"));
            item.put("date", d.getOrDefault(prefix + "date", "—"));
            item.put("mfg", d.getOrDefault(prefix + "mfg", "—"));
            soundDataMap.put(i, item);
        }
        if (lstSound.getSelectedIndex() < 0) lstSound.setSelectedIndex(0);

// 4. Tab Thiết bị Ngoại vi
        int inputCount = 0;
        try { inputCount = Integer.parseInt(d.getOrDefault("input_count", "0")); } catch (Exception ignored) {}
        if (inputCount > 0) {
            modelInput.setRowCount(0);
            boolean hasGearIssue = false;
            for (int i = 0; i < inputCount; i++) {
                String prefix = "input" + i + "_";
                String name   = d.getOrDefault(prefix + "name", "Input Device");
                String type   = d.getOrDefault(prefix + "type", "USB Device");
                String status = d.getOrDefault(prefix + "status", "Attached");
                String vid    = d.getOrDefault(prefix + "vid", "N/A");
                String pid    = d.getOrDefault(prefix + "pid", "N/A");
                String devid  = d.getOrDefault(prefix + "devid", "N/A");

                if (!"Attached".equalsIgnoreCase(status)) hasGearIssue = true;
                modelInput.addRow(new Object[]{i + 1, name, type, status, vid, pid, devid});
            }

            if (hasGearIssue) {
                lblGearBanner.setText("[CẢNH BÁO MẤT CẮP] Phát hiện rút hoặc ngắt kết nối thiết bị chuột / bàn phím!");
                lblGearBanner.setBackground(new Color(127, 29, 29)); // Red 900
                lblGearBanner.setForeground(new Color(254, 202, 202));
            } else {
                lblGearBanner.setText("[AN TOÀN] Đang kết nối " + inputCount + " thiết bị ngoại vi USB/HID hợp lệ.");
                lblGearBanner.setBackground(new Color(6, 78, 59));
                lblGearBanner.setForeground(new Color(52, 211, 153));
            }
        }

        // 5. Tab Ổ đĩa & Phân vùng
        int diskCount = 0;
        try { diskCount = Integer.parseInt(d.getOrDefault("disk_count", "0")); } catch (Exception ignored) {}
        if (diskCount > 0) {
            modelPhysDisk.setRowCount(0);
            for (int i = 0; i < diskCount; i++) {
                String prefix = "disk" + i + "_";
                modelPhysDisk.addRow(new Object[]{
                    "Disk " + i,
                    d.getOrDefault(prefix + "model", "N/A"),
                    d.getOrDefault(prefix + "size", "—"),
                    d.getOrDefault(prefix + "type", "—"),
                    d.getOrDefault(prefix + "interface", "—")
                });
            }
        }

        int partCount = 0;
        try { partCount = Integer.parseInt(d.getOrDefault("part_count", "0")); } catch (Exception ignored) {}
        if (partCount > 0) {
            modelPart.setRowCount(0);
            for (int i = 0; i < partCount; i++) {
                String prefix = "part" + i + "_";
                String letter = d.getOrDefault(prefix + "letter", "C:\\");
                String fs     = d.getOrDefault(prefix + "fs", "NTFS");
                String totalVal = d.get(prefix + "total_gb");
                String total  = (totalVal != null && !totalVal.isEmpty()) ? totalVal + " GB" : "—";
                String usedVal = d.get(prefix + "used_gb");
                String used   = (usedVal != null && !usedVal.isEmpty()) ? usedVal + " GB" : "—";
                String freeVal = d.get(prefix + "free_gb");
                String free   = (freeVal != null && !freeVal.isEmpty()) ? freeVal + " GB" : "—";
                double parsedPct = 0.0;
                try {
                    parsedPct = Double.parseDouble(d.getOrDefault(prefix + "pct", "0.0").replace("%", "").trim());
                    parsedPct = Math.max(0.0, Math.min(100.0, parsedPct));
                } catch (Exception ignored) {}
                String pct = String.format(Locale.US, "%.1f%%", parsedPct);
                modelPart.addRow(new Object[]{letter, fs, total, used, free, pct});
            }
        }
    }

    // ── Helper UI Methods ───────────────────────────────────────────────────
    private static JPanel createTitledCard(String title) {
        JPanel p = new JPanel();
        p.setBackground(UITheme.BG_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            new TitledBorder(new LineBorder(UITheme.BORDER, 1, true), title, TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12), UITheme.ACCENT),
            new EmptyBorder(10, 14, 10, 14)
        ));
        return p;
    }

    private static void addPropRow(JPanel p, String label, JLabel val) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(UITheme.TEXT_MUTED);
        val.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        val.setForeground(UITheme.TEXT_PRIMARY);
        p.add(l);
        p.add(val);
    }

    // ── Actions ─────────────────────────────────────────────────────────────
    private void sendNotificationDialog() {
        String msg = JOptionPane.showInputDialog(this, "Nhập thông báo gửi tới máy " + clientName + ":", "Gửi thông báo", JOptionPane.WARNING_MESSAGE);
        if (msg != null && !msg.trim().isEmpty() && commandSender != null) {
            commandSender.accept("NOTICE|[!] CẢNH BÁO TỪ SERVER: " + msg.trim());
        }
    }

    private void sendChatDialog() {
        String msg = JOptionPane.showInputDialog(this, "Nhập tin nhắn đến máy " + clientName + ":", "Nhắn tin", JOptionPane.QUESTION_MESSAGE);
        if (msg != null && !msg.trim().isEmpty() && commandSender != null) {
            commandSender.accept("CHAT|Server|" + msg.trim());
        }
    }

    private void confirmLock() {
        int r = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn KHÓA máy " + clientName + "?", "Xác nhận khóa máy", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION && commandSender != null) {
            commandSender.accept("LOCK");
        }
    }

    private void confirmUnlock() {
        if (commandSender != null) {
            commandSender.accept("UNLOCK");
            JOptionPane.showMessageDialog(this, "Đã gửi lệnh MỞ KHÓA tới máy " + clientName);
        }
    }

    public void setCachedMac(String mac) {
        if (mac != null && !mac.trim().isEmpty() && !"00:00:00:00:00:00".equals(mac) && !"N/A".equalsIgnoreCase(mac)) {
            this.cachedMac = mac.trim().replace('-', ':').toUpperCase(java.util.Locale.US);
            updateHeaderInfo();
            lblValNetInfo.setText("IP: " + this.clientIP + "  |  MAC: " + this.cachedMac);
        }
    }

    private void sendWakeOnLan() {
        String mac = cachedMac;
        if (mac == null || mac.isEmpty() || "00:00:00:00:00:00".equals(mac) || "N/A".equalsIgnoreCase(mac)) {
            if (clientIP != null && !clientIP.isEmpty() && !"Chưa có dữ liệu".equalsIgnoreCase(clientIP)) {
                String arpMac = ServerForm.getMacFromArp(clientIP);
                if (arpMac != null && !arpMac.isEmpty()) {
                    mac = arpMac;
                    setCachedMac(mac);
                }
            }
        }
        if (mac == null || mac.isEmpty() || "00:00:00:00:00:00".equals(mac) || "N/A".equalsIgnoreCase(mac)) {
            mac = JOptionPane.showInputDialog(this, "Nhập địa chỉ MAC của máy để gửi gói Magic Packet (WoL):", "Wake-on-LAN", JOptionPane.QUESTION_MESSAGE);
        }
        if (mac != null && !mac.trim().isEmpty()) {
            mac = mac.trim().replace('-', ':').toUpperCase(java.util.Locale.US);
            boolean ok = quanlyphongmay.server.WakeOnLan.sendMagicPacket(mac);
            if (ok) {
                setCachedMac(mac);
                JOptionPane.showMessageDialog(
                    this,
                    "Đã tự động lấy địa chỉ MAC và gửi thành công gói tin Magic Packet (WoL)!\n\n"
                    + "• Tên máy trạm: " + clientName + "\n"
                    + "• Địa chỉ MAC: " + mac + "\n\n"
                    + "Lưu ý: Card mạng máy trạm cần bật tính năng Wake-on-LAN trong BIOS/Windows.",
                    "Đã gửi Wake-on-LAN",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(this, "Gửi tín hiệu Wake-on-LAN thất bại tới MAC: " + mac, "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
