package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ActivityLogForm - Giao diện nhật ký hoạt động của phòng máy (Pure Java Swing)
 */
public class ActivityLogForm extends javax.swing.JFrame {

    // Lớp đại diện cho 1 dòng nhật ký
    public static class LogItem {
        private String thoiGian;
        private String maMay;
        private String hanhDong;
        private String chiTiet;

        public LogItem(String thoiGian, String maMay, String hanhDong, String chiTiet) {
            this.thoiGian = thoiGian;
            this.maMay = maMay;
            this.hanhDong = hanhDong;
            this.chiTiet = chiTiet;
        }

        public String getThoiGian() { return thoiGian; }
        public String getMaMay() { return maMay; }
        public String getHanhDong() { return hanhDong; }
        public String getChiTiet() { return chiTiet; }

        @Override
        public String toString() {
            return "[" + thoiGian + "] " + maMay + " - " + hanhDong + ": " + chiTiet;
        }
    }

    private static final List<LogItem> danhSachLog = new ArrayList<>();
    private static ActivityLogForm instanceHienTai = null;
    private static final DateTimeFormatter DFM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ActivityLogForm() {
        initComponents();
        setTitle("He thong quan ly phong may - Nhat ky hoat dong");
        setLocationRelativeTo(null);

        instanceHienTai = this;
        hienThiNhatKy(danhSachLog);
    }

    private void hienThiNhatKy(List<LogItem> list) {
        StringBuilder sb = new StringBuilder();
        for (LogItem item : list) {
            sb.append(item.toString()).append("\n");
        }
        txtNhatKy.setText(sb.toString());
        lblTongSo.setText("Tong so ban ghi: " + list.size());
    }

    /**
     * Ham ghi nhat ky dung chung toan he thong
     */
    public static void themNhatKy(String maMay, String hanhDong, String chiTiet) {
        String thoiGian = LocalDateTime.now().format(DFM);
        LogItem item = new LogItem(thoiGian, maMay, hanhDong, chiTiet);
        danhSachLog.add(item);

        if (instanceHienTai != null && instanceHienTai.isDisplayable()) {
            java.awt.EventQueue.invokeLater(() -> {
                instanceHienTai.hienThiNhatKy(danhSachLog);
            });
        }
    }

