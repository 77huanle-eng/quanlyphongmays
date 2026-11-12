package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * NotificationForm - Giao diện gửi thông báo (Pure Java Swing)
 * @author P.Nam
 */
public class NotificationForm extends javax.swing.JFrame {

    private DefaultListModel<String> historyModel;
    private javax.swing.JList<String> lstHistory;
    private javax.swing.JScrollPane scrollHistory;
    private ServerForm serverForm;

    /**
     * Creates new form NotificationForm
     */
    public NotificationForm() {
        initComponents();
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        
        // Khởi tạo model cho danh sách lịch sử
        historyModel = new DefaultListModel<>();
        lstHistory = new javax.swing.JList<>(historyModel);
        lstHistory.setBackground(UITheme.BG_CARD);
        lstHistory.setForeground(UITheme.TEXT_PRIMARY);
        lstHistory.setFont(UITheme.FONT_BODY);

        // Tạo thanh cuộn
        scrollHistory = new javax.swing.JScrollPane(lstHistory);
        UITheme.styleScrollPane(scrollHistory);

        // Hiển thị danh sách trong vùng lịch sử
        txtHistory.setLayout(new java.awt.BorderLayout());
        txtHistory.add(scrollHistory, java.awt.BorderLayout.CENTER);

        // Căn giữa màn hình
        setLocationRelativeTo(null);
    }

    public NotificationForm(ServerForm serverForm) {
        this();
        this.serverForm = serverForm;
        napDanhSachMayOnline();
    }

    public void napDanhSachMayOnline() {
        if (serverForm != null) {
            javax.swing.DefaultComboBoxModel<String> model = new javax.swing.DefaultComboBoxModel<>();
            model.addElement("Tất cả máy");
            java.util.List<String> onlineClients = serverForm.getOnlineClientNames();
            for (String name : onlineClients) {
                model.addElement(name);
            }
            cboTarget.setModel(model);
        }
    }

    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        cboTarget = new javax.swing.JComboBox<>();
        rdoConnected = new javax.swing.JRadioButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtSubject = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtNotification = new javax.swing.JTextArea();
        btnSendNotification = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();
        rdoDisconnected = new javax.swing.JRadioButton();
        txtHistory = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(UITheme.BG_DARK);

        jLabel1.setFont(UITheme.FONT_TITLE);
        jLabel1.setForeground(UITheme.ACCENT);
        jLabel1.setText("GỬI THÔNG BÁO");
        jLabel1.setHorizontalAlignment(SwingConstants.CENTER);

        jLabel2.setFont(UITheme.FONT_BODY);
        jLabel2.setForeground(UITheme.TEXT_PRIMARY);
        jLabel2.setText("Đối tượng nhận:");

        jLabel3.setFont(UITheme.FONT_BODY);
        jLabel3.setForeground(UITheme.TEXT_PRIMARY);
        jLabel3.setText("Tiêu đề:");

        jLabel4.setFont(UITheme.FONT_BODY);
        jLabel4.setForeground(UITheme.TEXT_PRIMARY);
        jLabel4.setText("Nội dung thông báo:");

        jLabel5.setFont(UITheme.FONT_BODY);
        jLabel5.setForeground(UITheme.TEXT_PRIMARY);
        jLabel5.setText("Lịch sử gửi thông báo:");

        cboTarget.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Máy 1", "Máy 2", "Máy 3", "Máy 4" }));
        UITheme.styleComboBox(cboTarget);
        cboTarget.addActionListener(this::cboTargetActionPerformed);

        rdoConnected.setText("Đã kết nối");
        rdoConnected.setBackground(UITheme.BG_DARK);
        rdoConnected.setForeground(UITheme.SUCCESS);
        rdoConnected.setFont(UITheme.FONT_BODY);

        rdoDisconnected.setText("Chưa kết nối");
        rdoDisconnected.setBackground(UITheme.BG_DARK);
        rdoDisconnected.setForeground(UITheme.DANGER);
        rdoDisconnected.setFont(UITheme.FONT_BODY);

        txtSubject.setColumns(20);
        txtSubject.setRows(2);
        UITheme.styleTextArea(txtSubject);
        jScrollPane1.setViewportView(txtSubject);
        UITheme.styleScrollPane(jScrollPane1);

        txtNotification.setColumns(20);
        txtNotification.setRows(4);
        UITheme.styleTextArea(txtNotification);
        jScrollPane2.setViewportView(txtNotification);
        UITheme.styleScrollPane(jScrollPane2);

        btnSendNotification.setText("GỬI THÔNG BÁO");
        UITheme.styleButtonAccent(btnSendNotification);
        btnSendNotification.addActionListener(this::btnSendNotificationActionPerformed);

        jButton2.setText("XÓA");
        UITheme.styleButtonDanger(jButton2);
        jButton2.addActionListener(this::jButton2ActionPerformed);

        txtHistory.setBackground(UITheme.BG_CARD);
        txtHistory.setBorder(BorderFactory.createLineBorder(UITheme.BORDER, 1));

