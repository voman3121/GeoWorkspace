package ui;

import model.Node;
import ui.ShapeValidator.ShapeType;
import ui.ShapeValidator.ValidationResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ShapePanel extends JPanel {

    // ── theme ─────────────────────────────────────────────────────────────────
    private static final Color BG       = new Color(10, 13, 22);
    private static final Color BG2      = new Color(16, 20, 34);
    private static final Color ACCENT   = new Color(0,  185, 255);
    private static final Color FG       = new Color(160, 205, 235);
    private static final Color FG_DIM   = new Color(70,  100, 140);
    private static final Color SUCCESS  = new Color(35,  195, 115);
    private static final Color ERROR    = new Color(215,  60,  60);
    private static final Color SEL      = new Color(0,   75, 135);
    private static final Color TAB_ACT  = new Color(0,   80, 140);
    private static final Color BOOL_COL = new Color(180, 120, 255);

    // ── state ─────────────────────────────────────────────────────────────────
    private ShapeType selectedShape = ShapeType.LINE;
    private final JLabel statusLabel = new JLabel("<html><i>Select a shape then<br>SHIFT+click nodes</i></html>");
    private final JLabel countLabel  = new JLabel("Selected: 0 nodes");
    private final JButton[] shapeButtons = new JButton[ShapeType.values().length];
    private final JPanel cardPanel;
    private final CardLayout cards = new CardLayout();

    private final BiConsumer<ShapeType, List<Node>> onCreateShape;
    private final Runnable onExtend;
    private final Runnable onBoolOp;
    private Supplier<List<Node>> getSelection;
    private Supplier<String>     getBoolMode;   // "ADD","SUBTRACT","INTERSECT"

    public ShapePanel(BiConsumer<ShapeType, List<Node>> onCreate,
                      Runnable onExtend, Runnable onBoolOp) {
        this.onCreateShape = onCreate;
        this.onExtend      = onExtend;
        this.onBoolOp      = onBoolOp;

        setBackground(BG);
        setPreferredSize(new Dimension(200, 0));
        setBorder(new EmptyBorder(0, 0, 0, 0));
        setLayout(new BorderLayout());

        // ── Tab bar ────────────────────────────────────────────────────────
        JPanel tabBar = new JPanel(new GridLayout(1, 3, 1, 0));
        tabBar.setBackground(new Color(6, 8, 16));
        tabBar.setBorder(new MatteBorder(0, 0, 1, 0, new Color(25, 45, 80)));
        JButton tabShapes  = tabButton("SHAPES",  "shapes");
        JButton tabBool    = tabButton("BOOLEAN", "bool");
        JButton tabExtend  = tabButton("EXTEND",  "extend");
        tabBar.add(tabShapes); tabBar.add(tabBool); tabBar.add(tabExtend);
        add(tabBar, BorderLayout.NORTH);

        // ── Card panel ─────────────────────────────────────────────────────
        cardPanel = new JPanel(cards);
        cardPanel.setBackground(BG);
        cardPanel.add(buildShapesCard(), "shapes");
        cardPanel.add(buildBoolCard(),   "bool");
        cardPanel.add(buildExtendCard(), "extend");
        add(cardPanel, BorderLayout.CENTER);

        // shared status + count labels (used by shapes card)
        statusLabel.setFont(new Font("Consolas", Font.PLAIN, 10));
        statusLabel.setForeground(FG_DIM);
        countLabel.setFont(new Font("Consolas", Font.PLAIN, 10));
        countLabel.setForeground(FG_DIM);

        // activate shapes tab by default
        activateTab(tabShapes, "shapes");

        // Tab switching
        tabShapes.addActionListener(e -> activateTab(tabShapes, "shapes"));
        tabBool  .addActionListener(e -> activateTab(tabBool,   "bool"));
        tabExtend.addActionListener(e -> activateTab(tabExtend, "extend"));
    }

    // ── tab helpers ───────────────────────────────────────────────────────────

    private JButton tabButton(String label, String card) {
        JButton b = new JButton(label);
        b.setFont(new Font("Consolas", Font.BOLD, 10));
        b.setForeground(FG_DIM);
        b.setBackground(new Color(8, 10, 18));
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setPreferredSize(new Dimension(0, 28));
        return b;
    }

    private void activateTab(JButton active, String card) {
        for (Component c : ((JPanel) active.getParent()).getComponents()) {
            JButton b = (JButton) c;
            b.setBackground(new Color(8, 10, 18));
            b.setForeground(FG_DIM);
        }
        active.setBackground(TAB_ACT);
        active.setForeground(ACCENT);
        cards.show(cardPanel, card);
    }

    // ── SHAPES card ───────────────────────────────────────────────────────────

    private JPanel buildShapesCard() {
        JPanel p = new JPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        p.add(sectionLabel("◈ SHAPE TYPE"));
        p.add(Box.createVerticalStrut(6));

        ShapeType[] types = ShapeType.values();
        for (int i = 0; i < types.length; i++) {
            ShapeType t = types[i];
            JButton btn = shapeTypeButton(t);
            shapeButtons[i] = btn;
            btn.setAlignmentX(LEFT_ALIGNMENT);
            p.add(btn);
            p.add(Box.createVerticalStrut(3));
        }
        highlightSelected();

        p.add(Box.createVerticalStrut(10));
        countLabel.setAlignmentX(LEFT_ALIGNMENT);
        p.add(countLabel);
        p.add(Box.createVerticalStrut(6));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        p.add(statusLabel);
        p.add(Box.createVerticalStrut(10));

        JButton create = actionButton("CREATE SHAPE", new Color(0, 95, 170));
        create.addActionListener(e -> attemptCreate());
        create.setAlignmentX(LEFT_ALIGNMENT);
        p.add(create);
        p.add(Box.createVerticalStrut(4));

        JButton clear = actionButton("CLEAR SELECTION", BG2);
        clear.setForeground(FG_DIM);
        clear.addActionListener(e -> {
            if (getSelection != null) { getSelection.get().clear(); updateSelectionCount(0); }
        });
        clear.setAlignmentX(LEFT_ALIGNMENT);
        p.add(clear);

        p.add(Box.createVerticalGlue());
        p.add(Box.createVerticalStrut(10));
        JLabel hint = new JLabel("<html><span style='color:#2a4060'>"
            + "SHIFT+click = multi-select<br>"
            + "Scroll / pinch = zoom<br>"
            + "RMB drag = rotate<br>"
            + "RMB click = delete</span></html>");
        hint.setFont(new Font("Consolas", Font.PLAIN, 9));
        hint.setAlignmentX(LEFT_ALIGNMENT);
        p.add(hint);
        return p;
    }

    // ── BOOLEAN card ──────────────────────────────────────────────────────────

    private JPanel buildBoolCard() {
        JPanel p = new JPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        p.add(sectionLabel("◈ BOOLEAN OPS"));
        p.add(Box.createVerticalStrut(8));

        String[] modes   = {"INTERSECT", "SUBTRACT", "ADD (UNION)"};
        String[] descs   = {
            "Highlights the overlapping region between two selected shapes.",
            "Removes the intersecting portion from the first selected shape.",
            "Merges two shapes into one, removing the internal shared boundary."
        };
        Color[]  colours = {
            new Color(255, 180, 30),
            new Color(215, 60, 60),
            new Color(60, 200, 120)
        };
        ButtonGroup bg = new ButtonGroup();
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            JRadioButton rb = new JRadioButton(modes[i]);
            rb.setFont(new Font("Consolas", Font.BOLD, 10));
            rb.setForeground(colours[i]);
            rb.setBackground(BG);
            rb.setOpaque(true);
            rb.setAlignmentX(LEFT_ALIGNMENT);
            if (i == 0) rb.setSelected(true);
            bg.add(rb); p.add(rb);
            p.add(Box.createVerticalStrut(2));

            JLabel desc = new JLabel("<html><span style='color:#2a4060;font-size:9px'>"
                    + descs[i] + "</span></html>");
            desc.setFont(new Font("Consolas", Font.PLAIN, 9));
            desc.setAlignmentX(LEFT_ALIGNMENT);
            p.add(desc);
            p.add(Box.createVerticalStrut(8));
        }

        p.add(Box.createVerticalStrut(4));
        JLabel inst = new JLabel("<html><span style='color:#2a4060'>"
            + "1. SHIFT+click nodes of<br>"
            + "&nbsp;&nbsp;&nbsp;shape A, press SELECT A<br>"
            + "2. SHIFT+click nodes of<br>"
            + "&nbsp;&nbsp;&nbsp;shape B, press SELECT B<br>"
            + "3. Press APPLY</span></html>");
        inst.setFont(new Font("Consolas", Font.PLAIN, 9));
        inst.setAlignmentX(LEFT_ALIGNMENT);
        p.add(inst);
        p.add(Box.createVerticalStrut(10));

        JButton apply = actionButton("APPLY OPERATION", new Color(80, 40, 160));
        apply.setForeground(BOOL_COL);
        apply.addActionListener(e -> onBoolOp.run());
        apply.setAlignmentX(LEFT_ALIGNMENT);
        p.add(apply);

        p.add(Box.createVerticalGlue());
        p.add(sectionLabel("⚠ Boolean ops"));
        JLabel wip = new JLabel("<html><span style='color:#2a4060'>"
            + "coming in next version —<br>requires closed polygon<br>selections.</span></html>");
        wip.setFont(new Font("Consolas", Font.PLAIN, 9));
        wip.setAlignmentX(LEFT_ALIGNMENT);
        p.add(wip);
        return p;
    }

    // ── EXTEND card ───────────────────────────────────────────────────────────

    private JPanel buildExtendCard() {
        JPanel p = new JPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        p.add(sectionLabel("◈ LINE EXTEND"));
        p.add(Box.createVerticalStrut(8));

        JLabel desc = new JLabel("<html><span style='color:#3a5a7a'>"
            + "Select exactly 2 edges on<br>"
            + "the canvas, then press<br>"
            + "PREDICT INTERSECTION.<br><br>"
            + "The app will compute and<br>"
            + "mark where the two lines<br>"
            + "would meet if extended.<br><br>"
            + "Works for non-parallel lines.<br>"
            + "Parallel lines have no<br>"
            + "intersection point.</span></html>");
        desc.setFont(new Font("Consolas", Font.PLAIN, 10));
        desc.setAlignmentX(LEFT_ALIGNMENT);
        p.add(desc);
        p.add(Box.createVerticalStrut(12));

        JButton predict = actionButton("PREDICT INTERSECTION", new Color(0, 95, 130));
        predict.addActionListener(e -> onExtend.run());
        predict.setAlignmentX(LEFT_ALIGNMENT);
        p.add(predict);

        p.add(Box.createVerticalStrut(8));
        JLabel note = new JLabel("<html><span style='color:#2a4060'>"
            + "Tip: SHIFT+click an edge<br>"
            + "to add it to selection.</span></html>");
        note.setFont(new Font("Consolas", Font.PLAIN, 9));
        note.setAlignmentX(LEFT_ALIGNMENT);
        p.add(note);

        p.add(Box.createVerticalGlue());
        return p;
    }

    // ── public API ────────────────────────────────────────────────────────────

    public void setSelectionSupplier(Supplier<List<Node>> s) { this.getSelection = s; }

    public void updateSelectionCount(int n) {
        countLabel.setText("Selected: " + n + " node" + (n == 1 ? "" : "s"));
        if (n > 0 && getSelection != null) {
            ValidationResult r = ShapeValidator.validate(selectedShape, getSelection.get());
            setStatus(r.valid, r.message);
        } else {
            statusLabel.setText("<html><i>" + selectedShape.hint + "</i></html>");
            statusLabel.setForeground(FG_DIM);
        }
    }

    public ShapeType getSelectedShape() { return selectedShape; }

    // ── private helpers ───────────────────────────────────────────────────────

    private void attemptCreate() {
        if (getSelection == null) return;
        List<Node> sel = getSelection.get();
        ValidationResult r = ShapeValidator.validate(selectedShape, sel);
        if (!r.valid) {
            setStatus(false, r.message);
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        onCreateShape.accept(selectedShape, List.copyOf(sel));
        setStatus(true, selectedShape.displayName + " created!");
    }

    private void setStatus(boolean ok, String msg) {
        statusLabel.setText("<html>" + msg.replace("\n", "<br>") + "</html>");
        statusLabel.setForeground(ok ? SUCCESS : ERROR);
    }

    private JButton shapeTypeButton(ShapeType type) {
        JButton b = new JButton(type.displayName + "  [" + type.requiredNodes + "]");
        b.setFont(new Font("Consolas", Font.PLAIN, 10));
        b.setForeground(FG); b.setBackground(BG2);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            selectedShape = type;
            highlightSelected();
            if (getSelection != null) updateSelectionCount(getSelection.get().size());
        });
        return b;
    }

    private void highlightSelected() {
        ShapeType[] types = ShapeType.values();
        for (int i = 0; i < types.length; i++) {
            boolean s = types[i] == selectedShape;
            shapeButtons[i].setBackground(s ? SEL  : BG2);
            shapeButtons[i].setForeground(s ? ACCENT : FG);
        }
    }

    private JButton actionButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Consolas", Font.BOLD, 10));
        b.setForeground(Color.WHITE); b.setBackground(bg);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Consolas", Font.BOLD, 11));
        l.setForeground(ACCENT);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }
}