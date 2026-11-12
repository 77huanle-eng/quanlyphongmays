package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * PermissionForm - Quản lý phân quyền và hạn chế client trong mạng LAN.
 * Cho phép khóa màn hình, giới hạn ứng dụng và gửi thông báo hàng loạt.
 */
public class PermissionForm extends JFrame {

    // ── Data ────────────────────────────────────────────────────────────────
    private final Map<Socket, PrintWriter> clientWriters;
    private final Map<String, Socket>      clientSockets;
    private final List<String>             onlineClients;

    // ── UI Components ────────────────────────────────────────────────────────
    private DefaultListModel<String> clientListModel;
    private JList<String>            clientJList;
    private JLabel                   statusBar;

    // Tab 2 – Giới hạn ứng dụng
    private JTextArea blacklistArea;

    // Tab 3 – Thông báo
    private JTextField notifTitleField;
    private JTextArea  notifContentArea;

    // ── Constructor ──────────────────────────────────────────────────────────
    public PermissionForm(Map<Socket, PrintWriter> clientWriters,
                          Map<String, Socket>      clientSockets,
                          List<String>             onlineClients) {
        this.clientWriters = clientWriters;
        this.clientSockets = clientSockets;
        this.onlineClients = onlineClients;

        initUI();
    }

    // ── UI Initialisation ────────────────────────────────────────────────────
    private void initUI() {
        setTitle("LANPC – Phân Quyền Hạn Chế Client");
        setSize(820, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // Root panel
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UITheme.BG_DARK);
        setContentPane(root);

        // Title bar
        root.add(buildTitleBar(), BorderLayout.NORTH);

        // Centre: left (client list) + right (tabs)
        JSplitPane centre = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildClientPanel(), buildTabsPanel());
        centre.setDividerLocation(220);
        centre.setDividerSize(4);
        centre.setBackground(UITheme.BG_DARK);
        centre.setBorder(BorderFactory.createEmptyBorder());
        root.add(centre, BorderLayout.CENTER);

