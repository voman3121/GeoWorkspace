package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Boolean Operations panel.
 * Intersection: highlights where edges cross.
 * Subtract: removes intersecting edges.
 * Add: informational (topology merge placeholder).
 */
public class BooleanPanel extends JPanel {

    private static final Color BG      = new Color(12, 14, 24);
    private static final Color BG2     = new Color(18, 22, 36);
    private static final Color ACCENT  = new Color(255, 180, 0);
    private static final Color FG      = new Color(170, 210, 240);
    private static final Color FG_DIM  = new Color(80,  110, 150);

    private WorkspacePanel canvas;

    public BooleanPanel(WorkspacePanel canvas) {
        this.canvas = canvas;
        setBackground(BG);
        setPreferredSize(new Dimension(190, 0));
        setBorder(new EmptyBorder(12, 10, 12, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(label("◈ BOOLEAN OPS", 12, Font.BOLD, ACCENT));
        add(Box.createVerticalStrut(8));

        JLabel desc = new JLabel("<html><span style='color:#3a5a8a'>"
            + "Operates on all edges<br>currently in the workspace."
            + "</span></html>");
        desc.setFont(new Font("Consolas", Font.PLAIN, 10));
        desc.setAlignmentX(LEFT_ALIGNMENT);
        add(desc);
        add(Box.createVerticalStrut(14));

        // ── INTERSECTION ──────────────────────────────────────────────────
        add(label("INTERSECTION", 10, Font.BOLD, FG));
        add(Box.createVerticalStrut(4));
        JLabel intDesc = new JLabel("<html><span style='color:#3a5a7a'>"
            + "Finds and highlights all points<br>where edges cross each other."
            + "</span></html>");
        intDesc.setFont(new Font("Consolas", Font.PLAIN, 9));
        intDesc.setAlignmentX(LEFT_ALIGNMENT);
        add(intDesc);
        add(Box.createVerticalStrut(6));

        JButton intBtn = btn("FIND INTERSECTIONS", new Color(180, 140, 0));
        intBtn.addActionListener(e -> canvas.computeIntersections());
        add(intBtn);
        add(Box.createVerticalStrut(3));

        JButton clearBtn = btn("CLEAR HIGHLIGHTS", BG2);
        clearBtn.setForeground(FG_DIM);
        clearBtn.addActionListener(e -> canvas.clearIntersections());
        add(clearBtn);

        add(Box.createVerticalStrut(16));

        // ── SUBTRACT ──────────────────────────────────────────────────────
        add(label("SUBTRACT", 10, Font.BOLD, FG));
        add(Box.createVerticalStrut(4));
        JLabel subDesc = new JLabel("<html><span style='color:#3a5a7a'>"
            + "Finds intersecting edge pairs<br>and lets you remove them."
            + "</span></html>");
        subDesc.setFont(new Font("Consolas", Font.PLAIN, 9));
        subDesc.setAlignmentX(LEFT_ALIGNMENT);
        add(subDesc);
        add(Box.createVerticalStrut(6));

        JButton subBtn = btn("SUBTRACT INTERSECTING", new Color(160, 50, 50));
        subBtn.addActionListener(e -> canvas.booleanSubtract());
        add(subBtn);

        add(Box.createVerticalStrut(16));

        // ── ADD ───────────────────────────────────────────────────────────
        add(label("ADD", 10, Font.BOLD, FG));
        add(Box.createVerticalStrut(4));
        JLabel addDesc = new JLabel("<html><span style='color:#3a5a7a'>"
            + "Merges geometry — connects<br>nearest open endpoints."
            + "</span></html>");
        addDesc.setFont(new Font("Consolas", Font.PLAIN, 9));
        addDesc.setAlignmentX(LEFT_ALIGNMENT);
        add(addDesc);
        add(Box.createVerticalStrut(6));

        JButton addBtn = btn("ADD / MERGE", new Color(0, 100, 60));
        addBtn.addActionListener(e -> canvas.booleanAdd());
        add(addBtn);

        add(Box.createVerticalGlue());
    }

    private JButton btn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Consolas", Font.BOLD, 10));
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Consolas", style, size));
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }
}