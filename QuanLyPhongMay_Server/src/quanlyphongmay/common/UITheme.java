package quanlyphongmay.common;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;

/**
 * UITheme - Design System hiện đại cho toàn bộ hệ thống Quản Lý Phòng Máy (Pure Java Swing).
 * Triển khai theo nguyên lý Game UI/UX:
 * - Phân cấp thông tin rõ ràng (Tier 1 HUD, Tier 2 Tactical, Tier 3 Strategic)
 * - Tương phản sắc nét, màu sắc định danh trạng thái chuẩn quốc tế
 * - Phản hồi trực quan đa tầng (Visual hover, Active indicator, Status pills)
 * - 100% Pure Java Swing, không sử dụng .form, không phụ thuộc font emoji
 */
public final class UITheme {

    // ── Bảng màu chính (Cyber Obsidian Command Palette) ──────────────────────
    public static final Color BG_DARK        = new Color(11, 15, 25);    // Nền tối tổng thể (Deep Space)
    public static final Color BG_CARD        = new Color(19, 28, 46);    // Bề mặt Card / Panel
    public static final Color BG_CARD_HOVER  = new Color(28, 41, 66);    // Card khi hover
    public static final Color BG_SIDEBAR     = new Color(15, 23, 42);    // Sidebar chuyên dụng
    public static final Color BG_INPUT       = new Color(28, 38, 56);    // Ô nhập liệu
    public static final Color BG_LOG         = new Color(8, 12, 20);     // Terminal console nền đen sâu

    // ── Màu viền và ngăn cách ───────────────────────────────────────────────
    public static final Color BORDER         = new Color(45, 59, 82);    // Viền chính
    public static final Color BORDER_LIGHT   = new Color(71, 85, 105);   // Viền sáng
    public static final Color ROW_ALT        = new Color(15, 22, 36);    // Dòng xen kẽ trong bảng

    // ── Màu điểm nhấn (Accents & Statuses) ──────────────────────────────────
    public static final Color ACCENT         = new Color(14, 165, 233);  // Sky / Cyan vibrant
    public static final Color ACCENT_DARK    = new Color(2, 132, 199);   // Cyan đậm
    public static final Color ACCENT_LIGHT   = new Color(56, 189, 248);  // Cyan sáng
    public static final Color SUCCESS        = new Color(16, 185, 129);  // Emerald green (Online / Hoạt động)
    public static final Color WARNING        = new Color(245, 158, 11);  // Amber vàng (Cảnh báo / Khóa)
    public static final Color DANGER         = new Color(239, 68, 68);   // Rose đỏ (Nguy hiểm / Rớt mạng)
    public static final Color PURPLE         = new Color(139, 92, 246);  // Violet (GPU / Tiến trình đặc biệt)

    // ── Màu chữ (Typography) ────────────────────────────────────────────────
    public static final Color TEXT_PRIMARY   = new Color(248, 250, 252); // Trắng sáng
    public static final Color TEXT_MUTED     = new Color(148, 163, 184); // Xám trung tính
    public static final Color TEXT_DARK      = new Color(15, 23, 42);

    // ── Hệ thống Font phân cấp ──────────────────────────────────────────────
    public static final Font FONT_TITLE      = new Font("Segoe UI", Font.BOLD,  20);
    public static final Font FONT_STAT       = new Font("Segoe UI", Font.BOLD,  26);
    public static final Font FONT_HEADER     = new Font("Segoe UI", Font.BOLD,  14);
    public static final Font FONT_SUBHEADER  = new Font("Segoe UI", Font.BOLD,  12);
    public static final Font FONT_BODY       = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL      = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO       = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_BTN        = new Font("Segoe UI", Font.BOLD,  12);
    public static final Font FONT_BUTTON     = FONT_BTN;

    public static final int RADIUS = 8;

    private UITheme() {}

    // ═══════════════════════════════════════════════════════════════════════
    // Áp dụng theme toàn cục cho UIManager (gọi khi khởi động app)
    // ═══════════════════════════════════════════════════════════════════════
    public static void applyGlobal() {
        setupGlobalTheme();
    }

