package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ChatForm - Giao diện trò chuyện phòng máy (Pure Java Swing)
 * Quản trị viên có thể nhắn tin tới từng máy trạm cụ thể hoặc phát thông điệp toàn phòng máy.
 * @author P.Nam
 */
public class ChatForm extends javax.swing.JFrame {

    public static final String BROADCAST_ALL = "Tất cả máy (Gửi toàn phòng)";

    private ServerForm serverForm; 
    private String clientName;
    private final ButtonGroup bgStatus = new ButtonGroup();

    /**
     * Creates new form ChatForm
     */
    public ChatForm() {
        this(null, "", null);
    }

    public ChatForm(ServerForm serverForm, String receiver) {
        this(serverForm, receiver, serverForm != null ? serverForm.getOnlineClientNames() : null);
    }

    public ChatForm(ServerForm serverForm, String receiver, List<String> onlineClients) {
        initComponents();
        this.serverForm = serverForm;
        this.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        bgStatus.add(jRadioButton1);
        bgStatus.add(jRadioButton2);
        jRadioButton1.setText("Đang kết nối");
        jRadioButton2.setText("Đã ngắt");
        jRadioButton1.setEnabled(false);
        jRadioButton2.setEnabled(false);

        applyTheme();
        populateClients(onlineClients, receiver);

        // Nhấn Enter trong txtMessage để gửi tin nhắn nhanh (Shift+Enter để xuống dòng)
        txtMessage.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    btnSendActionPerformed(null);
                }
            }
        });
    }

    /**
     * Nạp danh sách máy vào cboReceiver
     */
    public void populateClients(List<String> onlineClients, String targetToSelect) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(BROADCAST_ALL);

        java.util.Set<String> added = new java.util.HashSet<>();
        added.add(BROADCAST_ALL);

        if (onlineClients != null) {
            for (String c : onlineClients) {
                if (c != null && !c.trim().isEmpty() && added.add(c.trim())) {
                    model.addElement(c.trim());
                }
            }
        }

        // Nạp thêm các máy đã biết trong hệ thống nếu chưa có trong danh sách
        if (serverForm != null) {
            for (String name : serverForm.getAllKnownClientNames()) {
                if (name != null && !name.trim().isEmpty() && added.add(name.trim())) {
                    model.addElement(name.trim());
                }
            }
        }

        // Nếu máy nhận chỉ định chưa có trong danh sách nhưng hợp lệ, thêm vào
        if (targetToSelect != null && !targetToSelect.trim().isEmpty() && added.add(targetToSelect.trim())) {
            model.addElement(targetToSelect.trim());
        }

        cboReceiver.setModel(model);

        if (targetToSelect != null && !targetToSelect.trim().isEmpty()) {
            cboReceiver.setSelectedItem(targetToSelect.trim());
            this.clientName = targetToSelect.trim();
        } else if (model.getSize() > 1) {
            // Ưu tiên chọn máy đầu tiên nếu có máy online
            cboReceiver.setSelectedIndex(1);
            this.clientName = model.getElementAt(1);
        } else {
            cboReceiver.setSelectedIndex(0);
            this.clientName = BROADCAST_ALL;
        }

        updateHeaderTitleAndStatus();
    }

    /**
     * Cập nhật danh sách máy khi có máy kết nối hoặc ngắt kết nối
     */
    public void refreshClientList(List<String> onlineClients) {
        SwingUtilities.invokeLater(() -> {
            String currentSelected = (String) cboReceiver.getSelectedItem();
            populateClients(onlineClients, currentSelected);
        });
    }

    private void updateHeaderTitleAndStatus() {
        Object sel = cboReceiver.getSelectedItem();
        String target = (sel != null) ? sel.toString() : BROADCAST_ALL;
        this.clientName = target;

        if (BROADCAST_ALL.equals(target)) {
            setTitle("Trò chuyện phòng máy — Toàn phòng");
            jRadioButton1.setText("Toàn phòng");
            jRadioButton1.setSelected(true);
            jRadioButton2.setVisible(false);
        } else {
            setTitle("Chat với máy " + target);
            jRadioButton2.setVisible(true);
            jRadioButton1.setText("Trực tuyến");
            jRadioButton2.setText("Ngoại tuyến");
            boolean isOnline = (serverForm != null) && serverForm.isClientOnline(target);
            if (isOnline) {
                jRadioButton1.setSelected(true);
                jRadioButton2.setSelected(false);
            } else {
                jRadioButton2.setSelected(true);
                jRadioButton1.setSelected(false);
            }
        }
    }

    private void applyTheme() {
        getContentPane().setBackground(UITheme.BG_DARK);

        jLabel1.setFont(UITheme.FONT_TITLE);
        jLabel1.setForeground(UITheme.ACCENT);

        jLabel4.setFont(UITheme.FONT_HEADER);
        jLabel4.setForeground(UITheme.TEXT_PRIMARY);
        jLabel4.setBackground(UITheme.BG_DARK);
        jLabel4.setOpaque(false);

        UITheme.styleComboBox(cboReceiver);
        cboReceiver.setMaximumRowCount(10);

        UITheme.styleTextArea(txtChatHistory);
        UITheme.styleScrollPane(jScrollPane1);

        UITheme.styleTextPane(txtMessage);
        UITheme.styleScrollPane(jScrollPane2);

        UITheme.styleButtonAccent(btnSend);
        btnSend.setText("Gửi tin");

        jRadioButton1.setBackground(UITheme.BG_DARK);
        jRadioButton1.setForeground(UITheme.SUCCESS);
        jRadioButton1.setFont(UITheme.FONT_SMALL);

        jRadioButton2.setBackground(UITheme.BG_DARK);
        jRadioButton2.setForeground(UITheme.DANGER);
        jRadioButton2.setFont(UITheme.FONT_SMALL);
    }

    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        cboReceiver = new javax.swing.JComboBox<>();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtChatHistory = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtMessage = new javax.swing.JTextPane();
        btnSend = new javax.swing.JButton();
        jRadioButton1 = new javax.swing.JRadioButton();
        jRadioButton2 = new javax.swing.JRadioButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jLabel1.setText("TRÒ CHUYỆN PHÒNG MÁY");
        jLabel1.setHorizontalAlignment(SwingConstants.CENTER);

        jLabel4.setText("Người nhận:");

        cboReceiver.addActionListener(this::cboReceiverActionPerformed);

        txtChatHistory.setColumns(20);
        txtChatHistory.setRows(5);
        txtChatHistory.setEditable(false);
        txtChatHistory.setLineWrap(true);
        txtChatHistory.setWrapStyleWord(true);
        jScrollPane1.setViewportView(txtChatHistory);

        jScrollPane2.setViewportView(txtMessage);
        jScrollPane2.setPreferredSize(new Dimension(450, 45));

        btnSend.setText("Gửi tin");
        btnSend.addActionListener(this::btnSendActionPerformed);

        jRadioButton1.setText("Đang kết nối");
        jRadioButton2.setText("Đã ngắt");

        // Bố cục Pure Java Swing chuẩn
        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(UITheme.BG_DARK);
        mainPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        // 1. Vùng Header (North)
        JPanel northPanel = new JPanel(new BorderLayout(0, 10));
        northPanel.setOpaque(false);

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setOpaque(false);
        titlePanel.add(jLabel1);
        northPanel.add(titlePanel, BorderLayout.NORTH);

        JPanel controlsRow = new JPanel(new BorderLayout(10, 0));
        controlsRow.setOpaque(false);

        JPanel receiverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        receiverPanel.setOpaque(false);
        receiverPanel.add(jLabel4);
        cboReceiver.setPreferredSize(new Dimension(240, 34));
        receiverPanel.add(cboReceiver);
        controlsRow.add(receiverPanel, BorderLayout.WEST);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        radioPanel.setOpaque(false);
        radioPanel.add(jRadioButton1);
        radioPanel.add(jRadioButton2);
        controlsRow.add(radioPanel, BorderLayout.EAST);

        northPanel.add(controlsRow, BorderLayout.SOUTH);
        mainPanel.add(northPanel, BorderLayout.NORTH);

        // 2. Vùng Lịch sử chat (Center)
        mainPanel.add(jScrollPane1, BorderLayout.CENTER);

        // 3. Vùng nhập liệu & Nút gửi (South)
        JPanel southPanel = new JPanel(new BorderLayout(10, 0));
        southPanel.setOpaque(false);
        southPanel.add(jScrollPane2, BorderLayout.CENTER);

        btnSend.setPreferredSize(new Dimension(95, 45));
        southPanel.add(btnSend, BorderLayout.EAST);

        mainPanel.add(southPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setPreferredSize(new Dimension(680, 520));
        pack();
        setLocationRelativeTo(null);
    }

    private void btnSendActionPerformed(java.awt.event.ActionEvent evt) {
        String message = txtMessage.getText().trim();

        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập nội dung tin nhắn!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object selected = cboReceiver.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String target = selected.toString().trim();
        this.clientName = target;

        if (serverForm == null) {
            JOptionPane.showMessageDialog(this, "Chưa liên kết với Server!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

        if (BROADCAST_ALL.equals(target)) {
            serverForm.broadcastChatMessage(message);
            txtChatHistory.append("[" + time + "] [Server → Toàn phòng]: " + message + "\n");
        } else {
            serverForm.sendChatMessage(target, message);
            txtChatHistory.append("[" + time + "] [Server → " + target + "]: " + message + "\n");
        }

        txtChatHistory.setCaretPosition(txtChatHistory.getDocument().getLength());
        txtMessage.setText("");
        txtMessage.requestFocus();
    }

    private void cboReceiverActionPerformed(java.awt.event.ActionEvent evt) {
        updateHeaderTitleAndStatus();
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        setSelectedClient(clientName);
    }

    /**
     * Thiết lập hoặc chọn sẵn tên máy nhận tin nhắn trên ComboBox
     */
    public void setSelectedClient(String target) {
        if (target != null && !target.trim().isEmpty()) {
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) cboReceiver.getModel();
            boolean exists = false;
            for (int i = 0; i < model.getSize(); i++) {
                if (target.equalsIgnoreCase(model.getElementAt(i))) {
                    cboReceiver.setSelectedIndex(i);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                model.addElement(target.trim());
                cboReceiver.setSelectedItem(target.trim());
            }
            updateHeaderTitleAndStatus();
        }
    }

    /**
     * Nhận tin nhắn từ Client (gọi bởi ServerForm khi client gửi chat).
     * Hiển thị lên khung lịch sử chat.
     */
    public void receiveMessage(String senderName, String content) {
        if (senderName != null && !senderName.trim().isEmpty()) {
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) cboReceiver.getModel();
            boolean found = false;
            for (int i = 0; i < model.getSize(); i++) {
                if (senderName.equalsIgnoreCase(model.getElementAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                model.addElement(senderName.trim());
            }
        }

        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtChatHistory.append("[" + time + "] [" + senderName + " → Server]: " + content + "\n");
        txtChatHistory.setCaretPosition(txtChatHistory.getDocument().getLength());

        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {}
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            UITheme.setupGlobalTheme();
        } catch (Exception ignored) {}

        java.awt.EventQueue.invokeLater(() -> new ChatForm().setVisible(true));
    }

    // Khai báo các thành phần giao diện (giữ nguyên tên biến 100%)
    private javax.swing.JButton btnSend;
    private javax.swing.JComboBox<String> cboReceiver;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JRadioButton jRadioButton2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextArea txtChatHistory;
    private javax.swing.JTextPane txtMessage;
}
