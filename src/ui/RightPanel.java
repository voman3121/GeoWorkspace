package ui;

import model.Node;
import ui.ShapeValidator.ShapeType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Right panel: Shapes tab | Boolean tab
 * Extend is ONLY in this panel (removed from left bar).
 */
public class RightPanel extends JPanel {

    private static final Color BG      = new Color(10, 13, 22);
    private static final Color BG2     = new Color(16, 20, 34);
    private static final Color ACCENT  = new Color(0,  185, 255);
    private static final Color FG      = new Color(160, 205, 235);
    private static final Color FG_DIM  = new Color(70,  100, 140);
    private static final Color SUCCESS = new Color(35,  195, 115);
    private static final Color ERROR   = new Color(215,  60,  60);
    private static final Color SEL     = new Color(0,    75, 135);
    private static final Color TAB_ACT = new Color(0,    80, 140);

    private ShapeType selectedShape = ShapeType.LINE;
    private final JLabel shapeCountLabel;
    private final JLabel shapeStatusLabel;
    private final JLabel boolCountLabel;
    private final JButton[] shapeButtons = new JButton[ShapeType.values().length];

    private final JPanel    cardPanel = new JPanel(new CardLayout());
    private final CardLayout cards    = (CardLayout) cardPanel.getLayout();
    private final JButton   tabShapes, tabBool, tabExtend;

    private final WorkspacePanel canvas;
    private final BiConsumer<ShapeType, List<Node>> onCreateShape;

    public RightPanel(WorkspacePanel canvas, BiConsumer<ShapeType, List<Node>> onCreateShape) {
        this.canvas        = canvas;
        this.onCreateShape = onCreateShape;

        setBackground(BG);
        setPreferredSize(new Dimension(210, 0));
        setBorder(new EmptyBorder(0,0,0,0));
        setLayout(new BorderLayout());

        // Init labels BEFORE building cards
        shapeCountLabel  = lbl("Selected: 0 nodes", 10, Font.PLAIN, FG_DIM);
        shapeStatusLabel = lbl("<html><i>Pick shape, then SHAPE-SELECT nodes</i></html>", 10, Font.PLAIN, FG_DIM);
        boolCountLabel   = lbl("Shapes selected: 0", 10, Font.PLAIN, FG_DIM);

        // Tab bar
        JPanel tabBar = new JPanel(new GridLayout(1,3,1,0));
        tabBar.setBackground(new Color(6,8,16));
        tabBar.setBorder(new MatteBorder(0,0,1,0,new Color(25,45,80)));
        tabShapes = tabBtn("SHAPES");
        tabBool   = tabBtn("BOOLEAN");
        tabExtend = tabBtn("EXTEND");
        tabBar.add(tabShapes); tabBar.add(tabBool); tabBar.add(tabExtend);
        add(tabBar, BorderLayout.NORTH);

        cardPanel.setBackground(BG);
        cardPanel.add(buildShapesCard(), "shapes");
        cardPanel.add(buildBoolCard(),   "bool");
        cardPanel.add(buildExtendCard(), "extend");
        add(cardPanel, BorderLayout.CENTER);

        activateTab(tabShapes); cards.show(cardPanel,"shapes");
        tabShapes.addActionListener(e->{ activateTab(tabShapes); cards.show(cardPanel,"shapes"); });
        tabBool  .addActionListener(e->{ activateTab(tabBool);   cards.show(cardPanel,"bool"); });
        tabExtend.addActionListener(e->{ activateTab(tabExtend); cards.show(cardPanel,"extend");
                                          canvas.toggleMode(WorkspacePanel.Mode.EXTEND); });
    }

    // ── public API ────────────────────────────────────────────────────────────

    public void updateShapeCount(int n) {
        shapeCountLabel.setText("Selected: "+n+" node"+(n==1?"":"s"));
        if (n>0) {
            var r=ShapeValidator.validate(selectedShape, canvas.getShapeNodes());
            shapeStatusLabel.setText("<html>"+r.message.replace("\n","<br>")+"</html>");
            shapeStatusLabel.setForeground(r.valid?SUCCESS:ERROR);
        } else {
            shapeStatusLabel.setText("<html><i>"+selectedShape.hint+"</i></html>");
            shapeStatusLabel.setForeground(FG_DIM);
        }
    }

    public void updateBoolCount(int n) { boolCountLabel.setText("Shapes selected: "+n); }

    public boolean isBoolTabActive() { return tabBool.getBackground().equals(TAB_ACT); }

    public void clearModeButtons() { /* called after circle committed */ }

    // ── SHAPES card ───────────────────────────────────────────────────────────