    public static void setupGlobalTheme() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        UIManager.put("control",                   BG_CARD);
        UIManager.put("info",                      BG_INPUT);
        UIManager.put("nimbusBase",                BG_DARK);
        UIManager.put("nimbusBlueGrey",            BG_SIDEBAR);
        UIManager.put("nimbusLightBackground",     BG_INPUT);
        UIManager.put("nimbusFocus",               ACCENT);
        UIManager.put("nimbusSelectionBackground", ACCENT_DARK);
        UIManager.put("text",                      TEXT_PRIMARY);
        UIManager.put("Panel.background",          BG_DARK);
        UIManager.put("Table.background",          BG_CARD);
        UIManager.put("Table.alternateRowColor",   ROW_ALT);
        UIManager.put("Table.foreground",          TEXT_PRIMARY);
        UIManager.put("Table.gridColor",           BORDER);
        UIManager.put("TableHeader.background",    BG_SIDEBAR);
        UIManager.put("TableHeader.foreground",    ACCENT_LIGHT);
        UIManager.put("ScrollPane.background",     BG_CARD);
        UIManager.put("TextField.background",      BG_INPUT);
        UIManager.put("TextField.foreground",      TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("ComboBox.background",       BG_INPUT);
        UIManager.put("ComboBox.foreground",       TEXT_PRIMARY);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox:\"ComboBox.listRenderer\".background", BG_INPUT);
        UIManager.put("ComboBox:\"ComboBox.listRenderer\".foreground", TEXT_PRIMARY);
        UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].background", ACCENT);
        UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].foreground", Color.WHITE);
        UIManager.put("List.background",           BG_INPUT);
        UIManager.put("List.foreground",           TEXT_PRIMARY);
        UIManager.put("List.selectionBackground",  ACCENT);
        UIManager.put("List.selectionForeground",  Color.WHITE);
        UIManager.put("PopupMenu.background",      BG_INPUT);
        UIManager.put("PopupMenu.foreground",      TEXT_PRIMARY);
        UIManager.put("PopupMenu.border",          BorderFactory.createLineBorder(BORDER, 1));
        UIManager.put("TextArea.background",       BG_INPUT);
        UIManager.put("TextArea.foreground",       TEXT_PRIMARY);
        UIManager.put("TextPane.background",       BG_INPUT);
        UIManager.put("TextPane.foreground",       TEXT_PRIMARY);
        UIManager.put("Label.foreground",          TEXT_PRIMARY);
        UIManager.put("Button.font",               FONT_BTN);
    }

    public static void styleFrame(JFrame frame) {
        frame.getContentPane().setBackground(BG_DARK);
        SwingUtilities.updateComponentTreeUI(frame);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Button Styling (Layered visual feedback with smooth hover states)
    // ═══════════════════════════════════════════════════════════════════════
    public static void styleButtonAccent(JButton btn) {
        styleButtonPrimary(btn);
    }

    public static void styleButtonPrimary(JButton btn) {
        setupButton(btn, ACCENT, ACCENT_DARK, Color.WHITE);
    }

    public static void styleButtonSuccess(JButton btn) {
        setupButton(btn, SUCCESS, new Color(13, 148, 102), Color.WHITE);
    }

    public static void styleButtonDanger(JButton btn) {
        setupButton(btn, DANGER, new Color(220, 38, 38), Color.WHITE);
    }

    public static void styleButtonWarning(JButton btn) {
        setupButton(btn, WARNING, new Color(217, 119, 6), Color.WHITE);
    }

    public static void styleButtonSecondary(JButton btn) {
        styleButtonGhost(btn);
    }

    public static void styleButtonGhost(JButton btn) {
        btn.setBackground(BG_CARD);
        btn.setForeground(ACCENT_LIGHT);
        btn.setFont(FONT_BTN);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(14, 165, 233, 35));
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT, 1, true),
                    BorderFactory.createEmptyBorder(6, 14, 6, 14)
                ));
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_CARD);
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1, true),
                    BorderFactory.createEmptyBorder(6, 14, 6, 14)
                ));
            }
        });
    }

    private static void setupButton(JButton btn, Color baseBg, Color hoverBg, Color fg) {
        btn.setBackground(baseBg);
        btn.setForeground(fg);
        btn.setFont(FONT_BTN);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(hoverBg);
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(baseBg);
            }
        });
    }

    public static void styleButtonSidebar(JButton btn, boolean active) {
        btn.setBackground(active ? new Color(14, 165, 233, 40) : BG_SIDEBAR);
        btn.setForeground(active ? ACCENT_LIGHT : TEXT_MUTED);
        btn.setFont(FONT_BODY);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        Border leftIndicator = BorderFactory.createMatteBorder(0, active ? 4 : 0, 0, 0, ACCENT);
        Border padding = BorderFactory.createEmptyBorder(9, active ? 16 : 20, 9, 16);
        btn.setBorder(BorderFactory.createCompoundBorder(leftIndicator, padding));

        if (!active) {
            btn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    btn.setBackground(new Color(14, 165, 233, 22));
                    btn.setForeground(TEXT_PRIMARY);
                }
                @Override public void mouseExited(MouseEvent e) {
                    btn.setBackground(BG_SIDEBAR);
                    btn.setForeground(TEXT_MUTED);
                }
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Input & Text Area Styling
    // ═══════════════════════════════════════════════════════════════════════
    public static void styleTextField(JTextField tf) {
        tf.setBackground(BG_INPUT);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(ACCENT_LIGHT);
        tf.setFont(FONT_BODY);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
    }

    public static void styleTextArea(JTextArea ta) {
        ta.setBackground(BG_INPUT);
        ta.setForeground(TEXT_PRIMARY);
        ta.setCaretColor(ACCENT_LIGHT);
        ta.setFont(FONT_MONO);
        ta.setSelectionColor(ACCENT_DARK);
        ta.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    public static void styleTextPane(JTextPane tp) {
        tp.setBackground(BG_INPUT);
        tp.setForeground(TEXT_PRIMARY);
        tp.setCaretColor(ACCENT_LIGHT);
        tp.setFont(FONT_BODY);
        tp.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    }

    public static void styleComboBox(JComboBox<?> cb) {
        cb.setBackground(BG_INPUT);
        cb.setForeground(TEXT_PRIMARY);
        cb.setFont(FONT_BODY);
        cb.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        cb.setFocusable(false);

        cb.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton btn = new JButton("▼");
                btn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                btn.setForeground(TEXT_MUTED);
                btn.setBackground(BG_INPUT);
                btn.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
                btn.setContentAreaFilled(false);
                btn.setFocusPainted(false);
                btn.setOpaque(false);
                return btn;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(BG_INPUT);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override
            protected javax.swing.plaf.basic.ComboPopup createPopup() {
                javax.swing.plaf.basic.BasicComboPopup popup = new javax.swing.plaf.basic.BasicComboPopup(comboBox) {
                    @Override
                    protected JScrollPane createScroller() {
                        JScrollPane scroller = super.createScroller();
                        scroller.getViewport().setBackground(BG_INPUT);
                        scroller.getViewport().setOpaque(true);
                        scroller.setBackground(BG_INPUT);
                        scroller.setOpaque(true);
                        scroller.setBorder(BorderFactory.createLineBorder(BORDER, 1));
                        return scroller;
                    }

                    @Override
                    public void show() {
                        if (list != null) {
                            list.setBackground(BG_INPUT);
                            list.setForeground(TEXT_PRIMARY);
                            list.setSelectionBackground(ACCENT);
                            list.setSelectionForeground(Color.WHITE);
                            list.setOpaque(true);
                        }
                        super.show();
                    }
                };
                popup.setBorder(BorderFactory.createLineBorder(BORDER, 1));
                popup.setOpaque(true);
                popup.setBackground(BG_INPUT);
                if (popup.getList() != null) {
                    popup.getList().setOpaque(true);
                    popup.getList().setBackground(BG_INPUT);
                    popup.getList().setForeground(TEXT_PRIMARY);
                    popup.getList().setSelectionBackground(ACCENT);
                    popup.getList().setSelectionForeground(Color.WHITE);
                }
                return popup;
            }
        });

        cb.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setOpaque(true);
                l.setFont(FONT_BODY);
                l.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
                if (list != null) {
                    list.setOpaque(true);
                    list.setBackground(BG_INPUT);
                    list.setForeground(TEXT_PRIMARY);
                    list.setSelectionBackground(ACCENT);
                    list.setSelectionForeground(Color.WHITE);
                }
                if (isSelected) {
                    l.setBackground(ACCENT);
                    l.setForeground(Color.WHITE);
                } else {
                    l.setBackground(BG_INPUT);
                    l.setForeground(TEXT_PRIMARY);
                }
                return l;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Table Styling (High clarity, status badge column, custom alternating rows)
    // ═══════════════════════════════════════════════════════════════════════
    public static void styleTable(JTable table) {
        table.setBackground(BG_CARD);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(FONT_BODY);
        table.setGridColor(BORDER);
        table.setRowHeight(36);
        table.setSelectionBackground(ACCENT_DARK);
        table.setSelectionForeground(Color.WHITE);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_SIDEBAR);
        header.setForeground(ACCENT_LIGHT);
        header.setFont(FONT_HEADER);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT));
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(header.getWidth(), 38));

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                setFont(FONT_BODY);
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                
                String valStr = (value == null) ? "" : value.toString().trim();

                // Cột STT hoặc căn giữa
                if (col == 0) {
                    setHorizontalAlignment(CENTER);
                } else {
                    setHorizontalAlignment(LEFT);
                }

                if (!sel) {
                    setBackground(row % 2 == 0 ? BG_CARD : ROW_ALT);
                    setForeground(TEXT_PRIMARY);
                    // Nhận diện trạng thái để tô màu nổi bật
                    if ("Online".equalsIgnoreCase(valStr) || valStr.contains("Online")) {
                        setForeground(SUCCESS);
                        setFont(FONT_SUBHEADER);
                    } else if ("Offline".equalsIgnoreCase(valStr) || valStr.contains("Offline")) {
                        setForeground(TEXT_MUTED);
                    } else if (valStr.contains("Cảnh báo") || valStr.contains("ALERT")) {
                        setForeground(DANGER);
                        setFont(FONT_SUBHEADER);
                    }
                } else {
                    setBackground(ACCENT_DARK);
                    setForeground(Color.WHITE);
                }
                return this;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ScrollPane Styling with custom dark scrollbar
    // ═══════════════════════════════════════════════════════════════════════
    public static void styleScrollPane(JScrollPane sp) {
        sp.setBackground(BG_CARD);
        sp.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        sp.getViewport().setBackground(BG_CARD);
        
        sp.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        sp.getVerticalScrollBar().setBackground(BG_SIDEBAR);
        sp.getHorizontalScrollBar().setBackground(BG_SIDEBAR);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 8));
    }

    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = BORDER_LIGHT;
            this.trackColor = BG_SIDEBAR;
        }
        @Override
        protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }
        @Override
        protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }
        private JButton createZeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? ACCENT : BORDER_LIGHT);
            g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y + 1, thumbBounds.width - 2, thumbBounds.height - 2, 4, 4);
            g2.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Card & HUD Stat Card Factories (Tier 1 glanceability)
    // ═══════════════════════════════════════════════════════════════════════
    public static JPanel makeCard(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(BG_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        return p;
    }

    public static JPanel makeCardWithTitle(String title, LayoutManager layout) {
        JPanel card = makeCard(layout);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                title,
                TitledBorder.LEFT, TitledBorder.TOP,
                FONT_HEADER, ACCENT_LIGHT
            ),
            BorderFactory.createEmptyBorder(6, 10, 10, 10)
        ));
        return card;
    }

    /**
     * Tạo HUD Stat Card chuyên nghiệp với vạch màu nhận diện trên đỉnh, số liệu to rõ, tiêu đề và ghi chú.
     */
        public static JPanel makeStatCard(String title, JLabel valueLabel, String subtitle, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            new javax.swing.border.Border() {
                @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(BORDER);
                    g2.drawRect(x, y, width - 1, height - 1);
                    g2.setColor(accentColor);
                    g2.fillRect(x, y, width, 3);
                    g2.dispose();
                }
                @Override public Insets getBorderInsets(Component c) { return new Insets(4, 1, 1, 1); }
                @Override public boolean isBorderOpaque() { return false; }
            },
            BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        JLabel lblTitle = new JLabel(title.toUpperCase());
        lblTitle.setFont(FONT_SMALL);
        lblTitle.setForeground(TEXT_MUTED);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valueLabel.setForeground(accentColor);
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(FONT_SMALL);
        lblSub.setForeground(TEXT_MUTED);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(lblSub, BorderLayout.SOUTH);

        return card;
    }

    public static JLabel makeBadge(String text, Color bg) {
        return makePill(text, bg, Color.WHITE);
    }

    public static JLabel makePill(String text, Color bg, Color fg) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setOpaque(false);
        lbl.setBackground(bg);
        lbl.setForeground(fg);
        lbl.setFont(FONT_SMALL);
        lbl.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        return lbl;
    }

    public static JLabel makeStatusDot(boolean online) {
        JLabel dot = new JLabel("  ● " + (online ? "Online" : "Offline"));
        dot.setFont(FONT_SMALL);
        dot.setForeground(online ? SUCCESS : DANGER);
        return dot;
    }

    public static void styleLabelTitle(JLabel lbl) {
        lbl.setFont(FONT_TITLE);
        lbl.setForeground(TEXT_PRIMARY);
    }

    public static void styleLabelHeader(JLabel lbl) {
        lbl.setFont(FONT_HEADER);
        lbl.setForeground(ACCENT_LIGHT);
    }

    public static void styleLabelBody(JLabel lbl) {
        lbl.setFont(FONT_BODY);
        lbl.setForeground(TEXT_MUTED);
    }

    public static void styleLabelValue(JLabel lbl, Color color) {
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lbl.setForeground(color);
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
    }
}
