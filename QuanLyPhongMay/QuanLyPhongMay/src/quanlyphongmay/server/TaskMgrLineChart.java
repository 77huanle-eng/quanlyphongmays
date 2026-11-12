package quanlyphongmay.server;

import quanlyphongmay.common.UITheme;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

/**
 * TaskMgrLineChart - Biểu đồ đường phong cách Windows 11 Task Manager.
 * Hỗ trợ:
 * - 60 giây lịch sử dữ liệu (60 data points).
 * - Lưới ô vuông mờ chuẩn Task Manager (faint grid lines).
 * - Tô nền bán trong suốt (semi-transparent area fill) dưới đường kẻ.
 * - Hỗ trợ vẽ 1 đường hoặc 2 đường (Send & Receive cho Mạng).
 * - Chế độ Mini (dành cho Sparkline sidebar) và Chế độ Full (cho màn hình chi tiết).
 */
public class TaskMgrLineChart extends JPanel {

    private final float[] history = new float[60];
    private final float[] history2 = new float[60];
    private int count = 0;

    private Color lineColor;
    private Color line2Color = null;
    private boolean isMini = false;
    private float maxScale = 100.0f;
    private boolean autoScale = false;
    private String unit = "%";
    private String topLabel = "";
    private String bottomLabel = "0";

    public TaskMgrLineChart(Color lineColor) {
        this(lineColor, false);
    }

    public TaskMgrLineChart(Color lineColor, boolean isMini) {
        this.lineColor = lineColor;
        this.isMini = isMini;
        setOpaque(false);
        setBackground(UITheme.BG_INPUT);
    }

    public void setLineColor(Color c) {
        this.lineColor = c;
        repaint();
    }

    public void setDualLine(Color color1, Color color2) {
        this.lineColor = color1;
        this.line2Color = color2;
        repaint();
    }

    public void setScale(float maxScale, boolean autoScale, String unit) {
        this.maxScale = maxScale;
        this.autoScale = autoScale;
        this.unit = unit;
        repaint();
    }

    public void setLabels(String top, String bottom) {
        this.topLabel = top;
        this.bottomLabel = bottom;
        repaint();
    }

    /** Thêm 1 điểm dữ liệu mới vào biểu đồ (chạy mỗi giây / mỗi chu kỳ cập nhật) */
    public synchronized void addPoint(float value) {
        addPoint(value, -1);
    }

