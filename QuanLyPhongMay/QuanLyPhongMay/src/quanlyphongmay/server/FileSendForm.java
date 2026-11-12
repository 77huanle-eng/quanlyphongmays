package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * FileSendForm – giao diện gửi file từ Server đến Client(s) qua LAN.
 *
 * Protocol:
 *   FILESEND_START|filename|filesize   → báo hiệu bắt đầu
 *   FILESEND_CHUNK|base64data          → chunk dữ liệu (1024 byte mỗi chunk)
 *   FILESEND_END|filename              → hoàn tất truyền
 *
 * @author NAM
 */
public class FileSendForm extends JFrame {

    // ── Dependencies được inject qua constructor ────────────────────────────
    private final Map<Socket, PrintWriter> clientWriters;
    private final Map<String, Socket>      clientSockets;
    private final List<String>             onlineClients;

    // ── UI components ────────────────────────────────────────────────────────
    private JTextField  txtFilePath;
    private JButton     btnBrowse;
    private JButton     btnSelectAll;
    private JButton     btnSend;
    private JPanel      pnlClients;
    private JScrollPane scrClients;
    private JPanel      pnlProgress;
    private JScrollPane scrProgress;
    private JLabel      lblStatus;

    /** Map from clientName → progress row panel (for updates) */
    private final Map<String, JProgressBar> progressBars = new LinkedHashMap<>();

    // ── Chunk size ───────────────────────────────────────────────────────────
    private static final int CHUNK_BYTES = 1024;

    // ── Checkboxes list ─────────────────────────────────────────────────────
    private final List<JCheckBox> checkBoxes = new ArrayList<>();

