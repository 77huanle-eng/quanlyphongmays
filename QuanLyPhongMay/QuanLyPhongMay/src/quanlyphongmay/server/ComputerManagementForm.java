package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ComputerManagementForm - Giao diện quản lý danh sách máy (Pure Java Swing)
 */
public class ComputerManagementForm extends javax.swing.JFrame {

    // Lớp đại diện cho Máy tính
    public static class MayTinh {
        private String maMay;
        private String tenMay;
        private String diaChiIP;
        private String trangThai;

        public MayTinh(String maMay, String tenMay, String diaChiIP, String trangThai) {
            this.maMay = maMay;
            this.tenMay = tenMay;
            this.diaChiIP = diaChiIP;
            this.trangThai = trangThai;
        }

        public String getMaMay() {
            return maMay;
        }

        public void setMaMay(String maMay) {
            this.maMay = maMay;
        }

        public String getTenMay() {
            return tenMay;
        }

        public void setTenMay(String tenMay) {
            this.tenMay = tenMay;
        }

        public String getDiaChiIP() {
            return diaChiIP;
        }

        public void setDiaChiIP(String diaChiIP) {
            this.diaChiIP = diaChiIP;
        }

        public String getTrangThai() {
            return trangThai;
        }

        public void setTrangThai(String trangThai) {
            this.trangThai = trangThai;
        }

        @Override
        public String toString() {
            return maMay + " - " + tenMay + " - " + diaChiIP + " - " + trangThai;
        }
    }

    private final List<MayTinh> danhSachMay = new ArrayList<>();

    public ComputerManagementForm() {
        initComponents();
        setTitle("He thong quan ly phong may - Quan ly danh sach may");
        setLocationRelativeTo(null);

        khoiTaoDuLieuMau();
        capNhatHienThiDanhSach();
    }

    private void khoiTaoDuLieuMau() {
        danhSachMay.add(new MayTinh("PC01", "May tinh 01", "192.168.1.10", "Hoat dong"));
        danhSachMay.add(new MayTinh("PC02", "May tinh 02", "192.168.1.11", "Ngoai tuyen"));
        danhSachMay.add(new MayTinh("PC03", "May tinh 03", "192.168.1.12", "Dang bi khoa"));
    }

    private void capNhatHienThiDanhSach() {
        StringBuilder sb = new StringBuilder();
        for (MayTinh m : danhSachMay) {
            sb.append(m.toString()).append("\n");
        }
        txtDanhSach.setText(sb.toString());
    }

    private MayTinh timMay(String maMay) {
        for (MayTinh m : danhSachMay) {
            if (m.getMaMay().equalsIgnoreCase(maMay.trim())) {
                return m;
            }
        }
        return null;
    }

