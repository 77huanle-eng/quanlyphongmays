package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RealtimeDistanceForm – Đo khoảng cách và độ trễ RTT giữa Server và Client.
 * Hỗ trợ chế độ:
 *   1. "🎯 Đo ngay" – Đo thủ công theo yêu cầu (1 lần duy nhất, không đo liên tục).
 *   2. "🔄 Tự động (2s)" – Chế độ đo liên tục định kỳ (có thể Bật/Tắt).
 * Tương thích Windows & Linux/Ubuntu, đo lường đa tầng (ICMP Ping, TCP Handshake, Socket RST).
 */
public class RealtimeDistanceForm extends JFrame {

    private JComboBox<String> cbClientList;
    private JTextField txtTargetIP;
    private JTextField txtPort;
    private JButton btnMeasureOnce;
    private JButton btnToggle;
    private JButton btnClear;
    private JLabel lblStatus, lblPing, lblHops, lblDistance;
    private JProgressBar progressDistance;
    private JLabel lblDistCategory;
    private JTable tblHistory;
    private DefaultTableModel tableModel;

    private volatile boolean isRunning = false;
    private Thread monitorThread;

    private static final Pattern P_TIME = Pattern.compile("(?i)time[<=]\\s*([\\d\\.]+)\\s*ms|time<1ms");
    private static final Pattern P_TTL  = Pattern.compile("(?i)ttl\\s*=\\s*(\\d+)");

    public RealtimeDistanceForm() {
        this("127.0.0.1", 5000, null);
    }

    public RealtimeDistanceForm(String defaultIP, int defaultPort) {
        this(defaultIP, defaultPort, null);
    }

