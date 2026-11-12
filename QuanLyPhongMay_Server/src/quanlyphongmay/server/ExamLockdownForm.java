package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;

/**
 * ExamLockdownForm - Quản lý và điều khiển Chế độ thi cử an toàn (Safe Exam & Lockdown Mode).
 */
public class ExamLockdownForm extends JFrame {

    private final BiConsumer<String, String> commandSender;
    private final List<String> onlineMachines;

    private JTextField txtExamTitle;
    private JSpinner spinDuration;
    private JCheckBox chkBlockInternet;
    private JTable tblExamStatus;
    private DefaultTableModel modelExamStatus;
    private JButton btnStartExam;
    private JButton btnStopExam;
    private JLabel lblExamState;
    private boolean isExamRunning = false;

    public ExamLockdownForm(BiConsumer<String, String> commandSender, List<String> onlineMachines) {
        super("Quản lý Chế độ Khóa bảo mật (System Lockdown) — LANPC");
        this.commandSender = commandSender;
        this.onlineMachines = (onlineMachines != null) ? onlineMachines : new ArrayList<>();

        initComponents();
        buildUI();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(850, 600);
        setMinimumSize(new Dimension(750, 500));
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        txtExamTitle = new JTextField("Phiên Bảo Mật Hệ Thống — Enterprise Lockdown");
        UITheme.styleTextField(txtExamTitle);

        spinDuration = new JSpinner(new SpinnerNumberModel(45, 5, 240, 5));
        spinDuration.setFont(UITheme.FONT_BODY);

        chkBlockInternet = new JCheckBox("Chặn Internet ngoài (Chỉ cho phép kết nối LAN máy chủ)", true);
        chkBlockInternet.setFont(UITheme.FONT_BODY);
        chkBlockInternet.setForeground(UITheme.TEXT_PRIMARY);
        chkBlockInternet.setOpaque(false);

        modelExamStatus = new DefaultTableModel(
                new Object[]{"STT", "Mã máy", "Trạng thái bảo mật", "Chặn Internet", "Thời gian bắt đầu"}, 0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tblExamStatus = new JTable(modelExamStatus);
        UITheme.styleTable(tblExamStatus);
        tblExamStatus.setRowHeight(26);

        int stt = 1;
        for (String m : onlineMachines) {
            modelExamStatus.addRow(new Object[]{stt++, m, "Chưa bắt đầu", "Không", "—"});
        }

        btnStartExam = new JButton("[Lockdown] Kích hoạt Khóa bảo mật");
        UITheme.styleButtonDanger(btnStartExam);
        btnStartExam.addActionListener(e -> batDauCaThi());

        btnStopExam = new JButton("[Mở khóa] Gỡ bỏ Khóa bảo mật & Mở máy");
        UITheme.styleButtonAccent(btnStopExam);
        btnStopExam.setEnabled(false);
        btnStopExam.addActionListener(e -> ketThucCaThi());

        lblExamState = new JLabel("Trạng thái: Chưa kích hoạt Khóa bảo mật");
        lblExamState.setFont(UITheme.FONT_HEADER);
        lblExamState.setForeground(UITheme.TEXT_MUTED);
    }

    private void buildUI() {
        getContentPane().setBackground(UITheme.BG_DARK);
        setLayout(new BorderLayout(0, 12));

        // 1. North: Cấu hình ca thi
        JPanel northPanel = new JPanel(new BorderLayout(0, 10));
        northPanel.setOpaque(false);
        northPanel.setBorder(new EmptyBorder(15, 20, 0, 20));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        JLabel icon = new JLabel("[Lockdown]");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        JLabel title = new JLabel("CHẾ ĐỘ KHÓA BẢO MẬT HỆ THỐNG (SYSTEM LOCKDOWN)");
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

        // Dòng 0: Tên bài thi
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel l1 = new JLabel("Tên phiên bảo mật:");
        UITheme.styleLabelBody(l1);
        configCard.add(l1, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        configCard.add(txtExamTitle, gbc);

        // Dòng 1: Thời gian làm bài
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel l2 = new JLabel("Thời gian (phút):");
        UITheme.styleLabelBody(l2);
        configCard.add(l2, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE;
        configCard.add(spinDuration, gbc);

        // Dòng 2: Tùy chọn chặn mạng
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        configCard.add(chkBlockInternet, gbc);

        northPanel.add(configCard, BorderLayout.CENTER);
        add(northPanel, BorderLayout.NORTH);

        // 2. Center: Bảng trạng thái máy thi
        JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(0, 20, 0, 20));

        JLabel tblHeader = new JLabel("Danh sách máy trạm áp dụng chính sách bảo mật (" + onlineMachines.size() + " máy):");
        UITheme.styleLabelBody(tblHeader);
        centerPanel.add(tblHeader, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(tblExamStatus);
        UITheme.styleScrollPane(sp);
        centerPanel.add(sp, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // 3. South: Nút hành động
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBackground(UITheme.BG_CARD);
        southPanel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(10, 20, 10, 20)
        ));

        southPanel.add(lblExamState, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(btnStartExam);
        btnPanel.add(btnStopExam);
        southPanel.add(btnPanel, BorderLayout.EAST);

        add(southPanel, BorderLayout.SOUTH);
    }

    private void batDauCaThi() {
        if (onlineMachines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không có máy trạm nào đang kết nối để bắt đầu thi!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String title = txtExamTitle.getText().trim();
        if (title.isEmpty()) title = "Kiểm tra phòng máy";
        int duration = (int) spinDuration.getValue();
        boolean blockNet = chkBlockInternet.isSelected();

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Kích hoạt phiên bảo mật: \"" + title + "\" (" + duration + " phút)?\n"
                + "Hệ thống sẽ KHÓA TOÀN BỘ " + onlineMachines.size() + " máy trạm,\n"
                + "vô hiệu hóa phím tắt hệ thống và " + (blockNet ? "CHẶN INTERNET NGOẠI VI." : "không chặn Internet."),
                "Xác nhận kích hoạt Chế độ Khóa bảo mật",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        isExamRunning = true;
        btnStartExam.setEnabled(false);
        btnStopExam.setEnabled(true);
        lblExamState.setText("Trạng thái: ĐANG ÁP DỤNG KHÓA BẢO MẬT (" + duration + " phút)");
        lblExamState.setForeground(UITheme.DANGER);

        String startTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

        // Gửi lệnh xuống toàn bộ máy con
        String command = "EXAM_LOCK|ENABLE|" + title + "|" + duration + "|" + blockNet;
        for (int i = 0; i < modelExamStatus.getRowCount(); i++) {
            String m = String.valueOf(modelExamStatus.getValueAt(i, 1));
            if (commandSender != null) {
                commandSender.accept(m, command);
            }
            modelExamStatus.setValueAt("[Khóa] Đang khóa bảo mật", i, 2);
            modelExamStatus.setValueAt(blockNet ? "Đã chặn" : "Không", i, 3);
            modelExamStatus.setValueAt(startTime, i, 4);
        }

        ActivityLogForm.themNhatKy("Tất cả máy", "Bắt đầu ca thi", "Môn: " + title + " (" + duration + " phút)");
        JOptionPane.showMessageDialog(this, "Đã kích hoạt Chế độ thi cử an toàn cho toàn bộ phòng máy!");
    }

    private void ketThucCaThi() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc chắn muốn KẾT THÚC ca thi và MỞ KHÓA toàn bộ máy trạm?",
                "Xác nhận kết thúc thi",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        isExamRunning = false;
        btnStartExam.setEnabled(true);
        btnStopExam.setEnabled(false);
        lblExamState.setText("Trạng thái: Đã gỡ bỏ Khóa bảo mật");
        lblExamState.setForeground(UITheme.SUCCESS);

        String command = "EXAM_LOCK|DISABLE";
        for (int i = 0; i < modelExamStatus.getRowCount(); i++) {
            String m = String.valueOf(modelExamStatus.getValueAt(i, 1));
            if (commandSender != null) {
                commandSender.accept(m, command);
            }
            modelExamStatus.setValueAt("[Mở khóa] Bình thường", i, 2);
        }

        ActivityLogForm.themNhatKy("Tất cả máy", "Kết thúc phiên bảo mật", "Quản trị viên gỡ bỏ khóa bảo mật hệ thống");
        JOptionPane.showMessageDialog(this, "Đã kết thúc ca thi và mở khóa toàn bộ máy trạm.");
    }
}

