package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * TaskMgrPerformancePanel - Tái tạo chính xác 100% bố cục, màu sắc và thông số chi tiết
 * của tab Performance trong Windows 11 Task Manager (Master/Detail Layout).
 *
 * Cột trái: 7 Card mini (CPU, Memory, Disk, Ethernet, Wi-Fi, GPU 0, GPU 1).
 * Khung phải: CardLayout hiển thị màn hình chi tiết tương ứng.
 * Dưới cùng: Ngăn kéo Nhật ký Cảnh báo thu gọn/mở rộng mượt mà.
 */
public class TaskMgrPerformancePanel extends JPanel {
    public static String formatSpeed(double kbps) {
        if (kbps >= 1000.0) {
            return String.format(Locale.US, "%.1f Mbps", kbps / 1000.0);
        } else {
            return String.format(Locale.US, "%.1f Kbps", kbps);
        }
    }

    public static String formatDiskRate(double kbps) {
        if (kbps >= 1024.0) {
            return String.format(Locale.US, "%.1f MB/s", kbps / 1024.0);
        } else {
            return String.format(Locale.US, "%.1f KB/s", kbps);
        }
    }

    

    // ── Cột trái: 7 Sidebar Cards ─────────────────────────────────────────────
    private JPanel sidebarList;
    private final TaskMgrSidebarCard cardCPU;
    private final TaskMgrSidebarCard cardMemory;
    private final TaskMgrSidebarCard cardDisk;
    private final TaskMgrSidebarCard cardEthernet;
    private final TaskMgrSidebarCard cardWifi;
    private final TaskMgrSidebarCard cardGPU0;
    private final TaskMgrSidebarCard cardGPU1;
    private final java.util.List<TaskMgrSidebarCard> allCards = new ArrayList<>();

    // ── Khung phải: CardLayout ────────────────────────────────────────────────
    private final CardLayout cardLayout = new CardLayout();
    private String currentCardKey = "cpu";
    private final JPanel detailContainer = new JPanel(cardLayout);

    // ── Biểu đồ chính của từng màn hình chi tiết ──────────────────────────────
    private final TaskMgrLineChart chartCPU      = new TaskMgrLineChart(new Color(0, 180, 216));   // Cyan
    private final TaskMgrLineChart chartMem      = new TaskMgrLineChart(new Color(59, 130, 246));  // Blue
    private final TaskMgrLineChart chartDiskAct  = new TaskMgrLineChart(new Color(34, 197, 94));   // Green
    private final TaskMgrLineChart chartDiskRate = new TaskMgrLineChart(new Color(34, 197, 94));   // Green
    private final TaskMgrLineChart chartEth      = new TaskMgrLineChart(new Color(251, 113, 133)); // Coral
    private final TaskMgrLineChart chartWifi     = new TaskMgrLineChart(new Color(236, 72, 153));  // Hot Pink
    private final TaskMgrLineChart chartGpu0_3D  = new TaskMgrLineChart(new Color(168, 85, 247));  // Purple
    private final TaskMgrLineChart chartGpu0_Cpy = new TaskMgrLineChart(new Color(168, 85, 247));  // Purple
    private final TaskMgrLineChart chartGpu0_Enc = new TaskMgrLineChart(new Color(168, 85, 247));  // Purple
    private final TaskMgrLineChart chartGpu0_Dec = new TaskMgrLineChart(new Color(168, 85, 247));  // Purple
    private final TaskMgrLineChart chartGpu1_3D  = new TaskMgrLineChart(new Color(139, 92, 246));  // Indigo
    private final TaskMgrLineChart chartGpu1_Cpy = new TaskMgrLineChart(new Color(139, 92, 246));  // Indigo
    private final TaskMgrLineChart chartGpu1_Prc = new TaskMgrLineChart(new Color(139, 92, 246));  // Indigo
    private final TaskMgrLineChart chartGpu1_Cmp = new TaskMgrLineChart(new Color(139, 92, 246));  // Indigo

    // ── Thanh Memory Composition Bar ──────────────────────────────────────────
    private final MemoryCompositionBar memCompBar = new MemoryCompositionBar();

    // ── Thanh GPU Memory Gauges ───────────────────────────────────────────────
    private final JProgressBar progGpu0Ded = new JProgressBar(0, 100);
    private final JProgressBar progGpu0Shd = new JProgressBar(0, 100);
    private final JLabel lblGpu0DedText = new JLabel("—");
    private final JLabel lblGpu0ShdText = new JLabel("—");

    private final JProgressBar progGpu1Shd = new JProgressBar(0, 100);
    private final JLabel lblGpu1ShdText = new JLabel("—");

    // ── Labels thông số chi tiết (Bottom Stats Decks) ─────────────────────────
    // CPU
    private final JLabel lblCpuName       = new JLabel("CPU");
    private final JLabel lblCpuUtil       = new JLabel("0%");
    private final JLabel lblCpuSpeed      = new JLabel("—");
    private final JLabel lblCpuProcs      = new JLabel("—");
    private final JLabel lblCpuThreads    = new JLabel("—");
    private final JLabel lblCpuHandles    = new JLabel("—");
    private final JLabel lblCpuUptime     = new JLabel("0:00:00:00");
    private final JLabel lblCpuBaseSpeed  = new JLabel("—");
    private final JLabel lblCpuSockets    = new JLabel("—");
    private final JLabel lblCpuCores      = new JLabel("—");
    private final JLabel lblCpuLogical    = new JLabel("—");
    private final JLabel lblCpuLogicalTitle = new JLabel("Logical processors");
    private final JLabel lblCpuVirt       = new JLabel("Disabled");
    private final JLabel lblCpuVirtTitle    = new JLabel("Virtualization");
    private final JLabel lblCpuL1         = new JLabel("N/A");
    private final JLabel lblCpuL2         = new JLabel("N/A");
    private final JLabel lblCpuL3         = new JLabel("N/A");

    // Memory
    private final JLabel lblMemSpec       = new JLabel("—");
    private final JLabel lblMemInUse      = new JLabel("—");
    private final JLabel lblMemAvailable  = new JLabel("—");
    private final JLabel lblMemCommitted  = new JLabel("—");
    private final JLabel lblMemCached     = new JLabel("—");
    private final JLabel lblMemPaged      = new JLabel("—");
    private final JLabel lblMemNonPaged   = new JLabel("—");
    private final JLabel lblMemSpeed      = new JLabel("N/A");
    private final JLabel lblMemSlots      = new JLabel("N/A");
    private final JLabel lblMemForm       = new JLabel("N/A");
    private final JLabel lblMemReserved   = new JLabel("—");

    // Disk 0 (C:)
    private final JLabel lblDiskName      = new JLabel("Disk");
    private final JLabel lblDiskActive    = new JLabel("0%");
    private final JLabel lblDiskResp      = new JLabel("0.0 ms");
    private final JLabel lblDiskRead      = new JLabel("0 KB/s");
    private final JLabel lblDiskWrite     = new JLabel("0 KB/s");
    private final JLabel lblDiskCap       = new JLabel("—");
    private final JLabel lblDiskFormatted = new JLabel("—");
    private final JLabel lblDiskSystem    = new JLabel("Có (Yes)");
    private final JLabel lblDiskPageFile  = new JLabel("Có (Yes)");
    private final JLabel lblDiskType      = new JLabel("SSD/HDD");

    // Ethernet
    private final JLabel lblEthName       = new JLabel("Ethernet");
    private final JLabel lblEthSend       = new JLabel("0.0 KB/s (0 Kbps)");
    private final JLabel lblEthRecv       = new JLabel("0.0 KB/s (0 Kbps)");
    private final JLabel lblEthAdapter    = new JLabel("—");
    private final JLabel lblEthConn       = new JLabel("Ethernet");
    private final JLabel lblEthIpv4       = new JLabel("—");
    private final JLabel lblEthIpv6       = new JLabel("—");

    // Wi-Fi
    private final JLabel lblWifiName      = new JLabel("—");
    private final JLabel lblWifiSend      = new JLabel("0.0 KB/s (0 Kbps)");
    private final JLabel lblWifiRecv      = new JLabel("0.0 KB/s (0 Kbps)");
    private final JLabel lblWifiAdapter   = new JLabel("—");
    private final JLabel lblWifiSsid      = new JLabel("—");
    private final JLabel lblWifiConn      = new JLabel("—");
    private final JLabel lblWifiIpv4      = new JLabel("—");
    private final JLabel lblWifiIpv6      = new JLabel("—");
    private final JLabel lblWifiSignal    = new JLabel("—");

