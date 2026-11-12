package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * TaskMgrSidebarCard - Thẻ linh kiện bên cột Sidebar điều hướng (Master list).
 * Tái tạo chính xác thẻ linh kiện của Windows 11 Task Manager:
 * - Biểu đồ mini (Sparkline) bên trái (58x42px).
 * - Tên linh kiện + Thông số tóm tắt bên phải.
 * - Hiệu ứng Hover, vạch màu nhận diện 3.5px ở mép trái khi được chọn.
 */
public class TaskMgrSidebarCard extends JPanel {

    private final String key;
    private final String title;
    private final Color accentColor;
    private final TaskMgrLineChart miniChart;

    private final JLabel lblTitle = new JLabel();
    private final JLabel lblPrimary = new JLabel("—");
    private final JLabel lblSecondary = new JLabel("—");

    private boolean selected = false;
    private boolean hovered = false;
    private Runnable onSelectListener;

    public TaskMgrSidebarCard(String key, String title, Color accentColor, Runnable onSelect) {
        this.key = key;
        this.title = title;
        this.accentColor = accentColor;
        this.onSelectListener = onSelect;
        this.miniChart = new TaskMgrLineChart(accentColor, true);

        setLayout(new BorderLayout(8, 0));
        setBackground(UITheme.BG_CARD);
        setBorder(new EmptyBorder(6, 8, 6, 8));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(220, 64));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));

        // 1. Biểu đồ mini bên trái
        miniChart.setPreferredSize(new Dimension(58, 44));
        miniChart.setMinimumSize(new Dimension(50, 36));
        add(miniChart, BorderLayout.WEST);

        // 2. Nội dung text bên phải
        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);

        lblTitle.setText(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(Color.WHITE);

        lblPrimary.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPrimary.setForeground(accentColor);

        lblSecondary.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblSecondary.setForeground(UITheme.TEXT_MUTED);

        textCol.add(lblTitle);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(lblPrimary);
        textCol.add(Box.createVerticalStrut(1));
        textCol.add(lblSecondary);

        add(textCol, BorderLayout.CENTER);

        // 3. Sự kiện chuột Click & Hover
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onSelectListener != null) {
                    onSelectListener.run();
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    public String getKey() {
        return key;
    }

    public TaskMgrLineChart getMiniChart() {
        return miniChart;
    }

    public void setSelected(boolean sel) {
        this.selected = sel;
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    public void updateTitle(String newTitle) {
        if (newTitle != null) {
            lblTitle.setText(newTitle);
        }
    }

    public void updateValues(String primary, String secondary, float chartVal) {
        if (primary != null) lblPrimary.setText(primary);
        if (secondary != null) lblSecondary.setText(secondary);
        if (chartVal >= 0) miniChart.addPoint(chartVal);
    }

    public void updateDualValues(String primary, String secondary, float val1, float val2) {
        if (primary != null) lblPrimary.setText(primary);
        if (secondary != null) lblSecondary.setText(secondary);
        miniChart.addPoint(val1, val2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Nền thẻ: Selected > Hovered > Normal
        if (selected) {
            g2.setColor(new Color(30, 41, 59)); // Slate 800 active
        } else if (hovered) {
            g2.setColor(new Color(23, 35, 56)); // Slate 850 hover
        } else {
            g2.setColor(UITheme.BG_CARD);
        }
        g2.fillRect(0, 0, w, h);

        // Vạch màu nhận diện ở mép trái (3.5px) khi được chọn
        if (selected) {
            g2.setColor(accentColor);
            g2.fillRect(0, 0, 4, h);
            g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 60));
            g2.drawRect(0, 0, w - 1, h - 1);
        } else {
            g2.setColor(new Color(30, 41, 59, 120));
            g2.drawRect(0, 0, w - 1, h - 1);
        }

        g2.dispose();
    }
}