    private void initComponents() {
        lblTitle = new javax.swing.JLabel();
        lblMaMay = new javax.swing.JLabel();
        txtMaMay = new javax.swing.JTextField();
        lblLoaiHoatDong = new javax.swing.JLabel();
        txtLoaiHoatDong = new javax.swing.JTextField();
        lblNoiDung = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtNhatKy = new javax.swing.JTextArea();
        btnLoc = new javax.swing.JButton();
        btnXoaLog = new javax.swing.JButton();
        btnLamMoi = new javax.swing.JButton();
        lblTongSo = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(UITheme.BG_DARK);

        lblTitle.setFont(new java.awt.Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(UITheme.ACCENT);
        lblTitle.setText("NHAT KY HOAT DONG");
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);

        lblMaMay.setFont(UITheme.FONT_BODY);
        lblMaMay.setForeground(UITheme.TEXT_PRIMARY);
        lblMaMay.setText("Ma may can loc: ");

        txtMaMay.setText("PC01");
        UITheme.styleTextField(txtMaMay);

        lblLoaiHoatDong.setFont(UITheme.FONT_BODY);
        lblLoaiHoatDong.setForeground(UITheme.TEXT_PRIMARY);
        lblLoaiHoatDong.setText("Loai hoat dong: ");

        txtLoaiHoatDong.setText("Tat ca");
        UITheme.styleTextField(txtLoaiHoatDong);

        lblNoiDung.setFont(UITheme.FONT_BODY);
        lblNoiDung.setForeground(UITheme.TEXT_PRIMARY);
        lblNoiDung.setText("Noi dung nhat ky: ");

        txtNhatKy.setColumns(20);
        txtNhatKy.setRows(5);
        txtNhatKy.setText("\n");
        UITheme.styleTextArea(txtNhatKy);
        jScrollPane1.setViewportView(txtNhatKy);
        jScrollPane1.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));

        btnLoc.setText("Loc nhat ky");
        UITheme.styleButtonAccent(btnLoc);
        btnLoc.addActionListener(this::btnLocActionPerformed);

        btnXoaLog.setText("Xoa nhat ky");
        UITheme.styleButtonDanger(btnXoaLog);
        btnXoaLog.addActionListener(this::btnXoaLogActionPerformed);

        btnLamMoi.setText("Lam moi");
        UITheme.styleButtonGhost(btnLamMoi);
        btnLamMoi.addActionListener(this::btnLamMoiActionPerformed);

        lblTongSo.setFont(UITheme.FONT_BODY);
        lblTongSo.setForeground(UITheme.TEXT_MUTED);
        lblTongSo.setText("Tong so ban ghi: 5");
        lblTongSo.setHorizontalAlignment(SwingConstants.CENTER);

        // Bố cục giao diện bằng Layout Managers chuẩn
        JPanel mainPanel = new JPanel(new BorderLayout(0, 15));
        mainPanel.setBackground(UITheme.BG_DARK);
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

        // 1. Header (North)
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setOpaque(false);
        titlePanel.add(lblTitle);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // 2. Center: Các trường lọc và nhật ký
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);

        // Dòng 0: Mã máy
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        centerPanel.add(lblMaMay, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtMaMay, gbc);

        // Dòng 1: Loại hoạt động
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblLoaiHoatDong, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtLoaiHoatDong, gbc);

        // Dòng 2: Nội dung nhật ký
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblNoiDung, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        centerPanel.add(jScrollPane1, gbc);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 3. South: Nút bấm và thống kê tổng số
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setOpaque(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnLoc);
        buttonPanel.add(btnXoaLog);
        buttonPanel.add(btnLamMoi);
        southPanel.add(buttonPanel);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        infoPanel.setOpaque(false);
        infoPanel.add(lblTongSo);
        southPanel.add(infoPanel);

        mainPanel.add(southPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setPreferredSize(new Dimension(640, 540));
        pack();
    }

    private void btnLocActionPerformed(java.awt.event.ActionEvent evt) {
        String maCanLoc = txtMaMay.getText().trim().toLowerCase();
        String loaiCanLoc = txtLoaiHoatDong.getText().trim().toLowerCase();

        List<LogItem> ketQua = new ArrayList<>();
        for (LogItem item : danhSachLog) {
            boolean hopLeMa = maCanLoc.isEmpty() || item.getMaMay().toLowerCase().contains(maCanLoc);
            boolean hopLeLoai = loaiCanLoc.isEmpty() || "tat ca".equals(loaiCanLoc)
                    || item.getHanhDong().toLowerCase().contains(loaiCanLoc)
                    || item.getChiTiet().toLowerCase().contains(loaiCanLoc);

            if (hopLeMa && hopLeLoai) {
                ketQua.add(item);
            }
        }

        hienThiNhatKy(ketQua);
        JOptionPane.showMessageDialog(this, "Tim thay " + ketQua.size() + " ban ghi phu hop!");
    }

    private void btnXoaLogActionPerformed(java.awt.event.ActionEvent evt) {
        if (danhSachLog.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nhat ky hien tai dang trong!");
            return;
        }

        int xacNhan = JOptionPane.showConfirmDialog(
            this,
            "Ban co chac chan muon xoa toan bo nhat ky hoat dong?",
            "Xac nhan xoa",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (xacNhan == JOptionPane.YES_OPTION) {
            danhSachLog.clear();
            hienThiNhatKy(danhSachLog);
            JOptionPane.showMessageDialog(this, "Da xoa sach nhat ky thanh cong!");
        }
    }

    private void btnLamMoiActionPerformed(java.awt.event.ActionEvent evt) {
        txtMaMay.setText("");
        txtLoaiHoatDong.setText("Tat ca");
        hienThiNhatKy(danhSachLog);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ActivityLogForm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> new ActivityLogForm().setVisible(true));
    }

    // Khai báo các thành phần giao diện (giữ nguyên tên biến 100%)
    private javax.swing.JButton btnLamMoi;
    private javax.swing.JButton btnLoc;
    private javax.swing.JButton btnXoaLog;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblLoaiHoatDong;
    private javax.swing.JLabel lblMaMay;
    private javax.swing.JLabel lblNoiDung;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblTongSo;
    private javax.swing.JTextField txtLoaiHoatDong;
    private javax.swing.JTextField txtMaMay;
    private javax.swing.JTextArea txtNhatKy;
}
