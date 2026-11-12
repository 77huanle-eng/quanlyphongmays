package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * MultiScreenForm - Chế độ Quan sát nhiều màn hình (Toàn phòng máy / Multi-Screen Wall).
 * Đã tối ưu hóa giao diện siêu gọn:
 * - Loại bỏ các nút bấm rườm rà dưới mỗi ô, tối đa hóa diện tích hiển thị thumbnail.
 * - Header nhỏ gọn hiển thị: Tên máy, IP, Trạng thái, FPS và nút ba chấm [...].
 * - Menu thao tác nhanh Popup Menu khi Click Chuột Phải hoặc bấm nút ba chấm:
 *     + Phóng to màn hình (Single View)
 *     + Điều khiển từ xa (Interactive Control)
 *     + Khóa / Mở khóa máy (Lock/Unlock)
 *     + Chụp ảnh màn hình (Snapshot)
 *     + Tắt nguồn / Khởi động lại máy (Shutdown / Restart)
 *     + Bắt đầu / Dừng xem stream riêng máy này
 * - Bố cục lưới linh hoạt trong JScrollPane, tự động co giãn theo kích thước cửa sổ.
 */
public class MultiScreenForm extends JFrame {

    public java.util.List<String> getSelectedClientNames() {
        java.util.List<String> list = new ArrayList<>();
        for (ClientScreenCard card : clientCards.values()) {
            if (card.isSelected()) {
                list.add(card.getClientName());
            }
        }
        if (list.isEmpty()) {
            list.addAll(clientCards.keySet());
        }
        return list;
    }

    public void selectAllCards(boolean select) {
        for (ClientScreenCard card : clientCards.values()) {
            card.setSelected(select);
        }
    }


    private final BiConsumer<String, String> commandSender;
    private final Supplier<Map<String, String>> onlineClientsSupplier;
    private final ScreenStreamServer streamServer;
    private final BiConsumer<String, Boolean> openMonitorCallback;

    // Quản lý các card màn hình của máy trạm (clientName -> ClientScreenCard)
    private final Map<String, ClientScreenCard> clientCards = new ConcurrentHashMap<>();

    private JPanel gridContainer;
    private JScrollPane scrollPane;
    private JLabel lblStatusCount;
    private JComboBox<String> cboGridColumns;
    private JButton btnStartAll;
    private JButton btnStopAll;
    private JButton btnRefresh;
    private JButton btnLockAll;
    private JButton btnUnlockAll;

    private int gridCols = 3; // Mặc định 3 cột
    private boolean isAutoCols = false;
    private volatile boolean allStreaming = false;

