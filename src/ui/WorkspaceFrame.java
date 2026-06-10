package ui;

import ui.ShapeValidator.ShapeType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class WorkspaceFrame extends JFrame {

    public WorkspaceFrame() {
        setTitle("GeoWorkspace");
        setSize(1400, 860);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setBackground(new Color(6, 8, 16));

        WorkspacePanel canvas = new WorkspacePanel();

        // ── Shape panel ────────────────────────────────────────────────────────
        ShapePanel shapePanel = new ShapePanel(
        	    (ShapeType shape, List<model.Node> sel) -> canvas.createShape(shape, sel),
        	    () -> canvas.setMode(WorkspacePanel.InteractionMode.EXTEND),
        	    () -> canvas.computeIntersections()
        	);

        // ── Boolean ops panel ──────────────────────────────────────────────────
        BooleanPanel boolPanel = new BooleanPanel(canvas);

        // ── Tabbed right panel ─────────────────────────────────────────────────
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(new Color(10, 12, 22));
        tabs.setForeground(new Color(120, 180, 220));
        tabs.setFont(new Font("Consolas", Font.PLAIN, 11));
        tabs.addTab("Shapes", shapePanel);
        tabs.addTab("Boolean", boolPanel);
        tabs.setPreferredSize(new Dimension(200, 0));

        // ── Toolbar ────────────────────────────────────────────────────────────
        JToolBar toolbar = buildToolbar(canvas);

        // ── Title bar ──────────────────────────────────────────────────────────
        JLabel title = new JLabel("  ◈ GEOWORKSPACE  //  2D Geometric Environment");
        title.setFont(new Font("Consolas", Font.BOLD, 13));
        title.setForeground(new Color(0, 200, 255));
        title.setOpaque(true);
        title.setBackground(new Color(5, 6, 14));
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        // ── Status bar ─────────────────────────────────────────────────────────
        JLabel status = new JLabel(
            "  LMB=node/edge  SHIFT+LMB=multi-select  Scroll=zoom  RMBdrag=rotate  RMBclick=delete  Ctrl+Z/Y=undo/redo  Del=bulk delete");
        status.setFont(new Font("Consolas", Font.PLAIN, 10));
        status.setForeground(new Color(45, 85, 140));
        status.setOpaque(true);
        status.setBackground(new Color(5, 6, 14));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        // ── Layout ─────────────────────────────────────────────────────────────
        setLayout(new BorderLayout());
        add(title,   BorderLayout.NORTH);
        add(toolbar, BorderLayout.WEST);
        add(canvas,  BorderLayout.CENTER);
        add(tabs,    BorderLayout.EAST);
        add(status,  BorderLayout.SOUTH);
    }

    private JToolBar buildToolbar(WorkspacePanel canvas) {
        JToolBar tb = new JToolBar(JToolBar.VERTICAL);
        tb.setFloatable(false);
        tb.setBackground(new Color(8, 10, 20));
        tb.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));

        tb.add(toolBtn("✦ Normal",   "Place nodes & draw edges",
            e -> canvas.setMode(WorkspacePanel.InteractionMode.NORMAL)));
        tb.add(Box.createVerticalStrut(4));
        tb.add(toolBtn("⊞ Multi-sel","SHIFT+click nodes/edges then Del",
            e -> canvas.setMode(WorkspacePanel.InteractionMode.MULTI_SELECT)));
        tb.add(Box.createVerticalStrut(4));
        tb.add(toolBtn("⬡ Shape-sel","SHIFT+click nodes for shape panel",
            e -> canvas.setMode(WorkspacePanel.InteractionMode.SHAPE_SELECT)));
        tb.add(Box.createVerticalStrut(4));
        tb.add(toolBtn("⟋ Extend",  "Click 2 edges to predict intersection",
            e -> canvas.setMode(WorkspacePanel.InteractionMode.EXTEND)));
        tb.add(Box.createVerticalStrut(16));

        // Undo / Redo
        tb.add(toolBtn("↩ Undo", "Ctrl+Z", e -> {
            canvas.getUndoManager().undo();
            canvas.repaint();
        }));
        tb.add(Box.createVerticalStrut(4));
        tb.add(toolBtn("↪ Redo", "Ctrl+Y", e -> {
            canvas.getUndoManager().redo();
            canvas.repaint();
        }));

        return tb;
    }

    private JButton toolBtn(String text, String tooltip, ActionListener al) {
        JButton b = new JButton("<html><center>" + text + "</center></html>");
        b.setFont(new Font("Consolas", Font.PLAIN, 10));
        b.setForeground(new Color(140, 200, 240));
        b.setBackground(new Color(14, 18, 32));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(80, 44));
        b.setMaximumSize(new Dimension(80, 44));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(al);
        return b;
    }
}