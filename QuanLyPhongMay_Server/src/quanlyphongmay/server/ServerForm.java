/*

 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license

 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template

 */

package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.io.*;
import java.util.Properties;

import java.awt.*;

import javax.swing.*;

import java.awt.BorderLayout;

import java.awt.Color;

import java.awt.Dimension;

import java.awt.Font;

import java.awt.GridLayout;

import java.io.BufferedReader;

import java.io.IOException;

import java.io.InputStreamReader;

import java.io.PrintWriter;

import java.net.ServerSocket;

import java.net.Socket;

import java.net.InetAddress;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import java.nio.charset.StandardCharsets;

import java.time.LocalTime;

import java.time.format.DateTimeFormatter;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;

import javax.swing.BorderFactory;

import javax.swing.JButton;

import javax.swing.JLabel;

import javax.swing.JOptionPane;

import javax.swing.JPanel;

import javax.swing.JScrollPane;

import javax.swing.SwingConstants;

import javax.swing.SwingUtilities;

import javax.swing.table.DefaultTableModel;

/**
 *
 * 
 * 
 * @author ADMIN
 * 
 */

public class ServerForm extends javax.swing.JFrame {

    // ── MAC Address Resolution & ARP Helpers ("Tự Lấy") ─────────────────────
    public static String extractMacFromText(String text) {
        if (text == null || text.isEmpty())
            return null;
        java.util.regex.Pattern pKey = java.util.regex.Pattern.compile(
                "(?:mac_address|mac|MAC)\\s*=\\s*([0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2})");
        java.util.regex.Matcher mKey = pKey.matcher(text);
        if (mKey.find()) {
            return normalizeMac(mKey.group(1));
        }
        java.util.regex.Pattern pGen = java.util.regex.Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
        java.util.regex.Matcher mGen = pGen.matcher(text);
        if (mGen.find()) {
            return normalizeMac(mGen.group());
        }
        return null;
    }

    public static String normalizeMac(String mac) {
        if (mac == null)
            return "";
        return mac.trim().replace('-', ':').toUpperCase(java.util.Locale.US);
    }