    // ───────────────────────────────────────────────────────────────────────
    // Constructor
    // ───────────────────────────────────────────────────────────────────────
    public FileSendForm(
            Map<Socket, PrintWriter> clientWriters,
            Map<String, Socket>      clientSockets,
            List<String>             onlineClients) {

        this.clientWriters = clientWriters;
        this.clientSockets = clientSockets;
        this.onlineClients = (onlineClients != null) ? onlineClients : new ArrayList<>();

        setTitle("LANPC – Gửi File đến Client");
        setSize(680, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        buildUI();
        applyTheme();
    }

    // ───────────────────────────────────────────────────────────────────────
    // UI construction
    // ───────────────────────────────────────────────────────────────────────
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UITheme.BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));
        setContentPane(root);

        // ── Title bar ───────────────────────────────────────────────────────
        JLabel lblTitle = new JLabel("LANPC – Gửi File đến Client");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.ACCENT);
        lblTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        root.add(lblTitle, BorderLayout.NORTH);

        // ── Center (file picker + client list + progress) ───────────────────
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        root.add(center, BorderLayout.CENTER);

        // ── File picker card ────────────────────────────────────────────────
        JPanel cardFile = UITheme.makeCardWithTitle("Chọn File", new BorderLayout(8, 0));
        txtFilePath = new JTextField();
        txtFilePath.setEditable(false);
        txtFilePath.setText("(Chưa chọn file)");
        UITheme.styleTextField(txtFilePath);

        btnBrowse = new JButton("Duyệt…");
        UITheme.styleButtonAccent(btnBrowse);
        btnBrowse.addActionListener(e -> browseFile());

        cardFile.add(txtFilePath, BorderLayout.CENTER);
        cardFile.add(btnBrowse,   BorderLayout.EAST);
        center.add(cardFile, BorderLayout.NORTH);

        // ── Middle: clients + progress side by side ─────────────────────────
        JPanel middle = new JPanel(new GridLayout(1, 2, 10, 0));
        middle.setOpaque(false);
        center.add(middle, BorderLayout.CENTER);

        // ── Client list card ────────────────────────────────────────────────
        JPanel cardClients = UITheme.makeCardWithTitle("Máy nhận", new BorderLayout(0, 6));

        pnlClients = new JPanel();
        pnlClients.setLayout(new BoxLayout(pnlClients, BoxLayout.Y_AXIS));
        pnlClients.setBackground(UITheme.BG_CARD);

        populateClientCheckboxes();

        scrClients = new JScrollPane(pnlClients);
        UITheme.styleScrollPane(scrClients);
        scrClients.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cardClients.add(scrClients, BorderLayout.CENTER);

        btnSelectAll = new JButton("[x] Gửi tất cả");
        UITheme.styleButtonGhost(btnSelectAll);
        btnSelectAll.addActionListener(e -> toggleSelectAll());
        JPanel pnlSelectAllWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pnlSelectAllWrap.setOpaque(false);
        pnlSelectAllWrap.add(btnSelectAll);
        cardClients.add(pnlSelectAllWrap, BorderLayout.SOUTH);

        middle.add(cardClients);

        // ── Progress card ───────────────────────────────────────────────────
        JPanel cardProgress = UITheme.makeCardWithTitle("Tiến trình gửi", new BorderLayout());

        pnlProgress = new JPanel();
        pnlProgress.setLayout(new BoxLayout(pnlProgress, BoxLayout.Y_AXIS));
        pnlProgress.setBackground(UITheme.BG_CARD);

        scrProgress = new JScrollPane(pnlProgress);
        UITheme.styleScrollPane(scrProgress);
        scrProgress.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cardProgress.add(scrProgress, BorderLayout.CENTER);

        middle.add(cardProgress);

        // ── Bottom bar ──────────────────────────────────────────────────────
        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        lblStatus = new JLabel("Sẵn sàng.");
        lblStatus.setFont(UITheme.FONT_SMALL);
        lblStatus.setForeground(UITheme.TEXT_MUTED);

        btnSend = new JButton("  [Bắt đầu]  Gửi");
        btnSend.setPreferredSize(new Dimension(130, 36));
        UITheme.styleButtonAccent(btnSend);
        btnSend.addActionListener(e -> startSending());

        bottom.add(lblStatus, BorderLayout.CENTER);
        bottom.add(btnSend,   BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
    }

    // ───────────────────────────────────────────────────────────────────────
    // Theme
    // ───────────────────────────────────────────────────────────────────────
    private void applyTheme() {
        getContentPane().setBackground(UITheme.BG_DARK);
    }

    // ───────────────────────────────────────────────────────────────────────
    // Populate checkbox list from onlineClients
    // ───────────────────────────────────────────────────────────────────────
    private void populateClientCheckboxes() {
        checkBoxes.clear();
        pnlClients.removeAll();

        if (onlineClients.isEmpty()) {
            JLabel lbl = new JLabel("  (Không có Client nào online)");
            lbl.setFont(UITheme.FONT_BODY);
            lbl.setForeground(UITheme.TEXT_MUTED);
            pnlClients.add(lbl);
        } else {
            for (String name : onlineClients) {
                JCheckBox cb = new JCheckBox("  [PC]  " + name);
                cb.setFont(UITheme.FONT_BODY);
                cb.setForeground(UITheme.TEXT_PRIMARY);
                cb.setBackground(UITheme.BG_CARD);
                cb.setFocusPainted(false);
                cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                checkBoxes.add(cb);
                pnlClients.add(cb);
                pnlClients.add(Box.createVerticalStrut(4));
            }
        }
        pnlClients.revalidate();
        pnlClients.repaint();
    }

    // ───────────────────────────────────────────────────────────────────────
    // Select all / deselect all toggle
    // ───────────────────────────────────────────────────────────────────────
    private boolean allSelected = false;

    private void toggleSelectAll() {
        allSelected = !allSelected;
        for (JCheckBox cb : checkBoxes) {
            cb.setSelected(allSelected);
        }
        btnSelectAll.setText(allSelected ? "[ ] Bỏ chọn tất cả" : "[x] Gửi tất cả");
    }

    // ───────────────────────────────────────────────────────────────────────
    // Browse file
    // ───────────────────────────────────────────────────────────────────────
    private File selectedFile = null;

    private void browseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file cần gửi");
        chooser.setMultiSelectionEnabled(false);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            txtFilePath.setText(selectedFile.getAbsolutePath());
            lblStatus.setText("File: " + selectedFile.getName()
                    + "  (" + formatSize(selectedFile.length()) + ")");
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Collect selected client names and kick off transfers
    // ───────────────────────────────────────────────────────────────────────
    private void startSending() {
        if (selectedFile == null || !selectedFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn file trước khi gửi.",
                    "Chưa chọn file", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> targets = new ArrayList<>();
        for (JCheckBox cb : checkBoxes) {
            if (cb.isSelected()) {
                // Strip the icon prefix we added
                String rawLabel = cb.getText().replaceFirst("^\\s*[PC]\\s*", "").trim();
                targets.add(rawLabel);
            }
        }

        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng chọn ít nhất một Client.",
                    "Chưa chọn Client", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Build / refresh progress rows
        pnlProgress.removeAll();
        progressBars.clear();
        for (String name : targets) {
            addProgressRow(name);
        }
        pnlProgress.revalidate();
        pnlProgress.repaint();

        btnSend.setEnabled(false);
        btnBrowse.setEnabled(false);
        lblStatus.setText("Đang gửi tới " + targets.size() + " máy…");

        File fileToSend = selectedFile;

        // Delegate to static helper which spawns threads
        new Thread(() -> {
            sendFileToClients(targets, fileToSend, clientWriters, clientSockets);
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("[OK]  Hoàn tất gửi file: " + fileToSend.getName());
                btnSend.setEnabled(true);
                btnBrowse.setEnabled(true);
            });
        }, "FileSend-Coordinator").start();
    }

    // ───────────────────────────────────────────────────────────────────────
    // Add a labeled progress row into pnlProgress
    // ───────────────────────────────────────────────────────────────────────
    private void addProgressRow(String clientName) {
        JPanel row = new JPanel(new BorderLayout(6, 2));
        row.setBackground(UITheme.BG_CARD);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        JLabel lbl = new JLabel("[PC]  " + clientName);
        lbl.setFont(UITheme.FONT_SMALL);
        lbl.setForeground(UITheme.TEXT_MUTED);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setString("0%");
        bar.setForeground(UITheme.ACCENT);
        bar.setBackground(UITheme.BG_INPUT);
        bar.setFont(UITheme.FONT_SMALL);

        progressBars.put(clientName, bar);

        row.add(lbl, BorderLayout.NORTH);
        row.add(bar,  BorderLayout.CENTER);
        pnlProgress.add(row);
        pnlProgress.add(Box.createVerticalStrut(4));
    }

    // ───────────────────────────────────────────────────────────────────────
    // Update a progress bar (called from background thread → via EDT)
    // ───────────────────────────────────────────────────────────────────────
    private void updateProgress(String clientName, int percent, boolean done, boolean error) {
        SwingUtilities.invokeLater(() -> {
            JProgressBar bar = progressBars.get(clientName);
            if (bar == null) return;
            bar.setValue(Math.min(percent, 100));
            if (error) {
                bar.setString("Lỗi!");
                bar.setForeground(UITheme.DANGER);
            } else if (done) {
                bar.setValue(100);
                bar.setString("Hoàn thành");
                bar.setForeground(UITheme.SUCCESS);
            } else {
                bar.setString(percent + "%");
            }
        });
    }

    // ───────────────────────────────────────────────────────────────────────
    // Instance method: send file to a single client (uses this form's maps)
    // ───────────────────────────────────────────────────────────────────────
    public void sendFileToClient(String clientName, File file) {
        Thread t = new Thread(() -> {
            doSendFile(clientName, file, clientWriters, clientSockets,
                       (pct, done, err) -> updateProgress(clientName, pct, done, err));
        }, "FileSend-" + clientName);
        t.setDaemon(true);
        t.start();
    }

    // ───────────────────────────────────────────────────────────────────────
    // Static helper — called from ServerForm or other callers
    // ───────────────────────────────────────────────────────────────────────
    public static void sendFileToClients(
            List<String>             targets,
            File                     file,
            Map<Socket, PrintWriter> clientWriters,
            Map<String, Socket>      clientSockets) {

        List<Thread> threads = new ArrayList<>();
        for (String name : targets) {
            Thread t = new Thread(() ->
                doSendFile(name, file, clientWriters, clientSockets, null),
                "FileSend-" + name
            );
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        // Wait for all to finish (optional; caller thread joins)
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Core transfer logic (static so both instance + static paths share it)
    // ───────────────────────────────────────────────────────────────────────
    /**
     * Sends {@code file} to the named client.
     *
     * @param progressCallback optional; receives (percent, done, error) updates
     */
    private static void doSendFile(
            String                   clientName,
            File                     file,
            Map<Socket, PrintWriter> clientWriters,
            Map<String, Socket>      clientSockets,
            ProgressCallback         progressCallback) {

        Socket socket = clientSockets.get(clientName);
        if (socket == null || socket.isClosed()) {
            notifyProgress(progressCallback, 0, false, true);
            return;
        }

        PrintWriter writer = clientWriters.get(socket);
        if (writer == null) {
            notifyProgress(progressCallback, 0, false, true);
            return;
        }

        try {
            byte[] data     = Files.readAllBytes(file.toPath());
            long   fileSize = data.length;
            String fileName = file.getName();
            int    total    = (fileSize == 0) ? 1 : (int) Math.ceil((double) fileSize / CHUNK_BYTES);

            // ── START ──────────────────────────────────────────────────────
            writer.println("FILESEND_START|" + fileName + "|" + fileSize);
            writer.flush();

            // ── CHUNKS ─────────────────────────────────────────────────────
            int offset = 0;
            int chunkIndex = 0;
            while (offset < data.length) {
                int len = Math.min(CHUNK_BYTES, data.length - offset);
                byte[] chunk   = Arrays.copyOfRange(data, offset, offset + len);
                String encoded = Base64.getEncoder().encodeToString(chunk);
                writer.println("FILESEND_CHUNK|" + encoded);
                writer.flush();

                offset += len;
                chunkIndex++;

                int pct = (int) (((double) chunkIndex / total) * 100);
                notifyProgress(progressCallback, pct, false, false);
            }

            // ── END ────────────────────────────────────────────────────────
            writer.println("FILESEND_END|" + fileName);
            writer.flush();

            notifyProgress(progressCallback, 100, true, false);

        } catch (IOException e) {
            notifyProgress(progressCallback, 0, false, true);
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────
    @FunctionalInterface
    private interface ProgressCallback {
        void onProgress(int percent, boolean done, boolean error);
    }

    private static void notifyProgress(ProgressCallback cb, int pct, boolean done, boolean err) {
        if (cb != null) cb.onProgress(pct, done, err);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1048576)    return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.2f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }
}