    private JPanel buildShapesCard() {
        JPanel p = panel();
        p.add(sec("◈ SHAPE TYPE")); p.add(gap(6));

        ShapeType[] types = ShapeType.values();
        for (int i=0; i<types.length; i++) {
            final ShapeType t=types[i];
            JButton btn=shapeTypeBtn(t);
            shapeButtons[i]=btn; p.add(btn); p.add(gap(3));
        }
        highlightSelected();

        p.add(gap(10));
        shapeCountLabel.setAlignmentX(LEFT_ALIGNMENT); p.add(shapeCountLabel);
        p.add(gap(5));
        shapeStatusLabel.setAlignmentX(LEFT_ALIGNMENT); p.add(shapeStatusLabel);
        p.add(gap(10));

        JButton modeBtn=actionBtn("SHAPE-SELECT MODE",new Color(0,70,120));
        modeBtn.setToolTipText("Toggle shape-select — click nodes on canvas to build selection");
        modeBtn.addActionListener(e->canvas.toggleMode(WorkspacePanel.Mode.SHAPE_SELECT));
        modeBtn.setAlignmentX(LEFT_ALIGNMENT); p.add(modeBtn); p.add(gap(4));

        JButton create=actionBtn("CREATE SHAPE",new Color(0,95,170));
        create.addActionListener(e->onCreateShape.accept(selectedShape,canvas.getShapeNodes()));
        create.setAlignmentX(LEFT_ALIGNMENT); p.add(create); p.add(gap(4));

        JButton clear=actionBtn("CLEAR SELECTION",BG2);
        clear.setForeground(FG_DIM);
        clear.addActionListener(e->{ canvas.getShapeNodes().clear(); updateShapeCount(0); });
        clear.setAlignmentX(LEFT_ALIGNMENT); p.add(clear);

        p.add(Box.createVerticalGlue()); p.add(gap(10));
        JLabel hint=lbl("<html><span style='color:#1e3050'>"
            +"1. Pick shape type above<br>"
            +"2. Click SHAPE-SELECT MODE<br>"
            +"3. Click nodes in order<br>"
            +"4. Click CREATE SHAPE<br>"
            +"For Circle: use ● Circle<br>"
            +"button on left toolbar</span></html>",9,Font.PLAIN,FG_DIM);
        hint.setAlignmentX(LEFT_ALIGNMENT); p.add(hint);
        return p;
    }

    // ── BOOLEAN card ──────────────────────────────────────────────────────────

    private JPanel buildBoolCard() {
        JPanel p = panel();
        p.add(sec("◈ BOOLEAN OPS")); p.add(gap(6));

        JLabel desc=lbl("<html><span style='color:#2a4060'>"
            +"SHIFT+click inside shapes<br>to select them for boolean ops.</span></html>",10,Font.PLAIN,FG_DIM);
        desc.setAlignmentX(LEFT_ALIGNMENT); p.add(desc); p.add(gap(8));
        boolCountLabel.setAlignmentX(LEFT_ALIGNMENT); p.add(boolCountLabel); p.add(gap(10));

        // INTERSECT
        p.add(sec2("INTERSECT",new Color(255,180,30)));
        p.add(lbl2("<html><span style='color:#2a4060'>Finds crossing points<br>between 2 selected shapes.</span></html>"));
        p.add(gap(5));
        JButton intBtn=actionBtn("FIND INTERSECTIONS",new Color(140,100,0));
        intBtn.addActionListener(e->canvas.boolIntersect()); intBtn.setAlignmentX(LEFT_ALIGNMENT); p.add(intBtn);
        p.add(gap(3));
        JButton clrInt=actionBtn("CLEAR HIGHLIGHTS",BG2); clrInt.setForeground(FG_DIM);
        clrInt.addActionListener(e->canvas.clearIntersections()); clrInt.setAlignmentX(LEFT_ALIGNMENT); p.add(clrInt);
        p.add(gap(12));

        // SUBTRACT
        p.add(sec2("SUBTRACT",new Color(215,60,60)));
        p.add(lbl2("<html><span style='color:#2a4060'>Removes edges of shape B<br>that cross into shape A.</span></html>"));
        p.add(gap(5));
        JButton subBtn=actionBtn("SUBTRACT",new Color(140,40,40));
        subBtn.addActionListener(e->canvas.boolSubtract()); subBtn.setAlignmentX(LEFT_ALIGNMENT); p.add(subBtn);
        p.add(gap(12));

        // ADD
        p.add(sec2("ADD / UNION",new Color(60,200,120)));
        p.add(lbl2("<html><span style='color:#2a4060'>Connects nearest nodes<br>of two selected shapes.</span></html>"));
        p.add(gap(5));
        JButton addBtn=actionBtn("ADD / UNION",new Color(0,100,60));
        addBtn.addActionListener(e->canvas.boolAdd()); addBtn.setAlignmentX(LEFT_ALIGNMENT); p.add(addBtn);

        p.add(Box.createVerticalGlue());
        p.add(lbl("<html><span style='color:#1a2a3a'>SHIFT+click inside shape<br>to select it for boolean ops.</span></html>",9,Font.PLAIN,FG_DIM));
        return p;
    }