    public MultiScreenForm(
            BiConsumer<String, String> commandSender,
            Supplier<Map<String, String>> onlineClientsSupplier,
            ScreenStreamServer streamServer,
            BiConsumer<String, Boolean> openMonitorCallback
    ) {
        super("Lưới quan sát nhiều màn hình — Toàn phòng máy");
        this.commandSender = commandSender;
        this.onlineClientsSupplier = onlineClientsSupplier;
        this.streamServer = streamServer;
        this.openMonitorCallback = openMonitorCallback;

        initComponents();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1260, 820);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        // Khi đóng cửa sổ, tự động dừng tất cả stream để tiết kiệm băng thông mạng LAN
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAllStreams();
            }
        });

        // Tự động căn chỉnh lại số cột khi cửa sổ thay đổi kích thước nếu chọn chế độ Auto
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (isAutoCols) {
                    reapplyGridLayout();
                }
            }
        });

        // Nạp danh sách máy ban đầu
        refreshClientGrid();
    }

    private void initComponents() {
        getContentPane().setBackground(UITheme.BG_DARK);
        setLayout(new BorderLayout(0, 0));

        // ── 1. Top Toolbar ───────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(15, 0));
        topBar.setBackground(UITheme.BG_CARD);
        topBar.setBorder(new EmptyBorder(8, 16, 8, 16));

        // Tiêu đề & Đếm số máy bên trái
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("LƯỚI QUAN SÁT PHÒNG MÁY");
        lblTitle.setFont(UITheme.FONT_HEADER);
        lblTitle.setForeground(UITheme.ACCENT);

        lblStatusCount = new JLabel("Đang tải danh sách...");
        lblStatusCount.setFont(UITheme.FONT_SMALL);
        lblStatusCount.setForeground(UITheme.TEXT_MUTED);

        leftPanel.add(lblTitle);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        leftPanel.add(lblStatusCount);

        // Các nút điều khiển hàng loạt bên phải
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        btnStartAll = new JButton("Bắt đầu xem tất cả");
        UITheme.styleButtonAccent(btnStartAll);
        btnStartAll.addActionListener(e -> startAllStreams());

        btnStopAll = new JButton("Dừng xem tất cả");
        UITheme.styleButtonDanger(btnStopAll);
        btnStopAll.addActionListener(e -> stopAllStreams());

        btnRefresh = new JButton("Làm mới lưới");
        UITheme.styleButtonSecondary(btnRefresh);
        btnRefresh.addActionListener(e -> refreshClientGrid());

        JLabel lblCols = new JLabel("Bố cục cột:");
        lblCols.setFont(UITheme.FONT_SMALL);
        lblCols.setForeground(UITheme.TEXT_PRIMARY);

        cboGridColumns = new JComboBox<>(new String[]{"Tự động (Auto)", "2 Cột", "3 Cột", "4 Cột", "5 Cột", "6 Cột"});
        cboGridColumns.setSelectedIndex(2); // 3 cột mặc định
        UITheme.styleComboBox(cboGridColumns);
        cboGridColumns.addActionListener(e -> {
            int sel = cboGridColumns.getSelectedIndex();
            if (sel == 0) {
                isAutoCols = true;
            } else {
                isAutoCols = false;
                gridCols = sel + 1; // 1->2, 2->3, 3->4, 4->5, 5->6
            }
            reapplyGridLayout();
        });

        btnLockAll = new JButton("Khóa tất cả");
        UITheme.styleButtonGhost(btnLockAll);
        btnLockAll.setForeground(UITheme.WARNING);
        btnLockAll.addActionListener(e -> khoaTatCaMay());

        btnUnlockAll = new JButton("Mở khóa tất cả");
        UITheme.styleButtonGhost(btnUnlockAll);
        btnUnlockAll.setForeground(UITheme.SUCCESS);
        btnUnlockAll.addActionListener(e -> moKhoaTatCaMay());

        rightPanel.add(lblCols);
        rightPanel.add(cboGridColumns);
        rightPanel.add(Box.createHorizontalStrut(6));
        rightPanel.add(btnStartAll);
        rightPanel.add(btnStopAll);
        rightPanel.add(btnRefresh);
        rightPanel.add(Box.createHorizontalStrut(6));
        JButton btnSelectAllCards = new JButton("[Chọn tất cả]");
        UITheme.styleButtonGhost(btnSelectAllCards);
        btnSelectAllCards.addActionListener(e -> selectAllCards(true));

        JButton btnDeselectAllCards = new JButton("[Bỏ chọn]");
        UITheme.styleButtonGhost(btnDeselectAllCards);
        btnDeselectAllCards.addActionListener(e -> selectAllCards(false));

        rightPanel.add(btnSelectAllCards);
        rightPanel.add(btnDeselectAllCards);
        rightPanel.add(btnLockAll);
        rightPanel.add(btnUnlockAll);

        topBar.add(leftPanel, BorderLayout.WEST);
        topBar.add(rightPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ── 2. Center Grid Container ─────────────────────────────────────────────
        gridContainer = new JPanel();
        gridContainer.setBackground(UITheme.BG_DARK);
        gridContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        reapplyGridLayout();

        scrollPane = new JScrollPane(gridContainer);
        UITheme.styleScrollPane(scrollPane);
        scrollPane.getVerticalScrollBar().setUnitIncrement(30);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);
    }

    private void reapplyGridLayout() {
        if (gridContainer == null) return;

        int cols = gridCols;
        if (isAutoCols) {
            int availableWidth = (scrollPane != null && scrollPane.getViewport() != null)
                    ? scrollPane.getViewport().getWidth() : getWidth() - 30;
            if (availableWidth <= 0) availableWidth = 1200;
            cols = Math.max(1, Math.min(6, availableWidth / 340));
        }

        gridContainer.setLayout(new GridLayout(0, cols, 10, 10));
        gridContainer.revalidate();
        gridContainer.repaint();
    }

    /**
     * Quét và cập nhật lưới các máy trạm đang Online
     */
    public synchronized void refreshClientGrid() {
        Map<String, String> currentOnline = (onlineClientsSupplier != null)
                ? onlineClientsSupplier.get()
                : Collections.emptyMap();

        SwingUtilities.invokeLater(() -> {
            // Xóa các card của máy đã ngắt kết nối (Offline)
            List<String> toRemove = new ArrayList<>();
            for (String name : clientCards.keySet()) {
                if (!currentOnline.containsKey(name)) {
                    toRemove.add(name);
                }
            }
            for (String name : toRemove) {
                ClientScreenCard card = clientCards.remove(name);
                if (card != null) {
                    card.stopStream();
                    gridContainer.remove(card);
                }
            }

            // Thêm mới hoặc cập nhật các máy đang Online
            for (Map.Entry<String, String> entry : currentOnline.entrySet()) {
                String clientName = entry.getKey();
                String clientIP = entry.getValue();

                if (!clientCards.containsKey(clientName)) {
                    ClientScreenCard card = new ClientScreenCard(clientName, clientIP);
                    clientCards.put(clientName, card);
                    gridContainer.add(card);

                    // Nếu chế độ "Xem tất cả" đang bật, tự động kích hoạt stream cho máy mới vào
                    if (allStreaming) {
                        card.startStream();
                    }
                } else {
                    clientCards.get(clientName).updateIP(clientIP);
                }
            }

            // Cập nhật nhãn thống kê
            updateStatusText();

            // Nếu không có máy nào online
            if (clientCards.isEmpty()) {
                gridContainer.removeAll();
                JLabel emptyLbl = new JLabel("Hiện chưa có máy trạm nào đang Online trong phòng máy.", SwingConstants.CENTER);
                emptyLbl.setFont(UITheme.FONT_HEADER);
                emptyLbl.setForeground(UITheme.TEXT_MUTED);
                gridContainer.setLayout(new BorderLayout());
                gridContainer.add(emptyLbl, BorderLayout.CENTER);
            } else if (gridContainer.getLayout() instanceof BorderLayout) {
                reapplyGridLayout();
                for (ClientScreenCard card : clientCards.values()) {
                    gridContainer.add(card);
                }
            }

            gridContainer.revalidate();
            gridContainer.repaint();
        });
    }

    private void updateStatusText() {
        int totalOnline = clientCards.size();
        long streamingCount = clientCards.values().stream().filter(ClientScreenCard::isStreaming).count();
        lblStatusCount.setText(String.format("Máy Online: %d  |  Đang phát hình: %d", totalOnline, streamingCount));
    }

    /**
     * Bắt đầu nhận stream toàn bộ máy trạm trong phòng
     */
    private void startAllStreams() {
        allStreaming = true;
        for (ClientScreenCard card : clientCards.values()) {
            card.startStream();
        }
        updateStatusText();
    }

    /**
     * Dừng nhận stream toàn bộ máy trạm trong phòng
     */
    private void stopAllStreams() {
        allStreaming = false;
        for (ClientScreenCard card : clientCards.values()) {
            card.stopStream();
        }
        updateStatusText();
    }

    private void khoaTatCaMay() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc muốn KHÓA toàn màn hình " + clientCards.size() + " máy trạm đang Online không?",
                "Xác nhận khóa tất cả",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm == JOptionPane.YES_OPTION) {
            for (String clientName : clientCards.keySet()) {
                if (commandSender != null) {
                    commandSender.accept(clientName, "LOCK");
                }
            }
        }
    }

    private void moKhoaTatCaMay() {
        for (String clientName : clientCards.keySet()) {
            if (commandSender != null) {
                commandSender.accept(clientName, "UNLOCK");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ClientScreenCard - Ô hiển thị màn hình siêu thu gọn của từng máy trạm
    // Đã loại bỏ các nút bấm rườm rà phía dưới, tích hợp Menu Thao Tác Nhanh
    // ═══════════════════════════════════════════════════════════════════════════
    public class ClientScreenCard extends JPanel {

        private final String clientName;
        private final JCheckBox chkSelect = new JCheckBox();
        private String clientIP;
        private final ScreenMonitorPanel monitorPanel;
        private final JLabel lblName;
        private final JLabel lblIP;
        private final JLabel lblStatusDot;
        private final JButton btnMore;

        private boolean streaming = false;
        private boolean locked = false;

        public ClientScreenCard(String clientName, String clientIP) {
            this.clientName = clientName;
            this.clientIP = clientIP;

            setLayout(new BorderLayout(0, 0));
            setBackground(UITheme.BG_CARD);
            setBorder(new LineBorder(UITheme.BORDER, 1, true));

            // 1. Header Card siêu nhỏ gọn (Tên máy, IP, Trạng thái và nút ba chấm [...])
            JPanel cardHeader = new JPanel(new BorderLayout(6, 0));
            cardHeader.setBackground(UITheme.BG_SIDEBAR);
            cardHeader.setBorder(new EmptyBorder(4, 8, 4, 6));

            lblName = new JLabel(clientName);
            lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblName.setForeground(UITheme.ACCENT);

            lblIP = new JLabel(clientIP);
            lblIP.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblIP.setForeground(UITheme.TEXT_MUTED);

            JPanel nameBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            nameBox.setOpaque(false);
            chkSelect.setOpaque(false);
            chkSelect.setFocusPainted(false);
            chkSelect.setToolTipText("Chọn máy " + clientName + " để thao tác đa máy");
            nameBox.add(chkSelect);
            nameBox.add(lblName);
            nameBox.add(lblIP);

            // Bên phải header: Trạng thái & Nút ba chấm [...]
            JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rightBox.setOpaque(false);

            lblStatusDot = new JLabel("Sẵn sàng");
            lblStatusDot.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblStatusDot.setForeground(UITheme.TEXT_MUTED);

            btnMore = new JButton("[...]");
            btnMore.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnMore.setForeground(UITheme.TEXT_PRIMARY);
            btnMore.setBackground(UITheme.BG_CARD);
            btnMore.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            btnMore.setFocusPainted(false);
            btnMore.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnMore.setToolTipText("Menu thao tác nhanh cho " + clientName);
            btnMore.addActionListener(e -> showQuickMenu(btnMore, 0, btnMore.getHeight()));

            rightBox.add(lblStatusDot);
            rightBox.add(btnMore);

            cardHeader.add(nameBox, BorderLayout.WEST);
            cardHeader.add(rightBox, BorderLayout.EAST);
            add(cardHeader, BorderLayout.NORTH);

            // 2. Khung hình Stream ScreenMonitorPanel (Chiếm trọn vẹn diện tích còn lại)
            monitorPanel = new ScreenMonitorPanel();
            monitorPanel.setPreferredSize(new Dimension(320, 180));
            monitorPanel.setStatusMessage("Bấm xem hoặc menu [...]");
            monitorPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Bắt sự kiện trên màn hình:
            // - Click đúp chuột trái -> Phóng to màn hình (Single View)
            // - Click chuột phải -> Hiển thị Menu Thao Tác Nhanh
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                        showQuickMenu(e.getComponent(), e.getX(), e.getY());
                    } else if (e.getClickCount() == 2) {
                        openSingleMonitor(false);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showQuickMenu(e.getComponent(), e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showQuickMenu(e.getComponent(), e.getX(), e.getY());
                    }
                }
            };

            monitorPanel.addMouseListener(mouseHandler);
            cardHeader.addMouseListener(mouseHandler);

            add(monitorPanel, BorderLayout.CENTER);
            // ĐÃ LOẠI BỎ HOÀN TOÀN cardFooter để tiết kiệm tối đa không gian hiển thị
        }

        /**
         * Hiển thị Menu Thao Tác Nhanh (Popup Menu) với đầy đủ chức năng cốt lõi
         */
        private void showQuickMenu(Component invoker, int x, int y) {
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(UITheme.BG_CARD);
            menu.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));

            // 1. Xem / Dừng xem stream riêng máy này
            JMenuItem miToggle = new JMenuItem(streaming ? "[Dừng xem] Tạm dừng màn hình" : "[Xem] Bắt đầu xem màn hình");
            styleMenuItem(miToggle, streaming ? UITheme.DANGER : UITheme.ACCENT);
            miToggle.addActionListener(e -> toggleStream());
            menu.add(miToggle);

            // 2. Phóng to màn hình (Single View)
            JMenuItem miZoom = new JMenuItem("[Phóng to] Phóng to màn hình (Single View)");
            styleMenuItem(miZoom, UITheme.TEXT_PRIMARY);
            miZoom.addActionListener(e -> openSingleMonitor(false));
            menu.add(miZoom);

            // 3. Điều khiển từ xa (Interactive Control)
            JMenuItem miControl = new JMenuItem("[Điều khiển] Điều khiển từ xa (Interactive Control)");
            styleMenuItem(miControl, UITheme.WARNING);
            miControl.addActionListener(e -> openSingleMonitor(true));
            menu.add(miControl);

            menu.addSeparator();

            // 4. Khóa / Mở khóa máy (Lock)
            JMenuItem miLock = new JMenuItem(locked ? "[Mở khóa] Mở khóa máy trạm (Unlock)" : "[Khóa] Khóa máy trạm (Lock)");
            styleMenuItem(miLock, locked ? UITheme.SUCCESS : UITheme.WARNING);
            miLock.addActionListener(e -> toggleLock());
            menu.add(miLock);

            // 5. Chế độ ngủ (Sleep)
            JMenuItem miSleep = new JMenuItem("[Chế độ ngủ] Đưa máy vào chế độ ngủ (Sleep)");
            styleMenuItem(miSleep, new Color(147, 197, 253));
            miSleep.addActionListener(e -> confirmSleep());
            menu.add(miSleep);

            // 6. Tắt máy từ xa (Shut down)
            JMenuItem miShutdown = new JMenuItem("[Tắt nguồn] Tắt máy từ xa (Shut down)");
            styleMenuItem(miShutdown, UITheme.DANGER);
            miShutdown.addActionListener(e -> confirmShutdown());
            menu.add(miShutdown);

            // 7. Khởi động lại máy (Restart)
            JMenuItem miRestart = new JMenuItem("[Khởi động lại] Khởi động lại máy (Restart)");
            styleMenuItem(miRestart, UITheme.WARNING);
            miRestart.addActionListener(e -> confirmRestart());
            menu.add(miRestart);

            menu.addSeparator();

            // 8. Chụp ảnh màn hình (Snapshot)
            JMenuItem miSnapshot = new JMenuItem("[Chụp ảnh] Chụp ảnh màn hình (Snapshot)");
            styleMenuItem(miSnapshot, UITheme.TEXT_PRIMARY);
            miSnapshot.addActionListener(e -> takeSnapshot());
            menu.add(miSnapshot);

            menu.show(invoker, x, y);
        }

        private void styleMenuItem(JMenuItem item, Color fgColor) {
            item.setBackground(UITheme.BG_CARD);
            item.setForeground(fgColor);
            item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            item.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        }

        public boolean isStreaming() {
            return streaming;
        }

        public void updateIP(String ip) {
            this.clientIP = ip;
            lblIP.setText(ip);
        }

        public void startStream() {
            if (streaming) return;
            streaming = true;

            if (streamServer != null) {
                streamServer.registerMonitor(clientName, monitorPanel);
            }
            if (commandSender != null) {
                commandSender.accept(clientName, "START_STREAM|5001");
            }

            monitorPanel.setStatusMessage("Đang kết nối hình ảnh...");
            lblStatusDot.setText("[LIVE] Đang phát");
            lblStatusDot.setForeground(UITheme.SUCCESS);
        }

        public boolean isSelected() {
            return chkSelect.isSelected();
        }

        public void setSelected(boolean sel) {
            chkSelect.setSelected(sel);
        }

        public String getClientName() {
            return clientName;
        }

        public void stopStream() {
            if (!streaming) return;
            streaming = false;

            if (commandSender != null) {
                commandSender.accept(clientName, "STOP_STREAM");
            }
            if (streamServer != null) {
                streamServer.unregisterMonitor(clientName, monitorPanel);
            }

            monitorPanel.setStatusMessage("Bấm xem hoặc menu [...]");
            lblStatusDot.setText("Sẵn sàng");
            lblStatusDot.setForeground(UITheme.TEXT_MUTED);
        }

        private void toggleStream() {
            if (streaming) {
                stopStream();
            } else {
                startStream();
            }
            updateStatusText();
        }

        private void openSingleMonitor(boolean enableControl) {
            if (openMonitorCallback != null) {
                openMonitorCallback.accept(clientName, enableControl);
            }
        }

        private void toggleLock() {
            if (!locked) {
                if (commandSender != null) commandSender.accept(clientName, "LOCK");
                locked = true;
            } else {
                if (commandSender != null) commandSender.accept(clientName, "UNLOCK");
                locked = false;
            }
        }

        private void confirmSleep() {
            int cf = JOptionPane.showConfirmDialog(
                    MultiScreenForm.this,
                    "Bạn có chắc chắn muốn đưa máy trạm " + clientName + " (" + clientIP + ") vào CHẾ ĐỘ NGỦ (Sleep) không?",
                    "Xác nhận Chế độ ngủ (Sleep)",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (cf == JOptionPane.YES_OPTION && commandSender != null) {
                commandSender.accept(clientName, "SLEEP");
            }
        }

        private void confirmShutdown() {
            int cf = JOptionPane.showConfirmDialog(
                    MultiScreenForm.this,
                    "Bạn có chắc chắn muốn TẮT NGUỒN máy trạm " + clientName + " từ xa không?",
                    "Xác nhận tắt máy",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (cf == JOptionPane.YES_OPTION && commandSender != null) {
                commandSender.accept(clientName, "SHUTDOWN");
            }
        }

        private void confirmRestart() {
            int cf = JOptionPane.showConfirmDialog(
                    MultiScreenForm.this,
                    "Bạn có chắc chắn muốn KHỞI ĐỘNG LẠI máy trạm " + clientName + " từ xa không?",
                    "Xác nhận khởi động lại",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (cf == JOptionPane.YES_OPTION && commandSender != null) {
                commandSender.accept(clientName, "RESTART");
            }
        }

        private void takeSnapshot() {
            BufferedImage frame = monitorPanel.getCurrentFrame();
            if (frame == null) {
                JOptionPane.showMessageDialog(MultiScreenForm.this, "Máy " + clientName + " chưa có hình ảnh để chụp!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("Snapshot_" + clientName + "_" + System.currentTimeMillis() + ".png"));
            if (chooser.showSaveDialog(MultiScreenForm.this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".png")) {
                    file = new File(file.getAbsolutePath() + ".png");
                }
                if (monitorPanel.saveSnapshot(file)) {
                    JOptionPane.showMessageDialog(MultiScreenForm.this, "Đã lưu ảnh chụp máy " + clientName + " thành công:\n" + file.getAbsolutePath());
                } else {
                    JOptionPane.showMessageDialog(MultiScreenForm.this, "Lỗi khi lưu ảnh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