    // GPU 0
    private final JLabel lblGpu0Name      = new JLabel("GPU 0");
    private final JLabel lblGpu0Util      = new JLabel("0%");
    private final JLabel lblGpu0TotalMem  = new JLabel("—");
    private final JLabel lblGpu0DedMemVal = new JLabel("—");
    private final JLabel lblGpu0ShdMemVal = new JLabel("—");
    private final JLabel lblGpu0Temp      = new JLabel("—");
    private final JLabel lblGpu0Driver    = new JLabel("—");
    private final JLabel lblGpu0Date      = new JLabel("—");
    private final JLabel lblGpu0DirectX   = new JLabel("—");
    private final JLabel lblGpu0Loc       = new JLabel("—");
    private final JLabel lblGpu0Res       = new JLabel("—");

    // GPU 1
    private final JLabel lblGpu1Name      = new JLabel("GPU 1");
    private final JLabel lblGpu1Util      = new JLabel("0%");
    private final JLabel lblGpu1TotalMem  = new JLabel("—");
    private final JLabel lblGpu1ShdMemVal = new JLabel("—");
    private final JLabel lblGpu1Temp      = new JLabel("N/A");
    private final JLabel lblGpu1Driver    = new JLabel("—");
    private final JLabel lblGpu1Date      = new JLabel("—");
    private final JLabel lblGpu1DirectX   = new JLabel("—");
    private final JLabel lblGpu1Loc       = new JLabel("—");
    private volatile boolean isVmClient   = false;

    // ── Ngăn kéo Nhật ký Cảnh báo ─────────────────────────────────────────────
    private final JPanel alertDrawer = new JPanel(new BorderLayout());
    private final JPanel alertBody = new JPanel(new BorderLayout());
    private final JLabel lblAlertDrawerTitle = new JLabel("[!] Nhật ký Cảnh báo (Alerts) [0 cảnh báo]");
    private final JTextArea txtAlertArea;
    private boolean alertExpanded = false;

    public TaskMgrPerformancePanel(JTextArea sharedAlertArea, Runnable onClearAlert) {
        this.txtAlertArea = sharedAlertArea;
        setLayout(new BorderLayout(0, 0));
        setBackground(UITheme.BG_DARK);

        // Khởi tạo 7 Sidebar Cards
        cardCPU      = new TaskMgrSidebarCard("cpu",   "CPU",         new Color(0, 180, 216),   () -> selectCard("cpu"));
        cardMemory   = new TaskMgrSidebarCard("mem",   "Memory",      new Color(59, 130, 246),  () -> selectCard("mem"));
        cardDisk     = new TaskMgrSidebarCard("disk",  "Disk 0 (C:)", new Color(34, 197, 94),   () -> selectCard("disk"));
        cardEthernet = new TaskMgrSidebarCard("eth",   "Ethernet",    new Color(251, 113, 133), () -> selectCard("eth"));
        cardWifi     = new TaskMgrSidebarCard("wifi",  "Wi-Fi",       new Color(236, 72, 153),  () -> selectCard("wifi"));
        cardGPU0     = new TaskMgrSidebarCard("gpu0",  "GPU 0",       new Color(168, 85, 247),  () -> selectCard("gpu0"));
        cardGPU1     = new TaskMgrSidebarCard("gpu1",  "GPU 1",       new Color(139, 92, 246),  () -> selectCard("gpu1"));

        allCards.add(cardCPU);
        allCards.add(cardMemory);
        allCards.add(cardDisk);
        allCards.add(cardEthernet);
        allCards.add(cardWifi);
        allCards.add(cardGPU0);
        allCards.add(cardGPU1);

        cardWifi.setVisible(false);
        cardGPU0.setVisible(false);
        cardGPU1.setVisible(false);

        // Cấu hình thang đo cho các biểu đồ
        chartDiskAct.setScale(100.0f, false, "%");
        cardDisk.getMiniChart().setScale(100.0f, false, "%");
        chartDiskRate.setScale(100, true, " KB/s");
        chartEth.setDualLine(new Color(251, 113, 133), new Color(56, 189, 248));
        chartEth.setScale(100, true, " KB/s");
        cardEthernet.getMiniChart().setDualLine(new Color(251, 113, 133), new Color(56, 189, 248));

        chartWifi.setDualLine(new Color(236, 72, 153), new Color(56, 189, 248));
        chartWifi.setScale(500, true, " KB/s");
        cardWifi.getMiniChart().setDualLine(new Color(236, 72, 153), new Color(56, 189, 248));

        // Xây dựng 7 màn hình chi tiết bên phải
        buildDetailDeck();

        // Ghép SplitPane: Trái Sidebar, Phải Detail View
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebarPanel(), detailContainer);
        splitPane.setDividerLocation(215);
        splitPane.setResizeWeight(0.0);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setBackground(UITheme.BG_DARK);

        add(splitPane, BorderLayout.CENTER);

        // Xây dựng Ngăn kéo cảnh báo bên dưới
        buildAlertDrawer(onClearAlert);
        add(alertDrawer, BorderLayout.SOUTH);

