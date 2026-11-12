package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;

/**
 * SilentInstallForm - Module phân phối và cài đặt phần mềm ngầm hàng loạt (Silent Install Deployment).
 */
public class SilentInstallForm extends JFrame {

    private final Map<Socket, PrintWriter> clientWriters;
    private final Map<String, Socket> clientSockets;
    private final List<String> onlineClients;

    private File selectedInstaller = null;
    private JTextField txtFilePath;
    private JTextField txtSilentArgs;
    private JComboBox<String> cbPreset;
    private JProgressBar progressBar;
    private JTable tblTargets;
    private DefaultTableModel modelTargets;
    private JButton btnSelectFile;
    private JButton btnDeploy;

    // Singleton listener instance for receiving INSTALL_RESULT
    private static SilentInstallForm activeInstance = null;

    public static void handleInstallResult(String maMay, String fileName, boolean success, int exitCode, String message) {
        if (activeInstance != null) {
            SwingUtilities.invokeLater(() -> activeInstance.onInstallResult(maMay, fileName, success, exitCode, message));
        }
    }

    public SilentInstallForm(
            Map<Socket, PrintWriter> clientWriters,
            Map<String, Socket> clientSockets,
            List<String> onlineClients) {

        super("Cài đặt phần mềm ẩn hàng loạt (Silent Install) — LANPC");
        this.clientWriters = clientWriters;
        this.clientSockets = clientSockets;
        this.onlineClients = (onlineClients != null) ? onlineClients : new ArrayList<>();

        activeInstance = this;

        initComponents();
        buildUI();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(920, 680);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (activeInstance == SilentInstallForm.this) {
                    activeInstance = null;
                }
            }
        });
    }

    private void initComponents() {
        txtFilePath = new JTextField();
        txtFilePath.setEditable(false);
        UITheme.styleTextField(txtFilePath);

        btnSelectFile = new JButton("Chọn File Installer...");
        UITheme.styleButtonAccent(btnSelectFile);
        btnSelectFile.addActionListener(e -> chonFileInstaller());

        cbPreset = new JComboBox<>(new String[]{
            "Tự động theo định dạng file",
            "Inno Setup (/VERYSILENT /SUPPRESSMSGBOXES /NORESTART)",
            "NSIS Installer (/S)",
            "MSI Package (/qn /norestart)",
            "Chạy script nền (cmd/powershell)",
            "Tùy chỉnh tham số (Custom)"
        });
        UITheme.styleComboBox(cbPreset);
        cbPreset.addActionListener(e -> capNhatPreset());

        txtSilentArgs = new JTextField("/VERYSILENT /SUPPRESSMSGBOXES /NORESTART");
        UITheme.styleTextField(txtSilentArgs);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(UITheme.SUCCESS);
        progressBar.setBackground(UITheme.BG_INPUT);

        modelTargets = new DefaultTableModel(
                new Object[]{"Chọn", "Mã máy", "Trạng thái truyền file", "Tiến trình cài đặt", "Mã kết quả"}, 0
        ) {
            @Override
            public Class<?> getColumnClass(int col) {
                return (col == 0) ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 0;
            }
        };

        tblTargets = new JTable(modelTargets);
        UITheme.styleTable(tblTargets);
        tblTargets.setRowHeight(28);

        tblTargets.getColumnModel().getColumn(0).setMaxWidth(60);
        tblTargets.getColumnModel().getColumn(1).setPreferredWidth(100);
        tblTargets.getColumnModel().getColumn(2).setPreferredWidth(180);
        tblTargets.getColumnModel().getColumn(3).setPreferredWidth(220);
        tblTargets.getColumnModel().getColumn(4).setPreferredWidth(120);

        for (String m : onlineClients) {
            modelTargets.addRow(new Object[]{Boolean.TRUE, m, "Sẵn sàng", "Chưa chạy", "—"});
        }

        btnDeploy = new JButton("[Deploy] Bắt đầu cài đặt hàng loạt (Deploy)");
        UITheme.styleButtonDanger(btnDeploy);
        btnDeploy.addActionListener(e -> batDauTrienKhai());
    }

    private void buildUI() {
        getContentPane().setBackground(UITheme.BG_DARK);
        setLayout(new BorderLayout(0, 12));

        // 1. North: Chọn file & tham số silent
        JPanel northPanel = new JPanel(new BorderLayout(0, 10));
        northPanel.setOpaque(false);
        northPanel.setBorder(new EmptyBorder(15, 20, 0, 20));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        JLabel icon = new JLabel("[Cài đặt]");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        JLabel title = new JLabel("CÀI ĐẶT PHẦN MỀM ẨN HÀNG LOẠT (SILENT INSTALL DEPLOYMENT)");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.ACCENT);
        titleRow.add(icon);
        titleRow.add(title);
        northPanel.add(titleRow, BorderLayout.NORTH);

        JPanel configCard = new JPanel(new GridBagLayout());
        configCard.setBackground(UITheme.BG_CARD);
        configCard.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(12, 16, 12, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Dòng 0: Chọn file installer
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel l1 = new JLabel("File cài đặt (.exe, .msi):");
        UITheme.styleLabelBody(l1);
        configCard.add(l1, gbc);

        JPanel filePanel = new JPanel(new BorderLayout(8, 0));
        filePanel.setOpaque(false);
        filePanel.add(txtFilePath, BorderLayout.CENTER);
        filePanel.add(btnSelectFile, BorderLayout.EAST);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        configCard.add(filePanel, gbc);

        // Dòng 1: Preset
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel l2 = new JLabel("Preset cài đặt ngầm:");
        UITheme.styleLabelBody(l2);
        configCard.add(l2, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        configCard.add(cbPreset, gbc);

        // Dòng 2: Tham số Silent args
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel l3 = new JLabel("Tham số dòng lệnh (Args):");
        UITheme.styleLabelBody(l3);
        configCard.add(l3, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        configCard.add(txtSilentArgs, gbc);

        northPanel.add(configCard, BorderLayout.CENTER);
        add(northPanel, BorderLayout.NORTH);

        // 2. Center: Danh sách máy mục tiêu và tiến độ
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(0, 20, 0, 20));

        JPanel tableHeaderPanel = new JPanel(new BorderLayout());
        tableHeaderPanel.setOpaque(false);

        JLabel tblHeader = new JLabel("Danh sách máy trạm mục tiêu (" + onlineClients.size() + " máy):");
        UITheme.styleLabelBody(tblHeader);
        tableHeaderPanel.add(tblHeader, BorderLayout.WEST);

        JPanel selBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        selBtnRow.setOpaque(false);
        JButton btnSelectAll = new JButton("Chọn tất cả");
        UITheme.styleButtonGhost(btnSelectAll);
        btnSelectAll.addActionListener(e -> toggleAllSelection(true));

        JButton btnDeselectAll = new JButton("Bỏ chọn");
        UITheme.styleButtonGhost(btnDeselectAll);
        btnDeselectAll.addActionListener(e -> toggleAllSelection(false));

        selBtnRow.add(btnSelectAll);
        selBtnRow.add(btnDeselectAll);
        tableHeaderPanel.add(selBtnRow, BorderLayout.EAST);

        centerPanel.add(tableHeaderPanel, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(tblTargets);
        UITheme.styleScrollPane(sp);
        centerPanel.add(sp, BorderLayout.CENTER);

        JPanel progressPanel = new JPanel(new BorderLayout(0, 4));
        progressPanel.setOpaque(false);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        centerPanel.add(progressPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        // 3. South: Nút triển khai
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));
        southPanel.setBackground(UITheme.BG_CARD);
        southPanel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(6, 15, 6, 15)
        ));

        southPanel.add(btnDeploy);
        add(southPanel, BorderLayout.SOUTH);
    }

    private void chonFileInstaller() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn tệp tin cài đặt");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            selectedInstaller = chooser.getSelectedFile();
            txtFilePath.setText(selectedInstaller.getAbsolutePath() + " (" + formatSize(selectedInstaller.length()) + ")");
            capNhatPreset();
        }
    }

    private void capNhatPreset() {
        int idx = cbPreset.getSelectedIndex();
        if (selectedInstaller != null && idx == 0) {
            String name = selectedInstaller.getName().toLowerCase();
            if (name.endsWith(".msi")) {
                txtSilentArgs.setText("/qn /norestart");
            } else if (name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".ps1")) {
                txtSilentArgs.setText("");
            } else {
                txtSilentArgs.setText("/VERYSILENT /SUPPRESSMSGBOXES /NORESTART");
            }
        } else if (idx == 1) {
            txtSilentArgs.setText("/VERYSILENT /SUPPRESSMSGBOXES /NORESTART");
        } else if (idx == 2) {
            txtSilentArgs.setText("/S");
        } else if (idx == 3) {
            txtSilentArgs.setText("/qn /norestart");
        } else if (idx == 4) {
            txtSilentArgs.setText("");
        }
    }

    private void toggleAllSelection(boolean select) {
        for (int i = 0; i < modelTargets.getRowCount(); i++) {
            modelTargets.setValueAt(select, i, 0);
        }
    }

    private void batDauTrienKhai() {
        if (selectedInstaller == null || !selectedInstaller.exists()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn file cài đặt hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> targets = new ArrayList<>();
        for (int i = 0; i < modelTargets.getRowCount(); i++) {
            Boolean chk = (Boolean) modelTargets.getValueAt(i, 0);
            if (Boolean.TRUE.equals(chk)) {
                targets.add(String.valueOf(modelTargets.getValueAt(i, 1)));
            }
        }

        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa chọn máy trạm nào để cài đặt!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = selectedInstaller.getName();
        long fileSize = selectedInstaller.length();
        String silentArgs = txtSilentArgs.getText().trim();

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bắt đầu truyền file \"" + fileName + "\" (" + formatSize(fileSize) + ")\n"
                + "và kích hoạt cài đặt ngầm tới " + targets.size() + " máy trạm?",
                "Xác nhận cài đặt hàng loạt",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        btnDeploy.setEnabled(false);
        btnSelectFile.setEnabled(false);
        progressBar.setValue(0);

        // Reset trạng thái các máy được chọn
        for (int i = 0; i < modelTargets.getRowCount(); i++) {
            String m = String.valueOf(modelTargets.getValueAt(i, 1));
            if (targets.contains(m)) {
                modelTargets.setValueAt("Đang truyền file...", i, 2);
                modelTargets.setValueAt("Đang chờ file", i, 3);
                modelTargets.setValueAt("—", i, 4);
            }
        }

        // Luồng gửi file qua Socket
        new Thread(() -> {
            try {
                // 1. Gửi gói START
                String startPacket = "SILENT_INSTALL_START|" + fileName + "|" + fileSize + "|" + silentArgs;
                for (String m : targets) {
                    sendTo(m, startPacket);
                }

                // 2. Đọc và gửi từng CHUNK (64 KB)
                byte[] buf = new byte[64 * 1024];
                long totalSent = 0;

                try (FileInputStream fis = new FileInputStream(selectedInstaller)) {
                    int read;
                    while ((read = fis.read(buf)) != -1) {
                        byte[] chunk = (read == buf.length) ? buf : Arrays.copyOf(buf, read);
                        String b64 = Base64.getEncoder().encodeToString(chunk);
                        String chunkPacket = "SILENT_INSTALL_CHUNK|" + b64;

                        for (String m : targets) {
                            sendTo(m, chunkPacket);
                        }

                        totalSent += read;
                        int pct = (int) ((totalSent * 100) / fileSize);
                        SwingUtilities.invokeLater(() -> progressBar.setValue(pct));
                    }
                }

                // 3. Gửi gói END
                String endPacket = "SILENT_INSTALL_END|" + fileName;
                for (String m : targets) {
                    sendTo(m, endPacket);
                }

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    for (int i = 0; i < modelTargets.getRowCount(); i++) {
                        String m = String.valueOf(modelTargets.getValueAt(i, 1));
                        if (targets.contains(m)) {
                            modelTargets.setValueAt("[OK] Đã truyền xong", i, 2);
                            modelTargets.setValueAt("[...] Đang cài đặt ngầm...", i, 3);
                        }
                    }
                    btnDeploy.setEnabled(true);
                    btnSelectFile.setEnabled(true);
                });

                ActivityLogForm.themNhatKy("Tất cả máy", "Cài đặt ngầm", "Đã gửi installer " + fileName + " tới " + targets.size() + " máy");

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Lỗi truyền file cài đặt: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    btnDeploy.setEnabled(true);
                    btnSelectFile.setEnabled(true);
                });
            }
        }, "SilentDeploy-Sender").start();
    }

    private void sendTo(String clientName, String packet) {
        Socket s = clientSockets.get(clientName);
        if (s != null && !s.isClosed()) {
            PrintWriter pw = clientWriters.get(s);
            if (pw != null) {
                pw.println(packet);
                pw.flush();
            }
        }
    }

    public void onInstallResult(String maMay, String fileName, boolean success, int exitCode, String message) {
        for (int i = 0; i < modelTargets.getRowCount(); i++) {
            String m = String.valueOf(modelTargets.getValueAt(i, 1));
            if (m.equalsIgnoreCase(maMay)) {
                modelTargets.setValueAt(success ? "[OK] Thành công" : "[X] Thất bại", i, 3);
                modelTargets.setValueAt("Code: " + exitCode, i, 4);
                break;
            }
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / 1048576.0);
    }
}

