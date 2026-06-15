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

        // Register Ctrl+Z / Ctrl+Y on the root pane so they always fire
        registerKey(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, "undo",
            e -> { canvas.getUndoManager().undo(); canvas.loadFromDB(); canvas.repaint(); });
        registerKey(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, "redo",
            e -> { canvas.getUndoManager().redo(); canvas.loadFromDB(); canvas.repaint(); });
    }

    private void registerKey(int key, int mask, String name, ActionListener al) {
        KeyStroke ks = KeyStroke.getKeyStroke(key, mask);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { al.actionPerformed(e); }
        });
    }

    // ── Left bar: 3 toggleable mode buttons + import + undo/redo ─────────────
    private JPanel buildLeftBar(WorkspacePanel canvas) {
        JPanel bar = new JPanel();
        bar.setBackground(new Color(8, 10, 20));
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        bar.setPreferredSize(new Dimension(95, 0));

        bar.add(barLabel("MODES"));
        bar.add(Box.createVerticalStrut(6));

        // Three exclusive toggle buttons
        JToggleButton multiBtn  = modeBtn("⊞ Multi\nSelect",
            "Click nodes & edges to select, then Del to delete all at once");
        JToggleButton shapeBtn  = modeBtn("⬡ Shape\nSelect",
            "Click nodes in order to build shape selection, then use Shapes panel");
        JToggleButton circleBtn = modeBtn("● Circle",
            "Click to set center, drag/move to set radius, release to place");

        // Mutual exclusion: clicking one deactivates others
        JToggleButton[] group = {multiBtn, shapeBtn, circleBtn};

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
        bar.add(circleBtn); bar.add(Box.createVerticalStrut(14));

        // Divider
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(25, 40, 70));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        bar.add(sep); bar.add(Box.createVerticalStrut(10));

        bar.add(barLabel("ACTIONS"));
        bar.add(Box.createVerticalStrut(6));

        // Undo / Redo
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

        // Import
        JButton importBtn = actionBtn("⤓ Import", "Import a hand-drawn image as a reference layer");
        importBtn.addActionListener(e -> canvas.importImage());
        bar.add(importBtn);

        bar.add(Box.createVerticalGlue());
        return bar;
    }

    /** Keeps toggle group mutually exclusive and syncs visual state. */
    private void syncGroup(JToggleButton[] group, JToggleButton active, boolean isOn) {
        for (JToggleButton b : group) {
            if (b == active) {
                b.setSelected(isOn);
                b.setBackground(isOn ? new Color(0, 75, 135) : new Color(14, 18, 32));
            } else {
                b.setSelected(false);
                b.setBackground(new Color(14, 18, 32));
            }
        }
    }

    private JToggleButton modeBtn(String text, String tooltip) {
        String html = "<html><center>" + text.replace("\n", "<br>") + "</center></html>";
        JToggleButton b = new JToggleButton(html);
        b.setFont(new Font("Consolas", Font.PLAIN, 9));
        b.setForeground(new Color(120, 185, 225));
        b.setBackground(new Color(14, 18, 32));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setToolTipText(tooltip);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(ev ->
            b.setBackground(b.isSelected() ? new Color(0,75,135) : new Color(14,18,32)));
        return b;
    }

    private JButton actionBtn(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setFont(new Font("Consolas", Font.PLAIN, 9));
        b.setForeground(new Color(100, 165, 210));
        b.setBackground(new Color(14, 18, 32));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setToolTipText(tooltip);
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
}