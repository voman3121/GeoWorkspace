package ui;

import model.Node;
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
        RightPanel rightPanel = new RightPanel(canvas,
            (ShapeType shape, List<Node> sel) -> canvas.createShape(shape, sel));
        canvas.setRightPanel(rightPanel);

        JPanel leftBar = buildLeftBar(canvas);

        JLabel title = new JLabel("  ◈ GEOWORKSPACE  //  2D Geometric Environment");
        title.setFont(new Font("Consolas", Font.BOLD, 13));
        title.setForeground(new Color(0, 200, 255));
        title.setOpaque(true);
        title.setBackground(new Color(5, 6, 14));
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel status = new JLabel(
            "  LMB=node/edge  •  SHIFT+LMB=multi-select  •  RMB drag=rotate  "
            + "•  Scroll=zoom  •  RMB click=delete  •  Ctrl+Z/Y=undo/redo  •  Del=bulk delete");
        status.setFont(new Font("Consolas", Font.PLAIN, 10));
        status.setForeground(new Color(40, 75, 130));
        status.setOpaque(true);
        status.setBackground(new Color(5, 6, 14));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        setLayout(new BorderLayout());
        add(title,      BorderLayout.NORTH);
        add(leftBar,    BorderLayout.WEST);
        add(canvas,     BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(status,     BorderLayout.SOUTH);

        // Global Ctrl+Z / Ctrl+Y
        registerKey(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, "undo",
            e -> { canvas.getUndoManager().undo(); canvas.loadFromDB(); canvas.repaint(); });
        registerKey(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, "redo",
            e -> { canvas.getUndoManager().redo(); canvas.loadFromDB(); canvas.repaint(); });
    }

    private void registerKey(int key, int mask, String name, ActionListener al) {
        KeyStroke ks = KeyStroke.getKeyStroke(key, mask);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        getRootPane().getActionMap().put(name,
            new AbstractAction() { public void actionPerformed(ActionEvent e) { al.actionPerformed(e); } });
    }

    // ── Left bar ──────────────────────────────────────────────────────────────
    private JPanel buildLeftBar(WorkspacePanel canvas) {
        JPanel bar = new JPanel();
        bar.setBackground(new Color(8, 10, 20));
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        bar.setPreferredSize(new Dimension(95, 0));

        // ── Mode toggle buttons ───────────────────────────────────────────────
        bar.add(barLabel("MODES")); bar.add(Box.createVerticalStrut(6));

        JToggleButton multiBtn  = modeBtn("⊞ Multi\nSelect",
            "SHIFT+click also works in any mode.\nClick nodes/edges to select, then Del to delete.");
        JToggleButton shapeBtn  = modeBtn("⬡ Shape\nSelect",
            "Click nodes in order to build shape selection.\nThen use Shapes panel → CREATE SHAPE.");
        JToggleButton circleBtn = modeBtn("● Circle",
            "Click + drag to set center and radius.\nRelease to place. Prompts: full circle or semi-circle.");

        JToggleButton[] group = { multiBtn, shapeBtn, circleBtn };

        multiBtn.addActionListener(e -> {
            canvas.toggleMode(WorkspacePanel.Mode.MULTI_SELECT);
            syncGroup(group, multiBtn, canvas.getActiveMode() == WorkspacePanel.Mode.MULTI_SELECT);
        });
        shapeBtn.addActionListener(e -> {
            canvas.toggleMode(WorkspacePanel.Mode.SHAPE_SELECT);
            syncGroup(group, shapeBtn, canvas.getActiveMode() == WorkspacePanel.Mode.SHAPE_SELECT);
        });
        circleBtn.addActionListener(e -> {
            canvas.toggleMode(WorkspacePanel.Mode.CIRCLE);
            syncGroup(group, circleBtn, canvas.getActiveMode() == WorkspacePanel.Mode.CIRCLE);
        });

        bar.add(multiBtn);  bar.add(Box.createVerticalStrut(4));
        bar.add(shapeBtn);  bar.add(Box.createVerticalStrut(4));
        bar.add(circleBtn); bar.add(Box.createVerticalStrut(12));

        bar.add(divider()); bar.add(Box.createVerticalStrut(10));

        // ── Action buttons ────────────────────────────────────────────────────
        bar.add(barLabel("ACTIONS")); bar.add(Box.createVerticalStrut(6));

        JButton undoBtn = actionBtn("↩ Undo", "Ctrl+Z — undo last action");
        undoBtn.addActionListener(e -> {
            canvas.getUndoManager().undo(); canvas.loadFromDB(); canvas.repaint();
        });
        JButton redoBtn = actionBtn("↪ Redo", "Ctrl+Y — redo last undone action");
        redoBtn.addActionListener(e -> {
            canvas.getUndoManager().redo(); canvas.loadFromDB(); canvas.repaint();
        });

        bar.add(undoBtn); bar.add(Box.createVerticalStrut(4));
        bar.add(redoBtn); bar.add(Box.createVerticalStrut(10));

        bar.add(divider()); bar.add(Box.createVerticalStrut(10));

        // ── Import section ────────────────────────────────────────────────────
        bar.add(barLabel("IMPORT")); bar.add(Box.createVerticalStrut(6));

        JButton importBtn = actionBtn("⤓ Trace",
            "Auto-trace a hand-drawn sketch image.\n"
            + "OpenCV pipeline: Canny + Hough + contours.\n"
            + "Opens result in a NEW workspace window.");
        importBtn.addActionListener(e -> canvas.importImage());

        JButton meshBtn = actionBtn("⬡ Mesh",
            "Import an aerofoil image and generate a\n"
            + "structured O-grid CFD mesh around it.\n"
            + "Uses boundary extraction + diffusion smoothing.");
        meshBtn.setForeground(new Color(100, 220, 160));
        meshBtn.addActionListener(e -> new AerofoilMesher().run(canvas, () -> {
            canvas.loadFromDB(); canvas.repaint();
        }));

        bar.add(importBtn); bar.add(Box.createVerticalStrut(4));
        bar.add(meshBtn);   bar.add(Box.createVerticalStrut(10));

        bar.add(divider()); bar.add(Box.createVerticalStrut(10));

        // ── Help button ───────────────────────────────────────────────────────
        bar.add(barLabel("HELP")); bar.add(Box.createVerticalStrut(6));
        JButton helpBtn = actionBtn("? Help", "Show keyboard & mouse controls");
        helpBtn.setForeground(new Color(180, 220, 255));
        helpBtn.addActionListener(e -> showHelpDialog());
        bar.add(helpBtn);

        bar.add(Box.createVerticalGlue());
        return bar;
    }

    private void showHelpDialog() {
        JDialog dlg = new JDialog(this, "GeoWorkspace — Controls", false);
        dlg.setSize(480, 580);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(new Color(8, 10, 20));
        dlg.setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel();
        content.setBackground(new Color(8, 10, 20));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        String[][] sections = {
            { "MOUSE" },
            { "Left-click (empty)",     "Place node (snapped to grid)" },
            { "Left-click node→node",   "Draw edge between nodes" },
            { "SHIFT + click",          "Multi-select nodes / edges / shapes" },
            { "RMB drag",               "Rotate workspace" },
            { "RMB click (on item)",    "Delete context menu" },
            { "Scroll",                 "Zoom in / out" },
            { "Circle mode: drag",      "Center → radius → release to place" },
            { "" },
            { "KEYBOARD" },
            { "Ctrl+Z",                 "Undo (up to 10 steps)" },
            { "Ctrl+Y",                 "Redo" },
            { "Delete",                 "Delete all multi-selected items" },
            { "" },
            { "LEFT BAR MODES" },
            { "⊞ Multi-Select",         "Select nodes/edges; Del removes all" },
            { "⬡ Shape-Select",         "Click nodes IN ORDER, then CREATE SHAPE" },
            { "● Circle",               "Drag to draw circle or semi-circle" },
            { "" },
            { "SHAPES (right panel)" },
            { "Line",                   "2 nodes" },
            { "Triangle",               "3 nodes, non-collinear" },
            { "Rectangle",              "4 nodes, right angles, opposite sides equal" },
            { "Square",                 "4 nodes, all sides equal + right angles" },
            { "Pentagon / Hexagon",     "5 / 6 nodes, roughly equal sides" },
            { "Arc",                    "3 nodes: start, control point, end" },
            { "Semi-circle",            "Use Circle tool → choose Semi-circle on release" },
            { "Free Polygon",           "Any 3+ nodes — closed, non-self-intersecting" },
            { "" },
            { "BOOLEAN (right panel)" },
            { "SHIFT+click inside shape","Select shape for boolean operation" },
            { "INTERSECT",              "Highlights the actual overlapping region" },
            { "SUBTRACT",               "Removes overlap from whichever shape you choose" },
            { "ADD / UNION",            "Connects nearest endpoints of 2 shapes" },
            { "" },
            { "IMPORT (left bar)" },
            { "⤓ Trace",               "Auto-trace sketch photo → new workspace window" },
            { "⬡ Mesh",                "Extract aerofoil boundary → O-grid CFD mesh" },
            { "  Re-mesh sliders",      "Boundary pts, radial layers, smooth passes" },
        };

        for (String[] row : sections) {
            if (row.length == 1) {
                if (row[0].isEmpty()) { content.add(Box.createVerticalStrut(5)); continue; }
                JLabel h = new JLabel(row[0]);
                h.setFont(new Font("Consolas", Font.BOLD, 11));
                h.setForeground(new Color(0, 185, 255));
                h.setAlignmentX(LEFT_ALIGNMENT);
                content.add(h); content.add(Box.createVerticalStrut(3));
            } else {
                JPanel row2 = new JPanel(new BorderLayout(0, 0));
                row2.setBackground(new Color(8, 10, 20));
                row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                JLabel key = new JLabel(row[0]);
                key.setFont(new Font("Consolas", Font.BOLD, 9));
                key.setForeground(new Color(0, 140, 200));
                key.setPreferredSize(new Dimension(175, 16));
                JLabel val = new JLabel(row[1]);
                val.setFont(new Font("Consolas", Font.PLAIN, 9));
                val.setForeground(new Color(100, 145, 185));
                row2.add(key, BorderLayout.WEST);
                row2.add(val, BorderLayout.CENTER);
                row2.setAlignmentX(LEFT_ALIGNMENT);
                content.add(row2);
            }
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        scroll.setBackground(new Color(8, 10, 20));

        JButton close = new JButton("CLOSE");
        close.setFont(new Font("Consolas", Font.BOLD, 11));
        close.setForeground(Color.WHITE);
        close.setBackground(new Color(0, 80, 140));
        close.setBorderPainted(false); close.setFocusPainted(false); close.setOpaque(true);
        close.addActionListener(e -> dlg.dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setBackground(new Color(8, 10, 20));
        south.add(close);

        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void syncGroup(JToggleButton[] group, JToggleButton active, boolean isOn) {
        for (JToggleButton b : group) {
            boolean sel = (b == active) && isOn;
            b.setSelected(sel);
            b.setBackground(sel ? new Color(0, 75, 135) : new Color(14, 18, 32));
        }
    }

    private JToggleButton modeBtn(String text, String tooltip) {
        String html = "<html><center>" + text.replace("\n", "<br>") + "</center></html>";
        JToggleButton b = new JToggleButton(html);
        b.setFont(new Font("Consolas", Font.PLAIN, 9));
        b.setForeground(new Color(120, 185, 225));
        b.setBackground(new Color(14, 18, 32));
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setToolTipText("<html>" + tooltip.replace("\n", "<br>") + "</html>");
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(ev ->
            b.setBackground(b.isSelected() ? new Color(0, 75, 135) : new Color(14, 18, 32)));
        return b;
    }

    private JButton actionBtn(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setFont(new Font("Consolas", Font.PLAIN, 9));
        b.setForeground(new Color(100, 165, 210));
        b.setBackground(new Color(14, 18, 32));
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setToolTipText("<html>" + tooltip.replace("\n", "<br>") + "</html>");
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JLabel barLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Consolas", Font.BOLD, 9));
        l.setForeground(new Color(0, 150, 200));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JSeparator divider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(25, 40, 70));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }
}