    private void initComponents() {
        lblTitle = new javax.swing.JLabel();
        lblMaMay = new javax.swing.JLabel();
        txtMaMay = new javax.swing.JTextField();
        lblTenMay = new javax.swing.JLabel();
        txtTenMay = new javax.swing.JTextField();
        lblIP = new javax.swing.JLabel();
        txtIP = new javax.swing.JTextField();
        lblTrangThai = new javax.swing.JLabel();
        txtTrangThai = new javax.swing.JTextField();
        lblDanhSach = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtDanhSach = new javax.swing.JTextArea();
        btnThem = new javax.swing.JButton();
        btnSua = new javax.swing.JButton();
        btnXoa = new javax.swing.JButton();
        btnKhoa = new javax.swing.JButton();
        btnWol = new javax.swing.JButton();
        lblThongBao = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(UITheme.BG_DARK);

        lblTitle.setFont(new java.awt.Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(UITheme.ACCENT);
        lblTitle.setText("QUAN LY DANH SACH MAY");
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);

        lblMaMay.setFont(UITheme.FONT_BODY);
        lblMaMay.setForeground(UITheme.TEXT_PRIMARY);
        lblMaMay.setText("Ma may: ");

        txtMaMay.setText("PC01");
        UITheme.styleTextField(txtMaMay);

        lblTenMay.setFont(UITheme.FONT_BODY);
        lblTenMay.setForeground(UITheme.TEXT_PRIMARY);
        lblTenMay.setText("Ten may: ");

        txtTenMay.setText("May tinh 01");
        UITheme.styleTextField(txtTenMay);

        lblIP.setFont(UITheme.FONT_BODY);
        lblIP.setForeground(UITheme.TEXT_PRIMARY);
        lblIP.setText("Dia chi IP: ");

        txtIP.setText("192.168.1.10");
        UITheme.styleTextField(txtIP);

        lblTrangThai.setFont(UITheme.FONT_BODY);
        lblTrangThai.setForeground(UITheme.TEXT_PRIMARY);
        lblTrangThai.setText("Trang thai: ");

        txtTrangThai.setText("Hoat dong");
        UITheme.styleTextField(txtTrangThai);

        lblDanhSach.setFont(UITheme.FONT_BODY);
        lblDanhSach.setForeground(UITheme.TEXT_PRIMARY);
        lblDanhSach.setText("Danh sach may: ");

        txtDanhSach.setColumns(20);
        txtDanhSach.setRows(5);
        txtDanhSach.setText("\n");
        txtDanhSach.setEditable(false);
        UITheme.styleTextArea(txtDanhSach);
        jScrollPane1.setViewportView(txtDanhSach);
        jScrollPane1.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));

        btnThem.setText("Them may");
        UITheme.styleButtonAccent(btnThem);
        btnThem.addActionListener(this::btnThemActionPerformed);

        btnSua.setText("Sua may");
        UITheme.styleButtonGhost(btnSua);
        btnSua.addActionListener(this::btnSuaActionPerformed);

        btnXoa.setText("Xoa may");
        UITheme.styleButtonDanger(btnXoa);
        btnXoa.addActionListener(this::btnXoaActionPerformed);

        btnKhoa.setText("Khoa / Mo may");
        UITheme.styleButtonGhost(btnKhoa);
        btnKhoa.addActionListener(this::btnKhoaActionPerformed);

        btnWol.setText("[WoL] Bật nguồn (WoL)");
        UITheme.styleButtonAccent(btnWol);
        btnWol.addActionListener(e -> {
            String mac = javax.swing.JOptionPane.showInputDialog(this, "Nhập địa chỉ MAC máy trạm (VD: 00:11:22:33:44:55):", "Wake-on-LAN", javax.swing.JOptionPane.QUESTION_MESSAGE);
            if (mac != null && !mac.trim().isEmpty()) {
                boolean ok = WakeOnLan.sendMagicPacket(mac.trim());
                if (ok) {
                    lblThongBao.setText("Đã gửi Magic Packet tới MAC: " + mac.trim());
                    javax.swing.JOptionPane.showMessageDialog(this, "Đã gửi thành công gói tin Magic Packet tới MAC: " + mac.trim(), "Wake-on-LAN", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } else {
                    javax.swing.JOptionPane.showMessageDialog(this, "Địa chỉ MAC không đúng định dạng hex!", "Lỗi", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        lblThongBao.setFont(UITheme.FONT_BODY);
        lblThongBao.setForeground(UITheme.TEXT_MUTED);
        lblThongBao.setText("Trang thai: Binh thuong");
        lblThongBao.setHorizontalAlignment(SwingConstants.CENTER);

        // Bố cục Pure Java Swing chuẩn
        JPanel mainPanel = new JPanel(new BorderLayout(0, 15));
        mainPanel.setBackground(UITheme.BG_DARK);
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

        // 1. Header (North)
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setOpaque(false);
        titlePanel.add(lblTitle);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // 2. Center: Form nhập liệu & Danh sách máy
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);

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

        // Dòng 1: Tên máy
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblTenMay, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtTenMay, gbc);

        // Dòng 2: Địa chỉ IP
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblIP, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtIP, gbc);

        // Dòng 3: Trạng thái
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblTrangThai, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        centerPanel.add(txtTrangThai, gbc);

        // Dòng 4: Danh sách máy
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        centerPanel.add(lblDanhSach, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        jScrollPane1.setPreferredSize(new Dimension(340, 140));
        centerPanel.add(jScrollPane1, gbc);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 3. South: Hàng nút bấm và Trạng thái thông báo
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setOpaque(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnThem);
        buttonPanel.add(btnSua);
        buttonPanel.add(btnXoa);
        buttonPanel.add(btnKhoa);
        buttonPanel.add(btnWol);
        btnCleanSystem = new javax.swing.JButton("[Dọn rác] Dọn rác (Deep Clean)");
        UITheme.styleButtonGhost(btnCleanSystem);
        btnCleanSystem.addActionListener(e -> {
            javax.swing.JOptionPane.showMessageDialog(this, "Chức năng Dọn rác & Đóng băng (Deep Freeze Clean) đã được tích hợp qua lệnh CLEAN_SYSTEM.", "Dọn dẹp hệ thống", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(btnCleanSystem);
        southPanel.add(buttonPanel);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        statusPanel.setOpaque(false);
        statusPanel.add(lblThongBao);
        southPanel.add(statusPanel);

        mainPanel.add(southPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setPreferredSize(new Dimension(640, 580));
        pack();
    }

    private void btnThemActionPerformed(java.awt.event.ActionEvent evt) {
        String ma = txtMaMay.getText().trim();
        String ten = txtTenMay.getText().trim();
        String ip = txtIP.getText().trim();
        String trangThai = txtTrangThai.getText().trim();

        if (ma.isEmpty() || ten.isEmpty() || ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui long nhap day du Ma may, Ten may va IP!", "Thieu thong tin",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (timMay(ma) != null) {
            JOptionPane.showMessageDialog(this, "Ma may '" + ma + "' da ton tai trong he thong!", "Trung ma",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        danhSachMay.add(new MayTinh(ma, ten, ip, trangThai.isEmpty() ? "Hoat dong" : trangThai));
        capNhatHienThiDanhSach();

        lblThongBao.setText("Trang thai: Da them " + ma);
        ActivityLogForm.themNhatKy(ma, "Them may", "Them may moi IP " + ip + " vao he thong");
        JOptionPane.showMessageDialog(this, "Them may '" + ma + "' thanh cong!");
    }

    private void btnSuaActionPerformed(java.awt.event.ActionEvent evt) {
        String ma = txtMaMay.getText().trim();
        String ten = txtTenMay.getText().trim();
        String ip = txtIP.getText().trim();
        String trangThai = txtTrangThai.getText().trim();

        MayTinh m = timMay(ma);
        if (m == null) {
            JOptionPane.showMessageDialog(this, "Khong tim thay may co ma: " + ma, "Loi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (ten.isEmpty() || ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ten may va IP khong duoc de trong!", "Thieu thong tin",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        m.setTenMay(ten);
        m.setDiaChiIP(ip);
        if (!trangThai.isEmpty()) {
            m.setTrangThai(trangThai);
        }

        capNhatHienThiDanhSach();
        lblThongBao.setText("Trang thai: Da sua " + ma);
        ActivityLogForm.themNhatKy(ma, "Sua may", "Cap nhat thong tin IP=" + ip + ", TrangThai=" + m.getTrangThai());
        JOptionPane.showMessageDialog(this, "Cap nhat may '" + ma + "' thanh cong!");
    }

    private void btnXoaActionPerformed(java.awt.event.ActionEvent evt) {
        String ma = txtMaMay.getText().trim();
        MayTinh m = timMay(ma);
        if (m == null) {
            JOptionPane.showMessageDialog(this, "Khong tim thay may co ma: " + ma, "Loi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int xacNhan = JOptionPane.showConfirmDialog(
                this,
                "Ban co chac chan muon xoa may '" + ma + "' khoi he thong?",
                "Xac nhan xoa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (xacNhan == JOptionPane.YES_OPTION) {
            danhSachMay.remove(m);
            capNhatHienThiDanhSach();
            lblThongBao.setText("Trang thai: Da xoa " + ma);
            ActivityLogForm.themNhatKy(ma, "Xoa may", "Da xoa may khoi danh sach quan ly");
            JOptionPane.showMessageDialog(this, "Da xoa may '" + ma + "' thanh cong!");
        }
    }

    private void btnKhoaActionPerformed(java.awt.event.ActionEvent evt) {
        String ma = txtMaMay.getText().trim();
        MayTinh m = timMay(ma);
        if (m == null) {
            JOptionPane.showMessageDialog(this, "Khong tim thay may co ma: " + ma + " de khoa/mo khoa!", "Loi",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if ("Dang bi khoa".equalsIgnoreCase(m.getTrangThai())) {
            m.setTrangThai("Hoat dong");
            txtTrangThai.setText("Hoat dong");
            lblThongBao.setText("Trang thai: Da mo khoa " + ma);
            ActivityLogForm.themNhatKy(ma, "Mo khoa", "Admin da mo khoa hoat dong cho may");
            JOptionPane.showMessageDialog(this, "Da mo khoa cho may " + ma + " thanh cong!");
        } else {
            m.setTrangThai("Dang bi khoa");
            txtTrangThai.setText("Dang bi khoa");
            lblThongBao.setText("Trang thai: Da khoa " + ma);
            ActivityLogForm.themNhatKy(ma, "Khoa may", "Admin da khoa hoat dong cua may");
            JOptionPane.showMessageDialog(this, "Da khoa may " + ma + " thanh cong!");
        }

        capNhatHienThiDanhSach();
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
            java.util.logging.Logger.getLogger(ComputerManagementForm.class.getName())
                    .log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> new ComputerManagementForm().setVisible(true));
    }

    // Khai báo các thành phần giao diện (giữ nguyên tên biến 100%)
    private javax.swing.JButton btnKhoa;
    private javax.swing.JButton btnWol;
    private javax.swing.JButton btnCleanSystem;
    private javax.swing.JButton btnSua;
    private javax.swing.JButton btnThem;
    private javax.swing.JButton btnXoa;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblDanhSach;
    private javax.swing.JLabel lblIP;
    private javax.swing.JLabel lblMaMay;
    private javax.swing.JLabel lblTenMay;
    private javax.swing.JLabel lblThongBao;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblTrangThai;
    private javax.swing.JTextArea txtDanhSach;
    private javax.swing.JTextField txtIP;
    private javax.swing.JTextField txtMaMay;
    private javax.swing.JTextField txtTenMay;
    private javax.swing.JTextField txtTrangThai;
}