    // ── EXTEND card ───────────────────────────────────────────────────────────

    private JPanel buildExtendCard() {
        JPanel p=panel();
        p.add(sec("◈ LINE EXTEND")); p.add(gap(8));
        JLabel d=lbl("<html><span style='color:#3a5a7a'>"
            +"Click 2 edges on canvas<br>then PREDICT to see where<br>"
            +"they'd meet if extended.<br><br>"
            +"Activates EXTEND mode<br>automatically.</span></html>",10,Font.PLAIN,FG_DIM);
        d.setAlignmentX(LEFT_ALIGNMENT); p.add(d); p.add(gap(12));
        JButton pred=actionBtn("PREDICT INTERSECTION",new Color(0,95,130));
        pred.addActionListener(e->canvas.triggerExtend()); pred.setAlignmentX(LEFT_ALIGNMENT); p.add(pred);
        p.add(gap(6));
        JButton clr=actionBtn("CLEAR",BG2); clr.setForeground(FG_DIM);
        clr.addActionListener(e->{ canvas.setModeOff(); canvas.toggleMode(WorkspacePanel.Mode.EXTEND); });
        clr.setAlignmentX(LEFT_ALIGNMENT); p.add(clr);
        p.add(Box.createVerticalGlue());
        return p;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JPanel panel(){JPanel p=new JPanel();p.setBackground(BG);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(new EmptyBorder(10,10,10,10));return p;}
    private Component gap(int h){return Box.createVerticalStrut(h);}

    private JButton tabBtn(String text){
        JButton b=new JButton(text);b.setFont(new Font("Consolas",Font.BOLD,10));
        b.setForeground(FG_DIM);b.setBackground(new Color(8,10,18));
        b.setBorderPainted(false);b.setFocusPainted(false);b.setOpaque(true);
        b.setPreferredSize(new Dimension(0,28));return b;
    }
    private void activateTab(JButton active){
        for(Component c:active.getParent().getComponents()){JButton b=(JButton)c;b.setBackground(new Color(8,10,18));b.setForeground(FG_DIM);}
        active.setBackground(TAB_ACT);active.setForeground(ACCENT);
    }
    private JButton shapeTypeBtn(ShapeType type){
        JButton b=new JButton(type.displayName+"  ["+type.requiredNodes+"]");
        b.setFont(new Font("Consolas",Font.PLAIN,10));b.setForeground(FG);b.setBackground(BG2);
        b.setBorderPainted(false);b.setFocusPainted(false);b.setOpaque(true);
        b.setHorizontalAlignment(SwingConstants.LEFT);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,24));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e->{selectedShape=type;highlightSelected();updateShapeCount(canvas.getShapeNodes().size());});
        return b;
    }
    private void highlightSelected(){
        ShapeType[] types=ShapeType.values();
        for(int i=0;i<types.length;i++){boolean s=types[i]==selectedShape;shapeButtons[i].setBackground(s?SEL:BG2);shapeButtons[i].setForeground(s?ACCENT:FG);}
    }
    private JButton actionBtn(String text,Color bg){
        JButton b=new JButton(text);b.setFont(new Font("Consolas",Font.BOLD,10));
        b.setForeground(Color.WHITE);b.setBackground(bg);
        b.setBorderPainted(false);b.setFocusPainted(false);b.setOpaque(true);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    private JLabel lbl(String text,int size,int style,Color color){JLabel l=new JLabel(text);l.setFont(new Font("Consolas",style,size));l.setForeground(color);return l;}
    private JLabel lbl2(String text){JLabel l=new JLabel(text);l.setFont(new Font("Consolas",Font.PLAIN,9));l.setAlignmentX(LEFT_ALIGNMENT);return l;}
    private JLabel sec(String text){JLabel l=new JLabel(text);l.setFont(new Font("Consolas",Font.BOLD,11));l.setForeground(ACCENT);l.setAlignmentX(LEFT_ALIGNMENT);return l;}
    private JLabel sec2(String text,Color col){JLabel l=new JLabel(text);l.setFont(new Font("Consolas",Font.BOLD,10));l.setForeground(col);l.setAlignmentX(LEFT_ALIGNMENT);return l;}
}