        // Status bar
        root.add(buildStatusBar(), BorderLayout.SOUTH);
    }

    /** Thin coloured title strip at the top. */
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        bar.setBackground(UITheme.ACCENT);

        JLabel icon = new JLabel("[Phân Quyền]");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        icon.setForeground(Color.WHITE);

        JLabel title = new JLabel("LANPC – Phân Quyền Hạn Chế Client");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        bar.add(icon);
        bar.add(title);
        return bar;
    }

    /** Left panel: multi-select client list. */
    private JPanel buildClientPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UITheme.BG_CARD);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 0, 1, UITheme.BORDER),
                new EmptyBorder(12, 10, 12, 10)));

        JLabel lbl = new JLabel("Chọn máy Client:");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(Color.WHITE);
        panel.add(lbl, BorderLayout.NORTH);

        clientListModel = new DefaultListModel<>();
        for (String name : onlineClients) {
            clientListModel.addElement(name);
        }

        clientJList = new JList<>(clientListModel);
        clientJList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        clientJList.setBackground(UITheme.BG_DARK);
        clientJList.setForeground(Color.WHITE);
        clientJList.setSelectionBackground(UITheme.ACCENT);
        clientJList.setSelectionForeground(Color.WHITE);
        clientJList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        clientJList.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane scroll = new JScrollPane(clientJList);
        scroll.setBackground(UITheme.BG_DARK);
        scroll.getViewport().setBackground(UITheme.BG_DARK);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);

        // Online count badge
        JLabel countLbl = new JLabel("Online: " + onlineClients.size());
        countLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        countLbl.setForeground(UITheme.SUCCESS);
        panel.add(countLbl, BorderLayout.SOUTH);

        return panel;
    }

    /** Right panel: tabbed pane with 3 tabs. */
    private JTabbedPane buildTabsPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(UITheme.BG_CARD);
        tabs.setForeground(Color.WHITE);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Use custom UI to colour tab headers on dark background
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                highlight          = UITheme.BG_CARD;
                lightHighlight     = UITheme.BG_CARD;
                shadow             = UITheme.BG_DARK;
                darkShadow         = UITheme.BG_DARK;
                focus              = UITheme.ACCENT;
            }
        });

        tabs.addTab("[Khóa] Khóa màn hình",   buildLockTab());
        tabs.addTab("[Chặn] Giới hạn ứng dụng", buildRestrictTab());
        tabs.addTab("Thông báo",         buildNotifTab());

        return tabs;
    }

    // ── Tab 1: Khóa màn hình ────────────────────────────────────────────────
    private JPanel buildLockTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UITheme.BG_CARD);
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.insets  = new Insets(6, 0, 6, 0);
        gc.weightx = 1.0;
        gc.gridx   = 0;

        // Section label
        gc.gridy = 0;
        JLabel section = styledLabel("Điều khiển khóa màn hình:", Font.BOLD, 13);
        panel.add(section, gc);

        // All-clients buttons
        gc.gridy = 1;
        panel.add(styledButton("[Khóa] Khóa tất cả",       UITheme.DANGER,  e -> sendToAll("LOCK")),   gc);
        gc.gridy = 2;
        panel.add(styledButton("[Mở khóa] Mở khóa tất cả",    UITheme.SUCCESS, e -> sendToAll("UNLOCK")), gc);

        // Separator
        gc.gridy = 3;
        JSeparator sep = new JSeparator();
        sep.setForeground(UITheme.BORDER);
        sep.setBackground(UITheme.BORDER);
        panel.add(sep, gc);

        // Selected-clients buttons
        gc.gridy = 4;
        JLabel selLbl = styledLabel("Chỉ áp dụng cho client được chọn:", Font.ITALIC, 12);
        selLbl.setForeground(new Color(180, 180, 180));
        panel.add(selLbl, gc);

        gc.gridy = 5;
        panel.add(styledButton("[Khóa] Khóa client được chọn",    UITheme.WARNING, e -> sendToSelected("LOCK")),   gc);
        gc.gridy = 6;
        panel.add(styledButton("[Mở khóa] Mở khóa client được chọn", UITheme.SUCCESS, e -> sendToSelected("UNLOCK")), gc);

        // Filler
        gc.gridy   = 7;
        gc.weighty = 1.0;
        gc.fill    = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), gc);

        return panel;
    }

    // ── Tab 2: Giới hạn ứng dụng ────────────────────────────────────────────
    private JPanel buildRestrictTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UITheme.BG_CARD);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel topLbl = styledLabel("App bị cấm (VD: chrome.exe, winword.exe):", Font.BOLD, 13);
        panel.add(topLbl, BorderLayout.NORTH);

        blacklistArea = new JTextArea(6, 30);
        styleTextArea(blacklistArea, "chrome.exe\nwinword.exe");
        JScrollPane scroll = new JScrollPane(blacklistArea);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        scroll.getViewport().setBackground(UITheme.BG_DARK);
        panel.add(scroll, BorderLayout.CENTER);

        // Bottom controls
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBackground(UITheme.BG_CARD);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.gridx   = 0;
        gc.insets  = new Insets(4, 0, 4, 0);

        gc.gridy = 0;
        bottom.add(styledButton("[OK] Áp dụng cho client được chọn", UITheme.ACCENT, e -> applyRestrict()), gc);
        gc.gridy = 1;
        bottom.add(styledButton("Gỡ hạn chế",                   UITheme.DANGER, e -> sendToSelected("RESTRICT_APPS_CLEAR")), gc);

        gc.gridy  = 2;
        gc.insets = new Insets(10, 0, 0, 0);
        JLabel note = new JLabel("<html><i>Client sẽ nhận danh sách app bị cấm và hiển thị cảnh báo khi mở.</i></html>");
        note.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        note.setForeground(new Color(160, 160, 160));
        bottom.add(note, gc);

        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void applyRestrict() {
        String raw = blacklistArea.getText().trim();
        if (raw.isEmpty()) {
            setStatus("[!] Danh sách app trống – không gửi lệnh.", UITheme.WARNING);
            return;
        }
        // Build comma-separated list from lines
        String[] lines = raw.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String app = line.trim();
            if (!app.isEmpty()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(app);
            }
        }
        sendToSelected("RESTRICT_APPS|" + sb.toString());
    }

    // ── Tab 3: Thông báo hàng loạt ──────────────────────────────────────────
    private JPanel buildNotifTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UITheme.BG_CARD);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Top: title field
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setBackground(UITheme.BG_CARD);
        JLabel titleLbl = styledLabel("Tiêu đề:", Font.BOLD, 13);
        titleLbl.setPreferredSize(new Dimension(70, 24));
        notifTitleField = new JTextField("Thông báo từ Quản trị viên");
        styleTextField(notifTitleField);
        titleRow.add(titleLbl,       BorderLayout.WEST);
        titleRow.add(notifTitleField, BorderLayout.CENTER);
        panel.add(titleRow, BorderLayout.NORTH);

        // Centre: content area
        JPanel contentBox = new JPanel(new BorderLayout(0, 4));
        contentBox.setBackground(UITheme.BG_CARD);
        contentBox.add(styledLabel("Nội dung:", Font.BOLD, 13), BorderLayout.NORTH);
        notifContentArea = new JTextArea(5, 30);
        styleTextArea(notifContentArea, "Nhập nội dung thông báo ở đây...");
        JScrollPane scroll = new JScrollPane(notifContentArea);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        scroll.getViewport().setBackground(UITheme.BG_DARK);
        contentBox.add(scroll, BorderLayout.CENTER);
        panel.add(contentBox, BorderLayout.CENTER);

        // Bottom: send buttons
        JPanel btns = new JPanel(new GridBagLayout());
        btns.setBackground(UITheme.BG_CARD);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.gridx   = 0;
        gc.insets  = new Insets(4, 0, 4, 0);

        gc.gridy = 0;
        btns.add(styledButton("Gửi thông báo đến tất cả",      UITheme.ACCENT,  e -> sendNotif(true)),  gc);
        gc.gridy = 1;
        btns.add(styledButton("Gửi đến client được chọn",       UITheme.WARNING, e -> sendNotif(false)), gc);
        panel.add(btns, BorderLayout.SOUTH);

        return panel;
    }

    private void sendNotif(boolean all) {
        String title   = notifTitleField.getText().trim();
        String content = notifContentArea.getText().trim();
        if (title.isEmpty() && content.isEmpty()) {
            setStatus("[!] Tiêu đề và nội dung trống – không gửi.", UITheme.WARNING);
            return;
        }
        String cmd = "NOTICE|[!] CẢNH BÁO TỪ SERVER" + (!title.isEmpty() ? " [" + title + "]: " : ": ") + content;
        if (all) sendToAll(cmd);
        else      sendToSelected(cmd);
    }

    // ── Status bar ───────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(20, 20, 30));
        bar.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, UITheme.BORDER),
                new EmptyBorder(4, 12, 4, 12)));

        statusBar = new JLabel("Sẵn sàng.");
        statusBar.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        statusBar.setForeground(new Color(160, 160, 160));
        bar.add(statusBar, BorderLayout.WEST);
        return bar;
    }

    private void setStatus(String msg, Color color) {
        statusBar.setText(msg);
        statusBar.setForeground(color);
    }

    // ── Core send helpers ────────────────────────────────────────────────────

    /** Sends a command to a single named client. */
    private void sendToClient(String clientName, String command) {
        Socket socket = clientSockets.get(clientName);
        if (socket == null) {
            setStatus("[!] Không tìm thấy socket cho: " + clientName, UITheme.WARNING);
            return;
        }
        PrintWriter writer = clientWriters.get(socket);
        if (writer == null) {
            setStatus("[!] Không tìm thấy writer cho: " + clientName, UITheme.WARNING);
            return;
        }
        writer.println(command);
        writer.flush();
    }

    /** Sends a command to all clients currently selected in the JList. */
    private void sendToSelected(String command) {
        List<String> selected = getSelectedClients();
        if (selected.isEmpty()) {
            setStatus("[!] Chưa chọn client nào.", UITheme.WARNING);
            return;
        }
        for (String name : selected) {
            sendToClient(name, command);
        }
        setStatus("[OK] Đã gửi \"" + command + "\" đến " + selected.size() + " client.", UITheme.SUCCESS);
    }

    /** Sends a command to all online clients. */
    private void sendToAll(String command) {
        if (onlineClients.isEmpty()) {
            setStatus("[!] Không có client nào online.", UITheme.WARNING);
            return;
        }
        for (String name : onlineClients) {
            sendToClient(name, command);
        }
        setStatus("[OK] Đã gửi \"" + command + "\" đến tất cả " + onlineClients.size() + " client.", UITheme.SUCCESS);
    }

    /** Returns the list of client names currently selected in the JList. */
    private List<String> getSelectedClients() {
        return clientJList.getSelectedValuesList();
    }

    // ── Style helpers ────────────────────────────────────────────────────────

    private JLabel styledLabel(String text, int style, int size) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(Color.WHITE);
        return lbl;
    }

    private JButton styledButton(String text, Color bg, ActionListener action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 36));

        // Hover effect
        btn.addMouseListener(new MouseAdapter() {
            private final Color original = bg;
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(original.brighter());
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(original);
            }
        });

        btn.addActionListener(action);
        return btn;
    }

    private void styleTextArea(JTextArea ta, String placeholder) {
        ta.setBackground(UITheme.BG_DARK);
        ta.setForeground(Color.WHITE);
        ta.setCaretColor(Color.WHITE);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(6, 8, 6, 8));
        ta.setText(placeholder);

        // Simple placeholder behaviour
        ta.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (ta.getText().equals(placeholder)) ta.setText("");
            }
            @Override public void focusLost(FocusEvent e) {
                if (ta.getText().isBlank()) ta.setText(placeholder);
            }
        });
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(UITheme.BG_DARK);
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(Color.WHITE);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                new EmptyBorder(4, 8, 4, 8)));
    }
}
