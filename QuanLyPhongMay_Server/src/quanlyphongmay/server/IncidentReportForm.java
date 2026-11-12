package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * IncidentReportForm - Giao diện thống kê sự cố và các hành vi vi phạm phòng máy (Pure Java Swing).
 */
public class IncidentReportForm extends JFrame {

    private final BiConsumer<String, String> commandSender;
    private DefaultTableModel tableModel;
    private JTable table;
    private JComboBox<String> cbFilterMachine;
    private JComboBox<String> cbFilterType;
    private JComboBox<String> cbFilterSeverity;
    private JLabel lblTotalCount;
    private JLabel lblAppWebCount;
    private JLabel lblHardwareCount;

    public IncidentReportForm(BiConsumer<String, String> commandSender, List<String> onlineMachines) {
        super("Thống kê sự cố & Vi phạm phòng máy — LANPC");
        this.commandSender = commandSender;

        initComponents(onlineMachines);
        buildUI();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1050, 680);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        // Đăng ký nhận sự cố thời gian thực
        IncidentManager.addListener(item -> SwingUtilities.invokeLater(this::refreshData));
        refreshData();
    }

    private void initComponents(List<String> onlineMachines) {
        tableModel = new DefaultTableModel(
                new Object[]{"STT", "Thời gian", "Mã máy", "Loại sự cố", "Mức độ", "Tiêu đề vi phạm", "Chi tiết sự cố"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        UITheme.styleTable(table);
        table.setRowHeight(28);

        // Custom renderer cho cột Mức độ (cột 4)
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, isSel, hasFoc, row, col);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String sev = String.valueOf(val);
                if ("CRITICAL".equalsIgnoreCase(sev)) {
                    lbl.setForeground(UITheme.DANGER);
                } else if ("WARNING".equalsIgnoreCase(sev)) {
                    lbl.setForeground(UITheme.WARNING);
                } else {
                    lbl.setForeground(UITheme.ACCENT);
                }
                return lbl;
            }
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(4).setPreferredWidth(85);
        table.getColumnModel().getColumn(5).setPreferredWidth(200);
        table.getColumnModel().getColumn(6).setPreferredWidth(320);

        cbFilterMachine = new JComboBox<>();
        cbFilterMachine.addItem("— Tất cả máy —");
        if (onlineMachines != null) {
            for (String m : onlineMachines) {
                cbFilterMachine.addItem(m);
            }
        }
        UITheme.styleComboBox(cbFilterMachine);

        cbFilterType = new JComboBox<>(new String[]{
            "— Tất cả loại —", "BANNED_APP", "BANNED_WEB", "STORAGE_CRITICAL", "NETWORK_DROP"
        });
        UITheme.styleComboBox(cbFilterType);

        cbFilterSeverity = new JComboBox<>(new String[]{
            "— Tất cả mức độ —", "CRITICAL", "WARNING", "INFO"
        });
        UITheme.styleComboBox(cbFilterSeverity);

        cbFilterMachine.addActionListener(e -> refreshData());
        cbFilterType.addActionListener(e -> refreshData());
        cbFilterSeverity.addActionListener(e -> refreshData());

        lblTotalCount = new JLabel("0");
        lblAppWebCount = new JLabel("0");
        lblHardwareCount = new JLabel("0");
    }

    private void buildUI() {
        getContentPane().setBackground(UITheme.BG_DARK);
        setLayout(new BorderLayout(0, 12));

        // 1. Header & Stat cards
        JPanel northPanel = new JPanel(new BorderLayout(0, 10));
        northPanel.setOpaque(false);
        northPanel.setBorder(new EmptyBorder(15, 20, 0, 20));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titlePanel.setOpaque(false);
        JLabel icon = new JLabel("[!]");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        icon.setForeground(UITheme.WARNING);
        JLabel lblTitle = new JLabel("BÁO CÁO SỰ CỐ & VI PHẠM PHÒNG MÁY");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.TEXT_PRIMARY);
        titlePanel.add(icon);
        titlePanel.add(lblTitle);
        northPanel.add(titlePanel, BorderLayout.NORTH);

        // 3 Cards thống kê
        JPanel cardsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        cardsPanel.setOpaque(false);
        cardsPanel.add(makeStatCard("Tổng sự cố ghi nhận", lblTotalCount, UITheme.TEXT_PRIMARY));
        cardsPanel.add(makeStatCard("Vi phạm Game & Web cấm", lblAppWebCount, UITheme.WARNING));
        cardsPanel.add(makeStatCard("Cạn kiệt ổ đĩa / Lỗi mạng", lblHardwareCount, UITheme.DANGER));
        northPanel.add(cardsPanel, BorderLayout.CENTER);

        // Thanh bộ lọc
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        filterPanel.setBackground(UITheme.BG_CARD);
        filterPanel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(4, 10, 4, 10)
        ));

        JLabel l1 = new JLabel("Lọc theo máy:");
        UITheme.styleLabelBody(l1);
        filterPanel.add(l1);
        filterPanel.add(cbFilterMachine);

        JLabel l2 = new JLabel("Loại sự cố:");
        UITheme.styleLabelBody(l2);
        filterPanel.add(l2);
        filterPanel.add(cbFilterType);

        JLabel l3 = new JLabel("Mức độ:");
        UITheme.styleLabelBody(l3);
        filterPanel.add(l3);
        filterPanel.add(cbFilterSeverity);

        northPanel.add(filterPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // 2. Center: Bảng danh sách sự cố
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(0, 20, 0, 20));

        JScrollPane sp = new JScrollPane(table);
        UITheme.styleScrollPane(sp);
        centerPanel.add(sp, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // 3. South: Nút hành động
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        southPanel.setBackground(UITheme.BG_CARD);
        southPanel.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(6, 15, 6, 15)
        ));

        JButton btnWarn = new JButton("Nhắc nhở máy chọn");
        UITheme.styleButtonAccent(btnWarn);
        btnWarn.addActionListener(e -> nhacNhoMayChon());

        JButton btnLock = new JButton("[Khóa] Khóa máy vi phạm");
        UITheme.styleButtonDanger(btnLock);
        btnLock.addActionListener(e -> khoaMayChon());

        JButton btnExport = new JButton("[Nhận] Xuất báo cáo (CSV)");
        UITheme.styleButtonSecondary(btnExport);
        btnExport.addActionListener(e -> xuatBaoCaoCSV());

        JButton btnClear = new JButton("Xóa nhật ký");
        UITheme.styleButtonGhost(btnClear);
        btnClear.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa sạch danh sách sự cố?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                IncidentManager.clear();
                refreshData();
            }
        });

        southPanel.add(btnWarn);
        southPanel.add(btnLock);
        southPanel.add(new JSeparator(SwingConstants.VERTICAL));
        southPanel.add(btnExport);
        southPanel.add(btnClear);

        add(southPanel, BorderLayout.SOUTH);
    }

    private JPanel makeStatCard(String title, JLabel valueLabel, Color valColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(UITheme.BG_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UITheme.BORDER, 1),
                new EmptyBorder(10, 15, 10, 15)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UITheme.FONT_SMALL);
        lblTitle.setForeground(UITheme.TEXT_MUTED);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valueLabel.setForeground(valColor);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    public void refreshData() {
        tableModel.setRowCount(0);
        List<IncidentManager.IncidentItem> list = IncidentManager.getAllIncidents();

        String selMachine = (String) cbFilterMachine.getSelectedItem();
        String selType = (String) cbFilterType.getSelectedItem();
        String selSev = (String) cbFilterSeverity.getSelectedItem();

        int stt = 1;
        int appWeb = 0;
        int hw = 0;

        for (IncidentManager.IncidentItem item : list) {
            if ("BANNED_APP".equals(item.type) || "BANNED_WEB".equals(item.type)) {
                appWeb++;
            } else {
                hw++;
            }

            // Lọc
            if (selMachine != null && !selMachine.startsWith("—") && !item.maMay.equalsIgnoreCase(selMachine)) {
                continue;
            }
            if (selType != null && !selType.startsWith("—") && !item.type.equalsIgnoreCase(selType)) {
                continue;
            }
            if (selSev != null && !selSev.startsWith("—") && !item.severity.equalsIgnoreCase(selSev)) {
                continue;
            }

            tableModel.addRow(new Object[]{
                stt++,
                item.timestamp,
                item.maMay,
                item.getTypeLabel(),
                item.severity,
                item.title,
                item.description
            });
        }

        lblTotalCount.setText(String.valueOf(list.size()));
        lblAppWebCount.setText(String.valueOf(appWeb));
        lblHardwareCount.setText(String.valueOf(hw));
    }

    private void nhacNhoMayChon() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một sự cố trong bảng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String maMay = String.valueOf(tableModel.getValueAt(table.convertRowIndexToModel(r), 2));
        String tieuDe = String.valueOf(tableModel.getValueAt(table.convertRowIndexToModel(r), 5));

        String msg = JOptionPane.showInputDialog(this, "Nội dung nhắc nhở gửi xuống máy " + maMay + ":",
                "Cảnh báo vi phạm: " + tieuDe);
        if (msg != null && !msg.trim().isEmpty()) {
            if (commandSender != null) {
                commandSender.accept(maMay, "NOTICE|[!] CẢNH BÁO TỪ SERVER: " + msg.trim());
                ActivityLogForm.themNhatKy(maMay, "CẢNH BÁO TỪ SERVER", "Nhắc nhở vi phạm: " + msg.trim());
                JOptionPane.showMessageDialog(this, "Đã gửi thông báo nhắc nhở tới máy " + maMay);
            }
        }
    }

    private void khoaMayChon() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một dòng sự cố trong bảng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String maMay = String.valueOf(tableModel.getValueAt(table.convertRowIndexToModel(r), 2));
        int c = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn KHÓA toàn màn hình máy " + maMay + " do vi phạm không?",
                "Xác nhận khóa máy", JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION && commandSender != null) {
            commandSender.accept(maMay, "LOCK");
            JOptionPane.showMessageDialog(this, "Đã gửi lệnh khóa toàn màn hình máy " + maMay);
        }
    }

    private void xuatBaoCaoCSV() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("BaoCaoSuCoPhongMay_" + System.currentTimeMillis() + ".csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(target, StandardCharsets.UTF_8)) {
                // BOM cho Excel mở tiếng Việt không lỗi font
                fw.write('\ufeff');
                fw.write("STT,ThoiGian,MaMay,LoaiSuCo,MucDo,TieuDe,ChiTiet\n");
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < tableModel.getColumnCount(); j++) {
                        String cell = String.valueOf(tableModel.getValueAt(i, j)).replace("\"", "\"\"");
                        sb.append("\"").append(cell).append("\"");
                        if (j < tableModel.getColumnCount() - 1) sb.append(",");
                    }
                    sb.append("\n");
                    fw.write(sb.toString());
                }
                JOptionPane.showMessageDialog(this, "Đã xuất báo cáo CSV thành công:\n" + target.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Lỗi ghi file CSV: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