    public RealtimeDistanceForm(String defaultIP, int defaultPort, List<String> clientList) {
        setTitle("Quản Lý Phòng Máy – Đo Khoảng Cách & Độ Trễ (Server <-> Client)");
        setSize(920, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UITheme.BG_DARK);
        setLayout(new BorderLayout(10, 10));

        // ── 1. TOP PANEL: Cấu hình đầu vào & Các nút bấm ───────────────────
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topPanel.setBackground(UITheme.BG_SIDEBAR);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER));

        JLabel lblSelect = new JLabel("Chọn máy trạm:");
        lblSelect.setForeground(UITheme.ACCENT);
        lblSelect.setFont(UITheme.FONT_HEADER);
        topPanel.add(lblSelect);

        // Chuẩn bị danh sách máy cho Dropdown
        DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel<>();
        if (defaultIP != null && !defaultIP.isBlank() && !defaultIP.equals("127.0.0.1")) {
            comboModel.addElement(defaultIP + " (Client đang chọn)");
        }
        if (clientList != null && !clientList.isEmpty()) {
            for (String c : clientList) {
                if (!c.contains(defaultIP)) {
                    comboModel.addElement(c);
                }
            }
        }
        comboModel.addElement("127.0.0.1 (Localhost - Máy chủ)");
        comboModel.addElement("192.168.1.1 (Gateway / Switch phòng)");
        comboModel.addElement("8.8.8.8 (Google DNS - Ngoài Internet)");

        cbClientList = new JComboBox<>(comboModel);
        UITheme.styleComboBox(cbClientList);
        cbClientList.setPreferredSize(new Dimension(210, 32));
        topPanel.add(cbClientList);

        JLabel lblIp = new JLabel("IP:");
        lblIp.setForeground(UITheme.ACCENT);
        lblIp.setFont(UITheme.FONT_HEADER);
        topPanel.add(lblIp);

        String initialIp = (defaultIP != null && !defaultIP.isBlank()) ? defaultIP : "127.0.0.1";
        txtTargetIP = new JTextField(initialIp, 11);
        UITheme.styleTextField(txtTargetIP);
        topPanel.add(txtTargetIP);

        JLabel lblP = new JLabel("Port:");
        lblP.setForeground(UITheme.ACCENT);
        lblP.setFont(UITheme.FONT_HEADER);
        topPanel.add(lblP);

        txtPort = new JTextField(String.valueOf(defaultPort), 4);
        UITheme.styleTextField(txtPort);
        topPanel.add(txtPort);

        // NÚT 1: "🎯 Đo ngay" (Thực hiện đo 1 lần khi bấm)
        btnMeasureOnce = new JButton("Đo ngay");
        UITheme.styleButtonAccent(btnMeasureOnce);
        btnMeasureOnce.setPreferredSize(new Dimension(105, 32));
        btnMeasureOnce.setToolTipText("Bấm để thực hiện đo khoảng cách đúng 1 lần.");
        topPanel.add(btnMeasureOnce);

        // NÚT 2: "🔄 Tự động (2s)" (Bật / Tắt chế độ đo liên tục nếu cần)
        btnToggle = new JButton("Tự động");
        UITheme.styleButtonSidebar(btnToggle, false);
        btnToggle.setPreferredSize(new Dimension(100, 32));
        btnToggle.setToolTipText("Bật hoặc tắt chế độ tự động đo lặp lại mỗi 2 giây.");
        topPanel.add(btnToggle);

        // NÚT 3: "🗑 Xóa"
        btnClear = new JButton("Xóa");
        UITheme.styleButtonGhost(btnClear);
        btnClear.setPreferredSize(new Dimension(75, 32));
        btnClear.setToolTipText("Xóa sạch lịch sử các lần đo.");
        topPanel.add(btnClear);

        add(topPanel, BorderLayout.NORTH);

        // Sự kiện chọn máy nhanh từ Dropdown
        cbClientList.addActionListener(e -> {
            String selected = (String) cbClientList.getSelectedItem();
            if (selected != null) {
                String extractedIp = extractIpFromString(selected);
                txtTargetIP.setText(extractedIp);
            }
        });

        // ── 2. CENTER PANEL: Metrics + Visual Gauge + History ────────────────
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(UITheme.BG_DARK);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));

        // Metric Cards (4 cards)
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 12, 10));
        metricsPanel.setBackground(UITheme.BG_DARK);

        lblStatus   = createMetricCard(metricsPanel, "TRẠNG THÁI", "Sẵn sàng", UITheme.SUCCESS);
        lblPing     = createMetricCard(metricsPanel, "ĐỘ TRỄ RTT", "-- ms", UITheme.ACCENT);
        lblHops     = createMetricCard(metricsPanel, "SỐ TRẠM (HOPS)", "-- trạm", UITheme.WARNING);
        lblDistance = createMetricCard(metricsPanel, "KHOẢNG CÁCH THỰC", "-- m", UITheme.DANGER);

        // Visual Distance Bar Panel
        JPanel gaugePanel = new JPanel(new BorderLayout(8, 6));
        gaugePanel.setBackground(UITheme.BG_CARD);
        gaugePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JPanel gaugeHeader = new JPanel(new BorderLayout());
        gaugeHeader.setOpaque(false);
        JLabel gaugeTitle = new JLabel("Thước đo khoảng cách tương đối (Dựa trên độ trễ RTT & cấu trúc mạng LAN):");
        gaugeTitle.setFont(UITheme.FONT_SMALL);
        gaugeTitle.setForeground(UITheme.TEXT_MUTED);
        lblDistCategory = new JLabel("Bấm 'Đo ngay' để kiểm tra");
        lblDistCategory.setFont(UITheme.FONT_HEADER);
        lblDistCategory.setForeground(UITheme.ACCENT);
        gaugeHeader.add(gaugeTitle, BorderLayout.WEST);
        gaugeHeader.add(lblDistCategory, BorderLayout.EAST);

        progressDistance = new JProgressBar(0, 100);
        progressDistance.setValue(0);
        progressDistance.setStringPainted(true);
        progressDistance.setString("Chưa đo");
        progressDistance.setForeground(UITheme.ACCENT);
        progressDistance.setBackground(UITheme.BG_INPUT);
        progressDistance.setPreferredSize(new Dimension(100, 20));

        JPanel scaleLegend = new JPanel(new GridLayout(1, 4));
        scaleLegend.setOpaque(false);
        scaleLegend.add(createLegendLabel("● Kế bên (0-5m)", UITheme.SUCCESS));
        scaleLegend.add(createLegendLabel("● Cùng phòng (5-20m)", UITheme.WARNING));
        scaleLegend.add(createLegendLabel("● Khác phòng (20-50m)", new Color(249, 115, 22)));
        scaleLegend.add(createLegendLabel("● Ngoài mạng (>50m)", UITheme.DANGER));

        gaugePanel.add(gaugeHeader, BorderLayout.NORTH);
        gaugePanel.add(progressDistance, BorderLayout.CENTER);
        gaugePanel.add(scaleLegend, BorderLayout.SOUTH);

        // Gộp Top Metrics + Gauge vào 1 cụm
        JPanel topMetricsContainer = new JPanel(new BorderLayout(0, 8));
        topMetricsContainer.setOpaque(false);
        topMetricsContainer.add(metricsPanel, BorderLayout.NORTH);
        topMetricsContainer.add(gaugePanel, BorderLayout.CENTER);

        centerPanel.add(topMetricsContainer, BorderLayout.NORTH);

        // Bảng lịch sử đo
        String[] cols = {"Thời gian", "Địa chỉ IP", "Độ trễ RTT", "Số Hop", "Khoảng cách ước tính", "Vị trí thiết bị", "Phương thức"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblHistory = new JTable(tableModel);
        tblHistory.setRowHeight(26);
        UITheme.styleTable(tblHistory);

        // Căn giữa các cột thông số
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tblHistory.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tblHistory.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tblHistory.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tblHistory.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        tblHistory.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

        JScrollPane scrollPane = new JScrollPane(tblHistory);
        UITheme.styleScrollPane(scrollPane);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // ── Gán sự kiện cho các nút bấm ─────────────────────────────────────
        btnMeasureOnce.addActionListener(e -> measureOnce());
        btnToggle.addActionListener(e -> toggleContinuousMonitoring());
        btnClear.addActionListener(e -> tableModel.setRowCount(0));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                isRunning = false;
            }
        });

        // Tự động đo 1 lần duy nhất khi vừa mở giao diện để người dùng thấy kết quả ngay
        measureOnce();
    }

    private JLabel createLegendLabel(String text, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.FONT_SMALL);
        l.setForeground(c);
        return l;
    }

    private JLabel createMetricCard(JPanel parent, String title, String defaultValue, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(UITheme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(UITheme.FONT_SMALL);
        lblTitle.setForeground(UITheme.TEXT_MUTED);

        JLabel lblValue = new JLabel(defaultValue, SwingConstants.CENTER);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblValue.setForeground(valueColor);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(lblValue, BorderLayout.CENTER);
        parent.add(card);
        return lblValue;
    }

    private String extractIpFromString(String text) {
        if (text == null) return "127.0.0.1";
        if (text.contains("(") && text.contains(")")) {
            int start = text.indexOf('(') + 1;
            int end = text.indexOf(')');
            String sub = text.substring(start, end).trim();
            if (sub.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                return sub;
            }
        }
        String firstWord = text.split("[\\s\\-]+")[0].trim();
        if (firstWord.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|localhost|::1")) {
            return firstWord;
        }
        return text.trim();
    }

    /**
     * Bấm nút "🎯 Đo ngay": Thực hiện đo ĐÚNG 1 LẦN.
     */
    private void measureOnce() {
        String host = txtTargetIP.getText().trim();
        int p = 5000;
        try {
            p = Integer.parseInt(txtPort.getText().trim());
        } catch (Exception ignored) {}
        final int port = p;

        btnMeasureOnce.setEnabled(false);
        btnMeasureOnce.setText("Đang đo...");
        lblStatus.setText("Đang đo...");
        lblStatus.setForeground(UITheme.WARNING);

        new Thread(() -> {
            try {
                executeSingleMeasurement(host, port);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    btnMeasureOnce.setEnabled(true);
                    btnMeasureOnce.setText("Đo ngay");
                });
            }
        }, "DistanceMeasureOnce-" + host).start();
    }

    /**
     * Bật / Tắt chế độ tự động đo liên tục (mặc định tắt).
     */
    private void toggleContinuousMonitoring() {
        if (!isRunning) {
            isRunning = true;
            btnToggle.setText("Dừng tự động");
            UITheme.styleButtonDanger(btnToggle);
            startContinuousThread();
        } else {
            isRunning = false;
            btnToggle.setText("Tự động");
            UITheme.styleButtonSidebar(btnToggle, false);
            lblStatus.setText("Đã dừng tự động");
            lblStatus.setForeground(UITheme.WARNING);
        }
    }

    private void startContinuousThread() {
        monitorThread = new Thread(() -> {
            while (isRunning) {
                String host = txtTargetIP.getText().trim();
                int port = 5000;
                try {
                    port = Integer.parseInt(txtPort.getText().trim());
                } catch (Exception ignored) {}

                executeSingleMeasurement(host, port);

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        });
        monitorThread.setName("DistanceContinuous-" + txtTargetIP.getText().trim());
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * Thực hiện đo 1 lần và cập nhật giao diện
     */
    private void executeSingleMeasurement(String host, int port) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        MeasureResult res = measureMultiTier(host, port);

        SwingUtilities.invokeLater(() -> {
            String timeStr = sdf.format(new Date());

            if (res.online && res.rttMs >= 0) {
                lblStatus.setText("Online");
                lblStatus.setForeground(UITheme.SUCCESS);

                String rttDisplay = (res.rttMs < 1.0)
                    ? String.format("%.2f ms", res.rttMs)
                    : String.format("%.1f ms", res.rttMs);
                lblPing.setText(rttDisplay);

                int displayHops = Math.max(0, res.hops);
                lblHops.setText(displayHops + " trạm");

                DistanceInfo dist = calculateDistance(host, res.rttMs, displayHops);

                lblDistance.setText(dist.distanceStr);
                lblDistCategory.setText(dist.locationType);

                int progressVal = Math.min(100, Math.max(0, dist.progressPct));
                progressDistance.setValue(progressVal);
                progressDistance.setString(dist.distanceStr + " – " + dist.locationType);

                if (progressVal <= 25) {
                    progressDistance.setForeground(UITheme.SUCCESS);
                } else if (progressVal <= 50) {
                    progressDistance.setForeground(UITheme.WARNING);
                } else if (progressVal <= 75) {
                    progressDistance.setForeground(new Color(249, 115, 22));
                } else {
                    progressDistance.setForeground(UITheme.DANGER);
                }

                tableModel.insertRow(0, new Object[]{
                    timeStr, host,
                    rttDisplay,
                    displayHops,
                    dist.distanceStr,
                    dist.locationType,
                    res.method
                });

                if (tableModel.getRowCount() > 60) {
                    tableModel.removeRow(60);
                }
            } else {
                lblStatus.setText("Offline");
                lblStatus.setForeground(UITheme.DANGER);
                lblPing.setText("Timeout");
                lblHops.setText("--");
                lblDistance.setText("Không rõ");
                lblDistCategory.setText("Mất kết nối hoặc không phản hồi");

                progressDistance.setValue(0);
                progressDistance.setString("Không xác định");
                progressDistance.setForeground(UITheme.TEXT_MUTED);

                tableModel.insertRow(0, new Object[]{
                    timeStr, host, "Timeout", "--", "Không rõ", "Mất kết nối hoặc tường lửa chặn", res.method
                });
            }
        });
    }

    /**
     * Đo lường đa tầng:
     * 1. Thử ICMP Ping hệ thống (hỗ trợ cả Windows và Ubuntu).
     * 2. Nếu ICMP timeout, thử TCP Handshake / Socket RST.
     * 3. Thử InetAddress.isReachable.
     */
    private MeasureResult measureMultiTier(String host, int port) {
        if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host)) {
            return new MeasureResult(true, 0.1, 0, "Loopback");
        }
        try {
            if (host.equals(InetAddress.getLocalHost().getHostAddress())) {
                return new MeasureResult(true, 0.2, 0, "Localhost");
            }
        } catch (Exception ignored) {}

        MeasureResult r1 = measureIcmpPing(host);
        if (r1.online) return r1;

        MeasureResult r2 = measureTcpRtt(host, port);
        if (r2.online) return r2;

        MeasureResult r3 = measureInetAddress(host);
        if (r3.online) return r3;

        return new MeasureResult(false, -1, -1, "Mất kết nối");
    }

    /** Tầng 1: Ping hệ thống (Windows & Ubuntu tương thích) */
    private MeasureResult measureIcmpPing(String host) {
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        try {
            ProcessBuilder pb = isWin
                ? new ProcessBuilder("ping", "-n", "1", "-w", "800", host)
                : new ProcessBuilder("ping", "-c", "1", "-W", "1", host);

            pb.redirectErrorStream(true);
            Process p = pb.start();

            double rtt = -1;
            int hops = -1;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher mTtl = P_TTL.matcher(line);
                    if (mTtl.find()) {
                        int ttl = Integer.parseInt(mTtl.group(1));
                        int initialTtl = (ttl <= 64) ? 64 : (ttl <= 128) ? 128 : 255;
                        hops = Math.max(0, initialTtl - ttl);
                    }

                    Matcher mTime = P_TIME.matcher(line);
                    if (mTime.find()) {
                        String grp = mTime.group(1);
                        if (grp == null || grp.isEmpty()) {
                            rtt = 0.5; // time<1ms
                        } else {
                            rtt = Double.parseDouble(grp);
                        }
                    }
                }
            }

            p.waitFor();

            if (rtt >= 0) {
                if (host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost") || host.equals("::1")) {
                    hops = 0;
                }
                return new MeasureResult(true, rtt, hops, isWin ? "ICMP Ping (Win)" : "ICMP Ping (Linux)");
            }
        } catch (Exception ignored) {}

        return new MeasureResult(false, -1, -1, "ICMP Failed");
    }

    /** Tầng 2: TCP Socket Connect / RST */
    private MeasureResult measureTcpRtt(String host, int port) {
        int[] probePorts = {135, 445, 80};
        for (int p : probePorts) {
            long start = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, p), 700);
                long durNs = System.nanoTime() - start;
                double rtt = durNs / 1_000_000.0;
                return new MeasureResult(true, rtt, 0, "TCP Port " + p);
            } catch (java.net.ConnectException ce) {
                // Đích phản hồi gói TCP RST -> Máy đích ĐANG HOẠT ĐỘNG và RTT hoàn toàn chính xác!
                long durNs = System.nanoTime() - start;
                double rtt = durNs / 1_000_000.0;
                return new MeasureResult(true, rtt, 0, "TCP RST (Port " + p + ")");
            } catch (Exception ignored) {}
        }
        return new MeasureResult(false, -1, -1, "TCP Failed");
    }

    /** Tầng 3: InetAddress */
    private MeasureResult measureInetAddress(String host) {
        try {
            long start = System.currentTimeMillis();
            boolean reachable = InetAddress.getByName(host).isReachable(800);
            if (reachable) {
                long dur = System.currentTimeMillis() - start;
                return new MeasureResult(true, Math.max(0.5, dur), 0, "InetAddress");
            }
        } catch (Exception ignored) {}
        return new MeasureResult(false, -1, -1, "Timeout");
    }

    /** Tính khoảng cách và loại vị trí từ RTT và Hops */
    private DistanceInfo calculateDistance(String host, double rtt, int hops) {
        if (host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost") || host.equals("::1")) {
            return new DistanceInfo("0 m", "Máy chủ nội bộ (Localhost)", 0);
        }

        if (rtt < 1.0 && hops <= 0) {
            return new DistanceInfo("~ 2 - 4 m", "Máy Client kế bên (Cùng dãy bàn)", 15);
        } else if (rtt < 3.0 && hops <= 1) {
            int cableM = Math.max(5, (int)(rtt * 4.5));
            return new DistanceInfo("~ " + cableM + " m", "Cùng phòng máy LAN (Khác bàn)", 35);
        } else if (rtt < 10.0 && hops <= 1) {
            int cableM = (int)(rtt * 3.5);
            return new DistanceInfo("~ " + cableM + " m", "Cùng phòng máy (Cuối dãy / Khác Switch)", 55);
        } else if (rtt < 25.0 && hops <= 2) {
            return new DistanceInfo("~ 35 - 100 m", "Khác phòng / Khác tầng trong tòa nhà", 75);
        } else {
            int km = Math.max(1, (int)(rtt * 5));
            return new DistanceInfo("~ " + km + " km", "Ngoài mạng LAN / Qua Internet", 100);
        }
    }

    // ── Helper Classes ────────────────────────────────────────────────────────
    private static class MeasureResult {
        final boolean online;
        final double rttMs;
        final int hops;
        final String method;

        MeasureResult(boolean online, double rttMs, int hops, String method) {
            this.online = online;
            this.rttMs = rttMs;
            this.hops = hops;
            this.method = method;
        }
    }

    private static class DistanceInfo {
        final String distanceStr;
        final String locationType;
        final int progressPct;

        DistanceInfo(String distanceStr, String locationType, int progressPct) {
            this.distanceStr = distanceStr;
            this.locationType = locationType;
            this.progressPct = progressPct;
        }
    }
}