    public synchronized void addPoint(float value1, float value2) {
        if (count < 60) {
            history[count] = Math.max(0, value1);
            if (value2 >= 0) history2[count] = Math.max(0, value2);
            count++;
        } else {
            System.arraycopy(history, 1, history, 0, 59);
            history[59] = Math.max(0, value1);
            if (value2 >= 0) {
                System.arraycopy(history2, 1, history2, 0, 59);
                history2[59] = Math.max(0, value2);
            }
        }

        if (autoScale) {
            float m = 1.0f;
            for (int i = 0; i < count; i++) {
                if (history[i] > m) m = history[i];
                if (line2Color != null && history2[i] > m) m = history2[i];
            }
            // Làm tròn cận trên mượt mà
            if (m <= 10) maxScale = 10;
            else if (m <= 50) maxScale = 50;
            else if (m <= 100) maxScale = 100;
            else if (m <= 500) maxScale = 500;
            else if (m <= 1000) maxScale = 1000;
            else maxScale = (float) (Math.ceil(m / 500.0) * 500.0);
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Nền tối obsidian
        g2.setColor(new Color(11, 15, 25));
        g2.fillRect(0, 0, w, h);

        int padLeft = isMini ? 2 : 8;
        int padRight = isMini ? 2 : 45; // Dành khoảng trống hiển thị nhãn trục Y
        int padTop = isMini ? 2 : 12;
        int padBottom = isMini ? 2 : 18;

        int plotW = Math.max(10, w - padLeft - padRight);
        int plotH = Math.max(10, h - padTop - padBottom);

        // 2. Vẽ lưới ô vuông mờ chuẩn Windows 11 Task Manager
        g2.setColor(new Color(255, 255, 255, 16));
        g2.setStroke(new BasicStroke(1.0f));

        // Đường ngang (0%, 25%, 50%, 75%, 100%)
        int hLines = isMini ? 2 : 4;
        for (int i = 0; i <= hLines; i++) {
            int y = padTop + (int) ((float) i / hLines * plotH);
            g2.drawLine(padLeft, y, padLeft + plotW, y);
        }

        // Đường dọc (mỗi 10 giây)
        if (!isMini) {
            int vLines = 6;
            for (int i = 0; i <= vLines; i++) {
                int x = padLeft + (int) ((float) i / vLines * plotW);
                g2.drawLine(x, padTop, x, padTop + plotH);
            }
        }

        // Viền bao quanh khu vực vẽ
        g2.setColor(new Color(45, 59, 82));
        g2.drawRect(padLeft, padTop, plotW, plotH);

        // 3. Nhãn trục (chỉ ở chế độ Full)
        if (!isMini) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(new Color(148, 163, 184));
            FontMetrics fm = g2.getFontMetrics();

            // Nhãn Max (đỉnh trên)
            String topStr = !topLabel.isEmpty() ? topLabel : (String.format("%.0f", maxScale) + unit);
            g2.drawString(topStr, padLeft + plotW + 5, padTop + fm.getAscent());

            // Nhãn Min (đáy dưới)
            String botStr = !bottomLabel.isEmpty() ? bottomLabel : ("0" + unit);
            g2.drawString(botStr, padLeft + plotW + 5, padTop + plotH);

            // Nhãn 60 seconds (góc trái dưới)
            g2.drawString("60 giây", padLeft, h - 3);
        }

        if (count < 2) {
            g2.dispose();
            return;
        }

        // 4. Vẽ dữ liệu
        float currentMax = Math.max(1.0f, maxScale);

        // Nếu có đường 2 (Receive) -> Vẽ đường 2 trước
        if (line2Color != null) {
            drawLineAndArea(g2, history2, line2Color, padLeft, padTop, plotW, plotH, currentMax, true);
        }

        // Vẽ đường 1 (Send hoặc thông số chính)
        drawLineAndArea(g2, history, lineColor, padLeft, padTop, plotW, plotH, currentMax, false);

        g2.dispose();
    }

    private void drawLineAndArea(Graphics2D g2, float[] data, Color color,
                                int padLeft, int padTop, int plotW, int plotH,
                                float currentMax, boolean isDashed) {
        int n = Math.min(count, 60);
        int[] xs = new int[n];
        int[] ys = new int[n];

        for (int i = 0; i < n; i++) {
            xs[i] = padLeft + (int) ((float) i / 59.0f * plotW);
            float val = Math.max(0, Math.min(currentMax, data[i]));
            ys[i] = padTop + plotH - (int) ((val / currentMax) * plotH);
        }

        // A. Tô nền bán trong suốt (Area Fill)
        Path2D.Float area = new Path2D.Float();
        area.moveTo(xs[0], padTop + plotH);
        for (int i = 0; i < n; i++) {
            area.lineTo(xs[i], ys[i]);
        }
        area.lineTo(xs[n - 1], padTop + plotH);
        area.closePath();

        Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), isMini ? 45 : 35);
        g2.setColor(fillColor);
        g2.fill(area);

        // B. Kẻ đường viền Antialiased
        g2.setColor(color);
        if (isDashed) {
            g2.setStroke(new BasicStroke(isMini ? 1.5f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{4, 3}, 0.0f));
        } else {
            g2.setStroke(new BasicStroke(isMini ? 1.5f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }

        for (int i = 0; i < n - 1; i++) {
            g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        }
    }
}