        // Mặc định chọn CPU
        selectCard("cpu");
    }

    private void selectCard(String key) {
        currentCardKey = key;
        for (TaskMgrSidebarCard c : allCards) {
            c.setSelected(c.getKey().equals(key));
        }
        cardLayout.show(detailContainer, key);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Sidebar Panel (Cột trái ~225px)
    // ─────────────────────────────────────────────────────────────────────────
    private JComponent buildSidebarPanel() {
        sidebarList = new JPanel();
        JPanel list = sidebarList;
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(UITheme.BG_SIDEBAR);
        list.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (TaskMgrSidebarCard c : allCards) {
            list.add(c);
            list.add(Box.createVerticalStrut(4));
        }
        list.add(Box.createVerticalGlue());

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(new LineBorder(UITheme.BORDER, 1));
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        UITheme.styleScrollPane(sp);
        return sp;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Detail Deck (Khung phải - CardLayout)
    // ─────────────────────────────────────────────────────────────────────────
    private void buildDetailDeck() {
        detailContainer.setBackground(UITheme.BG_DARK);
        detailContainer.add(wrapScroll(buildCpuDetail()),   "cpu");
        detailContainer.add(wrapScroll(buildMemDetail()),   "mem");
        detailContainer.add(wrapScroll(buildDiskDetail()),  "disk");
        detailContainer.add(wrapScroll(buildEthDetail()),   "eth");
        detailContainer.add(wrapScroll(buildWifiDetail()),  "wifi");
        detailContainer.add(wrapScroll(buildGpu0Detail()),  "gpu0");
        detailContainer.add(wrapScroll(buildGpu1Detail()),  "gpu1");
    }

    private JScrollPane wrapScroll(JPanel p) {
        JScrollPane sp = new JScrollPane(p);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        UITheme.styleScrollPane(sp);
        return sp;
    }

    // ── 2.1 CPU Detail ───────────────────────────────────────────────────────
    private JPanel buildCpuDetail() {
        JPanel p = createBaseDetailPanel();
        p.setPreferredSize(new Dimension(540, 430));
        p.add(makeDetailHeader("CPU", lblCpuName, "% Khả năng sử dụng trong 60 giây"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        chartCPU.setPreferredSize(new Dimension(0, 140));
        center.add(chartCPU, BorderLayout.CENTER);

        // 2 Cột thông số bên dưới (Chuẩn Task Manager Windows 11)
        JPanel stats = new JPanel(new GridLayout(1, 2, 16, 0));
        stats.setOpaque(false);
        stats.setBorder(new EmptyBorder(6, 2, 6, 2));

        JPanel colLeft = new JPanel(new BorderLayout(0, 6));
        colLeft.setOpaque(false);

        // Top tiles: Utilization & Speed
        JPanel topTiles = new JPanel(new GridLayout(1, 2, 8, 0));
        topTiles.setOpaque(false);
        topTiles.add(makeStatCard("Utilization", lblCpuUtil));
        topTiles.add(makeStatCard("Speed", lblCpuSpeed));
        colLeft.add(topTiles, BorderLayout.NORTH);

        // 4 chỉ số bên dưới: Processes, Threads, Handles, Up time
        JPanel leftList = new JPanel(new GridLayout(4, 1, 0, 3));
        leftList.setOpaque(false);
        leftList.add(makeStatRow("Processes", lblCpuProcs, false));
        leftList.add(makeStatRow("Threads", lblCpuThreads, false));
        leftList.add(makeStatRow("Handles", lblCpuHandles, false));
        leftList.add(makeStatRow("Up time", lblCpuUptime, false));
        colLeft.add(leftList, BorderLayout.CENTER);

        JPanel colRight = new JPanel(new GridLayout(8, 1, 0, 3));
        colRight.setOpaque(false);
        colRight.add(makeStatRow("Base speed", lblCpuBaseSpeed, false));
        colRight.add(makeStatRow("Sockets", lblCpuSockets, false));
        colRight.add(makeStatRow("Cores", lblCpuCores, false));
        colRight.add(makeStatRow(lblCpuLogicalTitle, lblCpuLogical, false));
        colRight.add(makeStatRow(lblCpuVirtTitle, lblCpuVirt, false));
        colRight.add(makeStatRow("L1 cache", lblCpuL1, false));
        colRight.add(makeStatRow("L2 cache", lblCpuL2, false));
        colRight.add(makeStatRow("L3 cache", lblCpuL3, false));

        stats.add(colLeft);
        stats.add(colRight);
        center.add(stats, BorderLayout.SOUTH);

        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── 2.2 Memory Detail ────────────────────────────────────────────────────
    private JPanel buildMemDetail() {
        JPanel p = createBaseDetailPanel();
        p.add(makeDetailHeader("Memory", lblMemSpec, "Mức sử dụng bộ nhớ trong 60 giây"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(chartMem, BorderLayout.CENTER);

        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setOpaque(false);
        middle.add(memCompBar, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 2, 24, 0));
        stats.setOpaque(false);
        stats.setBorder(new EmptyBorder(6, 4, 8, 4));

        JPanel colLeft = new JPanel(new BorderLayout(0, 10));
        colLeft.setOpaque(false);

        JPanel topTiles = new JPanel(new GridLayout(1, 2, 16, 0));
        topTiles.setOpaque(false);
        topTiles.add(makeStatCard("In use (Compressed)", lblMemInUse));
        topTiles.add(makeStatCard("Available", lblMemAvailable));
        colLeft.add(topTiles, BorderLayout.NORTH);

        JPanel leftList = new JPanel(new GridLayout(4, 1, 0, 4));
        leftList.setOpaque(false);
        leftList.add(makeStatRow("Committed", lblMemCommitted, false));
        leftList.add(makeStatRow("Cached", lblMemCached, false));
        leftList.add(makeStatRow("Paged pool", lblMemPaged, false));
        leftList.add(makeStatRow("Non-paged pool", lblMemNonPaged, false));
        colLeft.add(leftList, BorderLayout.CENTER);

        JPanel colRight = new JPanel(new GridLayout(4, 1, 0, 4));
        colRight.setOpaque(false);
        colRight.add(makeStatRow("Speed", lblMemSpeed, false));
        colRight.add(makeStatRow("Slots used", lblMemSlots, false));
        colRight.add(makeStatRow("Form factor", lblMemForm, false));
        colRight.add(makeStatRow("Hardware reserved", lblMemReserved, false));

        stats.add(colLeft);
        stats.add(colRight);
        middle.add(stats, BorderLayout.CENTER);
        center.add(middle, BorderLayout.SOUTH);

        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── 2.3 Disk 0 (C:) Detail ───────────────────────────────────────────────
    private JPanel buildDiskDetail() {
        JPanel p = createBaseDetailPanel();
        p.add(makeDetailHeader("Disk 0 (C:)", lblDiskName, "Thời gian hoạt động & Tốc độ truyền tải"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);

        // 2 biểu đồ xếp dọc
        JPanel charts = new JPanel(new GridLayout(2, 1, 0, 8));
        charts.setOpaque(false);
        chartDiskAct.setLabels("100%", "0%");
        chartDiskRate.setLabels("KB/s", "0");
        charts.add(chartDiskAct);
        charts.add(chartDiskRate);
        center.add(charts, BorderLayout.CENTER);

        JPanel stats = new JPanel(new GridLayout(1, 2, 20, 0));
        stats.setOpaque(false);
        stats.setBorder(new EmptyBorder(6, 4, 10, 4));

        JPanel colLeft = new JPanel(new GridLayout(4, 1, 0, 4));
        colLeft.setOpaque(false);
        colLeft.add(makeStatRow("Active time", lblDiskActive, true));
        colLeft.add(makeStatRow("Avg. response time", lblDiskResp, false));
        colLeft.add(makeStatRow("Read speed", lblDiskRead, false));
        colLeft.add(makeStatRow("Write speed", lblDiskWrite, false));

        JPanel colRight = new JPanel(new GridLayout(5, 1, 0, 4));
        colRight.setOpaque(false);
        colRight.add(makeStatRow("Capacity", lblDiskCap, false));
        colRight.add(makeStatRow("Formatted", lblDiskFormatted, false));
        colRight.add(makeStatRow("System disk", lblDiskSystem, false));
        colRight.add(makeStatRow("Page file", lblDiskPageFile, false));
        colRight.add(makeStatRow("Type", lblDiskType, false));

        stats.add(colLeft);
        stats.add(colRight);
        center.add(stats, BorderLayout.SOUTH);

        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── 2.4 Ethernet Detail ──────────────────────────────────────────────────
    private JPanel buildEthDetail() {
        JPanel p = createBaseDetailPanel();
        p.add(makeDetailHeader("Ethernet", lblEthName, "Băng thông kết nối mạng LAN / VPN (Throughput)"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(chartEth, BorderLayout.CENTER);

        JPanel stats = new JPanel(new GridLayout(1, 2, 20, 0));
        stats.setOpaque(false);
        stats.setBorder(new EmptyBorder(10, 4, 10, 4));

        JPanel colLeft = new JPanel(new GridLayout(2, 1, 0, 6));
        colLeft.setOpaque(false);
        colLeft.add(makeStatRow("Send (Gửi)", lblEthSend, true));
        colLeft.add(makeStatRow("Receive (Nhận)", lblEthRecv, true));

        JPanel colRight = new JPanel(new GridLayout(4, 1, 0, 4));
        colRight.setOpaque(false);
        colRight.add(makeStatRow("Adapter name", lblEthAdapter, false));
        colRight.add(makeStatRow("Connection type", lblEthConn, false));
        colRight.add(makeStatRow("IPv4 address", lblEthIpv4, false));
        colRight.add(makeStatRow("IPv6 address", lblEthIpv6, false));

        stats.add(colLeft);
        stats.add(colRight);
        center.add(stats, BorderLayout.SOUTH);

        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── 2.5 Wi-Fi Detail ─────────────────────────────────────────────────────
    private JPanel buildWifiDetail() {
        JPanel p = createBaseDetailPanel();
        p.add(makeDetailHeader("Wi-Fi", lblWifiName, "Băng thông kết nối mạng Wi-Fi (Throughput)"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(chartWifi, BorderLayout.CENTER);

        JPanel stats = new JPanel(new GridLayout(1, 2, 20, 0));
        stats.setOpaque(false);
        stats.setBorder(new EmptyBorder(10, 4, 10, 4));

        JPanel colLeft = new JPanel(new GridLayout(2, 1, 0, 6));
        colLeft.setOpaque(false);
        colLeft.add(makeStatRow("Send (Gửi)", lblWifiSend, true));
        colLeft.add(makeStatRow("Receive (Nhận)", lblWifiRecv, true));

        JPanel colRight = new JPanel(new GridLayout(6, 1, 0, 4));
        colRight.setOpaque(false);
        colRight.add(makeStatRow("Adapter name", lblWifiAdapter, false));
        colRight.add(makeStatRow("SSID", lblWifiSsid, false));
        colRight.add(makeStatRow("Connection type", lblWifiConn, false));
        colRight.add(makeStatRow("IPv4 address", lblWifiIpv4, false));
        colRight.add(makeStatRow("IPv6 address", lblWifiIpv6, false));
        colRight.add(makeStatRow("Signal strength", lblWifiSignal, false));

        stats.add(colLeft);
        stats.add(colRight);
        center.add(stats, BorderLayout.SOUTH);

        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── 2.6 GPU 0 (Discrete) Detail ──────────────────────────────────────────
    private JPanel buildGpu0Detail() {
        JPanel p = createBaseDetailPanel();
        p.add(makeDetailHeader("GPU 0", lblGpu0Name, "Card đồ họa rời & Bộ nhớ chuyên dụng (Dedicated)"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);

        // Lưới 2x2 gồm 4 mini engines
        JPanel engines = new JPanel(new GridLayout(2, 2, 8, 8));
        engines.setOpaque(false);
        engines.setPreferredSize(new Dimension(0, 160));
        engines.add(wrapEngine("3D", chartGpu0_3D));
        engines.add(wrapEngine("Copy", chartGpu0_Cpy));
        engines.add(wrapEngine("Video Encode", chartGpu0_Enc));
        engines.add(wrapEngine("Video Decode", chartGpu0_Dec));
        center.add(engines, BorderLayout.CENTER);

        // Thanh đo Dedicated & Shared Memory
        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 6));
        middle.setOpaque(false);
        middle.setBorder(new EmptyBorder(6, 4, 6, 4));
        middle.add(makeGaugeRow("Dedicated GPU memory", progGpu0Ded, lblGpu0DedText, new Color(168, 85, 247)));
        middle.add(makeGaugeRow("Shared GPU memory", progGpu0Shd, lblGpu0ShdText, new Color(139, 92, 246)));

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(middle, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 2, 24, 0));
        stats.setOpaque(false);
        JPanel colLeft = new JPanel(new BorderLayout(0, 8));
        colLeft.setOpaque(false);

        JPanel topTiles = new JPanel(new GridLayout(1, 2, 16, 0));
        topTiles.setOpaque(false);
        topTiles.add(makeStatCard("Utilization", lblGpu0Util));
        topTiles.add(makeStatCard("GPU Memory", lblGpu0TotalMem));
        colLeft.add(topTiles, BorderLayout.NORTH);

        JPanel leftList = new JPanel(new GridLayout(3, 1, 0, 4));
        leftList.setOpaque(false);
        leftList.add(makeStatRow("Dedicated GPU memory", lblGpu0DedMemVal, false));
        leftList.add(makeStatRow("Shared GPU memory", lblGpu0ShdMemVal, false));
        leftList.add(makeStatRow("Temperature", lblGpu0Temp, false));
        colLeft.add(leftList, BorderLayout.CENTER);

        JPanel colRight = new JPanel(new GridLayout(5, 1, 0, 4));
        colRight.setOpaque(false);
        colRight.add(makeStatRow("Driver version", lblGpu0Driver, false));
        colRight.add(makeStatRow("Driver date", lblGpu0Date, false));
        colRight.add(makeStatRow("DirectX version", lblGpu0DirectX, false));
        colRight.add(makeStatRow("Physical location", lblGpu0Loc, false));
        colRight.add(makeStatRow("Hardware reserved", lblGpu0Res, false));

        stats.add(colLeft);
        stats.add(colRight);
        south.add(stats, BorderLayout.CENTER);

        center.add(south, BorderLayout.SOUTH);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ── 2.7 GPU 1 (Integrated) Detail ────────────────────────────────────────
    private JPanel buildGpu1Detail() {
        JPanel p = createBaseDetailPanel();
        p.add(makeDetailHeader("GPU 1", lblGpu1Name, "Card đồ họa tích hợp & Bộ nhớ chia sẻ (Shared)"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);

        // Lưới 2x2 gồm 4 mini engines
        JPanel engines = new JPanel(new GridLayout(2, 2, 8, 8));
        engines.setOpaque(false);
        engines.setPreferredSize(new Dimension(0, 160));
        engines.add(wrapEngine("3D", chartGpu1_3D));
        engines.add(wrapEngine("Copy", chartGpu1_Cpy));
        engines.add(wrapEngine("Video Decode", chartGpu1_Prc));
        engines.add(wrapEngine("Video Processing", chartGpu1_Cmp));
        center.add(engines, BorderLayout.CENTER);

        // Thanh đo Shared Memory
        JPanel middle = new JPanel(new GridLayout(1, 1, 0, 6));
        middle.setOpaque(false);
        middle.setBorder(new EmptyBorder(6, 4, 6, 4));
        middle.add(makeGaugeRow("Shared GPU memory", progGpu1Shd, lblGpu1ShdText, new Color(139, 92, 246)));

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(middle, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 2, 24, 0));
        stats.setOpaque(false);
        JPanel colLeft = new JPanel(new BorderLayout(0, 8));
        colLeft.setOpaque(false);

        JPanel topTiles = new JPanel(new GridLayout(1, 2, 16, 0));
        topTiles.setOpaque(false);
        topTiles.add(makeStatCard("Utilization", lblGpu1Util));
        topTiles.add(makeStatCard("GPU Memory", lblGpu1TotalMem));
        colLeft.add(topTiles, BorderLayout.NORTH);

        JPanel leftList = new JPanel(new GridLayout(2, 1, 0, 4));
        leftList.setOpaque(false);
        leftList.add(makeStatRow("Shared GPU memory", lblGpu1ShdMemVal, false));
        leftList.add(makeStatRow("Temperature", lblGpu1Temp, false));
        colLeft.add(leftList, BorderLayout.CENTER);

        JPanel colRight = new JPanel(new GridLayout(4, 1, 0, 4));
        colRight.setOpaque(false);
        colRight.add(makeStatRow("Driver version", lblGpu1Driver, false));
        colRight.add(makeStatRow("Driver date", lblGpu1Date, false));
        colRight.add(makeStatRow("DirectX version", lblGpu1DirectX, false));
        colRight.add(makeStatRow("Physical location", lblGpu1Loc, false));

        stats.add(colLeft);
        stats.add(colRight);
        south.add(stats, BorderLayout.CENTER);

        center.add(south, BorderLayout.SOUTH);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Ngăn kéo Nhật ký Cảnh báo (Collapsible Alert Drawer)
    // ─────────────────────────────────────────────────────────────────────────
    private void buildAlertDrawer(Runnable onClear) {
        alertDrawer.setBackground(UITheme.BG_CARD);
        alertDrawer.setBorder(new LineBorder(UITheme.BORDER, 1));

        // Header bar thu gọn (cao 30px)
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(new Color(19, 28, 46));
        bar.setBorder(new EmptyBorder(6, 12, 6, 12));
        bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        lblAlertDrawerTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblAlertDrawerTitle.setForeground(new Color(245, 158, 11)); // Amber

        JLabel lblHint = new JLabel("Click để Mở rộng / Thu gọn");
        lblHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblHint.setForeground(UITheme.TEXT_MUTED);

        bar.add(lblAlertDrawerTitle, BorderLayout.WEST);
        bar.add(lblHint, BorderLayout.EAST);

        bar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleAlertDrawer();
            }
        });

        alertDrawer.add(bar, BorderLayout.NORTH);

        // Body khu vực log
        alertBody.setBackground(UITheme.BG_DARK);
        alertBody.setBorder(new EmptyBorder(6, 8, 6, 8));
        alertBody.setPreferredSize(new Dimension(0, 130));

        txtAlertArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtAlertArea.setBackground(new Color(15, 20, 30));
        txtAlertArea.setForeground(new Color(254, 202, 202));
        txtAlertArea.setEditable(false);

        JScrollPane sp = new JScrollPane(txtAlertArea);
        UITheme.styleScrollPane(sp);
        alertBody.add(sp, BorderLayout.CENTER);

        JButton btnClear = new JButton("Xóa log");
        UITheme.styleButtonDanger(btnClear);
        btnClear.addActionListener(e -> {
            if (onClear != null) onClear.run();
            updateAlertBadge(0);
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        btnRow.setOpaque(false);
        btnRow.add(btnClear);
        alertBody.add(btnRow, BorderLayout.SOUTH);

        // Mặc định thu gọn
        alertBody.setVisible(false);
        alertDrawer.add(alertBody, BorderLayout.CENTER);
    }

    public void toggleAlertDrawer() {
        alertExpanded = !alertExpanded;
        alertBody.setVisible(alertExpanded);
        lblAlertDrawerTitle.setText((alertExpanded ? "[-] " : "[+] ") + "Nhật ký Cảnh báo (Alerts)");
        revalidate();
        repaint();
    }

    public void updateAlertBadge(int count) {
        lblAlertDrawerTitle.setText((alertExpanded ? "[-] " : "[+] ") + "Nhật ký Cảnh báo (Alerts) [" + count + " cảnh báo]");
        if (count > 0 && !alertExpanded) {
            lblAlertDrawerTitle.setForeground(UITheme.DANGER);
        } else {
            lblAlertDrawerTitle.setForeground(new Color(245, 158, 11));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. API Cập nhật Dữ liệu Thời gian thực từ ServerForm
    // ─────────────────────────────────────────────────────────────────────────

    /** Cập nhật thông số tĩnh phần cứng từ gói HARDWARE_DETAIL| */
    private static String getVal(Map<String, String> kv, String... keys) {
        if (kv == null) return null;
        for (String k : keys) {
            if (kv.containsKey(k)) {
                String v = kv.get(k);
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return null;
    }

    public void updateHardwareDetail(Map<String, String> kv) {
        if (kv == null || kv.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            String cpu = getVal(kv, "cpuName", "cpu_name");
            if (cpu != null) lblCpuName.setText(cpu);
            String baseSpeed = getVal(kv, "baseSpeed", "cpu_speed");
            if (baseSpeed != null) lblCpuBaseSpeed.setText(baseSpeed);
            String sockets = getVal(kv, "sockets", "cpu_sockets");
            if (sockets != null) lblCpuSockets.setText(sockets);
            String cores = getVal(kv, "cores", "cpu_cores");
            if (cores != null) lblCpuCores.setText(cores);
            String logical = getVal(kv, "logical", "cpu_logical");
            if (logical != null) lblCpuLogical.setText(logical);

            String isVm = getVal(kv, "isVm", "is_vm");
            boolean isVmBool = "Yes".equalsIgnoreCase(isVm) || "true".equalsIgnoreCase(isVm);
            String ethAdapterVal = getVal(kv, "ethAdapter", "eth_adapter", "ethName", "eth_name");
            if (ethAdapterVal != null) {
                String el = ethAdapterVal.toLowerCase();
                if (el.contains("vmware") || el.contains("virtual") || el.contains("vbox") || el.contains("82574l")) {
                    isVmBool = true;
                }
            }
            String mfgVal = getVal(kv, "manufacturer", "maker");
            if (mfgVal != null) {
                String ml = mfgVal.toLowerCase();
                if (ml.contains("vmware") || ml.contains("virtual") || ml.contains("qemu") || ml.contains("kvm") || ml.contains("innotek")) {
                    isVmBool = true;
                }
            }
            String mdlVal = getVal(kv, "model", "system_model");
            if (mdlVal != null) {
                String ml = mdlVal.toLowerCase();
                if (ml.contains("vmware") || ml.contains("virtual") || ml.contains("vbox") || ml.contains("qemu")) {
                    isVmBool = true;
                }
            }
            String biosVal = getVal(kv, "bios", "bios_version");
            if (biosVal != null) {
                String bl = biosVal.toLowerCase();
                if (bl.contains("vmw") || bl.contains("virtualbox") || bl.contains("qemu")) {
                    isVmBool = true;
                }
            }
            String dNameVal = getVal(kv, "diskName", "disk0_model");
            if (dNameVal != null) {
                String dl = dNameVal.toLowerCase();
                if (dl.contains("vmware") || dl.contains("virtual") || dl.contains("vbox") || dl.contains("qemu")) {
                    isVmBool = true;
                }
            }
            isVmClient = isVmBool;
            if (isVmBool) {
                lblCpuLogicalTitle.setText("Virtual processors");
                lblCpuVirtTitle.setText("Virtual machine");
                lblCpuVirt.setText("Yes");
            } else {
                lblCpuLogicalTitle.setText("Logical processors");
                lblCpuVirtTitle.setText("Virtualization");
                String virt = getVal(kv, "virtualization", "virt");
                if (virt != null) lblCpuVirt.setText(virt);
            }
            String l1 = getVal(kv, "l1Cache", "l1_cache");
            if (l1 != null) lblCpuL1.setText(l1);
            String l2 = getVal(kv, "l2Cache", "l2_cache");
            if (l2 != null) lblCpuL2.setText(l2);
            String l3 = getVal(kv, "l3Cache", "l3_cache");
            if (l3 != null) lblCpuL3.setText(l3);

            String ramSlots = getVal(kv, "ramSlots", "ram_slots");
            if (ramSlots != null) lblMemSlots.setText(ramSlots);
            String ramTotal = getVal(kv, "ramTotal", "ram_total", "ram_total_mb");
            if (ramTotal != null) {
                if (ramTotal.matches("\\d+") && !ramTotal.contains("GB")) {
                    try {
                        long mb = Long.parseLong(ramTotal);
                        lblMemSpec.setText(String.format(Locale.US, "%.1f GB", mb / 1024.0));
                    } catch (Exception ignored) {
                        lblMemSpec.setText(ramTotal);
                    }
                } else {
                    lblMemSpec.setText(ramTotal);
                }
            }
            String ramSpeed = getVal(kv, "ramSpeed", "ram_speed");
            if (ramSpeed != null) lblMemSpeed.setText(ramSpeed);
            String ramForm = getVal(kv, "ramForm", "ram_form");
            if (ramForm != null) lblMemForm.setText(ramForm);
            String ramReserved = getVal(kv, "ramReserved", "ram_reserved");
            if (ramReserved != null) lblMemReserved.setText(ramReserved);

            String diskName = getVal(kv, "diskName", "disk0_model", "disk0_name");
            if (diskName != null) lblDiskName.setText(diskName);
            String diskType = getVal(kv, "diskType", "disk0_type");
            if (diskType != null) lblDiskType.setText(diskType);
            String cap = getVal(kv, "diskCap", "disk0_size", "disk_size");
            if (cap != null) {
                lblDiskCap.setText(cap);
                String formatted = getVal(kv, "diskFormatted", "disk0_size", "disk_size");
                lblDiskFormatted.setText(formatted != null ? formatted : cap);
            }

            String clientIp = getVal(kv, "client_ip", "clientIp");

            // Ẩn/Hiện Card Wi-Fi: chỉ hiện khi máy vật lý có adapter Wi-Fi hoạt động (máy ảo VMware/VBox luôn ẩn)
            String hasWifiStr = getVal(kv, "has_wifi", "hasWifi");
            String wifiName = getVal(kv, "wifiName", "wifi_name");
            boolean hasWifi = !isVmBool && "true".equalsIgnoreCase(hasWifiStr)
                    && (wifiName != null && !wifiName.equalsIgnoreCase("N/A")
                        && !wifiName.isEmpty()
                        && !wifiName.toLowerCase().contains("ethernet")
                        && !wifiName.toLowerCase().contains("realtek pcie gbe")
                        && !wifiName.toLowerCase().contains("vmware")
                        && !wifiName.toLowerCase().contains("virtual"));
            cardWifi.setVisible(hasWifi);

            String ethName = getVal(kv, "ethName", "eth0_name");
            if (ethName == null || ethName.isEmpty() || "—".equals(ethName)) {
                ethName = isVmBool ? "Intel(R) 82574L Gigabit Network Connection" : "Realtek PCIe GbE Family Controller";
            }
            lblEthName.setText(ethName);
            String ethAdapter = getVal(kv, "ethAdapter", "eth0_adapter");
            lblEthAdapter.setText(ethAdapter != null && !ethAdapter.isEmpty() ? ethAdapter : "Ethernet");

            String ethIp = getVal(kv, "ethIpv4", "eth0_ipv4", "ethIp");
            if (ethIp == null || ethIp.isEmpty() || "N/A".equalsIgnoreCase(ethIp) || ethIp.startsWith("169.254.")) {
                if (clientIp != null && !clientIp.isEmpty() && !clientIp.startsWith("127.")) {
                    ethIp = clientIp;
                } else {
                    ethIp = "N/A";
                }
            }
            lblEthIpv4.setText(ethIp);

            String ethIpv6 = getVal(kv, "ethIpv6", "eth0_ipv6");
            if (ethIpv6 == null || ethIpv6.isEmpty() || "N/A".equalsIgnoreCase(ethIpv6) || "::1".equals(ethIpv6)) {
                ethIpv6 = "N/A";
            } else {
                int pct = ethIpv6.indexOf('%');
                String ipPart = (pct > 0) ? ethIpv6.substring(0, pct) : ethIpv6;
                ethIpv6 = ipPart.replaceAll("(:0)+:", "::") + (pct > 0 ? ethIpv6.substring(pct) : "");
            }
            lblEthIpv6.setText(ethIpv6);

            if (hasWifi) {
                lblWifiName.setText(wifiName);
                String wifiAdapter = getVal(kv, "wifiAdapter", "wifi_adapter");
                lblWifiAdapter.setText(wifiAdapter != null && !wifiAdapter.isEmpty() ? wifiAdapter : "Wi-Fi");

                String ssid = getVal(kv, "wifiSsid", "wifi_ssid", "ssid");
                lblWifiSsid.setText(ssid != null && !ssid.isEmpty() ? ssid : "Chưa kết nối");

                String conn = getVal(kv, "wifiConn", "wifi_conn", "connectionType", "radio_type");
                lblWifiConn.setText(conn != null && !conn.isEmpty() ? conn : "N/A");

                String wifiIp = getVal(kv, "wifiIpv4", "wifi_ipv4", "wifiIp", "wifi_ip");
                if (wifiIp == null || wifiIp.isEmpty() || "Wi-Fi".equals(wifiIp) || "N/A".equals(wifiIp) || wifiIp.startsWith("169.254.")) {
                    if (hasWifi && clientIp != null && !clientIp.isEmpty() && !clientIp.startsWith("127.")) {
                        wifiIp = clientIp;
                    } else {
                        wifiIp = "N/A";
                    }
                }
                lblWifiIpv4.setText(wifiIp);

                String wifiIpv6 = getVal(kv, "wifiIpv6", "wifi_ipv6");
                if (wifiIpv6 == null || wifiIpv6.isEmpty() || "N/A".equalsIgnoreCase(wifiIpv6) || "::1".equals(wifiIpv6)) {
                    wifiIpv6 = "N/A";
                } else {
                    int pct = wifiIpv6.indexOf('%');
                    String ipPart = (pct > 0) ? wifiIpv6.substring(0, pct) : wifiIpv6;
                    wifiIpv6 = ipPart.replaceAll("(:0)+:", "::") + (pct > 0 ? wifiIpv6.substring(pct) : "");
                }
                lblWifiIpv6.setText(wifiIpv6);

                String signal = getVal(kv, "wifiSignal", "wifi_signal", "signal");
                lblWifiSignal.setText(signal != null && !signal.isEmpty() ? signal : "0%");
            } else {
                cardWifi.setVisible(false);
                lblWifiName.setText("N/A");
                lblWifiAdapter.setText("N/A");
                lblWifiSsid.setText("N/A");
                lblWifiConn.setText("N/A");
                lblWifiIpv4.setText("N/A");
                lblWifiIpv6.setText("N/A");
                lblWifiSignal.setText("0%");
                if ("wifi".equals(currentCardKey)) {
                    selectCard("eth");
                }
            }

            // Ẩn/Hiện Card GPU: Hỗ trợ cả máy vật lý (NVIDIA/Intel/AMD) và máy ảo (VMware SVGA 3D/VBox)
            int gpuCount = 0;
            String gpuCntStr = getVal(kv, "gpuCount", "gpu_count");
            if (gpuCntStr != null) {
                try { gpuCount = Integer.parseInt(gpuCntStr.trim()); } catch (Exception ignored) {}
            }

            String g0 = getVal(kv, "gpu0Name", "gpu0_name", "gpu0");
            String g1 = getVal(kv, "gpu1Name", "gpu1_name", "gpu1");
            if (g0 == null) g0 = "N/A";
            if (g1 == null) g1 = "N/A";
            if (gpuCount == 0) {
                if (!g0.equalsIgnoreCase("N/A") && !g0.isEmpty()) gpuCount = 1;
                if (!g1.equalsIgnoreCase("N/A") && !g1.isEmpty()) gpuCount = 2;
            }

            boolean isG0Real = (gpuCount >= 1) && !g0.equalsIgnoreCase("N/A")
                    && !g0.toLowerCase().contains("parsec")
                    && !g0.toLowerCase().contains("remote")
                    && !g0.isEmpty();
            boolean isG1Real = (gpuCount >= 2) && !g1.equalsIgnoreCase("N/A")
                    && !g1.toLowerCase().contains("parsec")
                    && !g1.toLowerCase().contains("remote")
                    && !g1.isEmpty();

            if (!isG0Real) {
                cardGPU0.setVisible(false);
                cardGPU1.setVisible(false);
            } else {
                cardGPU0.setVisible(true);
                cardGPU0.updateTitle(g0);
                lblGpu0Name.setText(g0);

                String g0Driver = getVal(kv, "gpu0Version", "gpu0_version", "gpu0Driver", "gpu0_driver");
                lblGpu0Driver.setText(g0Driver != null && !g0Driver.isEmpty() ? g0Driver : "N/A");

                String g0Date = getVal(kv, "gpu0Date", "gpu0_date");
                lblGpu0Date.setText(g0Date != null && !g0Date.isEmpty() ? g0Date : "N/A");

                String g0Dx = getVal(kv, "gpu0DirectX", "gpu0_directx", "directx_ver");
                if (g0Dx != null && !g0Dx.isEmpty()) {
                    if (!g0Dx.contains("DirectX") && !g0Dx.contains("FL")) g0Dx = "12 (FL " + g0Dx + ")";
                    lblGpu0DirectX.setText(g0Dx);
                } else {
                    lblGpu0DirectX.setText("N/A");
                }

                String g0Loc = getVal(kv, "gpu0Loc", "gpu0_loc", "gpu0_location");
                lblGpu0Loc.setText(g0Loc != null && !g0Loc.isEmpty() ? g0Loc : "N/A");

                String g0Res = getVal(kv, "gpu0Res", "gpu0_res");
                lblGpu0Res.setText(g0Res != null && !g0Res.isEmpty() ? g0Res : "N/A");

                String g0Vram = getVal(kv, "gpu0Vram", "gpu0_vram");
                if (g0Vram != null && !g0Vram.isEmpty()) {
                    try {
                        double mb = Double.parseDouble(g0Vram);
                        lblGpu0DedMemVal.setText(String.format(Locale.US, "0,0/%.1f GB", mb / 1024.0));
                    } catch (Exception ignored) {
                        lblGpu0DedMemVal.setText("N/A");
                    }
                } else {
                    lblGpu0DedMemVal.setText("N/A");
                }

                String g0Shared = getVal(kv, "gpu0Shared", "gpu0_shared");
                if (g0Shared != null && !g0Shared.isEmpty()) {
                    try {
                        double mb = Double.parseDouble(g0Shared);
                        lblGpu0ShdMemVal.setText(String.format(Locale.US, "0,0/%.1f GB", mb / 1024.0));
                    } catch (Exception ignored) {
                        lblGpu0ShdMemVal.setText("N/A");
                    }
                } else {
                    lblGpu0ShdMemVal.setText("N/A");
                }

                if (g0Vram != null && g0Shared != null) {
                    try {
                        double vmb = Double.parseDouble(g0Vram);
                        double smb = Double.parseDouble(g0Shared);
                        lblGpu0TotalMem.setText(String.format(Locale.US, "0,0/%.1f GB", (vmb + smb) / 1024.0));
                    } catch (Exception ignored) {
                        lblGpu0TotalMem.setText("N/A");
                    }
                } else {
                    lblGpu0TotalMem.setText("N/A");
                }

                if (!isG1Real) {
                    cardGPU1.setVisible(false);
                } else {
                    cardGPU1.setVisible(true);
                    cardGPU1.updateTitle(g1);
                    lblGpu1Name.setText(g1);

                    String g1Driver = getVal(kv, "gpu1Version", "gpu1_version", "gpu1Driver", "gpu1_driver");
                    lblGpu1Driver.setText(g1Driver != null && !g1Driver.isEmpty() ? g1Driver : "N/A");

                    String g1Date = getVal(kv, "gpu1Date", "gpu1_date");
                    lblGpu1Date.setText(g1Date != null && !g1Date.isEmpty() ? g1Date : "N/A");

                    String g1Dx = getVal(kv, "gpu1DirectX", "gpu1_directx");
                    if (g1Dx != null && !g1Dx.isEmpty()) {
                        if (!g1Dx.contains("DirectX") && !g1Dx.contains("FL")) g1Dx = "12 (FL " + g1Dx + ")";
                        lblGpu1DirectX.setText(g1Dx);
                    } else {
                        lblGpu1DirectX.setText("N/A");
                    }

                    String g1Loc = getVal(kv, "gpu1Loc", "gpu1_loc", "gpu1_location");
                    lblGpu1Loc.setText(g1Loc != null && !g1Loc.isEmpty() ? g1Loc : "N/A");

                    lblGpu1Temp.setText("N/A");
                    String g1Shared = getVal(kv, "gpu1Shared", "gpu1_shared");
                    if (g1Shared != null && !g1Shared.isEmpty()) {
                        try {
                            double mb = Double.parseDouble(g1Shared);
                            lblGpu1ShdMemVal.setText(String.format(Locale.US, "0,0/%.1f GB", mb / 1024.0));
                            lblGpu1TotalMem.setText(String.format(Locale.US, "0,0/%.1f GB", mb / 1024.0));
                        } catch (Exception ignored) {
                            lblGpu1ShdMemVal.setText("N/A");
                            lblGpu1TotalMem.setText("N/A");
                        }
                    } else {
                        lblGpu1ShdMemVal.setText("N/A");
                        lblGpu1TotalMem.setText("N/A");
                    }
                }
            }

            if ((!hasWifi && "wifi".equals(currentCardKey)) ||
                (!isG0Real && ("gpu0".equals(currentCardKey) || "gpu1".equals(currentCardKey)))) {
                selectCard("cpu");
            }

            if (sidebarList != null) {
                sidebarList.revalidate();
                sidebarList.repaint();
            }
            revalidate();
            repaint();
        });
    }

    public void updatePerfMetrics(Map<String, String> kv, double cpu, double ramPct, String ramLabel,
                                 double disk, double netUp, double netDown, double gpu, long ping) {
        SwingUtilities.invokeLater(() -> {
            // 1. CPU
            float fCpu = (float) Math.min(100.0, Math.max(0.0, cpu));
            double cpuSpeed = getDbl(kv, "cpuSpeed", getDbl(kv, "baseSpeed", 0.0));
            String uptime = kv.getOrDefault("uptime", "0:00:00:00");
            if (uptime.contains(".")) {
                uptime = uptime.substring(0, uptime.indexOf('.'));
            } else if (uptime.split(":").length > 4) {
                int lastColon = uptime.lastIndexOf(':');
                uptime = uptime.substring(0, lastColon);
            }
            int procs = getInt(kv, "procs", 0);
            int threads = getInt(kv, "threads", 0);
            int handles = getInt(kv, "handles", 0);

            chartCPU.addPoint(fCpu);
            cardCPU.updateValues(String.format(Locale.US, "%.0f%%  %.2f GHz", fCpu, cpuSpeed), lblCpuName.getText(), fCpu);
            lblCpuUtil.setText(String.format(Locale.US, "%.1f%%", fCpu));
            lblCpuSpeed.setText(String.format(Locale.US, "%.2f GHz", cpuSpeed));
            lblCpuProcs.setText(String.valueOf(procs));
            lblCpuThreads.setText(threads > 0 ? String.format(Locale.US, "%,d", threads) : "N/A");
            lblCpuHandles.setText(handles > 0 ? String.format(Locale.US, "%,d", handles) : "N/A");
            lblCpuUptime.setText(uptime);

            // 2. Memory
            double usedGB = getDbl(kv, "usedGB", getDbl(kv, "ramUsed", 0.0));
            double totalGB = getDbl(kv, "totalGB", getDbl(kv, "ramTotal", 0.0));
            double availGB = getDbl(kv, "ramAvailGB", Math.max(0.0, totalGB - usedGB));
            float fRam = (float) Math.min(100.0, Math.max(0.0, (totalGB > 0) ? ((usedGB / totalGB) * 100.0) : ramPct));
            double commitUsed = getDbl(kv, "commitUsed", 0.0);
            double commitTotal = getDbl(kv, "commitTotal", 0.0);

            chartMem.addPoint(fRam);
            cardMemory.updateValues(String.format(Locale.US, "%.1f/%.1f GB (%.0f%%)", usedGB, totalGB, fRam), kv.getOrDefault("ramType", "RAM"), fRam);
            memCompBar.updateValues((float) Math.min(1.0, Math.max(0.0, totalGB > 0 ? usedGB / totalGB : 0)), 0.02f, (float) (totalGB > 0 ? availGB * 0.7 / totalGB : 0), (float) (totalGB > 0 ? availGB * 0.3 / totalGB : 0));
            
            lblMemInUse.setText(String.format(Locale.US, "%.1f GB", usedGB));
            if (availGB < 1.0) {
                lblMemAvailable.setText(String.format(Locale.US, "%d MB", (int) Math.round(availGB * 1024)));
            } else {
                lblMemAvailable.setText(String.format(Locale.US, "%.2f GB", availGB));
            }
            if (commitUsed > 0 && commitTotal > 0) {
                lblMemCommitted.setText(String.format(Locale.US, "%.1f / %.1f GB", commitUsed, commitTotal));
            } else {
                lblMemCommitted.setText(kv.getOrDefault("ramCommitted", "N/A"));
            }
            String cachedStr = kv.get("ramCached");
            if (cachedStr != null && !cachedStr.isEmpty()) {
                lblMemCached.setText(cachedStr);
            } else {
                lblMemCached.setText(availGB > 0 ? (availGB < 1.0 ? String.format(Locale.US, "%d MB", (int) Math.round(availGB * 1024)) : String.format(Locale.US, "%.1f GB", availGB)) : "N/A");
            }
            int paged = getInt(kv, "ramPaged", 0);
            lblMemPaged.setText(paged > 0 ? (paged + " MB") : "N/A");
            double nonPaged = getDbl(kv, "ramNonPaged", 0.0);
            lblMemNonPaged.setText(nonPaged > 0 ? String.format(Locale.US, "%.1f MB", nonPaged) : "N/A");

            // 3. Disk 0 (C:)
            float fDiskAct = (float) Math.min(100.0, Math.max(0.0, getDbl(kv, "diskActive", Math.min(100.0, Math.max(0.0, disk)))));
            float fDiskRate = (float) Math.max(0.0, (getDbl(kv, "diskRead", 0) + getDbl(kv, "diskWrite", 0.0)));
            chartDiskAct.addPoint(fDiskAct);
            chartDiskRate.addPoint(fDiskRate);

            boolean isVm = isVmClient || "Yes".equalsIgnoreCase(getVal(kv, "isVm", "is_vm")) || "true".equalsIgnoreCase(getVal(kv, "isVm", "is_vm"));
            String diskLabel = isVm ? "Virtual disk" : "SSD";
            String diskNameVal = getVal(kv, "diskName", "disk0_model");
            if (isVm) {
                diskLabel = "Virtual disk";
            } else if (diskNameVal != null && !diskNameVal.isEmpty() && !"N/A".equalsIgnoreCase(diskNameVal)) {
                diskLabel = diskNameVal.contains("NVMe") ? "SSD (NVMe)" : diskNameVal;
            }
            cardDisk.updateValues(String.format(Locale.US, "%.0f%%", fDiskAct), diskLabel, fDiskAct);
            lblDiskActive.setText(String.format(Locale.US, "%.1f%%", fDiskAct));
            lblDiskResp.setText(String.format(Locale.US, "%.1f ms", getDbl(kv, "diskResp", 0.0)));
            lblDiskRead.setText(formatDiskRate(getDbl(kv, "diskRead", 0.0)));
            lblDiskWrite.setText(formatDiskRate(getDbl(kv, "diskWrite", 0.0)));

            // 4. Ethernet (Chuyển đổi KB/s sang Kbps chuẩn Task Manager Windows)
            float fEthUp = (float) getDbl(kv, "ethUp", 0.0);
            float fEthDown = (float) getDbl(kv, "ethDown", 0.0);
            float fEthUpKbps = fEthUp * 8.0f;
            float fEthDownKbps = fEthDown * 8.0f;
            chartEth.addPoint(fEthUpKbps, fEthDownKbps);
            cardEthernet.updateDualValues("S: " + formatSpeed(fEthUpKbps) + "  R: " + formatSpeed(fEthDownKbps), "Ethernet", fEthUpKbps, fEthDownKbps);
            lblEthSend.setText(formatSpeed(fEthUpKbps));
            lblEthRecv.setText(formatSpeed(fEthDownKbps));

            // 5. Wi-Fi
            if (cardWifi.isVisible()) {
                float fWifiUp = (float) getDbl(kv, "wifiUp", 0.0);
                float fWifiDown = (float) getDbl(kv, "wifiDown", 0.0);
                float fWifiUpKbps = fWifiUp * 8.0f;
                float fWifiDownKbps = fWifiDown * 8.0f;
                chartWifi.addPoint(fWifiUpKbps, fWifiDownKbps);
                cardWifi.updateDualValues("S: " + formatSpeed(fWifiUpKbps) + "  R: " + formatSpeed(fWifiDownKbps), "Wi-Fi", fWifiUpKbps, fWifiDownKbps);
                lblWifiSend.setText(formatSpeed(fWifiUpKbps));
                lblWifiRecv.setText(formatSpeed(fWifiDownKbps));

                String liveSignal = getVal(kv, "wifiSignal", "wifi_signal");
                if (liveSignal != null && !liveSignal.isEmpty() && !"N/A".equalsIgnoreCase(liveSignal)) {
                    lblWifiSignal.setText(liveSignal);
                }
                String liveSsid = getVal(kv, "wifiSsid", "wifi_ssid");
                if (liveSsid != null && !liveSsid.isEmpty() && !"N/A".equalsIgnoreCase(liveSsid)) {
                    lblWifiSsid.setText(liveSsid);
                }
            }

            // 6. GPU 0 (Discrete / First GPU)
            if (cardGPU0.isVisible()) {
                float fGpu0 = (float) Math.min(100.0, Math.max(0.0, getDbl(kv, "gpu0", (gpu > 0 ? gpu : 0.0))));
                int tempGpu0 = getInt(kv, "gpu0Temp", 0);
                double dedUsed = getDbl(kv, "gpu0DedUsed", 0.0);
                double shdUsed0 = getDbl(kv, "gpu0ShdUsed", 0.0);
                double dedTotal0 = getDbl(kv, "gpu0VramGB", getDbl(kv, "gpu0Vram", 0.0) / 1024.0);
                double shdTotal0 = getDbl(kv, "gpu0SharedGB", getDbl(kv, "gpu0Shared", 0.0) / 1024.0);

                chartGpu0_3D.addPoint(fGpu0);
                chartGpu0_Cpy.addPoint(0);
                chartGpu0_Enc.addPoint(0);
                chartGpu0_Dec.addPoint(0);

                String card0Val = (tempGpu0 > 0)
                    ? String.format(Locale.US, "%.0f%% (%d °C)", fGpu0, tempGpu0)
                    : String.format(Locale.US, "%.0f%%", fGpu0);
                cardGPU0.updateValues(card0Val, lblGpu0Name.getText(), fGpu0);

                if (dedTotal0 > 0) {
                    int dedPct0 = Math.min(100, Math.max(0, (int) Math.round((dedUsed / dedTotal0) * 100)));
                    progGpu0Ded.setValue(dedPct0);
                    lblGpu0DedText.setText(String.format(Locale.US, "%.2f / %.2f GB (%d%%)", dedUsed, dedTotal0, dedPct0));
                    lblGpu0DedMemVal.setText(String.format(Locale.US, "%.1f / %.1f GB", dedUsed, dedTotal0));
                } else {
                    lblGpu0DedText.setText("N/A");
                    lblGpu0DedMemVal.setText("N/A");
                }
                lblGpu0Util.setText(String.format(Locale.US, "%.1f%%", fGpu0));
                if (dedTotal0 > 0 || shdTotal0 > 0) {
                    lblGpu0TotalMem.setText(String.format(Locale.US, "%.1f / %.1f GB", dedUsed + shdUsed0, dedTotal0 + shdTotal0));
                } else {
                    lblGpu0TotalMem.setText("N/A");
                }
                if (shdTotal0 > 0) {
                    lblGpu0ShdMemVal.setText(String.format(Locale.US, "%.1f / %.1f GB", shdUsed0, shdTotal0));
                } else {
                    lblGpu0ShdMemVal.setText("N/A");
                }
                lblGpu0Temp.setText(tempGpu0 > 0 ? (tempGpu0 + " °C") : "N/A");
            }

            // 7. GPU 1 (Integrated / Second GPU)
            if (cardGPU1.isVisible()) {
                float fGpu1 = (float) Math.min(100.0, Math.max(0.0, getDbl(kv, "gpu1", 0.0)));
                int tempGpu1 = getInt(kv, "gpu1Temp", 0);
                double dedUsed1 = getDbl(kv, "gpu1DedUsed", 0.0);
                double shdUsed1 = getDbl(kv, "gpu1ShdUsed", 0.0);
                double shdTotal1 = getDbl(kv, "gpu1SharedGB", getDbl(kv, "gpu1Shared", 0.0) / 1024.0);

                chartGpu1_3D.addPoint(fGpu1);
                chartGpu1_Cpy.addPoint(0);
                chartGpu1_Prc.addPoint(0);
                chartGpu1_Cmp.addPoint(0);

                String card1Val = (tempGpu1 > 0)
                    ? String.format(Locale.US, "%.0f%% (%d °C)", fGpu1, tempGpu1)
                    : String.format(Locale.US, "%.1f%%", fGpu1);
                cardGPU1.updateValues(card1Val, lblGpu1Name.getText(), fGpu1);

                if (shdTotal1 > 0) {
                    int shdPct1 = Math.min(100, Math.max(0, (int) Math.round((shdUsed1 / shdTotal1) * 100)));
                    progGpu1Shd.setValue(shdPct1);
                    lblGpu1ShdText.setText(String.format(Locale.US, "%.2f / %.2f GB (%d%%)", shdUsed1, shdTotal1, shdPct1));
                    lblGpu1TotalMem.setText(String.format(Locale.US, "%.1f / %.1f GB", shdUsed1, shdTotal1));
                    lblGpu1ShdMemVal.setText(String.format(Locale.US, "%.1f / %.1f GB", shdUsed1, shdTotal1));
                } else {
                    lblGpu1ShdText.setText("N/A");
                    lblGpu1TotalMem.setText("N/A");
                    lblGpu1ShdMemVal.setText("N/A");
                }
                lblGpu1Util.setText(String.format(Locale.US, "%.1f%%", fGpu1));
                lblGpu1Temp.setText(tempGpu1 > 0 ? (tempGpu1 + " °C") : "N/A");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel createBaseDetailPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(UITheme.BG_DARK);
        p.setBorder(new EmptyBorder(10, 16, 12, 16));
        return p;
    }

    private JPanel makeDetailHeader(String title, JLabel nameLabel, String subtitle) {
        JPanel p = new JPanel(new BorderLayout(10, 2));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 0, 6, 0));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(Color.WHITE);

        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setForeground(new Color(148, 163, 184)); // Muted slate
        nameLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(new Color(100, 116, 139));

        p.add(lblTitle, BorderLayout.WEST);
        p.add(nameLabel, BorderLayout.EAST);
        p.add(lblSub, BorderLayout.SOUTH);
        return p;
    }

    private JPanel makeStatCard(String label, JLabel valLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(148, 163, 184)); // Muted slate

        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        valLabel.setForeground(Color.WHITE);
        valLabel.setHorizontalAlignment(SwingConstants.LEFT);

        card.add(lbl, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel makeStatRow(String label, JLabel valLabel, boolean isPrimary) {
        return makeStatRow(new JLabel(label), valLabel, isPrimary);
    }

    private JPanel makeStatRow(JLabel lbl, JLabel valLabel, boolean isPrimary) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);

        lbl.setFont(new Font("Segoe UI", isPrimary ? Font.BOLD : Font.PLAIN, isPrimary ? 12 : 11));
        lbl.setForeground(isPrimary ? new Color(190, 200, 215) : new Color(148, 163, 184));
        lbl.setPreferredSize(new Dimension(110, 18));

        valLabel.setFont(new Font("Segoe UI", isPrimary ? Font.BOLD : Font.PLAIN, isPrimary ? 13 : 11));
        valLabel.setForeground(isPrimary ? Color.WHITE : new Color(226, 232, 240));
        valLabel.setHorizontalAlignment(SwingConstants.LEFT);

        row.add(lbl, BorderLayout.WEST);
        row.add(valLabel, BorderLayout.CENTER);
        return row;
    }

    private JPanel wrapEngine(String engineTitle, TaskMgrLineChart chart) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setBorder(new LineBorder(new Color(30, 41, 59), 1));

        JLabel lbl = new JLabel("  " + engineTitle);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(new Color(192, 132, 252));
        lbl.setBackground(new Color(23, 30, 46));
        lbl.setOpaque(true);

        p.add(lbl, BorderLayout.NORTH);
        p.add(chart, BorderLayout.CENTER);
        return p;
    }

    private JPanel makeGaugeRow(String label, JProgressBar bar, JLabel textVal, Color barColor) {
        JPanel p = new JPanel(new BorderLayout(10, 2));
        p.setOpaque(false);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(148, 163, 184));

        textVal.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        textVal.setForeground(new Color(226, 232, 240));

        bar.setOpaque(false);
        bar.setBackground(new Color(15, 23, 42));
        bar.setForeground(barColor);
        bar.setBorder(new LineBorder(new Color(45, 59, 82), 1));
        bar.setPreferredSize(new Dimension(0, 14));

        p.add(lbl, BorderLayout.WEST);
        p.add(textVal, BorderLayout.EAST);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    private double getDbl(Map<String, String> m, String k, double def) {
        if (m == null || !m.containsKey(k)) return def;
        try { return Double.parseDouble(m.get(k).trim().replace(',', '.')); } catch (Exception e) { return def; }
    }

    private int getInt(Map<String, String> m, String k, int def) {
        if (m == null || !m.containsKey(k)) return def;
        try { return Integer.parseInt(m.get(k).trim()); } catch (Exception e) { return def; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Memory Composition Bar (Thanh phân bổ bộ nhớ chuẩn Task Manager)
    // ─────────────────────────────────────────────────────────────────────────
    private static class MemoryCompositionBar extends JPanel {
        private float inUse = 0.85f;
        private float modified = 0.02f;
        private float standby = 0.10f;
        private float free = 0.03f;

        public MemoryCompositionBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(0, 36));
        }

        public void updateValues(float inUse, float modified, float standby, float free) {
            this.inUse = Math.max(0, inUse);
            this.modified = Math.max(0, modified);
            this.standby = Math.max(0, standby);
            this.free = Math.max(0, free);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int barH = 14;

            float sum = inUse + modified + standby + free;
            if (sum <= 0) sum = 1.0f;

            int wInUse = (int) (w * (inUse / sum));
            int wMod   = (int) (w * (modified / sum));
            int wStb   = (int) (w * (standby / sum));
            int wFree  = w - wInUse - wMod - wStb;

            // In use (Blue)
            g2.setColor(new Color(59, 130, 246));
            g2.fillRect(0, 0, wInUse, barH);

            // Modified (Amber)
            g2.setColor(new Color(245, 158, 11));
            g2.fillRect(wInUse, 0, wMod, barH);

            // Standby (Cyan)
            g2.setColor(new Color(6, 182, 212));
            g2.fillRect(wInUse + wMod, 0, wStb, barH);

            // Free (Slate)
            g2.setColor(new Color(51, 65, 85));
            g2.fillRect(wInUse + wMod + wStb, 0, wFree, barH);

            // Viền
            g2.setColor(new Color(30, 41, 59));
            g2.drawRect(0, 0, w - 1, barH);

            // Chú thích (Legend)
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            int legY = barH + 14;

            drawLegend(g2, 0, legY, new Color(59, 130, 246), "Đang dùng (In use)");
            drawLegend(g2, 140, legY, new Color(245, 158, 11), "Đã sửa đổi (Modified)");
            drawLegend(g2, 290, legY, new Color(6, 182, 212), "Chờ sẵn (Standby)");
            drawLegend(g2, 420, legY, new Color(51, 65, 85), "Trống (Free)");

            g2.dispose();
        }

        private void drawLegend(Graphics2D g2, int x, int y, Color c, String text) {
            g2.setColor(c);
            g2.fillRect(x, y - 8, 8, 8);
            g2.setColor(new Color(148, 163, 184));
            g2.drawString(text, x + 12, y);
        }
    }
}