        // Bố cục Pure Java Swing chuẩn
        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(UITheme.BG_DARK);
        mainPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        // 1. Title (North)
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setOpaque(false);
        titlePanel.add(jLabel1);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // 2. Center Content
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // Hàng 1: Người nhận & Radio kết nối
        JPanel rowTarget = new JPanel(new BorderLayout(10, 0));
        rowTarget.setOpaque(false);

        JPanel targetLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        targetLeft.setOpaque(false);
        targetLeft.add(jLabel2);
        cboTarget.setPreferredSize(new Dimension(200, 32));
        targetLeft.add(cboTarget);
        rowTarget.add(targetLeft, BorderLayout.WEST);

        JPanel targetRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        targetRight.setOpaque(false);
        targetRight.add(rdoConnected);
        targetRight.add(rdoDisconnected);
        rowTarget.add(targetRight, BorderLayout.EAST);

        centerPanel.add(rowTarget);
        centerPanel.add(Box.createVerticalStrut(10));

        // Hàng 2: Tiêu đề
        JPanel rowSubject = new JPanel(new BorderLayout(10, 0));
        rowSubject.setOpaque(false);
        rowSubject.add(jLabel3, BorderLayout.WEST);
        jScrollPane1.setPreferredSize(new Dimension(500, 48));
        rowSubject.add(jScrollPane1, BorderLayout.CENTER);
        centerPanel.add(rowSubject);
        centerPanel.add(Box.createVerticalStrut(10));

        // Hàng 3: Nội dung & Nút gửi
        JPanel rowMessage = new JPanel(new BorderLayout(10, 0));
        rowMessage.setOpaque(false);
        rowMessage.add(jLabel4, BorderLayout.WEST);

        jScrollPane2.setPreferredSize(new Dimension(380, 90));
        rowMessage.add(jScrollPane2, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        btnPanel.setOpaque(false);
        btnSendNotification.setPreferredSize(new Dimension(140, 40));
        jButton2.setPreferredSize(new Dimension(140, 40));
        btnPanel.add(btnSendNotification);
        btnPanel.add(jButton2);

        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnWrapper.setOpaque(false);
        btnWrapper.add(btnPanel);
        rowMessage.add(btnWrapper, BorderLayout.EAST);

        centerPanel.add(rowMessage);
        centerPanel.add(Box.createVerticalStrut(12));

        // Hàng 4: Lịch sử gửi thông báo
        JPanel rowHistoryLabel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rowHistoryLabel.setOpaque(false);
        rowHistoryLabel.add(jLabel5);
        centerPanel.add(rowHistoryLabel);
        centerPanel.add(Box.createVerticalStrut(6));

        txtHistory.setPreferredSize(new Dimension(500, 140));
        centerPanel.add(txtHistory);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
        setPreferredSize(new Dimension(720, 560));
        pack();
    }

    private void btnSendNotificationActionPerformed(java.awt.event.ActionEvent evt) {
        String subject = txtSubject.getText().trim();
        String message = txtNotification.getText().trim();

        // Kiểm tra dữ liệu
        if (subject.isEmpty() || message.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Vui lòng nhập tiêu đề và nội dung thông báo!",
                "Thiếu thông tin",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Lấy người nhận
        String receiver = String.valueOf(cboTarget.getSelectedItem());

        // Lấy thời gian hiện tại
        String time = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );

        // Tạo nội dung lịch sử
        String notificationRecord = "[" + time + "] "
            + receiver + " | " + subject + ": " + message;

        // Thêm thông báo vào danh sách lịch sử
        historyModel.addElement(notificationRecord);

        if (serverForm != null) {
            serverForm.broadcastNotice(receiver, subject, message);
        }

        // Cuộn xuống thông báo mới nhất
        if (historyModel.getSize() > 0) {
            lstHistory.ensureIndexIsVisible(historyModel.getSize() - 1);
        }

        // Báo thành công
        JOptionPane.showMessageDialog(
            this,
            "Đã gửi thông báo thành công!",
            "Thông báo",
            JOptionPane.INFORMATION_MESSAGE
        );

        // Xóa nội dung nhập
        txtSubject.setText("");
        txtNotification.setText("");
        txtSubject.requestFocus();
    }

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {
        // Xóa tiêu đề và nội dung
        txtSubject.setText("");
        txtNotification.setText("");

        // Xóa toàn bộ lịch sử
        historyModel.clear();

        // Đưa con trỏ về ô tiêu đề
        txtSubject.requestFocus();
    }

    private void cboTargetActionPerformed(java.awt.event.ActionEvent evt) {
        // Xu ly su kien
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
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(NotificationForm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new NotificationForm().setVisible(true);
            }
        });
    }

    // Khai báo các thành phần giao diện (giữ nguyên tên biến 100%)
    private javax.swing.JButton btnSendNotification;
    private javax.swing.JComboBox<String> cboTarget;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JRadioButton rdoConnected;
    private javax.swing.JRadioButton rdoDisconnected;
    private javax.swing.JPanel txtHistory;
    private javax.swing.JTextArea txtNotification;
    private javax.swing.JTextArea txtSubject;
}