    public static String getMacFromArp(String ip) {
        if (ip == null || ip.trim().isEmpty() || "127.0.0.1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
            return null;
        }
        ip = ip.trim();
        try {
            Process process = new ProcessBuilder("arp", "-a", ip).redirectErrorStream(true).start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                java.util.regex.Pattern macPattern = java.util.regex.Pattern
                        .compile("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
                while ((line = reader.readLine()) != null) {
                    if (line.contains(ip)) {
                        java.util.regex.Matcher matcher = macPattern.matcher(line);
                        if (matcher.find()) {
                            return normalizeMac(matcher.group());
                        }
                    }
                }
            }
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return null;
    }

    public String resolveClientMac(String clientName) {
        if (clientName == null || clientName.trim().isEmpty())
            return null;
        clientName = clientName.trim();

        // 1. Kiểm tra ClientInfo trong allKnownClients / connectedClients
        ClientInfo info = allKnownClients.get(clientName);
        if (info == null) {
            for (Map.Entry<String, ClientInfo> e : allKnownClients.entrySet()) {
                if (e.getKey().equalsIgnoreCase(clientName)) {
                    info = e.getValue();
                    break;
                }
            }
        }
        if (info != null && info.mac != null && !info.mac.trim().isEmpty() && !"N/A".equalsIgnoreCase(info.mac)
                && !"00:00:00:00:00:00".equals(info.mac)) {
            return normalizeMac(info.mac);
        }

        // 2. Tìm trong lastSpecsMap (từ gói tin SPECS_REALTIME)
        String specs = lastSpecsMap.get(clientName);
        if (specs != null) {
            String m = extractMacFromText(specs);
            if (m != null) {
                if (info != null)
                    info.mac = m;
                return m;
            }
        }

        // 3. Tìm trong info.details (từ gói tin INFO)
        if (info != null && info.details != null) {
            String m = extractMacFromText(info.details);
            if (m != null) {
                info.mac = m;
                return m;
            }
        }

        // 4. Tìm địa chỉ IP của máy này
        String ip = (info != null) ? info.ip : null;
        if (ip == null || ip.isEmpty()) {
            for (int r = 0; r < clientTableModel.getRowCount(); r++) {
                if (clientName.equalsIgnoreCase(String.valueOf(clientTableModel.getValueAt(r, 1)))) {
                    ip = String.valueOf(clientTableModel.getValueAt(r, 2));
                    break;
                }
            }
        }

        // 5. Nếu IP hợp lệ, tra cứu trong ARP cache của Windows
        if (ip != null && !ip.isEmpty() && !ip.contains("N/A")) {
            String arpMac = getMacFromArp(ip);
            if (arpMac != null && !arpMac.isEmpty()) {
                if (info != null)
                    info.mac = arpMac;
                return arpMac;
            }
        }

        return null;
    }

    // ── Multi-Target Batch Execution Engine ───────────────────────────────────
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "Server-BatchExecutor");
        t.setDaemon(true);
        return t;
    });

    public static class BatchResult {
        public final int total;
        public final java.util.List<String> succeeded = new java.util.concurrent.CopyOnWriteArrayList<>();
        public final java.util.List<String> failed = new java.util.concurrent.CopyOnWriteArrayList<>();

        public BatchResult(int total) {
            this.total = total;
        }

        public int getSuccessCount() {
            return succeeded.size();
        }

        public int getFailedCount() {
            return failed.size();
        }
    }

    public BatchResult sendToSelectedClients(java.util.List<String> targetClients, String cmd) {
        if (targetClients == null || targetClients.isEmpty()) {
            return new BatchResult(0);
        }
        final BatchResult res = new BatchResult(targetClients.size());
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(targetClients.size());

        for (String clientName : targetClients) {
            batchExecutor.submit(() -> {
                try {
                    sendCommandToClient(clientName, cmd);
                    res.succeeded.add(clientName);
                } catch (Exception ex) {
                    res.failed.add(clientName);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(6, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

        return res;
    }

    public void broadcastCommand(String cmd) {
        java.util.List<String> online = getOnlineClientNames();
        sendToSelectedClients(online, cmd);
    }

    public void showBatchFeedback(String actionName, BatchResult result) {
        SwingUtilities.invokeLater(() -> {
            String msg = String.format(java.util.Locale.US,
                    "Đã thực thi [%s] thành công trên %d/%d máy trạm.",
                    actionName, result.getSuccessCount(), result.total);
            if (result.getFailedCount() > 0) {
                msg += "\nThất bại trên " + result.getFailedCount() + " máy: " + String.join(", ", result.failed);
            }
            JOptionPane.showMessageDialog(this, msg, "Kết quả thực thi Đa Máy Trạm",
                    result.getFailedCount() == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        });
    }

    public java.util.List<String> getTargetClientNames() {
        java.util.List<String> list = new ArrayList<>();
        int[] selectedRows = tblClients.getSelectedRows();
        if (selectedRows != null && selectedRows.length > 0) {
            for (int r : selectedRows) {
                int mr = tblClients.convertRowIndexToModel(r);
                String name = String.valueOf(clientTableModel.getValueAt(mr, 1));
                if (name != null && !name.trim().isEmpty() && !"N/A".equalsIgnoreCase(name)) {
                    list.add(name.trim());
                }
            }
        }
        if (list.isEmpty()) {
            list = getOnlineClientNames();
        }
        return list;
    }

    // Các nút lệnh tác vụ nhanh được khai báo toàn cục

    private final javax.swing.JButton btnSendFile = new javax.swing.JButton();

    private final javax.swing.JButton btnPermission = new javax.swing.JButton();

    private final javax.swing.JButton btnLockAll = new javax.swing.JButton();

    private final javax.swing.JButton btnUnlockAll = new javax.swing.JButton();

    private final javax.swing.JButton btnWolAction = new javax.swing.JButton();

    private final javax.swing.JButton btnExamMode = new javax.swing.JButton();

    private final javax.swing.JButton btnIncidentReport = new javax.swing.JButton();

    private final javax.swing.JButton btnSilentInstall = new javax.swing.JButton();

    private final javax.swing.JButton btnDeepClean = new javax.swing.JButton();

    private final Map<Socket, PrintWriter> clientWriters = new ConcurrentHashMap<>();

    private final Map<String, Socket> clientSockets = new ConcurrentHashMap<>();

    private DefaultTableModel clientTableModel;

    private final Map<Socket, ClientInfo> connectedClients = new ConcurrentHashMap<>();

    // Lưu TẤT CẢ máy đã từng kết nối (key = tên máy), dùng để hiển thị Offline

    private final Map<String, ClientInfo> allKnownClients = new ConcurrentHashMap<>();

    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();

    // Track ChatForm đang mở cho từng client (key = tên máy client)

    private final Map<String, ChatForm> openChatForms = new ConcurrentHashMap<>();

    private volatile ServerSocket serverSocket;

    private volatile boolean serverRunning = false;

    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(ServerForm.class.getName());

    private ServerDiscoveryResponder discoveryResponder;

    private ScreenStreamServer screenStreamServer;

    private final Map<String, Long> lastResourceAlertTime = new ConcurrentHashMap<>();

    private final Map<String, Long> lastAlertCooldown = new ConcurrentHashMap<>();

    /**
     * 
     * Creates new form ServerForm
     * 
     */

    private void showSelectedClientDetail() {

        int selectedRow = tblClients.getSelectedRow();

        if (selectedRow < 0) {

            JOptionPane.showMessageDialog(

                    this,

                    "Vui lòng chọn một máy Client.",

                    "Thông báo",

                    JOptionPane.INFORMATION_MESSAGE

            );

            return;

        }

        int row = tblClients.convertRowIndexToModel(selectedRow);

        String name = String.valueOf(clientTableModel.getValueAt(row, 1));

        String ip = String.valueOf(clientTableModel.getValueAt(row, 2));

        String status = String.valueOf(clientTableModel.getValueAt(row, 3));

        String connectedAt = String.valueOf(clientTableModel.getValueAt(row, 4));

        String details = String.valueOf(clientTableModel.getValueAt(row, 5));

        openOrUpdateComputerDetail(name, ip, status, connectedAt, details);

    }

    /** Mở MonitorForm giám sát realtime cho client được chọn */

    private void showSelectedClientMonitor() {

        int selectedRow = tblClients.getSelectedRow();

        if (selectedRow < 0) {

            JOptionPane.showMessageDialog(

                    this, "Vui lòng chọn một máy Client.",

                    "Thông báo", JOptionPane.INFORMATION_MESSAGE

            );

            return;

        }

        int row = tblClients.convertRowIndexToModel(selectedRow);

        String name = String.valueOf(clientTableModel.getValueAt(row, 1));

        String ip = String.valueOf(clientTableModel.getValueAt(row, 2));

        showMonitorForm(name, ip);

    }

    public ServerForm() {

        initComponents();
        loadKnownClients();

        btnViewLog.addActionListener(e -> btnActivityLogActionPerformed(null));

        btnOpenSettings.addActionListener(e -> openSettingsForm());

        // ── Khởi tạo các nút Tác Vụ Nhanh (HUD Tier 1 & 2 Action Deck) ──────

        btnSendFile.setText("[Triển khai] Phân Phối Tệp");

        UITheme.styleButtonAccent(btnSendFile);

        btnSendFile.addActionListener(e -> openFileSendForm());

        btnPermission.setText("[Chính sách] Kiểm Soát Web/App");

        UITheme.styleButtonGhost(btnPermission);

        btnPermission.addActionListener(e -> openPermissionForm());

        btnLockAll.setText("[Khóa] Khóa Toàn Phòng");

        UITheme.styleButtonDanger(btnLockAll);

        btnLockAll.addActionListener(e -> khoaTatCaMay());

        btnUnlockAll.setText("[Mở khóa] Mở Khóa Tất Cả");

        UITheme.styleButtonSuccess(btnUnlockAll);

        btnUnlockAll.addActionListener(e -> moKhoaTatCaMay());

        btnWolAction.setText("[WoL] Bật Nguồn Từ Xa");

        UITheme.styleButtonGhost(btnWolAction);

        btnWolAction.addActionListener(e -> thucHienWakeOnLanPrompt());

        btnExamMode.setText("[Lockdown] Khóa Bảo Mật");

        UITheme.styleButtonAccent(btnExamMode);

        btnExamMode.addActionListener(e -> openExamLockdownForm());

        btnIncidentReport.setText("[Sự cố] Nhật Ký & Cảnh Báo");

        UITheme.styleButtonWarning(btnIncidentReport);

        btnIncidentReport.addActionListener(e -> openIncidentReportForm());

        btnSilentInstall.setText("[Triển khai] Cài Đặt Ngầm");

        UITheme.styleButtonGhost(btnSilentInstall);

        btnSilentInstall.addActionListener(e -> openSilentInstallForm());

        btnDeepClean.setText("[Bảo trì] Khôi Phục Ổ Đĩa");

        UITheme.styleButtonGhost(btnDeepClean);

        btnDeepClean.addActionListener(e -> thucHienDonRacAll());

        // Thêm vào pnlQuickActions theo nhóm trực quan

        pnlQuickActions.removeAll();

        pnlQuickActions.setLayout(new BoxLayout(pnlQuickActions, BoxLayout.Y_AXIS));

        pnlQuickActions.setBackground(UITheme.BG_CARD);

        pnlQuickActions.setBorder(BorderFactory.createCompoundBorder(

                BorderFactory.createLineBorder(UITheme.BORDER, 1, true),

                BorderFactory.createEmptyBorder(12, 12, 12, 12)

        ));

        JLabel lblGroupPower = new JLabel("LỆNH NGUỒN & KHÓA");

        lblGroupPower.setFont(UITheme.FONT_SMALL);

        lblGroupPower.setForeground(UITheme.ACCENT_LIGHT);

        lblGroupPower.setAlignmentX(Component.LEFT_ALIGNMENT);

        pnlQuickActions.add(lblGroupPower);

        pnlQuickActions.add(Box.createVerticalStrut(8));

        btnLockAll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnUnlockAll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnWolAction.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        pnlQuickActions.add(btnLockAll);

        pnlQuickActions.add(Box.createVerticalStrut(6));

        pnlQuickActions.add(btnUnlockAll);

        pnlQuickActions.add(Box.createVerticalStrut(6));

        pnlQuickActions.add(btnWolAction);

        pnlQuickActions.add(Box.createVerticalStrut(14));

        JLabel lblGroupTasks = new JLabel("QUẢN TRỊ & BẢO MẬT TRẠM");

        lblGroupTasks.setFont(UITheme.FONT_SMALL);

        lblGroupTasks.setForeground(UITheme.ACCENT_LIGHT);

        lblGroupTasks.setAlignmentX(Component.LEFT_ALIGNMENT);

        pnlQuickActions.add(lblGroupTasks);

        pnlQuickActions.add(Box.createVerticalStrut(8));

        btnExamMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnIncidentReport.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnSendFile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnSilentInstall.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnDeepClean.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        btnPermission.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        pnlQuickActions.add(btnExamMode);

        pnlQuickActions.add(Box.createVerticalStrut(6));

        pnlQuickActions.add(btnIncidentReport);

        pnlQuickActions.add(Box.createVerticalStrut(6));

        pnlQuickActions.add(btnSendFile);

        pnlQuickActions.add(Box.createVerticalStrut(6));

        pnlQuickActions.add(btnSilentInstall);

        pnlQuickActions.add(Box.createVerticalStrut(6));

        pnlQuickActions.add(btnDeepClean);

        pnlQuickActions.add(Box.createVerticalStrut(6));

        pnlQuickActions.add(btnPermission);

        setTitle("LANPC - Quản trị Server");

        setLocationRelativeTo(null);

        setMinimumSize(new Dimension(1100, 700));

        clientTableModel = new DefaultTableModel(

                new Object[] { "STT", "Tên máy", "IP Address", "Trạng thái", "Thời gian kết nối", "Chi tiết" }, 0

        ) {

            @Override

            public boolean isCellEditable(int row, int column) {

                return false;

            }

        };

        tblClients.setModel(clientTableModel);

        tblClients.setRowHeight(26);

        tblClients.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        tblClients.setAutoCreateRowSorter(true);

        btnStartServer.setEnabled(true);

        btnStopServer.setEnabled(false);

        lblServerIP.setText("IP máy chủ (LAN): " + getLanIP());

        lblFooterIP.setText("IP: " + getLanIP());

        lblServerStatus.setText("Server chưa khởi động");

        lblFooterStatus.setText("Server chưa khởi động");

        lblServerPort.setText("Cổng: " + txtPort.getText());

        lblFooterPort.setText("Port: " + txtPort.getText());

        btnStopServer.addActionListener(e -> stopServer());

        btnRefresh.addActionListener(e -> refreshTable());

        btnClearLog.addActionListener(e -> {

            txtServerLog.setText("");

            addLog("Đã xóa nhật ký hiển thị.");

        });

        // Nút "Chi tiết" đã được gán trong applyTheme/initComponents

        // Thêm nút Giám sát vào sidebar (nếu chưa có trong .form)

        btnComputerDetail.addActionListener(e -> showSelectedClientDetail());

        // Nút giám sát realtime - gắn qua double-click trên table

        tblClients.addMouseListener(new java.awt.event.MouseAdapter() {

            @Override

            public void mouseClicked(java.awt.event.MouseEvent e) {

                if (e.getClickCount() == 2) {

                    showSelectedClientMonitor();

                }

            }

        });

        // Context menu chuột phải

        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();

        javax.swing.JMenuItem miDetail = new javax.swing.JMenuItem("[DxDiag] Chi tiết máy");

        javax.swing.JMenuItem miMonitor = new javax.swing.JMenuItem("[PERF] Giám sát realtime");

        javax.swing.JMenuItem miDistance = new javax.swing.JMenuItem("Đo khoảng cách");

        javax.swing.JMenuItem miChat = new javax.swing.JMenuItem("Nhắn tin");

        javax.swing.JMenuItem miLock = new javax.swing.JMenuItem("[Khóa] Khóa máy");

        javax.swing.JMenuItem miUnlock = new javax.swing.JMenuItem("[Mở khóa] Mở khóa");

        miDetail.addActionListener(e -> showSelectedClientDetail());

        miMonitor.addActionListener(e -> showSelectedClientMonitor());

        miDistance.addActionListener(e -> {

            int r = tblClients.getSelectedRow();

            String ip = null;

            if (r >= 0) {

                ip = String.valueOf(clientTableModel.getValueAt(

                        tblClients.convertRowIndexToModel(r), 2));

            }

            openDistanceForm(ip);

        });

        miLock.addActionListener(e -> {

            int r = tblClients.getSelectedRow();

            if (r >= 0) {

                String n = String.valueOf(clientTableModel.getValueAt(

                        tblClients.convertRowIndexToModel(r), 1));

                sendCommandToClient(n, "LOCK");

            }

        });

        miUnlock.addActionListener(e -> {

            int r = tblClients.getSelectedRow();

            if (r >= 0) {

                String n = String.valueOf(clientTableModel.getValueAt(

                        tblClients.convertRowIndexToModel(r), 1));

                sendCommandToClient(n, "UNLOCK");

            }

        });

        popup.add(miDetail);

        popup.add(miMonitor);

        popup.add(miDistance);

        popup.addSeparator();

        popup.add(miChat);

        javax.swing.JMenuItem miAlert = new javax.swing.JMenuItem("[!] Gửi cảnh báo từ Server");

        miAlert.addActionListener(e -> {

            int r = tblClients.getSelectedRow();

            if (r >= 0) {

                String n = String.valueOf(clientTableModel.getValueAt(tblClients.convertRowIndexToModel(r), 1));

                guiCanhBaoTuServerChoMay(n);

            }

        });

        popup.add(miAlert);

        popup.addSeparator();

        popup.add(miLock);

        popup.add(miUnlock);

        popup.addSeparator();

        javax.swing.JMenuItem miLiveScreen = new javax.swing.JMenuItem("[PC] Quan sát màn hình");

        miLiveScreen.addActionListener(e -> showSelectedClientMonitor());

        popup.add(miLiveScreen);

        javax.swing.JMenuItem miMultiScreen = new javax.swing.JMenuItem("[PC] Mở Chế độ nhiều máy (Toàn phòng)");

        miMultiScreen.addActionListener(e -> openMultiScreenForm());

        popup.add(miMultiScreen);

        javax.swing.JMenuItem miWol = new javax.swing.JMenuItem("[WoL] Khởi động từ xa (Wake-on-LAN)");

        miWol.addActionListener(e -> {

            int r = tblClients.getSelectedRow();

            if (r >= 0) {

                String n = String.valueOf(clientTableModel.getValueAt(tblClients.convertRowIndexToModel(r), 1));

                thucHienWakeOnLan(n);

            }

        });

        popup.add(miWol);

        // ── Thêm mục Gửi File vào context menu ────────────────────────────

        javax.swing.JMenuItem miSendFile = new javax.swing.JMenuItem("Gửi File");

        miSendFile.addActionListener(e -> openFileSendForm());

        popup.addSeparator();

        popup.add(miSendFile);

        javax.swing.JMenuItem miClean = new javax.swing.JMenuItem("[Dọn rác] Dọn rác & Khôi phục máy này");

        miClean.addActionListener(e -> thucHienDonRacSelected());

        popup.add(miClean);

        tblClients.setComponentPopupMenu(popup);

        addLog("Giao diện Server đã sẵn sàng.");

        addLog("[i] Double-click vào Client để mở giám sát realtime.");

        applyTheme();

        // Tự động khởi động Server lắng nghe kết nối ngay khi mở ứng dụng

        startServer();

    }

    /** Áp dụng dark-modern theme cho toàn bộ giao diện ServerForm */

    private void applyTheme() {

        getContentPane().setBackground(UITheme.BG_DARK);

        // ── Sidebar ─────────────────────────────────────────────────────────

        pnlSidebar.setBackground(UITheme.BG_SIDEBAR);

        UITheme.styleButtonSidebar(btnOverview, false);

        UITheme.styleButtonSidebar(btnComputerList, false);

        UITheme.styleButtonSidebar(btnChat, false);

        UITheme.styleButtonSidebar(btnNotification, false);

        UITheme.styleButtonSidebar(btnActivityLog, false);

        UITheme.styleButtonSidebar(btnMultiScreen, false);

        UITheme.styleLabelBody(lblFooterStatus);

        UITheme.styleLabelBody(lblFooterIP);

        UITheme.styleLabelBody(lblFooterPort);

        // ── Logo / Header ────────────────────────────────────────────────────

        lblLogo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 20));

        lblLogo.setForeground(UITheme.ACCENT);

        lblLogo.setBackground(UITheme.BG_DARK);

        lblLogo.setOpaque(true);

        pnlHeader.setBackground(UITheme.BG_CARD);

        pnlHeader.setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER));

        lblTitle.setFont(UITheme.FONT_HEADER);

        lblTitle.setForeground(UITheme.ACCENT);

        lblTitle.setText("Trạng thái hệ thống");

        UITheme.styleLabelBody(lblServerStatus);

        UITheme.styleLabelBody(lblServerIP);

        UITheme.styleLabelBody(lblServerPort);

        // ── Stat cards ───────────────────────────────────────────────────────

        styleStatCard(pnlTotal, lblTotalTitle, lblTotalCount, UITheme.ACCENT);

        styleStatCard(pnlOnline, lblOnlineTitle, lblOnlineCount, UITheme.SUCCESS);

        styleStatCard(pnlOffline, lblOfflineTitle1, lblOfflineCount, UITheme.DANGER);

        // ── Control bar ──────────────────────────────────────────────────────

        jPanel1.setBackground(UITheme.BG_CARD);

        jPanel1.setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER));

        UITheme.styleLabelBody(lblPortTitle);

        UITheme.styleTextField(txtPort);

        UITheme.styleButtonAccent(btnStartServer);

        UITheme.styleButtonDanger(btnStopServer);

        UITheme.styleButtonGhost(btnRefresh);

        UITheme.styleButtonGhost(btnComputerDetail);

        // ── Client table ─────────────────────────────────────────────────────

        pnlClientList.setBackground(UITheme.BG_CARD);

        pnlClientList.setBorder(javax.swing.BorderFactory.createCompoundBorder(

                javax.swing.BorderFactory.createLineBorder(UITheme.BORDER, 1, true),

                javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8)

        ));

        UITheme.styleLabelHeader(lblClientListTitle);

        UITheme.styleTable(tblClients);

        UITheme.styleScrollPane(scrClients);

        // ── Log area ─────────────────────────────────────────────────────────

        pnlLog.setBackground(UITheme.BG_CARD);

        UITheme.styleScrollPane(scrServerLog);

        UITheme.styleTextArea(txtServerLog);

        UITheme.styleButtonDanger(btnClearLog);

        // ── Quick actions ────────────────────────────────────────────────────

        pnlQuickActions.setBackground(UITheme.BG_CARD);

        pnlQuickActions.setBorder(javax.swing.BorderFactory.createCompoundBorder(

                javax.swing.BorderFactory.createLineBorder(UITheme.BORDER, 1, true),

                javax.swing.BorderFactory.createEmptyBorder(12, 14, 12, 14)

        ));

        UITheme.styleLabelHeader(lblQuickTitle);

        UITheme.styleButtonAccent(btnManageComputer);

        UITheme.styleButtonGhost(btnViewLog);

        UITheme.styleButtonGhost(btnOpenSettings);

        // Re-style các nút thêm bằng code (không nằm trong GEN-BEGIN)

        for (java.awt.Component c : pnlQuickActions.getComponents()) {

            if (c instanceof javax.swing.JButton btn) {

                String txt = btn.getText();

                if (txt.contains("Gửi File"))
                    UITheme.styleButtonAccent(btn);

                if (txt.contains("Phân Quyền"))
                    UITheme.styleButtonGhost(btn);

            }

        }

    }

    // ── Helper: lấy danh sách tên máy client đang Online ────────────────────
    public java.util.List<String> getOnlineClientNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String c : clientSockets.keySet()) {
            Socket s = clientSockets.get(c);
            if (s != null && !s.isClosed() && !names.contains(c)) {
                names.add(c);
            }
        }
        for (java.util.Map.Entry<String, ClientInfo> entry : allKnownClients.entrySet()) {
            if ("Online".equalsIgnoreCase(entry.getValue().status) && !names.contains(entry.getKey())) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    public java.util.List<String> getAllKnownClientNames() {
        return new java.util.ArrayList<>(allKnownClients.keySet());
    }

    // ── Mở FileSendForm ──────────────────────────────────────────────────────

    private void openFileSendForm() {

        java.util.List<String> online = getOnlineClientNames();

        if (online.isEmpty()) {

            JOptionPane.showMessageDialog(this,

                    "Chưa có Client nào đang Online.",

                    "Gửi File", JOptionPane.WARNING_MESSAGE);

            return;

        }

        FileSendForm form = new FileSendForm(clientWriters, clientSockets, online);

        form.setLocationRelativeTo(this);

        form.setVisible(true);

    }

    // ── Mở PermissionForm ────────────────────────────────────────────────────

    private void openPermissionForm() {

        java.util.List<String> online = getOnlineClientNames();

        if (online.isEmpty()) {

            JOptionPane.showMessageDialog(this,

                    "Chưa có Client nào đang Online.",

                    "Phân Quyền", JOptionPane.WARNING_MESSAGE);

            return;

        }

        PermissionForm form = new PermissionForm(clientWriters, clientSockets, online);

        form.setLocationRelativeTo(this);

        form.setVisible(true);

    }

    private void setupStatCard(javax.swing.JPanel card, String title,
            javax.swing.JLabel count, String subtitle, java.awt.Color color) {
        card.removeAll();
        card.setLayout(new BorderLayout(0, 2));
        card.setBackground(UITheme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.Border() {
                    @Override
                    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setColor(UITheme.BORDER);
                        g2.drawRect(x, y, width - 1, height - 1);
                        g2.setColor(color);
                        g2.fillRect(x, y, width, 3);
                        g2.dispose();
                    }

                    @Override
                    public Insets getBorderInsets(Component c) {
                        return new Insets(4, 1, 1, 1);
                    }

                    @Override
                    public boolean isBorderOpaque() {
                        return false;
                    }
                },
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UITheme.FONT_SMALL);
        lblTitle.setForeground(UITheme.TEXT_MUTED);

        count.setFont(new Font("Segoe UI", Font.BOLD, 28));
        count.setForeground(color);
        count.setHorizontalAlignment(SwingConstants.LEFT);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_MUTED);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(count, BorderLayout.CENTER);
        card.add(lblSub, BorderLayout.SOUTH);
    }

    private void styleStatCard(javax.swing.JPanel card, javax.swing.JLabel title,
            javax.swing.JLabel count, java.awt.Color color) {
        // Legacy no-op: stat cards are configured cleanly via setupStatCard
    }

    private JPanel makeStatPanel(

            String title, JLabel value, Color color) {

        JPanel panel = new JPanel(new BorderLayout(4, 8));

        panel.setBackground(color);

        panel.setBorder(

                BorderFactory.createEmptyBorder(12, 8, 12, 8));

        JLabel lbl = new JLabel(title, SwingConstants.CENTER);

        lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));

        value.setFont(new Font("SansSerif", Font.BOLD, 24));

        panel.add(lbl, BorderLayout.NORTH);

        panel.add(value, BorderLayout.CENTER);

        return panel;

    }

    private void handleClient(Socket socket) {

        ClientInfo info = null;

        String ip = socket.getInetAddress().getHostAddress();

        try (

                Socket clientSocket = socket;

                BufferedReader in = new BufferedReader(

                        new InputStreamReader(

                                clientSocket.getInputStream(),

                                StandardCharsets.UTF_8

                        )

                );

                PrintWriter out = new PrintWriter(

                        clientSocket.getOutputStream(),

                        true,

                        StandardCharsets.UTF_8

                )

        ) {

            clientSocket.setKeepAlive(true);
            clientSocket.setSoTimeout(60000);
            clientWriters.put(clientSocket, out);

            addLog("Có kết nối từ " + ip);

            String line;

            while (true) {
                try {
                    line = in.readLine();
                } catch (java.net.SocketTimeoutException ste) {
                    try {
                        out.println("PING");
                        out.flush();
                        clientSocket.setSoTimeout(15000);
                        line = in.readLine();
                        clientSocket.setSoTimeout(60000);
                    } catch (Exception ex) {
                        break;
                    }
                }
                if (line == null)
                    break;
                line = line.trim();
                if (line.isEmpty())
                    continue;

                if (line.startsWith("DISCONNECT") || "QUIT".equals(line)) {
                    break;
                }

                if (line.startsWith("REGISTER|")) {

                    // Định dạng: REGISTER|mãMáy|tênMáy|macAddress|authToken
                    String[] parts = line.substring(9).split("\\|", 4);
                    String name = parts[0].trim();
                    String tenMay = (parts.length > 1) ? parts[1].trim() : name;
                    String mac = (parts.length > 2) ? parts[2].trim() : "";
                    String token = (parts.length > 3) ? parts[3].trim() : "";

                    if (name.isEmpty()) {
                        name = "PC-" + ip.replace('.', '-');
                    }

                    // Xác thực chữ ký HMAC-SHA256 Token
                    boolean authOk = false;
                    if (!token.isEmpty()) {
                        authOk = quanlyphongmay.common.SecurityUtil.verifyHmac(
                                name + "|" + tenMay + "|" + mac,
                                quanlyphongmay.common.SecurityUtil.SHARED_HMAC_SECRET,
                                token
                        );
                    }
                    if (!authOk) {
                        out.println("REJECTED|AUTH_FAILED");
                        out.flush();
                        addLog("[Bảo mật] Chặn kết nối không có chữ ký xác thực HMAC hợp lệ từ " + ip + " (Mã: " + name + ")");
                        break;
                    }

                    // ── Kiểm tra trùng lặp hoặc kết nối lại: nếu máy đã Online ──────────
                    ClientInfo existing = allKnownClients.get(name);
                    if (existing == null && !mac.isEmpty()) {
                        for (ClientInfo ci : allKnownClients.values()) {
                            if (mac.equalsIgnoreCase(ci.mac)) {
                                existing = ci;
                                break;
                            }
                        }
                    }

                    if (existing != null && "Online".equals(existing.status)) {
                        // Nếu cùng IP kết nối lại (reconnect / khôi phục kết nối)
                        if (ip.equals(existing.ip)) {
                            Socket oldSocket = clientSockets.get(existing.name);
                            if (oldSocket != null && oldSocket != clientSocket && !oldSocket.isClosed()) {
                                try {
                                    oldSocket.close();
                                } catch (Exception ignored) {
                                }
                                connectedClients.remove(oldSocket);
                                clientWriters.remove(oldSocket);
                            }
                        } else {
                            // Khác IP cố tình dùng trùng tên máy -> Từ chối
                            out.println("REJECTED|ALREADY_LOGGED_IN");
                            out.flush();
                            addLog("[Chặn] Từ chối kết nối trùng tên: " + name + " (" + ip
                                    + ") — máy đang Online từ IP " + existing.ip);
                            break;
                        }
                    }

                    // Kiểm tra trùng lặp IP: máy khác đang Online có cùng IP không
                    boolean ipDuplicate = false;
                    if (!"127.0.0.1".equals(ip) && !"localhost".equalsIgnoreCase(ip)) {
                        for (ClientInfo other : allKnownClients.values()) {
                            if ("Online".equals(other.status) && ip.equals(other.ip) && !name.equals(other.name)) {
                                ipDuplicate = true;
                                break;
                            }
                        }
                    }

                    if (ipDuplicate) {
                        out.println("REJECTED|DUPLICATE_IP");
                        out.flush();
                        addLog("[Chặn] Từ chối kết nối trùng IP (" + ip + "): Máy " + name + " trùng với máy khác!");
                        break;
                    }

                    // Nếu máy này đã biết trước (từng kết nối Offline), giữ thông tin nhưng reset
                    // cache phần cứng để nạp mới 100% động
                    if (existing != null) {
                        if (!existing.name.equals(name)) {
                            allKnownClients.remove(existing.name);
                        }
                        info = existing;
                        info.name = name;
                        info.hardwareDetail = null;
                        info.details = "";
                        info.lastPerfMap = null;
                        info.dxdiagOs = "";
                        info.dxdiagCpu = "";
                        info.dxdiagRam = "";
                        info.dxdiagGpu = "";
                        info.ip = ip;
                        if (!mac.isEmpty()) {
                            info.mac = mac;
                        }
                        info.connectedAt = LocalTime.now().format(
                                DateTimeFormatter.ofPattern("HH:mm:ss"));
                        info.status = "Online";
                    } else {
                        info = new ClientInfo(
                                name,
                                ip,
                                LocalTime.now().format(
                                        DateTimeFormatter.ofPattern("HH:mm:ss")));
                    }

                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        info.details = parts[1].trim();
                    }

                    lastSpecsMap.remove(name);
                    MonitorForm mf = openMonitorForms.remove(name);
                    if (mf != null) {
                        try {
                            mf.dispose();
                        } catch (Exception ignored) {
                        }
                    }
                    ComputerDetailForm cdf = openDetailForms.remove(name);
                    if (cdf != null) {
                        try {
                            cdf.dispose();
                        } catch (Exception ignored) {
                        }
                    }

                    connectedClients.put(clientSocket, info);
                    clientSockets.put(name, clientSocket);
                    allKnownClients.put(name, info); // Lưu vào danh sách tổng
                    saveKnownClients();

                    if (multiScreenForm != null && multiScreenForm.isDisplayable())
                        multiScreenForm.refreshClientGrid();

                    ActivityLogForm.themNhatKy(name, "Kết nối",
                            "Máy " + name + " (" + ip + ") đã kết nối vào hệ thống");
                    addLog("[+] MÁY KẾT NỐI: " + name + " (" + ip + ") đã kết nối thành công!");
                    hienThiThongBaoServerNonBlocking("Máy trạm kết nối",
                            "Máy trạm [" + name + "] (" + ip + ") vừa kết nối vào hệ thống!",
                            JOptionPane.INFORMATION_MESSAGE);

                    refreshTable();

                    out.println("OK|REGISTERED");

                } else if (line.startsWith("INFO|")) {

                    // Định dạng: INFO|mãMáy|OS=...|CPU=...|RAM=...|DISK=...

                    // Bỏ phần mãMáy, chỉ lưu phần thông tin phần cứng

                    if (info != null) {

                        String rawInfo = line.substring(5); // bỏ "INFO|"

                        // rawInfo = "PC01|OS=...|CPU=...|RAM=...|DISK=..."

                        int firstPipe = rawInfo.indexOf('|');

                        if (firstPipe >= 0 && firstPipe < rawInfo.length() - 1) {

                            // Lấy phần sau mãMáy: "OS=...|CPU=...|RAM=...|DISK=..."

                            info.details = rawInfo.substring(firstPipe + 1).trim();

                        } else {

                            // Không có pipe → có thể đã đúng định dạng

                            info.details = rawInfo.trim();

                        }

                        if (info.details.contains("MAC=")) {

                            String[] mParts = info.details.split("##SEP##");

                            for (String mp : mParts) {

                                if (mp.startsWith("MAC=")) {

                                    info.mac = mp.substring(4).trim();
                                    saveKnownClients();
                                    break;

                                }

                            }

                        }

                        addLog("Nhận thông tin phần cứng từ: " + info.name
                                + (info.mac.isEmpty() ? "" : " (MAC: " + info.mac + ")"));
                        lastSpecsMap.put(info.name, info.details);
                        ComputerDetailForm initialCdf = openDetailForms.get(info.name);
                        if (initialCdf != null) {
                            initialCdf.updateRealtimeSpecs(info.details);
                        }

                        refreshTable();

                    }

                    out.println("OK|INFO");
                    try {
                        out.println("REQ_FULL_SPECS");
                        out.flush();
                    } catch (Exception ignored) {
                    }

                } else if (line.startsWith("CHAT|")) {

                    // Định dạng từ Client: CHAT|maMayGui|noidung

                    // parts[1] = maMáy của người GỬI (chính là client này)

                    // parts[2] = nội dung tin nhắn

                    String[] parts = line.split("\\|", 3);

                    if (parts.length == 3) {

                        String senderName = (info != null) ? info.name : ip;

                        String content = parts[2];

                        addLog("Chat từ " + senderName + ": " + content);

                        // Đẩy tin nhắn lên ChatForm của client này (nếu đang mở)

                        deliverMessageToChatForm(senderName, senderName, content);

                    }

                    out.println("OK|CHAT");

                } else if (line.startsWith("NOTIFY|")) {

                    addLog("Thông báo từ " + ip + ": " + line.substring(7));

                    out.println("OK|NOTIFY");

                } else if (line.startsWith("PERF|")) {

                    // Định dạng: PERF|maMay|cpu=45.2|ram=62.1|ramUsed=10/16
                    // GB|disk=35|netUp=1.2|netDown=3.8|gpu=25|ping=12

                    handlePerfPacket(line, info);

                    out.println("OK|PERF");

                } else if (line.startsWith("HARDWARE_DETAIL|")) {

                    handleHardwareDetailPacket(line, info);

                    out.println("OK|HARDWARE_DETAIL");

                } else if (line.startsWith("DXDIAG|")) {

                    // Định dạng:
                    // DXDIAG|maMay|os=...|cpu=...|ram=...|gpu=...|dx=...|bios=...|model=...|maker=...|name=...

                    handleDxDiagPacket(line, info);

                    out.println("OK|DXDIAG");

                } else if (line.startsWith("ALERT|")) {

                    // Định dạng: ALERT|maMay|CPU_HIGH|85.5

                    handleAlertPacket(line, info);

                    out.println("OK|ALERT");

                } else if (line.startsWith("INCIDENT|")) {

                    // INCIDENT|maMay|type|severity|title|description

                    String[] p = line.split("\\|", 6);

                    if (p.length >= 6) {

                        String maMay = p[1];

                        String type = p[2];

                        String severity = p[3];

                        String title = p[4];

                        String desc = p[5];

                        IncidentManager.logIncident(maMay, type, severity, title, desc);

                        addLog("[!] CẢNH BÁO TỪ SERVER [SỰ CỐ " + maMay + "] " + title + " (" + desc + ")");

                        ActivityLogForm.themNhatKy(maMay, "CẢNH BÁO TỪ SERVER",
                                "[" + type + "] " + title + " - " + desc);

                        // Phản hồi cảnh báo 2 chiều trực tiếp xuống Client

                        String serverIncidentPacket = "NOTICE|[!] CẢNH BÁO TỪ SERVER [" + type + "]: Phát hiện vi phạm "
                                + title + " (" + desc + "). Yêu cầu dừng ngay hành vi vi phạm!";

                        sendCommandToClient(maMay, serverIncidentPacket);

                    }

                    out.println("OK|INCIDENT");

                } else if (line.startsWith("INSTALL_RESULT|")) {

                    // INSTALL_RESULT|maMay|fileName|SUCCESS/FAILED|exitCode|message

                    String[] p = line.split("\\|", 6);

                    if (p.length >= 5) {

                        String maMay = p[1];

                        String fName = p[2];

                        boolean success = "SUCCESS".equalsIgnoreCase(p[3]);

                        int exitCode = -1;

                        try {
                            exitCode = Integer.parseInt(p[4].trim());
                        } catch (Exception ignored) {
                        }

                        String msg = (p.length > 5) ? p[5] : "";

                        SilentInstallForm.handleInstallResult(maMay, fName, success, exitCode, msg);

                        addLog("[Cài đặt] [CÀI ĐẶT " + maMay + "] " + fName + " -> "
                                + (success ? "THÀNH CÔNG" : "THẤT BẠI (Code " + exitCode + ")"));

                        ActivityLogForm.themNhatKy(maMay, "Cài đặt phần mềm",
                                fName + ": " + (success ? "Thành công" : "Thất bại (Mã " + exitCode + ")"));

                    }

                    out.println("OK|INSTALL_RESULT");

                } else if (line.startsWith("CLEAN_RESULT|")) {

                    // CLEAN_RESULT|maMay|freedMB|filesDeleted|status

                    String[] p = line.split("\\|", 5);

                    if (p.length >= 5) {

                        String maMay = p[1];

                        String freedMB = p[2];

                        String files = p[3];

                        String status = p[4];

                        addLog("[Dọn rác] [DỌN RÁC " + maMay + "] Đã xóa " + files + " file, giải phóng " + freedMB
                                + " MB (" + status + ")");

                        ActivityLogForm.themNhatKy(maMay, "Dọn dẹp hệ thống",
                                "Xóa " + files + " file, giải phóng " + freedMB + " MB");

                    }

                    out.println("OK|CLEAN_RESULT");

                } else if (line.startsWith("SNAPSHOT_DATA|")) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length >= 3) {
                        String client = parts[1];
                        String b64 = parts[2];
                        try {
                            byte[] bytes = java.util.Base64.getDecoder().decode(b64);
                            File dir = new File("snapshots");
                            if (!dir.exists())
                                dir.mkdirs();
                            String ts = java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            File snapFile = new File(dir, "SNAPSHOT_" + client + "_" + ts + ".jpg");
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(snapFile)) {
                                fos.write(bytes);
                            }
                            addLog("[Ảnh chụp] Đã lưu ảnh chụp màn hình máy " + client + " -> " + snapFile.getName());
                            ActivityLogForm.themNhatKy(client, "Chụp màn hình",
                                    "Lưu ảnh màn hình: " + snapFile.getName());
                        } catch (Exception ex) {
                            addLog("[Ảnh chụp] Lỗi giải mã ảnh từ máy " + client + ": " + ex.getMessage());
                        }
                    }

                } else if (line.startsWith("ACTION_ACK|")) {
                    // Định dạng: ACTION_ACK|maMay|loaiHanhDong|trangThai|chiTietThongBao
                    String[] p = line.split("\\|", 5);
                    if (p.length >= 4) {
                        String maMay = p[1];
                        String loaiHanhDong = p[2];
                        String trangThai = p[3];
                        String chiTiet = (p.length >= 5) ? p[4] : "";

                        addLog("[ACK " + trangThai + "] " + maMay + " -> " + loaiHanhDong + ": " + chiTiet);
                        ActivityLogForm.themNhatKy(maMay, loaiHanhDong, chiTiet + " (" + trangThai + ")");

                        int msgType = "SUCCESS".equalsIgnoreCase(trangThai) ? JOptionPane.INFORMATION_MESSAGE
                                : JOptionPane.WARNING_MESSAGE;
                        hienThiThongBaoServerNonBlocking("Phản hồi từ [" + maMay + "]: " + loaiHanhDong, chiTiet,
                                msgType);

                        if ("KILL_PROCESS".equalsIgnoreCase(loaiHanhDong)) {
                            sendCommandToClient(maMay, "GET_PROCESSES");
                        }
                    }
                    out.println("OK|ACTION_ACK");

                } else if (line.startsWith("SPECS_REALTIME|")) {
                    handleSpecsRealtimePacket(line, info);
                    out.println("OK|SPECS_REALTIME");

                } else if (line.startsWith("PROC_LIST|")) {

                    handleProcessesPacket(line, info);

                    out.println("OK|PROC_LIST");

                } else if (line.startsWith("STARTUP_SERVICES|")) {

                    handleStartupServicesPacket(line, info);

                    out.println("OK|STARTUP_SERVICES");

                } else if (line.startsWith("PROCESSES|")) {

                    // Định dạng: PROCESSES|maMay|proc1~~proc2

                    handleProcessesPacket(line, info);

                    out.println("OK|PROCESSES");

                } else if (line.startsWith("APPS|")) {

                    // Định dạng: APPS|maMay|app1~~app2

                    handleProcessesPacket(line, info);

                    out.println("OK|APPS");

                } else if ("LOCK_REQUEST".equals(line)) {

                    addLog("Đã nhận yêu cầu khóa từ " + ip);

                    out.println("LOCK");

                } else if ("UNLOCK_REQUEST".equals(line)) {

                    addLog("Đã nhận yêu cầu mở khóa từ " + ip);

                    out.println("UNLOCK");

                } else if ("PING".equals(line)) {

                    out.println("PONG");

                } else if (line.startsWith("PONG|")) {

                    try {

                        long sendTime = Long.parseLong(line.substring(5).trim());

                        long rtt = Math.max(0, System.currentTimeMillis() - sendTime);

                        if (info != null) {

                            info.ping = rtt;

                        }

                    } catch (Exception ignored) {
                    }

                } else if ("PONG".equals(line)) {

                    // Client phản hồi PONG đơn giản

                } else if ("QUIT".equals(line)) {

                    break;

                } else {

                    out.println("ERROR|UNKNOWN_COMMAND");

                }

            }

        } catch (IOException e) {

            addLog("Kết nối Client " + ip + " kết thúc: " + e.getMessage());

        } finally {
            clientWriters.remove(socket);

            ClientInfo removed = connectedClients.remove(socket);
            String clientName = (removed != null) ? removed.name : (info != null ? info.name : null);

            // Chỉ xử lý ngắt kết nối nếu đây thực sự là Client đã đăng ký thành công
            if (clientName == null || clientName.trim().isEmpty()) {
                return;
            }

            // Nếu máy này đã có socket mới thay thế đang hoạt động (reconnected), không báo
            // ngắt kết nối
            Socket activeSocket = clientSockets.get(clientName);
            if (activeSocket != null && activeSocket != socket && !activeSocket.isClosed()) {
                return;
            }

            ClientInfo ki = allKnownClients.get(clientName);
            if (ki == null || "Offline".equalsIgnoreCase(ki.status)) {
                // Máy đã offline trước đó hoặc không tồn tại -> Không spam cảnh báo lặp lại
                return;
            }

            clientSockets.remove(clientName);
            ki.status = "Offline";
            ki.cpu = 0;
            ki.ram = 0;
            ki.ping = -1;
            if (removed != null) {
                removed.status = "Offline";
                removed.cpu = 0;
                removed.ram = 0;
                removed.ping = -1;
            }
            if (info != null) {
                info.status = "Offline";
                info.cpu = 0;
                info.ram = 0;
                info.ping = -1;
            }
            saveKnownClients();

            final String fClientName = clientName;
            final String fIp = ip;
            javax.swing.SwingUtilities.invokeLater(() -> {
                refreshTable();
                if (multiScreenForm != null && multiScreenForm.isDisplayable()) {
                    multiScreenForm.refreshClientGrid();
                }
                MonitorForm mf = openMonitorForms.get(fClientName);
                if (mf != null) {
                    mf.setClientOffline();
                }
                ComputerDetailForm cdf = openDetailForms.get(fClientName);
                if (cdf != null) {
                    cdf.setClientOffline();
                }
            });

            ActivityLogForm.themNhatKy(clientName, "Ngắt kết nối",
                    "Máy " + clientName + " (" + ip + ") đã ngắt kết nối khỏi hệ thống");
            addLog("[!] MÁY NGẮT KẾT NỐI: " + clientName + " (" + ip + ") đã ngắt kết nối khỏi hệ thống!");
            hienThiThongBaoServerNonBlocking("Máy trạm ngắt kết nối",
                    "Máy trạm [" + clientName + "] (" + ip + ") đã ngắt kết nối khỏi hệ thống!",
                    JOptionPane.WARNING_MESSAGE);

            try {
                java.awt.Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {
            }
        }

    }

    /**
     * 
     * Gửi tin nhắn chat từ Server Admin xuống Client cụ thể.
     * 
     * Được gọi bởi ChatForm khi admin nhấn "Gửi".
     * 
     */

    public void sendChatMessage(String clientName, String message) {

        Socket targetSocket = clientSockets.get(clientName);

        if (targetSocket == null || targetSocket.isClosed()) {

            javax.swing.SwingUtilities.invokeLater(() ->

            javax.swing.JOptionPane.showMessageDialog(

                    this,

                    "Client '" + clientName + "' chưa kết nối hoặc đã ngắt kết nối.",

                    "Lỗi gửi chat",

                    javax.swing.JOptionPane.ERROR_MESSAGE

            )

            );

            return;

        }

        PrintWriter writer = clientWriters.get(targetSocket);

        if (writer != null) {

            // Gửi xuống client: CHAT|Server|nộiDung

            writer.println("CHAT|Server|" + message);

            writer.flush();

            addLog("[Server → " + clientName + "] " + message);

        }

    }

    public void broadcastChatMessage(String message) {
        if (message == null || message.trim().isEmpty())
            return;
        java.util.List<String> online = getOnlineClientNames();
        int sent = 0;
        for (String cName : online) {
            Socket s = clientSockets.get(cName);
            if (s != null && !s.isClosed()) {
                PrintWriter writer = clientWriters.get(s);
                if (writer != null) {
                    writer.println("CHAT|Server|" + message);
                    writer.flush();
                    sent++;
                }
            }
        }
        addLog("[Server → Toàn phòng (" + sent + " máy)] " + message);
    }

    public boolean isClientOnline(String clientName) {
        if (clientName == null || clientName.trim().isEmpty())
            return false;
        Socket s = clientSockets.get(clientName);
        if (s != null && !s.isClosed())
            return true;
        ClientInfo ki = allKnownClients.get(clientName);
        return ki != null && "Online".equalsIgnoreCase(ki.status);
    }

    /**
     * 
     * Đăng ký ChatForm đang mở cho một client.
     * 
     * Gọi khi ChatForm được mở từ btnChatActionPerformed.
     * 
     */

    public void registerChatForm(String clientName, ChatForm form) {

        openChatForms.put(clientName, form);

    }

    /**
     * 
     * Hủy đăng ký ChatForm khi cửa sổ chat bị đóng.
     * 
     */

    public void unregisterChatForm(String clientName) {

        openChatForms.remove(clientName);

    }

    // ── MonitorForm map ────────────────────────────────────────────────────────

    private final java.util.concurrent.ConcurrentHashMap<String, ComputerDetailForm> openDetailForms = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, String> lastSpecsMap = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.ConcurrentHashMap<String, MonitorForm> openMonitorForms

            = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 
     * Mở (hoặc focus) MonitorForm cho client được chọn.
     * 
     * Khi mở lần đầu, push ngay dữ liệu đã cache trong ClientInfo.
     * 
     */

    public MonitorForm showMonitorForm(String clientName, String clientIP) {

        MonitorForm existing = openMonitorForms.get(clientName);

        if (existing != null && existing.isDisplayable()) {

            existing.toFront();

            existing.requestFocus();

            return existing;

        }

        MonitorForm form = new MonitorForm(clientName, clientIP, cmd -> {

            try {

                sendCommandToClient(clientName, cmd);

            } catch (Exception ex) {

                addLog("Lỗi gửi lệnh tới " + clientName + ": " + ex.getMessage());

            }

        }, screenStreamServer);

        form.setOnOpenMultiScreen(this::openMultiScreenForm);

        form.addWindowListener(new java.awt.event.WindowAdapter() {

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {

                openMonitorForms.remove(clientName);

            }

        });

        openMonitorForms.put(clientName, form);

        // Push ngay dữ liệu đã cache nếu có

        ClientInfo cached = allKnownClients.get(clientName);

        if (cached != null) {

            // Push PERF cache

            form.updatePerf(

                    cached.cpu, cached.ram, cached.ramLabel,

                    cached.disk, cached.netUp, cached.netDown,

                    cached.gpu, cached.ping

            );

            // Push DxDiag cache nếu đã nhận

            if (!cached.dxdiagOs.isEmpty()) {

                form.updateDxDiag(

                        cached.dxdiagOs, cached.dxdiagCpu, cached.dxdiagRam,

                        cached.dxdiagGpu, cached.dxdiagDx, cached.dxdiagBios,

                        cached.dxdiagModel, cached.dxdiagMaker, cached.dxdiagName

                );

            }

            // Push cached alerts đã ghi nhận trước đó

            if (cached.cachedAlerts != null) {

                for (String[] alert : cached.cachedAlerts) {

                    form.addAlert(alert[0], alert[1]);

                }

            }

            // Push cached hardware detail & last perf nếu có

            if (cached.lastPerfMap != null && !cached.lastPerfMap.isEmpty()) {

                form.updatePerf(cached.lastPerfMap, cached.cpu, cached.ram, cached.ramLabel, cached.disk, cached.netUp,
                        cached.netDown, cached.gpu, cached.ping);

            }

            if (cached.hardwareDetail != null) {
                java.util.Map<String, String> kv = new java.util.HashMap<>();
                for (String pair : cached.hardwareDetail.split("\\|")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        kv.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                    }
                }
                if (cached.ip != null && !cached.ip.isEmpty()) {
                    kv.put("client_ip", cached.ip);
                    if (!kv.containsKey("ethIpv4") || "N/A".equalsIgnoreCase(kv.get("ethIpv4"))) {
                        kv.put("ethIpv4", cached.ip);
                        kv.put("ethIp", cached.ip);
                    }
                }
                boolean isVmCache = "Yes".equalsIgnoreCase(kv.get("isVm")) || "true".equalsIgnoreCase(kv.get("is_vm"));
                if (isVmCache) {
                    kv.put("has_wifi", "false");
                    kv.put("wifiName", "N/A");
                    kv.put("wifiIpv4", "N/A");
                    kv.put("wifiIpv6", "N/A");
                }
                form.updateHardwareDetail(kv);
            } else {
                java.util.Map<String, String> kv = new java.util.HashMap<>();
                kv.put("client_ip", clientIP);
                kv.put("ethIpv4", clientIP);
                kv.put("ethIp", clientIP);
                kv.put("has_wifi", "false");
                form.updateHardwareDetail(kv);
            }

            // Yêu cầu Client gửi Hardware Detail và danh sách tiến trình

            sendCommandToClient(clientName, "GET_HARDWARE_DETAIL");

            sendCommandToClient(clientName, "GET_PROCESSES");

        }

        form.setVisible(true);

        return form;

    }

    /**
     * Hiển thị Toast / Popup thông báo không chặn (Non-blocking) trên Máy chủ
     * Server
     * Tự động biến mất sau 7 giây hoặc khi đóng, không làm treo Socket thread.
     */
    public void hienThiThongBaoServerNonBlocking(String title, String message, int messageType) {
        SwingUtilities.invokeLater(() -> {
            try {
                JDialog dialog = new JDialog(this, title, false);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                JPanel panel = new JPanel(new BorderLayout(15, 12));
                panel.setBackground(UITheme.BG_CARD);
                panel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UITheme.BORDER, 1),
                        BorderFactory.createEmptyBorder(12, 16, 12, 16)));

                Color iconColor = (messageType == JOptionPane.ERROR_MESSAGE) ? UITheme.DANGER
                        : (messageType == JOptionPane.WARNING_MESSAGE) ? UITheme.WARNING : UITheme.SUCCESS;
                String symbol = (messageType == JOptionPane.ERROR_MESSAGE) ? "✕"
                        : (messageType == JOptionPane.WARNING_MESSAGE) ? "⚠" : "✓";
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
                JLabel lblMsg = new JLabel(
                        "<html><body style='width: 320px; color: #f1f5f9;'>" + message + "</body></html>");
                lblMsg.setFont(UITheme.FONT_BODY);
                msgPanel.add(lblTitle);
                msgPanel.add(lblMsg);
                panel.add(msgPanel, BorderLayout.CENTER);

                JButton btnClose = new JButton("Đóng (7s)");
                UITheme.styleButtonPrimary(btnClose);
                btnClose.addActionListener(e -> dialog.dispose());
                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                btnPanel.setOpaque(false);
                btnPanel.add(btnClose);
                panel.add(btnPanel, BorderLayout.SOUTH);

                dialog.setContentPane(panel);
                dialog.pack();
                dialog.setLocationRelativeTo(this);
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);

                Timer t = new Timer(7000, e -> {
                    if (dialog.isDisplayable()) {
                        dialog.dispose();
                    }
                });
                t.setRepeats(false);
                t.start();
            } catch (Exception ignored) {
            }
        });
    }

    // ── PERF handler ───────────────────────────────────────────────────────────

    // Đếm số gói PERF nhận để tránh log spam

    private final java.util.concurrent.atomic.AtomicInteger perfCount = new java.util.concurrent.atomic.AtomicInteger(
            0);

    private void handlePerfPacket(String line, ClientInfo info) {

        // PERF|maMay|cpu=45.2|ram=62.1|ramUsed=10.2/16.0
        // GB|disk=35|netUp=1.2|netDown=3.8|gpu=25|ping=12

        try {

            String[] parts = line.split("\\|");

            if (parts.length < 3)
                return;

            // parts[0]=PERF, parts[1]=maMay, parts[2..n]=key=value

            double cpu = 0, ram = 0, disk = 0, netUp = 0, netDown = 0, gpu = 0;

            long ping = -1;

            String ramLabel = "";

            java.util.Map<String, String> perfMap = new java.util.HashMap<>();

            for (int i = 2; i < parts.length; i++) {

                String kv = parts[i];

                int eq = kv.indexOf('=');

                if (eq > 0) {

                    perfMap.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());

                }

                if (kv.startsWith("cpu="))
                    cpu = Math.min(100.0, Math.max(0.0, dbl(kv.substring(4))));

                else if (kv.startsWith("ram="))
                    ram = Math.min(100.0, Math.max(0.0, dbl(kv.substring(4))));

                else if (kv.startsWith("ramUsed="))
                    ramLabel = kv.substring(8);

                else if (kv.startsWith("disk="))
                    disk = Math.min(100.0, Math.max(0.0, dbl(kv.substring(5))));

                else if (kv.startsWith("netUp="))
                    netUp = dbl(kv.substring(6));

                else if (kv.startsWith("netDown="))
                    netDown = dbl(kv.substring(8));

                else if (kv.startsWith("gpu="))
                    gpu = Math.min(100.0, Math.max(0.0, dbl(kv.substring(4))));

                else if (kv.startsWith("ping="))
                    ping = (long) dbl(kv.substring(5));

            }

            // Lưu vào ClientInfo để cache và hiển thị trong table

            if (info != null) {

                info.cpu = cpu;

                info.ram = ram;

                info.ping = ping;

                info.disk = disk;

                info.netUp = netUp;

                info.netDown = netDown;

                info.gpu = gpu;

                info.ramLabel = ramLabel;

            }

            // Cập nhật MonitorForm nếu đang mở

            final String name = (info != null) ? info.name : parts[1];

            final double fCpu = cpu, fRam = ram, fDisk = disk;

            final double fNetUp = netUp, fNetDown = netDown, fGpu = gpu;

            final long fPing = ping;

            final String fRamLabel = ramLabel;

            // Log mỗi 20 gói để tránh spam (mỗi ~100 giây với interval 5s)

            int cnt = perfCount.incrementAndGet();

            if (cnt % 20 == 1) {

                addLog("[PERF] PERF [" + name + "] CPU=" + String.format("%.1f", cpu) + "% RAM="
                        + String.format("%.1f", ram) + "%");

            }

            if (info != null) {

                info.lastPerfMap = perfMap;

            }

            MonitorForm mf = openMonitorForms.get(name);

            if (mf != null) {

                mf.updatePerf(perfMap, fCpu, fRam, fRamLabel, fDisk, fNetUp, fNetDown, fGpu, fPing);

            }

            // Tự động kiểm tra và gửi cảnh báo RAM/Disk xuống Client khi vượt ngưỡng

            kiemTraCanhBaoTaiNguyen(name, ram, disk);

            // Cập nhật table (CPU%, RAM%, Ping)

            javax.swing.SwingUtilities.invokeLater(this::refreshTable);

        } catch (Exception e) {

            logger.warning("[Server] handlePerfPacket lỗi: " + e.getMessage());

        }

    }

    // ── DXDIAG handler ─────────────────────────────────────────────────────────

    private void handleHardwareDetailPacket(String line, ClientInfo info) {
        try {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3)
                return;
            String clientName = (info != null) ? info.name : parts[1];
            String data = parts[2];
            if (info != null) {
                info.hardwareDetail = data;
            }

            java.util.Map<String, String> kv = new java.util.HashMap<>();
            for (String pair : data.split("\\|")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    kv.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
            if (info != null && info.ip != null && !info.ip.isEmpty()) {
                kv.put("client_ip", info.ip);
                if (!kv.containsKey("ethIpv4") || "N/A".equalsIgnoreCase(kv.get("ethIpv4"))) {
                    kv.put("ethIpv4", info.ip);
                    kv.put("ethIp", info.ip);
                }
            }
            boolean isVmHw = "Yes".equalsIgnoreCase(kv.get("isVm")) || "true".equalsIgnoreCase(kv.get("is_vm"));
            if (isVmHw) {
                kv.put("has_wifi", "false");
                kv.put("wifiName", "N/A");
                kv.put("wifiIpv4", "N/A");
                kv.put("wifiIpv6", "N/A");
            }

            lastSpecsMap.put(clientName, data);

            MonitorForm mf = openMonitorForms.get(clientName);
            if (mf != null) {
                mf.updateHardwareDetail(kv);
            }
            ComputerDetailForm cdf = openDetailForms.get(clientName);
            if (cdf != null) {
                cdf.updateRealtimeSpecs(data);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleDxDiagPacket(String line, ClientInfo info) {

        // DXDIAG|maMay|os=...|cpu=...|ram=...|gpu=...|dx=...|bios=...|model=...|maker=...|name=...

        try {

            String[] parts = line.split("\\|");

            if (parts.length < 3)
                return;

            String os = "", cpu = "", ram = "", gpu = "", dx = "", bios = "", model = "", maker = "", name = "";

            for (int i = 2; i < parts.length; i++) {

                String kv = parts[i];

                if (kv.startsWith("os="))
                    os = kv.substring(3);

                else if (kv.startsWith("cpu="))
                    cpu = kv.substring(4);

                else if (kv.startsWith("ram="))
                    ram = kv.substring(4);

                else if (kv.startsWith("gpu="))
                    gpu = kv.substring(4);

                else if (kv.startsWith("dx="))
                    dx = kv.substring(3);

                else if (kv.startsWith("bios="))
                    bios = kv.substring(5);

                else if (kv.startsWith("model="))
                    model = kv.substring(6);

                else if (kv.startsWith("maker="))
                    maker = kv.substring(6);

                else if (kv.startsWith("name="))
                    name = kv.substring(5);

            }

            // Lưu DxDiag vào ClientInfo.details để ComputerDetailForm dùng

            if (info != null) {

                info.details = "OS=" + os + "##SEP##CPU=" + cpu + "##SEP##RAM="

                        + ram + "##SEP##DISK=" + gpu;

                info.dxdiagOs = os;

                info.dxdiagCpu = cpu;

                info.dxdiagRam = ram;

                info.dxdiagGpu = gpu;

                info.dxdiagDx = dx;

                info.dxdiagBios = bios;

                info.dxdiagModel = model;

                info.dxdiagMaker = maker;

                info.dxdiagName = name;

            }

            // Cập nhật MonitorForm nếu đang mở

            final String clientName = (info != null) ? info.name : parts[1];

            final String fOs = os, fCpu = cpu, fRam = ram, fGpu = gpu;

            final String fDx = dx, fBios = bios, fModel = model, fMaker = maker, fName = name;

            MonitorForm mf = openMonitorForms.get(clientName);

            if (mf != null) {

                mf.updateDxDiag(fOs, fCpu, fRam, fGpu, fDx, fBios, fModel, fMaker, fName);

            }

            addLog("[DxDiag] DxDiag nhận từ: " + clientName + " – " + os);

            javax.swing.SwingUtilities.invokeLater(this::refreshTable);

        } catch (Exception ignored) {
        }

    }

    // ── ALERT handler (Tiếp nhận & cảnh báo 2 chiều Client - Server) ───────────

    private void handleAlertPacket(String line, ClientInfo info) {

        // ALERT|maMay|RAM_FULL|ChiTietLyDo hoặc ALERT|maMay|DISK_FULL|ChiTietLyDo

        try {

            String[] parts = line.split("\\|", 4);

            if (parts.length < 4)
                return;

            String clientName = (info != null) ? info.name : parts[1];

            String alertType = parts[2];

            String alertValue = parts[3];

            String displayMsg = switch (alertType) {

                case "RAM_FULL" -> "BỘ NHỚ RAM ĐẦY: " + alertValue;

                case "DISK_FULL" -> "Ổ ĐĨA C: SẮP HẾT DUNG LƯỢNG: " + alertValue;

                case "CPU_HIGH" -> "Tài nguyên CPU quá tải: " + alertValue;

                case "RAM_HIGH" -> "Bộ nhớ RAM quá tải: " + alertValue;

                case "DISK_HIGH" -> "Dung lượng ổ đĩa gần đầy: " + alertValue;

                default -> alertType + ": " + alertValue;

            };

            // 1. Ghi vào nhật ký màn hình chính Server

            addLog("[!] CẢNH BÁO TỪ CLIENT [" + clientName + "] " + displayMsg);

            // 2. Ghi nhận sự cố vào Nhật ký hoạt động chung (ActivityLogForm)

            ActivityLogForm.themNhatKy(clientName, "CẢNH BÁO TỪ CLIENT", displayMsg);

            // 3. Đẩy an toàn lên khu vực giao diện "Nhật ký Cảnh báo" trên MonitorForm

            MonitorForm mf = openMonitorForms.get(clientName);

            if (mf != null) {

                mf.addAlert(alertType, alertValue);

            }

            // 4. Lưu vào danh sách cache để hiển thị ngay khi MonitorForm được mở sau

            if (info != null) {

                info.cachedAlerts.add(new String[] { alertType, alertValue });

                if (info.cachedAlerts.size() > 50) {

                    info.cachedAlerts.remove(0);

                }

            }

            // 4. Phản hồi cảnh báo 2 chiều từ Server xuống Client (cooldown 60s để tránh
            // lặp)

            long now = System.currentTimeMillis();

            String cooldownKey = clientName + "_" + alertType;

            Long lastAlert = lastAlertCooldown.get(cooldownKey);

            if (lastAlert == null || (now - lastAlert) > 60000L) {

                lastAlertCooldown.put(cooldownKey, now);

                String serverWarnPacket = "NOTICE|[CẢNH BÁO TỪ SERVER] " + displayMsg
                        + ". Vui lòng kiểm tra và xử lý ngay!";

                sendCommandToClient(clientName, serverWarnPacket);

            }

        } catch (Exception ignored) {
        }

    }

    // ── PROCESSES & APPS handler ───────────────────────────────────────────────

    void handleSpecsRealtimePacket(String line, ClientInfo info) {
        try {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3)
                return;
            String clientName = (info != null) ? info.name : parts[1];
            String data = parts[2];

            lastSpecsMap.put(clientName, data);
            if (info != null) {
                info.details = data;
            }
            ClientInfo kiInfo = allKnownClients.get(clientName);
            if (kiInfo != null) {
                kiInfo.details = data;
            }

            String mac = extractMacFromText(data);
            if (mac != null && !mac.isEmpty()) {
                if (info != null)
                    info.mac = mac;
                ClientInfo ki = allKnownClients.get(clientName);
                if (ki != null)
                    ki.mac = mac;
            }

            ComputerDetailForm cdf = openDetailForms.get(clientName);
            if (cdf == null) {
                for (java.util.Map.Entry<String, ComputerDetailForm> entry : openDetailForms.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(clientName) || clientName.contains(entry.getKey())
                            || entry.getKey().contains(clientName)) {
                        cdf = entry.getValue();
                        break;
                    }
                }
            }
            if (cdf == null && openDetailForms.size() == 1) {
                cdf = openDetailForms.values().iterator().next();
            }

            if (cdf != null) {
                cdf.updateRealtimeSpecs(data);
            }

            MonitorForm mf = openMonitorForms.get(clientName);
            if (mf != null) {
                java.util.Map<String, String> kv = new java.util.HashMap<>();
                String delimiter = data.contains(";;") ? ";;" : "\\|";
                for (String pair : data.split(delimiter)) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        kv.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                    }
                }
                if (info != null && info.ip != null && !info.ip.isEmpty()) {
                    kv.put("client_ip", info.ip);
                    if (!kv.containsKey("ethIpv4") || "N/A".equalsIgnoreCase(kv.get("ethIpv4"))) {
                        kv.put("ethIpv4", info.ip);
                        kv.put("ethIp", info.ip);
                    }
                }
                boolean isVmReal = "Yes".equalsIgnoreCase(kv.get("isVm")) || "true".equalsIgnoreCase(kv.get("is_vm"));
                if (isVmReal) {
                    kv.put("has_wifi", "false");
                    kv.put("wifiName", "N/A");
                    kv.put("wifiIpv4", "N/A");
                    kv.put("wifiIpv6", "N/A");
                    kv.put("gpuCount", "0");
                }
                mf.updateHardwareDetail(kv);
            }
        } catch (Exception ignored) {
        }
    }

    void handleStartupServicesPacket(String line, ClientInfo info) {
        try {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3)
                return;
            String clientName = (info != null) ? info.name : parts[1];
            String data = parts[2];

            MonitorForm mf = openMonitorForms.get(clientName);
            if (mf != null) {
                mf.updateServicesList(data);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleProcessesPacket(String line, ClientInfo info) {

        try {

            String[] parts = line.split("\\|", 3);

            if (parts.length < 3)
                return;

            String clientName = (info != null) ? info.name : parts[1];

            String data = parts[2];

            MonitorForm mf = openMonitorForms.get(clientName);

            if (mf != null) {

                mf.updateProcessList(data);

            }

        } catch (Exception ignored) {
        }

    }

    private void handleAppsPacket(String line, ClientInfo info) {

        handleProcessesPacket(line, info);

    }

    /** Parse double an toàn (hỗ trợ cả dấu chấm . và dấu phẩy , theo locale) */

    private double dbl(String s) {

        if (s == null)
            return 0;

        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        }

        catch (Exception e) {
            return 0;
        }

    }

    /**
     * 
     * Đẩy tin nhắn nhận từ client lên ChatForm tương ứng (nếu đang mở).
     * 
     * Chạy trên EDT để cập nhật UI an toàn.
     * 
     */

    private void deliverMessageToChatForm(String clientName,

            String senderLabel, String content) {

        ChatForm form = openChatForms.get(clientName);

        if (form != null && form.isDisplayable()) {

            javax.swing.SwingUtilities.invokeLater(() ->

            form.receiveMessage(senderLabel, content)

            );

        } else {
            // Đẩy lên ChatForm đang mở bất kỳ
            for (ChatForm cf : openChatForms.values()) {
                if (cf != null && cf.isDisplayable()) {
                    javax.swing.SwingUtilities.invokeLater(() -> cf.receiveMessage(senderLabel, content));
                    return;
                }
            }
        }

    }

    public void sendCommandToClient(String clientName, String command) {

        Socket socket = clientSockets.get(clientName);

        if (socket == null || socket.isClosed()

                || !socket.isConnected()) {

            throw new IllegalStateException(

                    "Client chưa kết nối hoặc đã ngắt kết nối."

            );

        }

        PrintWriter writer = clientWriters.get(socket);

        if (writer == null || writer.checkError()) {

            throw new IllegalStateException(

                    "Không tìm thấy luồng gửi dữ liệu của Client."

            );

        }

        writer.println(command);

        writer.flush();

        if (writer.checkError()) {

            throw new IllegalStateException("Gửi lệnh thất bại.");

        }

        addLog("Đã gửi lệnh " + command + " đến " + clientName);

    }

    private void startServer() {

        if (serverRunning) {

            return;

        }

        final int port;

        try {

            port = Integer.parseInt(txtPort.getText().trim());

            if (port < 1 || port > 65535) {

                throw new NumberFormatException();

            }

        } catch (NumberFormatException e) {

            JOptionPane.showMessageDialog(

                    this,

                    "Cổng phải là số nguyên từ 1 đến 65535.",

                    "Cổng không hợp lệ",

                    JOptionPane.WARNING_MESSAGE

            );

            return;

        }

        // Khóa chặt 1 Server duy nhất (Single Server Active Enforcement): Dò tìm xem đã có Server nào đang chạy trên mạng LAN chưa
        try {
            boolean activeInLan = ServerDiscoveryResponder.isAnotherServerActive(5001, 700)
                    || ServerDiscoveryResponder.isAnotherServerActive(8888, 700);
            if (activeInLan) {
                JOptionPane.showMessageDialog(
                        this,
                        "[Khóa Server Duy Nhất] Đã phát hiện một Máy chủ Server khác đang hoạt động trong mạng LAN!\n\n"
                                + "Hệ thống chỉ cho phép duy nhất 1 Server hoạt động để bảo đảm an toàn dữ liệu.",
                        "Cảnh báo vi phạm bảo mật Server",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
        } catch (Exception ignored) {}

        try {

            serverSocket = new ServerSocket(

                    port, 50, InetAddress.getByName("0.0.0.0")

            );

            serverRunning = true;

            btnStartServer.setEnabled(false);

            btnStopServer.setEnabled(true);

            txtPort.setEnabled(false);

            String currentIp = getLanIP();

            lblServerIP.setText("IP máy chủ (LAN): " + currentIp);

            lblFooterIP.setText("IP: " + currentIp);

            lblServerStatus.setText("Server đang chạy - Port " + port);

            lblServerPort.setText("Cổng: " + port);

            lblFooterPort.setText("Port: " + port);

            lblFooterStatus.setText("Server đang chạy");

            addLog("Server khởi động thành công tại cổng " + port);

            // Khởi động UDP Auto-Discovery Responder (Port 8888)

            discoveryResponder = new ServerDiscoveryResponder(port, "Server Phòng Máy");

            discoveryResponder.start();

            addLog("[LAN] Đã kích hoạt UDP Auto-Discovery trên cổng 8888");

            // Khởi động Screen Stream Server (Port 5001)

            screenStreamServer = new ScreenStreamServer(5001);

            screenStreamServer.start();

            addLog("[PC] Đã kích hoạt Screen Stream Server trên cổng 5001");

            clientExecutor.submit(() -> {

                while (serverRunning) {

                    try {

                        Socket socket = serverSocket.accept();

                        clientExecutor.submit(() -> {

                            handleClient(socket);

                        });

                    } catch (IOException e) {

                        if (serverRunning) {

                            addLog(

                                    "Lỗi nhận kết nối: " + e.getMessage()

                            );

                        }

                    }

                }

            });

        } catch (IOException e) {

            serverRunning = false;

            serverSocket = null;

            lblServerStatus.setText("Server chưa khởi động");

            lblFooterStatus.setText("Server chưa khởi động");

            addLog("Không thể khởi động Server: " + e.getMessage());

            JOptionPane.showMessageDialog(

                    this,

                    "Không thể mở cổng " + port + ":\n" + e.getMessage(),

                    "Lỗi khởi động Server",

                    JOptionPane.ERROR_MESSAGE

            );

        }

    }

    private void stopServer() {

        serverRunning = false;

        if (discoveryResponder != null) {

            discoveryResponder.stop();

            discoveryResponder = null;

        }

        if (screenStreamServer != null) {

            screenStreamServer.stop();

            screenStreamServer = null;

        }

        try {

            if (serverSocket != null) {

                serverSocket.close();

                serverSocket = null;

            }

        } catch (IOException e) {

            addLog("Lỗi khi dừng Server: " + e.getMessage());

        }

        for (Socket socket : connectedClients.keySet()) {

            try {

                socket.close();

            } catch (IOException ignored) {

            }

        }

        connectedClients.clear();

        refreshTable();

        btnStartServer.setEnabled(true);

        btnStopServer.setEnabled(false);

        txtPort.setEnabled(true);

        lblServerStatus.setText("Server chưa khởi động");

        lblFooterStatus.setText("Server chưa khởi động");

        lblServerIP.setText("IP máy chủ (LAN): " + getLanIP());

        lblFooterIP.setText("IP: " + getLanIP());

        lblServerPort.setText("Cổng: " + txtPort.getText());

        lblFooterPort.setText("Port: " + txtPort.getText());

        addLog("Server đã dừng.");

    }

    private void refreshTable() {

        if (!SwingUtilities.isEventDispatchThread()) {

            SwingUtilities.invokeLater(this::refreshTable);

            return;

        }

        clientTableModel.setRowCount(0);

        // Hiển thị TẤT CẢ máy đã từng kết nối (Online + Offline)

        java.util.List<ClientInfo> allClients =

                new java.util.ArrayList<>(allKnownClients.values());

        allClients.sort(

                java.util.Comparator.comparing(

                        info -> info.name,

                        String.CASE_INSENSITIVE_ORDER

                )

        );

        int stt = 1;

        int onlineCount = 0;

        int offlineCount = 0;

        for (ClientInfo info : allClients) {

            clientTableModel.addRow(new Object[] {

                    stt++,

                    info.name,

                    info.ip,

                    info.status,

                    info.connectedAt,

                    info.details

            });

            if ("Online".equals(info.status)) {

                onlineCount++;

            } else {

                offlineCount++;

            }

        }

        int total = allClients.size();

        lblOnlineCount.setText(String.valueOf(onlineCount));

        lblOfflineCount.setText(String.valueOf(offlineCount));

        lblTotalCount.setText(String.valueOf(total));

        // Cập nhật danh sách máy online cho các cửa sổ ChatForm đang mở
        java.util.List<String> currentOnline = getOnlineClientNames();
        for (ChatForm cf : openChatForms.values()) {
            if (cf != null && cf.isDisplayable()) {
                cf.refreshClientList(currentOnline);
            }
        }

    }

    public String getServerPort() {

        return txtPort.getText().trim();

    }

    public void setServerPort(String port) {

        txtPort.setText(port);

        lblServerPort.setText("Cổng: " + port);

        lblFooterPort.setText("Port: " + port);

    }

    public boolean isServerRunning() {

        return serverRunning;

    }

    public void addLog(String message) {

        SwingUtilities.invokeLater(() -> {

            String time = LocalTime.now().format(

                    DateTimeFormatter.ofPattern("HH:mm:ss"));

            txtServerLog.append(

                    time + " - " + message

                            + System.lineSeparator());

            txtServerLog.setCaretPosition(

                    txtServerLog.getDocument().getLength());

        });

    }

    private static class ClientInfo {

        String name;

        String ip;

        String connectedAt;

        String details = "Chưa gửi thông tin phần cứng";

        String status = "Online"; // "Online" hoặc "Offline"

        String mac = ""; // Địa chỉ MAC dùng cho Wake-on-LAN

        String hardwareDetail = null;

        java.util.Map<String, String> lastPerfMap = new java.util.concurrent.ConcurrentHashMap<>();

        // Metrics realtime (từ gói PERF|) – cache để push khi mở MonitorForm

        double cpu = 0;

        double ram = 0;

        double disk = 0;

        double netUp = 0;

        double netDown = 0;

        double gpu = 0;

        long ping = -1;

        String ramLabel = "";

        // DxDiag fields (từ gói DXDIAG|)

        String dxdiagOs = "";

        String dxdiagCpu = "";

        String dxdiagRam = "";

        String dxdiagGpu = "";

        String dxdiagDx = "";

        String dxdiagBios = "";

        String dxdiagModel = "";

        String dxdiagMaker = "";

        String dxdiagName = "";

        final java.util.List<String[]> cachedAlerts = java.util.Collections
                .synchronizedList(new java.util.ArrayList<>());

        ClientInfo(String name, String ip, String connectedAt) {

            this.name = name;

            this.ip = ip;

            this.connectedAt = connectedAt;

        }

    }

    private String getLanIP() {

        // Cách 1: Dùng DatagramSocket để hệ điều hành tự chọn đúng card mạng chính đang
        // kết nối LAN/Internet

        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {

            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);

            String ip = socket.getLocalAddress().getHostAddress();

            if (ip != null && !ip.equals("0.0.0.0") && !ip.startsWith("127.")) {

                return ip;

            }

        } catch (Exception ignored) {

        }

        // Cách 2: Dự phòng khi phòng máy không có Internet (chỉ nối Switch nội bộ)

        // Duyệt các card mạng đang bật và bỏ qua card ảo (VMware, VirtualBox, WSL,
        // Docker...)

        try {

            String fallbackIp = null;

            java.util.Enumeration<java.net.NetworkInterface> interfaces =

                    java.net.NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {

                java.net.NetworkInterface network = interfaces.nextElement();

                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {

                    continue;

                }

                String displayName = network.getDisplayName().toLowerCase();

                String netName = network.getName().toLowerCase();

                if (displayName.contains("virtual") || displayName.contains("vmware")

                        || displayName.contains("vbox") || displayName.contains("hyper-v")

                        || displayName.contains("wsl") || displayName.contains("docker")

                        || displayName.contains("radmin") || displayName.contains("zerotier")

                        || displayName.contains("loopback") || netName.startsWith("veth")) {

                    continue;

                }

                java.util.Enumeration<InetAddress> addresses = network.getInetAddresses();

                while (addresses.hasMoreElements()) {

                    InetAddress address = addresses.nextElement();

                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {

                        String ip = address.getHostAddress();

                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {

                            return ip; // Ưu tiên IP LAN chuẩn

                        }

                        if (fallbackIp == null) {

                            fallbackIp = ip;

                        }

                    }

                }

            }

            if (fallbackIp != null) {

                return fallbackIp;

            }

        } catch (Exception ignored) {

        }

        return "127.0.0.1";

    }

    /**
     * 
     * This method is called from within the constructor to initialize the form.
     * 
     * WARNING: Do NOT modify this code. The content of this method is always
     * 
     * regenerated by the Form Editor.
     * 
     */

    private void initComponents() {

        scrServerLog = new javax.swing.JScrollPane();

        txtServerLog = new javax.swing.JTextArea();

        pnlSidebar = new javax.swing.JPanel();

        btnOverview = new javax.swing.JButton();

        btnComputerList = new javax.swing.JButton();

        btnChat = new javax.swing.JButton();

        btnNotification = new javax.swing.JButton();

        btnActivityLog = new javax.swing.JButton();

        btnMultiScreen = new javax.swing.JButton("[PC] Lưới nhiều máy");

        btnMultiScreen.addActionListener(e -> openMultiScreenForm());

        lblFooterStatus = new javax.swing.JLabel();

        lblFooterIP = new javax.swing.JLabel();

        lblFooterPort = new javax.swing.JLabel();

        lblLogo = new javax.swing.JLabel();

        pnlHeader = new javax.swing.JPanel();

        lblServerStatus = new javax.swing.JLabel();

        lblTitle = new javax.swing.JLabel();

        lblServerIP = new javax.swing.JLabel();

        lblServerPort = new javax.swing.JLabel();

        pnlTotal = new javax.swing.JPanel();

        lblTotalTitle = new javax.swing.JLabel();

        lblTotalCount = new javax.swing.JLabel();

        pnlOnline = new javax.swing.JPanel();

        lblOnlineTitle = new javax.swing.JLabel();

        lblOnlineCount = new javax.swing.JLabel();

        pnlOffline = new javax.swing.JPanel();

        lblOfflineTitle1 = new javax.swing.JLabel();

        lblOfflineCount = new javax.swing.JLabel();

        jPanel1 = new javax.swing.JPanel();

        txtPort = new javax.swing.JTextField();

        lblPortTitle = new javax.swing.JLabel();

        btnStartServer = new javax.swing.JButton();

        btnStopServer = new javax.swing.JButton();

        btnRefresh = new javax.swing.JButton();

        lblClientListTitle = new javax.swing.JLabel();

        pnlClientList = new javax.swing.JPanel();

        scrClients = new javax.swing.JScrollPane();

        tblClients = new javax.swing.JTable();

        btnClearLog = new javax.swing.JButton();

        btnComputerDetail = new javax.swing.JButton();

        pnlLog = new javax.swing.JPanel();

        pnlQuickActions = new javax.swing.JPanel();

        lblQuickTitle = new javax.swing.JLabel();

        btnManageComputer = new javax.swing.JButton();

        btnViewLog = new javax.swing.JButton();

        btnOpenSettings = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        // Server Log text area & scroll pane

        txtServerLog.setColumns(20);

        txtServerLog.setRows(5);

        scrServerLog.setViewportView(txtServerLog);

        // Sidebar components

        btnOverview.setText("Tổng quan");

        btnOverview.addActionListener(this::btnOverviewActionPerformed);

        btnComputerList.setText("Đo khoảng cách");

        btnComputerList.addActionListener(this::btnComputerListActionPerformed);

        btnChat.setText("Chat");

        btnChat.addActionListener(this::btnChatActionPerformed);

        btnNotification.setText("Gửi thông báo");

        btnNotification.addActionListener(this::btnNotificationActionPerformed);

        btnActivityLog.setText("Nhật ký hoạt động");

        btnActivityLog.addActionListener(this::btnActivityLogActionPerformed);

        lblFooterStatus.setText("Server đang chạy");

        lblFooterIP.setText("IP: 192.168.1.10");

        lblFooterPort.setText("Port: 5000");

        // Top Logo

        lblLogo.setText("LANPC - Quản trị Server");

        // Header and Stats cards

        lblTitle.setText("Trạng thái sever");

        lblServerStatus.setText("Đang chạy");

        lblServerIP.setText("IP máy chủ (LAN): ");

        lblServerPort.setText("Cổng: 5000");

        lblOnlineTitle.setText("             Máy Online");

        lblOnlineCount.setText("0");

        lblOfflineTitle1.setText("Máy Offline");

        lblOfflineCount.setText("0");

        lblTotalTitle.setText("Tổng số máy");

        lblTotalCount.setText("0");

        // Controls

        lblPortTitle.setText("Cổng Server:");

        txtPort.setText("5000");

        txtPort.addActionListener(this::txtPortActionPerformed);

        btnStartServer.setText("Khởi động");

        btnStartServer.addActionListener(this::btnStartServerActionPerformed);

        btnStopServer.setText("Dừng");

        btnRefresh.setText("Làm mới");

        btnClearLog.setText("Xóa nhật ký");

        btnComputerDetail.setText("Chi tiết");

        btnComputerDetail.addActionListener(this::btnComputerDetailActionPerformed);

        lblClientListTitle.setText("Danh sách máy Client");

        scrClients.setViewportView(tblClients);

        // Quick Actions

        lblQuickTitle.setText("Thông tin nhanh");

        btnManageComputer.setText("Quản lý máy");

        btnManageComputer.addActionListener(this::btnManageComputerActionPerformed);

        btnViewLog.setText("Xem nhật ký");

        btnViewLog.addActionListener(this::btnViewLogActionPerformed);

        btnOpenSettings.setText("Cài đặt");

        // ═══════════════════════════════════════════════════════════════════════

        // BỐ CỤC PURE JAVA SWING HIỆN ĐẠI (GAME UI/UX DESIGN SYSTEM)

        // ═══════════════════════════════════════════════════════════════════════

        JPanel rootPanel = new JPanel(new BorderLayout(0, 0));

        rootPanel.setBackground(UITheme.BG_DARK);

        // 1. Top Command Header (North)

        JPanel topHeader = new JPanel(new BorderLayout(15, 0));

        topHeader.setBackground(UITheme.BG_SIDEBAR);

        topHeader.setBorder(BorderFactory.createCompoundBorder(

                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),

                BorderFactory.createEmptyBorder(10, 18, 10, 18)

        ));

        lblLogo.setText("LANPC  SYSTEM  ADMINISTRATION");

        lblLogo.setFont(UITheme.FONT_TITLE);

        lblLogo.setForeground(UITheme.ACCENT_LIGHT);

        lblLogo.setIconTextGap(10);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));

        headerRight.setOpaque(false);

        JLabel lblServerBadge = new JLabel("● TRUNG TÂM QUẢN TRỊ MẠNG LAN");

        lblServerBadge.setFont(UITheme.FONT_SMALL);

        lblServerBadge.setForeground(UITheme.SUCCESS);

        headerRight.add(lblServerBadge);

        topHeader.add(lblLogo, BorderLayout.WEST);

        topHeader.add(headerRight, BorderLayout.EAST);

        rootPanel.add(topHeader, BorderLayout.NORTH);

        // 2. Sidebar Điều Hướng Phân Tầng (West)

        pnlSidebar.setLayout(new BorderLayout(0, 12));

        pnlSidebar.setPreferredSize(new Dimension(240, 600));

        pnlSidebar.setBackground(UITheme.BG_SIDEBAR);

        pnlSidebar.setBorder(BorderFactory.createCompoundBorder(

                BorderFactory.createMatteBorder(0, 0, 0, 1, UITheme.BORDER),

                BorderFactory.createEmptyBorder(12, 10, 12, 10)

        ));

        JPanel navContainer = new JPanel();

        navContainer.setLayout(new BoxLayout(navContainer, BoxLayout.Y_AXIS));

        navContainer.setOpaque(false);

        JLabel lblNavGroup1 = new JLabel("GIÁM SÁT & TRUYỀN THÔNG");

        lblNavGroup1.setFont(UITheme.FONT_SMALL);

        lblNavGroup1.setForeground(UITheme.TEXT_MUTED);

        lblNavGroup1.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 4));

        lblNavGroup1.setAlignmentX(Component.LEFT_ALIGNMENT);

        navContainer.add(lblNavGroup1);

        btnMultiScreen.setText("[Lưới máy] Quan Sát Toàn Phòng");

        UITheme.styleButtonSidebar(btnMultiScreen, false);

        btnMultiScreen.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnMultiScreen);

        navContainer.add(Box.createVerticalStrut(4));

        btnOverview.setText("[Tổng quan] Bảng Thông Tin");

        UITheme.styleButtonSidebar(btnOverview, true);

        btnOverview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnOverview);

        navContainer.add(Box.createVerticalStrut(4));

        btnComputerDetail.setText("[Chi tiết] Thông Số Máy Trạm");

        UITheme.styleButtonSidebar(btnComputerDetail, false);

        btnComputerDetail.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnComputerDetail);

        navContainer.add(Box.createVerticalStrut(4));

        btnChat.setText("[Nhắn tin] Chat Phòng Máy");

        UITheme.styleButtonSidebar(btnChat, false);

        btnChat.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnChat);

        navContainer.add(Box.createVerticalStrut(4));

        btnNotification.setText("[Thông báo] Phát Thanh Khẩn");

        UITheme.styleButtonSidebar(btnNotification, false);

        btnNotification.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnNotification);

        navContainer.add(Box.createVerticalStrut(14));

        JLabel lblNavGroup2 = new JLabel("CÔNG CỤ & QUẢN TRỊ");

        lblNavGroup2.setFont(UITheme.FONT_SMALL);

        lblNavGroup2.setForeground(UITheme.TEXT_MUTED);

        lblNavGroup2.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 4));

        lblNavGroup2.setAlignmentX(Component.LEFT_ALIGNMENT);

        navContainer.add(lblNavGroup2);

        btnManageComputer.setText("[Phòng máy] Danh Sách Máy");

        UITheme.styleButtonSidebar(btnManageComputer, false);

        btnManageComputer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnManageComputer);

        navContainer.add(Box.createVerticalStrut(4));

        btnActivityLog.setText("[Nhật ký] Lịch Sử Hoạt Động");

        UITheme.styleButtonSidebar(btnActivityLog, false);

        btnActivityLog.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnActivityLog);

        navContainer.add(Box.createVerticalStrut(4));

        btnComputerList.setText("[Cự ly] Đo Khoảng Cách");

        UITheme.styleButtonSidebar(btnComputerList, false);

        btnComputerList.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnComputerList);

        navContainer.add(Box.createVerticalStrut(4));

        btnOpenSettings.setText("[Cài đặt] Cấu Hình Server");

        UITheme.styleButtonSidebar(btnOpenSettings, false);

        btnOpenSettings.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        navContainer.add(btnOpenSettings);

        JScrollPane scrNav = new JScrollPane(navContainer);

        scrNav.setOpaque(false);

        scrNav.getViewport().setOpaque(false);

        scrNav.setBorder(null);

        pnlSidebar.add(scrNav, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new GridLayout(3, 1, 0, 4));

        footerPanel.setOpaque(false);

        footerPanel.setBorder(BorderFactory.createCompoundBorder(

                BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER),

                BorderFactory.createEmptyBorder(10, 8, 4, 8)

        ));

        lblFooterStatus.setFont(UITheme.FONT_SMALL);

        lblFooterStatus.setForeground(UITheme.SUCCESS);

        lblFooterIP.setFont(UITheme.FONT_SMALL);

        lblFooterIP.setForeground(UITheme.TEXT_MUTED);

        lblFooterPort.setFont(UITheme.FONT_SMALL);

        lblFooterPort.setForeground(UITheme.TEXT_MUTED);

        footerPanel.add(lblFooterStatus);

        footerPanel.add(lblFooterIP);

        footerPanel.add(lblFooterPort);

        pnlSidebar.add(footerPanel, BorderLayout.SOUTH);

        rootPanel.add(pnlSidebar, BorderLayout.WEST);

        // 3. Vùng Nội Dung Trung Tâm (Center Workspace)

        JPanel centerArea = new JPanel(new BorderLayout(0, 12));

        centerArea.setOpaque(false);

        centerArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Hàng HUD 4 Thẻ Thống Kê Hiện Đại (Top Stats)

        JPanel statsRow = new JPanel(new GridLayout(1, 4, 12, 0));

        statsRow.setOpaque(false);

        statsRow.setPreferredSize(new Dimension(800, 102));

        // Card 1: Server Status & Quick Port

        pnlHeader.removeAll();

        pnlHeader.setLayout(new BorderLayout(0, 4));

        pnlHeader.setBackground(UITheme.BG_CARD);

        pnlHeader.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.Border() {
                    @Override
                    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setColor(UITheme.BORDER);
                        g2.drawRect(x, y, width - 1, height - 1);
                        g2.setColor(UITheme.ACCENT);
                        g2.fillRect(x, y, width, 3);
                        g2.dispose();
                    }

                    @Override
                    public Insets getBorderInsets(Component c) {
                        return new Insets(4, 1, 1, 1);
                    }

                    @Override
                    public boolean isBorderOpaque() {
                        return false;
                    }
                },
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        lblTitle.setText("SERVER LAN");

        lblTitle.setFont(UITheme.FONT_SMALL);

        lblTitle.setForeground(UITheme.TEXT_MUTED);

        lblServerStatus.setFont(UITheme.FONT_HEADER);

        lblServerStatus.setForeground(UITheme.SUCCESS);

        JPanel srvInfoBox = new JPanel(new GridLayout(2, 1, 0, 2));

        srvInfoBox.setOpaque(false);

        lblServerIP.setFont(UITheme.FONT_SMALL);

        lblServerIP.setForeground(UITheme.TEXT_PRIMARY);

        lblServerPort.setFont(UITheme.FONT_SMALL);

        lblServerPort.setForeground(UITheme.TEXT_MUTED);

        srvInfoBox.add(lblServerIP);

        srvInfoBox.add(lblServerPort);

        pnlHeader.add(lblTitle, BorderLayout.NORTH);

        pnlHeader.add(lblServerStatus, BorderLayout.CENTER);

        pnlHeader.add(srvInfoBox, BorderLayout.SOUTH);

        statsRow.add(pnlHeader);

        // Card 2: Máy Online
        setupStatCard(pnlOnline, "MÁY ĐANG HOẠT ĐỘNG", lblOnlineCount, "Sẵn sàng giám sát & điều khiển",
                UITheme.SUCCESS);
        statsRow.add(pnlOnline);

        // Card 3: Máy Offline
        setupStatCard(pnlOffline, "MÁY NGOẠI TUYẾN", lblOfflineCount, "Chưa kết nối socket mạng LAN", UITheme.DANGER);
        statsRow.add(pnlOffline);

        // Card 4: Tổng Số Máy
        setupStatCard(pnlTotal, "TỔNG MÁY PHÒNG LAB", lblTotalCount, "Toàn bộ danh mục phòng máy", UITheme.ACCENT);
        statsRow.add(pnlTotal);

        centerArea.add(statsRow, BorderLayout.NORTH);

        // Vùng Giữa: Thanh Điều Khiển, Bảng Danh Sách & Bảng Tác Vụ Nhanh

        JPanel midPanel = new JPanel(new BorderLayout(12, 0));

        midPanel.setOpaque(false);

        // Bảng Client & Control Bar

        jPanel1.removeAll();

        jPanel1.setLayout(new BorderLayout(0, 10));

        jPanel1.setOpaque(false);

        JPanel controlBar = new JPanel(new BorderLayout(10, 0));

        controlBar.setOpaque(false);

        controlBar.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));

        JPanel ctrlLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        ctrlLeft.setOpaque(false);

        lblPortTitle.setFont(UITheme.FONT_BODY);

        lblPortTitle.setForeground(UITheme.TEXT_MUTED);

        ctrlLeft.add(lblPortTitle);

        txtPort.setPreferredSize(new Dimension(75, 32));

        UITheme.styleTextField(txtPort);

        ctrlLeft.add(txtPort);

        UITheme.styleButtonSuccess(btnStartServer);

        UITheme.styleButtonDanger(btnStopServer);

        UITheme.styleButtonGhost(btnRefresh);

        ctrlLeft.add(btnStartServer);

        ctrlLeft.add(btnStopServer);

        ctrlLeft.add(btnRefresh);

        controlBar.add(ctrlLeft, BorderLayout.WEST);

        JPanel ctrlRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));

        ctrlRight.setOpaque(false);

        UITheme.styleButtonGhost(btnClearLog);

        UITheme.styleButtonPrimary(btnComputerDetail);

        ctrlRight.add(btnClearLog);

        ctrlRight.add(btnComputerDetail);

        controlBar.add(ctrlRight, BorderLayout.EAST);

        jPanel1.add(controlBar, BorderLayout.NORTH);

        // Bảng Client danh sách máy

        pnlClientList.removeAll();

        pnlClientList.setLayout(new BorderLayout(0, 6));

        pnlClientList.setOpaque(false);

        lblClientListTitle.setFont(UITheme.FONT_HEADER);

        lblClientListTitle.setForeground(UITheme.ACCENT_LIGHT);

        pnlClientList.add(lblClientListTitle, BorderLayout.NORTH);

        UITheme.styleTable(tblClients);

        UITheme.styleScrollPane(scrClients);

        pnlClientList.add(scrClients, BorderLayout.CENTER);

        jPanel1.add(pnlClientList, BorderLayout.CENTER);

        midPanel.add(jPanel1, BorderLayout.CENTER);

        // Bảng Điều Khiển Tác Vụ Nhanh (Sidebar phải)

        pnlQuickActions.setPreferredSize(new Dimension(220, 360));

        midPanel.add(pnlQuickActions, BorderLayout.EAST);

        centerArea.add(midPanel, BorderLayout.CENTER);

        // 4. Panel Nhật Ký Hệ Thống Terminal (South)

        pnlLog.removeAll();

        pnlLog.setLayout(new BorderLayout(0, 4));

        pnlLog.setPreferredSize(new Dimension(800, 130));

        pnlLog.setBackground(UITheme.BG_CARD);

        pnlLog.setBorder(BorderFactory.createCompoundBorder(

                BorderFactory.createLineBorder(UITheme.BORDER, 1, true),

                BorderFactory.createEmptyBorder(6, 10, 6, 10)

        ));

        JLabel lblLogTitle = new JLabel("NHẬT KÝ HỆ THỐNG THỜI GIAN THỰC");

        lblLogTitle.setFont(UITheme.FONT_SMALL);

        lblLogTitle.setForeground(UITheme.ACCENT_LIGHT);

        pnlLog.add(lblLogTitle, BorderLayout.NORTH);

        txtServerLog.setBackground(UITheme.BG_LOG);

        txtServerLog.setForeground(UITheme.TEXT_PRIMARY);

        txtServerLog.setCaretColor(UITheme.ACCENT_LIGHT);

        txtServerLog.setFont(UITheme.FONT_MONO);

        UITheme.styleScrollPane(scrServerLog);

        pnlLog.add(scrServerLog, BorderLayout.CENTER);

        centerArea.add(pnlLog, BorderLayout.SOUTH);

        rootPanel.add(centerArea, BorderLayout.CENTER);

        setContentPane(rootPanel);

        pack();

    }

    private void openDistanceForm(String targetIP) {

        int port = 5000;

        try {

            port = Integer.parseInt(txtPort.getText().trim());

        } catch (Exception ignored) {
        }

        java.util.List<String> clientDisplayList = new java.util.ArrayList<>();

        for (int i = 0; i < clientTableModel.getRowCount(); i++) {

            String name = String.valueOf(clientTableModel.getValueAt(i, 1));

            String ip = String.valueOf(clientTableModel.getValueAt(i, 2));

            clientDisplayList.add(name + " (" + ip + ")");

        }

        if (targetIP == null || targetIP.isBlank()) {

            int selectedRow = tblClients.getSelectedRow();

            if (selectedRow != -1) {

                targetIP = String.valueOf(clientTableModel.getValueAt(

                        tblClients.convertRowIndexToModel(selectedRow), 2));

            } else if (clientTableModel.getRowCount() > 0) {

                targetIP = String.valueOf(clientTableModel.getValueAt(0, 2));

            } else {

                targetIP = "127.0.0.1";

            }

        }

        RealtimeDistanceForm distanceForm = new RealtimeDistanceForm(targetIP, port, clientDisplayList);

        distanceForm.setLocationRelativeTo(this);

        distanceForm.setVisible(true);

    }

    private void btnComputerListActionPerformed(java.awt.event.ActionEvent evt) {

        openDistanceForm(null);

    }

    private void btnOverviewActionPerformed(java.awt.event.ActionEvent evt) {

        // Xu ly su kien

        JOptionPane.showMessageDialog(

                this,

                "Thông tin tổng quan Server\n"

                        + "Trạng thái: " + (serverRunning ? "Đang chạy" : "Đã dừng")

                        + "\nSố Client đang kết nối: " + connectedClients.size()

                        + "\nIP máy chủ: " + getLanIP()

                        + "\nCổng: " + txtPort.getText(),

                "Tổng quan",

                JOptionPane.INFORMATION_MESSAGE

        );

    }

    private void btnChatActionPerformed(java.awt.event.ActionEvent evt) {
        // Lấy tên máy đang chọn (nếu có)
        String clientName = null;
        int selectedRow = tblClients.getSelectedRow();
        if (selectedRow >= 0) {
            int row = tblClients.convertRowIndexToModel(selectedRow);
            clientName = String.valueOf(clientTableModel.getValueAt(row, 1));
        }

        final String finalClientName = (clientName != null && !clientName.trim().isEmpty())
                ? clientName.trim()
                : "";

        java.util.List<String> online = getOnlineClientNames();

        // Mở ChatForm và truyền ServerForm (this) + danh sách máy online
        ChatForm chatForm = new ChatForm(this, finalClientName, online);
        chatForm.setLocationRelativeTo(this);

        // Đăng ký ChatForm để nhận tin nhắn từ client (kể cả khi mở ở chế độ toàn
        // phòng)
        final String regKey = !finalClientName.isEmpty() ? finalClientName
                : ("ALL_OR_GENERAL_" + System.identityHashCode(chatForm));
        registerChatForm(regKey, chatForm);

        // Khi đóng cửa sổ chat → hủy đăng ký
        chatForm.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                unregisterChatForm(regKey);
            }
        });

        chatForm.setVisible(true);
    }

    public void openSettingsForm() {

        SettingsForm settings = new SettingsForm(this);

        settings.setLocationRelativeTo(this);

        settings.setVisible(true);

    }

    public void btnSettingsActionPerformed(java.awt.event.ActionEvent evt) {

        openSettingsForm();

    }

    public void broadcastNotice(String targetClient, String subject, String message) {

        String titlePrefix = (subject != null && !subject.trim().isEmpty()) ? "[" + subject.trim() + "] " : "";

        String noticePayload = "NOTICE|[!] CẢNH BÁO TỪ SERVER: " + titlePrefix + message.trim();

        if (targetClient == null || "Tất cả máy".equalsIgnoreCase(targetClient)
                || "ALL".equalsIgnoreCase(targetClient)) {

            for (java.util.Map.Entry<java.net.Socket, java.io.PrintWriter> entry : clientWriters.entrySet()) {

                try {

                    entry.getValue().println(noticePayload);

                    entry.getValue().flush();

                } catch (Exception ignored) {
                }

            }

            addLog("[!] CẢNH BÁO TỪ SERVER [→ Tất cả máy] " + subject + ": " + message);

            ActivityLogForm.themNhatKy("Tất cả máy", "CẢNH BÁO TỪ SERVER", subject + ": " + message);

        } else {

            java.net.Socket s = clientSockets.get(targetClient);

            if (s != null) {

                java.io.PrintWriter pw = clientWriters.get(s);

                if (pw != null) {

                    pw.println(noticePayload);

                    pw.flush();

                    addLog("[!] CẢNH BÁO TỪ SERVER [→ " + targetClient + "] " + subject + ": " + message);

                    ActivityLogForm.themNhatKy(targetClient, "CẢNH BÁO TỪ SERVER", subject + ": " + message);

                }

            } else {

                addLog("[!] [CẢNH BÁO] Không tìm thấy máy: " + targetClient);

            }

        }

    }

    private void btnNotificationActionPerformed(java.awt.event.ActionEvent evt) {

        NotificationForm notif = new NotificationForm(this);

        notif.setLocationRelativeTo(this);

        notif.setVisible(true);

    }

    private void btnActivityLogActionPerformed(java.awt.event.ActionEvent evt) {

        ActivityLogForm activityLogForm = new ActivityLogForm();

        activityLogForm.setLocationRelativeTo(this); // Căn giữa so với màn hình ServerForm

        activityLogForm.setVisible(true);

    }

    private void txtPortActionPerformed(java.awt.event.ActionEvent evt) {

        // Xu ly su kien

        startServer();

    }

    private void btnStartServerActionPerformed(java.awt.event.ActionEvent evt) {

        startServer();

        // Xu ly su kien

    }

    public void openOrUpdateComputerDetail(String name, String ip, String status, String connectedAt, String details) {
        if (name == null || name.trim().isEmpty() || "Chưa có dữ liệu".equalsIgnoreCase(name)) {
            if (tblClients.getSelectedRow() >= 0) {
                int mr = tblClients.convertRowIndexToModel(tblClients.getSelectedRow());
                name = String.valueOf(tblClients.getModel().getValueAt(mr, 1));
                ip = String.valueOf(tblClients.getModel().getValueAt(mr, 2));
                status = String.valueOf(tblClients.getModel().getValueAt(mr, 3));
                connectedAt = String.valueOf(tblClients.getModel().getValueAt(mr, 4));
                details = String.valueOf(tblClients.getModel().getValueAt(mr, 5));
            } else {
                return;
            }
        }

        final String finalName = name;
        ComputerDetailForm form = openDetailForms.get(finalName);
        if (form == null) {
            form = new ComputerDetailForm(finalName, ip, status);
            openDetailForms.put(finalName, form);
            final ComputerDetailForm f = form;
            form.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    openDetailForms.remove(finalName);
                }
            });
        }

        form.setCommandSender(cmd -> sendCommandToClient(finalName, cmd));

        String cached = lastSpecsMap.get(finalName);
        if (cached != null && !cached.isEmpty()) {
            form.updateRealtimeSpecs(cached);
        } else if (details != null && !details.isEmpty()) {
            form.setComputerInfo(finalName, ip, status, connectedAt, details);
        }

        sendCommandToClient(finalName, "REQ_FULL_SPECS");
        sendCommandToClient(finalName, "GET_HARDWARE_DETAIL");

        form.setLocationRelativeTo(this);
        form.setVisible(true);
        form.toFront();
    }

    private void btnComputerDetailActionPerformed(java.awt.event.ActionEvent evt) {
        int selectedRow = tblClients.getSelectedRow();
        if (selectedRow < 0) {
            if (tblClients.getRowCount() > 0) {
                tblClients.setRowSelectionInterval(0, 0);
                selectedRow = 0;
            } else {
                javax.swing.JOptionPane.showMessageDialog(
                        this,
                        "Chưa có máy trạm nào kết nối để xem chi tiết phần cứng.",
                        "Thông báo",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        int modelRow = tblClients.convertRowIndexToModel(selectedRow);
        String name = String.valueOf(tblClients.getModel().getValueAt(modelRow, 1));
        String ip = String.valueOf(tblClients.getModel().getValueAt(modelRow, 2));
        String status = String.valueOf(tblClients.getModel().getValueAt(modelRow, 3));
        String connectedAt = String.valueOf(tblClients.getModel().getValueAt(modelRow, 4));
        String details = String.valueOf(tblClients.getModel().getValueAt(modelRow, 5));

        openOrUpdateComputerDetail(name, ip, status, connectedAt, details);
    }

    public void btnClearLogActionPerformed(java.awt.event.ActionEvent evt) {

        txtServerLog.setText(""); // Xu ly su kien

    }

    private void btnViewLogActionPerformed(java.awt.event.ActionEvent evt) {

        btnActivityLogActionPerformed(evt);

    }

    private void btnManageComputerActionPerformed(java.awt.event.ActionEvent evt) {

        ComputerManagementForm managementForm = new ComputerManagementForm();

        managementForm.setLocationRelativeTo(this);

        managementForm.setVisible(true);

    }

    /**
     * 
     * @param args the command line arguments
     * 
     */

    public static void main(String args[]) {

        /* Set the Nimbus look and feel */

        /*
         * If Nimbus (introduced in Java SE 6) is not available, stay with the default
         * look and feel.
         * 
         * For details see
         * http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         * 
         */

        try {
            quanlyphongmay.common.UITheme.setupGlobalTheme();
        } catch (Exception ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }

        /* Create and display the form */

        java.awt.EventQueue.invokeLater(() -> new ServerForm().setVisible(true));

    }

    // Khai báo các thành phần giao diện (giữ nguyên tên biến 100%)

    private javax.swing.JButton btnActivityLog;

    private javax.swing.JButton btnMultiScreen;

    private javax.swing.JButton btnChat;

    private javax.swing.JButton btnClearLog;

    private javax.swing.JButton btnComputerDetail;

    private javax.swing.JButton btnComputerList;

    private javax.swing.JButton btnManageComputer;

    private javax.swing.JButton btnNotification;

    private javax.swing.JButton btnOpenSettings;

    private javax.swing.JButton btnOverview;

    private javax.swing.JButton btnRefresh;

    private javax.swing.JButton btnStartServer;

    private javax.swing.JButton btnStopServer;

    private javax.swing.JButton btnViewLog;

    private javax.swing.JPanel jPanel1;

    private javax.swing.JLabel lblClientListTitle;

    private javax.swing.JLabel lblFooterIP;

    private javax.swing.JLabel lblFooterPort;

    private javax.swing.JLabel lblFooterStatus;

    private javax.swing.JLabel lblLogo;

    private javax.swing.JLabel lblOfflineCount;

    private javax.swing.JLabel lblOfflineTitle1;

    private javax.swing.JLabel lblOnlineCount;

    private javax.swing.JLabel lblOnlineTitle;

    private javax.swing.JLabel lblPortTitle;

    private javax.swing.JLabel lblQuickTitle;

    private javax.swing.JLabel lblServerIP;

    private javax.swing.JLabel lblServerPort;

    private javax.swing.JLabel lblServerStatus;

    private javax.swing.JLabel lblTitle;

    private javax.swing.JLabel lblTotalCount;

    private javax.swing.JLabel lblTotalTitle;

    private javax.swing.JPanel pnlClientList;

    private javax.swing.JPanel pnlHeader;

    private javax.swing.JPanel pnlLog;

    private javax.swing.JPanel pnlOffline;

    private javax.swing.JPanel pnlOnline;

    private javax.swing.JPanel pnlQuickActions;

    private javax.swing.JPanel pnlSidebar;

    private javax.swing.JPanel pnlTotal;

    private javax.swing.JScrollPane scrClients;

    private javax.swing.JScrollPane scrServerLog;

    private javax.swing.JTable tblClients;

    private javax.swing.JTextField txtPort;

    private javax.swing.JTextArea txtServerLog;

    // ── Cảnh báo tài nguyên RAM/Disk tự động (Cảnh báo 2 chiều từ Server) ──────

    private void kiemTraCanhBaoTaiNguyen(String clientName, double ram, double disk) {

        long now = System.currentTimeMillis();

        Long lastAlert = lastResourceAlertTime.get(clientName);

        if (lastAlert != null && (now - lastAlert) < 60000L) {

            return; // Cooldown 60s

        }

        if (ram >= 90.0) {

            lastResourceAlertTime.put(clientName, now);

            String reason = String.format(java.util.Locale.US,
                    "Dung lượng RAM đang sử dụng đạt %.1f%%, vượt ngưỡng an toàn 90%%", ram);

            String warnMsg = "[CẢNH BÁO TỪ SERVER] " + reason + ". Vui lòng đóng bớt ứng dụng ngay!";

            try {

                sendCommandToClient(clientName, "NOTICE|" + warnMsg);

                addLog("[!] CẢNH BÁO TỪ SERVER [" + clientName + "] " + reason);

                ActivityLogForm.themNhatKy(clientName, "CẢNH BÁO TỪ SERVER", reason);

                MonitorForm mf = openMonitorForms.get(clientName);

                if (mf != null) {

                    mf.addAlert("RAM_FULL", reason);

                }

            } catch (Exception ignored) {
            }

        } else if (disk >= 95.0) {

            lastResourceAlertTime.put(clientName, now);

            String reason = String.format(java.util.Locale.US,
                    "Dung lượng ổ đĩa hệ thống đã sử dụng %.1f%%. Vui lòng dọn dẹp ổ đĩa!", disk);

            String warnMsg = "[!] CẢNH BÁO TỪ SERVER: " + reason;

            try {

                sendCommandToClient(clientName, "NOTICE|" + warnMsg);

                addLog("[!] CẢNH BÁO TỪ SERVER [" + clientName + "] " + reason);

                ActivityLogForm.themNhatKy(clientName, "CẢNH BÁO TỪ SERVER", reason);

                MonitorForm mf = openMonitorForms.get(clientName);

                if (mf != null) {

                    mf.addAlert("DISK_FULL", reason);

                }

            } catch (Exception ignored) {
            }

        }

    }

    // ── Khóa và mở khóa tất cả máy trạm ─────────────────────────────────────

    private void khoaTatCaMay() {

        java.util.List<String> online = getOnlineClientNames();

        if (online.isEmpty()) {

            JOptionPane.showMessageDialog(this, "Hiện không có máy trạm nào đang Online.", "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);

            return;

        }

        int confirm = JOptionPane.showConfirmDialog(

                this,

                "Bạn có chắc chắn muốn KHÓA toàn màn hình " + online.size() + " máy trạm đang Online không?",

                "Xác nhận khóa tất cả",

                JOptionPane.YES_NO_OPTION,

                JOptionPane.WARNING_MESSAGE

        );

        if (confirm == JOptionPane.YES_OPTION) {

            for (String clientName : online) {

                try {

                    sendCommandToClient(clientName, "LOCK");

                } catch (Exception ignored) {
                }

            }

            addLog("[Khóa] Đã gửi lệnh KHÓA TOÀN MÀN HÌNH tới " + online.size() + " máy Online.");

            ActivityLogForm.themNhatKy("Đa máy trạm", "Khóa máy", "Quản trị viên khóa màn hình máy trạm");

        }

    }

    private void moKhoaTatCaMay() {

        java.util.List<String> online = getOnlineClientNames();

        if (online.isEmpty()) {

            JOptionPane.showMessageDialog(this, "Hiện không có máy trạm nào đang Online.", "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);

            return;

        }

        for (String clientName : online) {

            try {

                sendCommandToClient(clientName, "UNLOCK");

            } catch (Exception ignored) {
            }

        }

        addLog("[Mở khóa] Đã gửi lệnh MỞ KHÓA tới " + online.size() + " máy Online.");

        ActivityLogForm.themNhatKy("Đa máy trạm", "Mở khóa máy", "Quản trị viên mở khóa toàn bộ máy trạm");

    }

    // ── Khởi động từ xa Wake-on-LAN ──────────────────────────────────────────

    private void thucHienWakeOnLan(String clientName) {

        ClientInfo info = allKnownClients.get(clientName);

        String mac = (info != null && info.mac != null && !info.mac.isEmpty() && !"N/A".equalsIgnoreCase(info.mac))

                ? info.mac
                : null;

        if (mac == null) {

            mac = JOptionPane.showInputDialog(

                    this,

                    "Chưa có địa chỉ MAC đã lưu cho máy \"" + clientName
                            + "\".\nVui lòng nhập địa chỉ MAC (VD: 00:11:22:33:44:55 hoặc 00-11-22-33-44-55):",

                    "Khởi động từ xa qua mạng (Wake-on-LAN)",

                    JOptionPane.QUESTION_MESSAGE

            );

        }

        if (mac != null && !mac.trim().isEmpty()) {

            mac = mac.trim();

            boolean ok = WakeOnLan.sendMagicPacket(mac);

            if (ok) {

                if (info != null) {
                    info.mac = mac;
                    saveKnownClients();
                }

                addLog("[WoL] Đã phát gói tin Wake-on-LAN tới MAC [" + mac + "] của máy: " + clientName);

                ActivityLogForm.themNhatKy(clientName, "Wake-on-LAN", "Gửi Magic Packet bật nguồn qua MAC: " + mac);

                JOptionPane.showMessageDialog(

                        this,

                        "Đã gửi thành công gói tin Magic Packet (Wake-on-LAN)!\n"

                                + "Tên máy: " + clientName + "\n"

                                + "Địa chỉ MAC: " + mac + "\n\n"

                                + "Lưu ý: Card mạng máy trạm cần bật tính năng Wake-on-LAN trong BIOS/Windows.",

                        "Đã gửi Wake-on-LAN",

                        JOptionPane.INFORMATION_MESSAGE

                );

            } else {

                JOptionPane.showMessageDialog(

                        this,

                        "Gửi gói tin Wake-on-LAN thất bại. Vui lòng kiểm tra lại định dạng địa chỉ MAC (12 ký tự hex)!",

                        "Lỗi gửi Magic Packet",

                        JOptionPane.ERROR_MESSAGE

                );

            }

        }

    }

    private void thucHienWakeOnLanPrompt() {

        int r = tblClients.getSelectedRow();

        if (r >= 0) {

            String name = String.valueOf(clientTableModel.getValueAt(tblClients.convertRowIndexToModel(r), 1));

            thucHienWakeOnLan(name);

        } else {

            String mac = JOptionPane.showInputDialog(

                    this,

                    "Nhập địa chỉ MAC của máy trạm cần khởi động từ xa (VD: 00:11:22:33:44:55):",

                    "Khởi động từ xa (Wake-on-LAN)",

                    JOptionPane.QUESTION_MESSAGE

            );

            if (mac != null && !mac.trim().isEmpty()) {

                boolean ok = WakeOnLan.sendMagicPacket(mac.trim());

                if (ok) {

                    addLog("[WoL] Đã gửi gói tin Magic Packet tới MAC: " + mac);

                    JOptionPane.showMessageDialog(this, "Đã gửi Magic Packet tới MAC: " + mac, "Thành công",
                            JOptionPane.INFORMATION_MESSAGE);

                } else {

                    JOptionPane.showMessageDialog(this, "Địa chỉ MAC không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);

                }

            }

        }

    }

    // ── Mở các Form tính năng nâng cao ──────────────────────────────────────

    public void openExamLockdownForm() {

        ExamLockdownForm form = new ExamLockdownForm(this::sendCommandToClient, getOnlineClientNames());

        form.setVisible(true);

    }

    public void openIncidentReportForm() {

        IncidentReportForm form = new IncidentReportForm(this::sendCommandToClient, getOnlineClientNames());

        form.setVisible(true);

    }

    public void openSilentInstallForm() {

        SilentInstallForm form = new SilentInstallForm(clientWriters, clientSockets, getOnlineClientNames());

        form.setVisible(true);

    }

    private void thucHienDonRacAll() {

        java.util.List<String> online = getOnlineClientNames();

        if (online.isEmpty()) {

            JOptionPane.showMessageDialog(this, "Không có máy trạm nào đang Online.", "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);

            return;

        }

        int confirm = JOptionPane.showConfirmDialog(

                this,

                "Kích hoạt dọn dẹp hệ thống (Deep Clean / Temp / Cache) trên toàn bộ " + online.size() + " máy Online?",

                "Xác nhận dọn dẹp hệ thống",

                JOptionPane.YES_NO_OPTION,

                JOptionPane.QUESTION_MESSAGE

        );

        if (confirm == JOptionPane.YES_OPTION) {

            for (String m : online) {

                try {

                    sendCommandToClient(m, "CLEAN_SYSTEM|FULL_RESTORE");

                } catch (Exception ignored) {
                }

            }

            addLog("[Dọn rác] Đã phát lệnh dọn dẹp hệ thống (Deep Clean) tới " + online.size() + " máy.");

        }

    }

    private void thucHienDonRacSelected() {

        int r = tblClients.getSelectedRow();

        if (r >= 0) {

            String n = String.valueOf(clientTableModel.getValueAt(tblClients.convertRowIndexToModel(r), 1));

            try {

                sendCommandToClient(n, "CLEAN_SYSTEM|FULL_RESTORE");

                addLog("[Dọn rác] Đã phát lệnh dọn dẹp hệ thống tới máy: " + n);

                JOptionPane.showMessageDialog(this, "Đã gửi lệnh dọn dẹp hệ thống tới máy: " + n);

            } catch (Exception ex) {

                JOptionPane.showMessageDialog(this, "Lỗi gửi lệnh: " + ex.getMessage(), "Lỗi",
                        JOptionPane.ERROR_MESSAGE);

            }

        }

    }

    private void guiCanhBaoTuServerChoMay(String clientName) {

        if (clientName == null || clientName.trim().isEmpty())
            return;

        String msg = JOptionPane.showInputDialog(

                this,

                "Nhập nội dung cảnh báo từ Server gửi tới [" + clientName + "]:",

                "[!] CẢNH BÁO TỪ SERVER",

                JOptionPane.WARNING_MESSAGE

        );

        if (msg != null && !msg.trim().isEmpty()) {

            String fullMsg = "NOTICE|[!] CẢNH BÁO TỪ SERVER: " + msg.trim();

            sendCommandToClient(clientName, fullMsg);

            addLog("[!] CẢNH BÁO TỪ SERVER [→ " + clientName + "] " + msg.trim());

            ActivityLogForm.themNhatKy(clientName, "CẢNH BÁO TỪ SERVER", msg.trim());

            JOptionPane.showMessageDialog(this, "Đã gửi cảnh báo 2 chiều thành công tới: " + clientName, "Thành công",
                    JOptionPane.INFORMATION_MESSAGE);

        }

    }

    private MultiScreenForm multiScreenForm = null;

    public void openMultiScreenForm() {

        if (screenStreamServer == null) {

            JOptionPane.showMessageDialog(this, "Screen Stream Server chưa khởi động!", "Lỗi",
                    JOptionPane.ERROR_MESSAGE);

            return;

        }

        if (multiScreenForm == null || !multiScreenForm.isDisplayable()) {

            multiScreenForm = new MultiScreenForm(

                    this::sendCommandToClient,

                    this::getOnlineClientsMap,

                    screenStreamServer,

                    (name, enableControl) -> {

                        ClientInfo info = allKnownClients.get(name);

                        String ip = (info != null) ? info.ip : null;

                        MonitorForm mf = showMonitorForm(name, ip);

                        if (Boolean.TRUE.equals(enableControl) && mf != null) {

                            mf.switchToLiveStream(true);

                        }

                    }

            );

        }

        multiScreenForm.setVisible(true);

        multiScreenForm.toFront();

        multiScreenForm.refreshClientGrid();

    }

    public java.util.Map<String, String> getOnlineClientsMap() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, ClientInfo> entry : allKnownClients.entrySet()) {
            if ("Online".equals(entry.getValue().status)) {
                map.put(entry.getKey(), entry.getValue().ip);
            }
        }
        return map;
    }

    public static File getKnownClientsFile() {
        return new File("known_clients.properties");
    }

    public synchronized void loadKnownClients() {
        File f = getKnownClientsFile();
        if (!f.exists())
            return;
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String key : props.stringPropertyNames()) {
                String val = props.getProperty(key, "").trim();
                if (val.isEmpty())
                    continue;
                String[] parts = val.split("\\|", 3);
                String ip = parts.length > 0 ? parts[0].trim() : "127.0.0.1";
                String mac = parts.length > 1 ? parts[1].trim() : "";
                String name = parts.length > 2 ? parts[2].trim() : key;
                ClientInfo info = allKnownClients.get(key);
                if (info == null) {
                    info = new ClientInfo(name, ip, "00:00:00");
                    info.status = "Offline";
                    info.mac = mac;
                    allKnownClients.put(key, info);
                } else {
                    if (info.mac == null || info.mac.isEmpty())
                        info.mac = mac;
                }
            }
            refreshTable();
        } catch (Exception e) {
            logger.warning("[Server] Lỗi đọc known_clients.properties: " + e.getMessage());
        }
    }

    public synchronized void saveKnownClients() {
        File f = getKnownClientsFile();
        Properties props = new Properties();
        for (Map.Entry<String, ClientInfo> entry : allKnownClients.entrySet()) {
            ClientInfo ci = entry.getValue();
            if (ci != null) {
                String val = (ci.ip != null ? ci.ip : "127.0.0.1") + "|" + (ci.mac != null ? ci.mac : "") + "|"
                        + ci.name;
                props.setProperty(entry.getKey(), val);
            }
        }
        try (OutputStream out = new FileOutputStream(f)) {
            props.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "Known LANPC Clients (MAC & IP Registry)");
        } catch (Exception e) {
            logger.warning("[Server] Lỗi lưu known_clients.properties: " + e.getMessage());
        }
    }
}
