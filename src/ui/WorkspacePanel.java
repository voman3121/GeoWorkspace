package ui;

import db.dao.EdgeDAO;
import db.dao.NodeDAO;
import db.dao.ShapeDAO;
import model.Edge;
import model.Node;
import model.Shape;
import ui.ShapeValidator.ShapeType;
import ui.ShapeValidator.ValidationResult;
import ui.UndoManager.Operation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WorkspacePanel extends JPanel {

    // ── palette ───────────────────────────────────────────────────────────────
    private static final Color BG          = new Color(6,   8,  16);
    private static final Color GRID_DOT    = new Color(28,  38,  62);
    private static final Color GRID_MAJOR  = new Color(18,  26,  46);
    private static final Color AXIS_COL    = new Color(22,  52, 100, 160);
    private static final Color NODE_FILL   = new Color(0,  185, 255);
    private static final Color NODE_RING   = new Color(60, 210, 255);
    private static final Color NODE_SEL1   = new Color(255, 195,  25);
    private static final Color NODE_SHAPE  = new Color(60,  255, 150);
    private static final Color NODE_HOVER  = new Color(130, 225, 255);
    private static final Color NODE_MSEL   = new Color(255, 100, 100);
    private static final Color EDGE_COL    = new Color(0,  140, 195, 190);
    private static final Color EDGE_HOVER  = new Color(255, 115,  25, 235);
    private static final Color EDGE_MSEL   = new Color(255, 100, 100, 220);
    private static final Color LABEL_COL   = new Color(140, 200, 245);
    private static final Color SHAPE_FILL  = new Color(0,  140, 255,  20);
    private static final Color SHAPE_HOV   = new Color(0,  200, 255,  50);
    private static final Color SHAPE_SEL_C = new Color(255, 200,  30,  60);
    private static final Color INTERSECT_C = new Color(255, 220,  50, 200);
    private static final Color PREDICT_COL = new Color(120, 255, 120, 180);
    private static final Color CIRCLE_COL  = new Color(0,  200, 255, 200);
    private static final Color ARC_COL     = new Color(180, 100, 255, 220);

    private static final double NODE_R    = 3.5;
    private static final double HIT_R     = 10.0;
    private static final float  EDGE_HIT  = 6f;
    private static final int    GRID_STEP = 40;

    // ── data ──────────────────────────────────────────────────────────────────
    private final List<Node>  nodes  = new ArrayList<>();
    private final List<Edge>  edges  = new ArrayList<>();
    private final List<Shape> shapes = new ArrayList<>();

    private final NodeDAO  nodeDAO  = new NodeDAO();
    private final EdgeDAO  edgeDAO  = new EdgeDAO();
    private final ShapeDAO shapeDAO = new ShapeDAO();
    private final UndoManager undoManager = new UndoManager();

    // ── modes ─────────────────────────────────────────────────────────────────
    public enum Mode { MULTI_SELECT, SHAPE_SELECT, EXTEND, CIRCLE }
    private Mode activeMode = null;

    // ── interaction state ─────────────────────────────────────────────────────
    private Node        edgeStart   = null;
    private List<Node>  shapeNodes  = new ArrayList<>();   // ordered node selection for shape creation
    private List<Node>  multiNodes  = new ArrayList<>();
    private List<Edge>  multiEdges  = new ArrayList<>();
    private List<Shape> boolShapes  = new ArrayList<>();   // shapes selected for boolean ops

    // extend
    private List<Edge> extendEdges = new ArrayList<>();
    private Point2D    extendPt    = null;

    // circle drawing — press to set center, drag/move, release to commit
    private Point2D circleCenter  = null;
    private Point2D circleCurrent = null;
    private boolean circlePressed = false;

    // intersections overlay
    private List<Point2D> intersectPts = new ArrayList<>();

    // hover
    private Node  hoveredNode  = null;
    private Edge  hoveredEdge  = null;
    private Shape hoveredShape = null;

    private RightPanel rightPanel;

    // ── viewport ──────────────────────────────────────────────────────────────
    private double camX=0,camY=0,zoom=1.0,rotateDeg=0;
    private boolean rmbDragging=false;
    private int rmbStartX=0; private double rmbStartRot=0;
    private int lmbPressX=0, lmbPressY=0;
    private boolean lmbDragged=false;

    private Timer syncTimer;

    // ── constructor ───────────────────────────────────────────────────────────
    public WorkspacePanel() {
        setBackground(BG);
        setFocusable(true);
        loadFromDB();
        attachListeners();
        startSyncTimer();
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setInitialDelay(300);
        ToolTipManager.sharedInstance().setDismissDelay(7000);
    }

    public void setRightPanel(RightPanel rp) { this.rightPanel=rp; }
    public UndoManager getUndoManager()       { return undoManager; }
    public List<Node>  getShapeNodes()        { return shapeNodes; }
    public List<Shape> getBoolShapes()        { return boolShapes; }
    public List<Shape> getAllShapes()         { return shapes; }

    // ── DB ────────────────────────────────────────────────────────────────────
    public void loadFromDB() {
        try {
            nodes.clear(); edges.clear(); shapes.clear();
            nodes.addAll(nodeDAO.getAll());
            edges.addAll(edgeDAO.getAll());
            shapes.addAll(shapeDAO.getAll());
        } catch(Exception e){ System.err.println("[DB] "+e.getMessage()); }
    }

    private void startSyncTimer() {
        syncTimer=new Timer(1500,e->{ loadFromDB(); repaint(); });
        syncTimer.start();
    }

    // ── mode management ───────────────────────────────────────────────────────
    public void toggleMode(Mode m) {
        activeMode=(activeMode==m)?null:m;
        edgeStart=null;
        if(activeMode!=Mode.MULTI_SELECT)  { multiNodes.clear(); multiEdges.clear(); }
        if(activeMode!=Mode.SHAPE_SELECT)  { shapeNodes.clear(); notifyShapeCount(); }
        if(activeMode!=Mode.EXTEND)        { extendEdges.clear(); extendPt=null; }
        if(activeMode!=Mode.CIRCLE)        { circleCenter=null; circleCurrent=null; circlePressed=false; }
        repaint();
    }
    public void setModeOff(){ activeMode=null; edgeStart=null; repaint(); }
    public Mode getActiveMode(){ return activeMode; }
    private void notifyShapeCount(){ if(rightPanel!=null) rightPanel.updateShapeCount(shapeNodes.size()); }

    // ── paint ─────────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        AffineTransform saved=g2.getTransform();
        applyViewport(g2);
        drawGrid(g2);
        drawAxes(g2);
        drawShapeFills(g2);
        drawEdges(g2);
        drawNodes(g2);
        drawIntersections(g2);
        drawExtendPredict(g2);
        drawCirclePreview(g2);
        g2.setTransform(saved);
        drawHUD(g2);
    }

    private void applyViewport(Graphics2D g2) {
        int cx=getWidth()/2,cy=getHeight()/2;
        g2.translate(cx,cy);
        g2.rotate(Math.toRadians(rotateDeg));
        g2.scale(zoom,zoom);
        g2.translate(-camX,-camY);
    }

    // ── grid ──────────────────────────────────────────────────────────────────
    private void drawGrid(Graphics2D g2) {
        double hw=(getWidth()/2.0)/zoom+GRID_STEP*3, hh=(getHeight()/2.0)/zoom+GRID_STEP*3;
        int x0=(int)(((camX-hw)/GRID_STEP)-1)*GRID_STEP, x1=(int)(((camX+hw)/GRID_STEP)+1)*GRID_STEP;
        int y0=(int)(((camY-hh)/GRID_STEP)-1)*GRID_STEP, y1=(int)(((camY+hh)/GRID_STEP)+1)*GRID_STEP;
        g2.setColor(GRID_DOT); g2.setStroke(new BasicStroke(0.4f));
        for(int gx=x0;gx<=x1;gx+=GRID_STEP) for(int gy=y0;gy<=y1;gy+=GRID_STEP){
            g2.draw(new Line2D.Double(gx-1,gy,gx+1,gy)); g2.draw(new Line2D.Double(gx,gy-1,gx,gy+1));
        }
        int major=GRID_STEP*5;
        g2.setColor(GRID_MAJOR); g2.setStroke(new BasicStroke(0.5f));
        for(int gx=(int)(((camX-hw)/major)-1)*major;gx<=x1;gx+=major) g2.draw(new Line2D.Double(gx,y0,gx,y1));
        for(int gy=(int)(((camY-hh)/major)-1)*major;gy<=y1;gy+=major) g2.draw(new Line2D.Double(x0,gy,x1,gy));
    }

    private void drawAxes(Graphics2D g2) {
        g2.setColor(AXIS_COL); g2.setStroke(new BasicStroke(0.8f));
        g2.draw(new Line2D.Double(-99999,0,99999,0));
        g2.draw(new Line2D.Double(0,-99999,0,99999));
        g2.setColor(new Color(0,180,255,90));
        g2.fill(new Ellipse2D.Double(-2,-2,4,4));
    }

    // ── shape fills (CRITICAL: polygon, circle, arc, semicircle) ─────────────
    private void drawShapeFills(Graphics2D g2) {
        for(Shape s:shapes){
            boolean hov=hoveredShape!=null&&hoveredShape.getId()==s.getId();
            boolean sel=boolShapes.stream().anyMatch(b->b.getId()==s.getId());
            String type=s.getShapeType();
            switch(type){
                case "Circle"     -> drawCircleShape(g2,s,hov,sel);
                case "Arc"        -> drawArcShape(g2,s,hov,sel);
                case "Semi-circle"-> drawSemicircleShape(g2,s,hov,sel);
                default           -> drawPolygonShape(g2,s,hov,sel);
            }
        }
    }

    private void drawPolygonShape(Graphics2D g2,Shape s,boolean hov,boolean sel){
        Path2D path=buildPolygonPath(s);
        if(path==null) return;
        g2.setColor(sel?SHAPE_SEL_C:hov?SHAPE_HOV:SHAPE_FILL);
        g2.fill(path);
        g2.setColor(new Color(0,160,255,90));
        g2.setStroke(new BasicStroke(0.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g2.draw(path);
    }

    private void drawCircleShape(Graphics2D g2,Shape s,boolean hov,boolean sel){
        String[]parts=s.getExtraData().split(",");
        if(parts.length<3) return;
        try{
            double cx=Double.parseDouble(parts[0].trim());
            double cy=Double.parseDouble(parts[1].trim());
            double r =Double.parseDouble(parts[2].trim());
            g2.setColor(sel?SHAPE_SEL_C:hov?SHAPE_HOV:SHAPE_FILL);
            g2.fill(new Ellipse2D.Double(cx-r,cy-r,r*2,r*2));
            g2.setColor(hov?new Color(0,230,255,230):CIRCLE_COL);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(cx-r,cy-r,r*2,r*2));
            // cross at center
            g2.setStroke(new BasicStroke(0.6f));
            g2.draw(new Line2D.Double(cx-4,cy,cx+4,cy));
            g2.draw(new Line2D.Double(cx,cy-4,cx,cy+4));
        }catch(NumberFormatException ignored){}
    }

    private void drawArcShape(Graphics2D g2,Shape s,boolean hov,boolean sel){
        long[]ids=s.getNodeIds();
        if(ids.length<3) return;
        Node n0=findById(ids[0]),n1=findById(ids[1]),n2=findById(ids[2]);
        if(n0==null||n1==null||n2==null) return;
        // Quadratic Bezier: n0=start, n1=control, n2=end
        Path2D arc=new Path2D.Double();
        arc.moveTo(n0.getX(),n0.getY());
        arc.quadTo(n1.getX(),n1.getY(),n2.getX(),n2.getY());
        g2.setColor(hov?new Color(200,120,255,240):ARC_COL);
        g2.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g2.draw(arc);
        // show control point dashed line
        g2.setColor(new Color(150,80,200,80));
        float[]dash={3f,4f};
        g2.setStroke(new BasicStroke(0.7f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,dash,0f));
        g2.draw(new Line2D.Double(n0.getX(),n0.getY(),n1.getX(),n1.getY()));
        g2.draw(new Line2D.Double(n1.getX(),n1.getY(),n2.getX(),n2.getY()));
    }

    private void drawSemicircleShape(Graphics2D g2,Shape s,boolean hov,boolean sel){
        String[] parts=s.getExtraData().split(",");
        if(parts.length<4) return;
        double cx,cy,r,startAngle;
        try{
            cx=Double.parseDouble(parts[0].trim());
            cy=Double.parseDouble(parts[1].trim());
            r =Double.parseDouble(parts[2].trim());
            startAngle=Double.parseDouble(parts[3].trim());
        }catch(NumberFormatException ex){ return; }

        // Semi-circle = exactly half the circle, starting at startAngle and sweeping 180°
        Arc2D arc=new Arc2D.Double(cx-r,cy-r,r*2,r*2,-startAngle,-180,Arc2D.CHORD);
        g2.setColor(sel?SHAPE_SEL_C:hov?SHAPE_HOV:SHAPE_FILL);
        g2.fill(arc);
        g2.setColor(hov?new Color(0,230,255,230):CIRCLE_COL);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(arc);

        // diameter line — the chord that closes the semicircle
        double rad1=Math.toRadians(startAngle), rad2=Math.toRadians(startAngle+180);
        double x1=cx+r*Math.cos(rad1), y1=cy+r*Math.sin(rad1);
        double x2=cx+r*Math.cos(rad2), y2=cy+r*Math.sin(rad2);
        g2.setColor(new Color(0,160,220,120));
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(new Line2D.Double(x1,y1,x2,y2));
        // center marker
        g2.setStroke(new BasicStroke(0.6f));
        g2.draw(new Line2D.Double(cx-4,cy,cx+4,cy));
        g2.draw(new Line2D.Double(cx,cy-4,cx,cy+4));
    }

    private Path2D buildPolygonPath(Shape s){
        long[]ids=s.getNodeIds();
        if(ids.length<3) return null;
        Path2D path=new Path2D.Double(); boolean first=true;
        for(long id:ids){Node n=findById(id);if(n==null)return null;
            if(first){path.moveTo(n.getX(),n.getY());first=false;}else path.lineTo(n.getX(),n.getY());}
        path.closePath(); return path;
    }

    // ── circle preview while drawing ──────────────────────────────────────────
    private void drawCirclePreview(Graphics2D g2){
        if(activeMode!=Mode.CIRCLE||circleCenter==null||circleCurrent==null) return;
        double r=Math.hypot(circleCurrent.getX()-circleCenter.getX(),circleCurrent.getY()-circleCenter.getY());
        if(r<1) return;
        g2.setColor(new Color(0,200,255,35));
        g2.fill(new Ellipse2D.Double(circleCenter.getX()-r,circleCenter.getY()-r,r*2,r*2));
        g2.setColor(CIRCLE_COL);
        g2.setStroke(new BasicStroke(1.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g2.draw(new Ellipse2D.Double(circleCenter.getX()-r,circleCenter.getY()-r,r*2,r*2));
        g2.fill(new Ellipse2D.Double(circleCenter.getX()-2.5,circleCenter.getY()-2.5,5,5));
        // radius label
        g2.setColor(LABEL_COL);
        AffineTransform old=g2.getTransform();
        g2.translate(circleCenter.getX()+r*0.5,circleCenter.getY()-6/zoom);
        g2.scale(1.0/zoom,1.0/zoom);
        g2.setFont(new Font("Consolas",Font.PLAIN,10));
        g2.drawString(String.format("r=%.0f",r),0,0);
        g2.setTransform(old);
    }

    // ── edges ─────────────────────────────────────────────────────────────────
    private void drawEdges(Graphics2D g2){
        for(Edge e:edges){
            Node a=findById(e.getNodeAId()),b=findById(e.getNodeBId());
            if(a==null||b==null) continue;
            boolean hov=hoveredEdge!=null&&e.getId()==hoveredEdge.getId();
            boolean msel=multiEdges.stream().anyMatch(me->me.getId()==e.getId());
            boolean ext=extendEdges.stream().anyMatch(ee->ee.getId()==e.getId());
            Color col=msel?EDGE_MSEL:ext?PREDICT_COL:hov?EDGE_HOVER:EDGE_COL;
            g2.setColor(col);
            g2.setStroke(new BasicStroke(msel||ext?2.0f:hov?1.8f:1.0f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(a.getX(),a.getY(),b.getX(),b.getY()));
            double mx=(a.getX()+b.getX())/2,my=(a.getY()+b.getY())/2;
            g2.setColor(hov?EDGE_HOVER:new Color(0,155,200,90));
            g2.fill(new Ellipse2D.Double(mx-1.2,my-1.2,2.4,2.4));
        }
    }

    // ── nodes ─────────────────────────────────────────────────────────────────
    private void drawNodes(Graphics2D g2){
        for(Node n:nodes){
            double cx=n.getX(),cy=n.getY();
            boolean isSel=edgeStart!=null&&edgeStart.getId()==n.getId();
            boolean isSh=shapeNodes.stream().anyMatch(s->s.getId()==n.getId());
            boolean isMSel=multiNodes.stream().anyMatch(s->s.getId()==n.getId());
            boolean isHov=hoveredNode!=null&&hoveredNode.getId()==n.getId();
            Color fill=isMSel?NODE_MSEL:isSel?NODE_SEL1:isSh?NODE_SHAPE:isHov?NODE_HOVER:NODE_FILL;
            if(isSel||isSh||isMSel||isHov){
                g2.setColor(new Color(fill.getRed(),fill.getGreen(),fill.getBlue(),40));
                double gr=NODE_R+4; g2.fill(new Ellipse2D.Double(cx-gr,cy-gr,gr*2,gr*2));
            }
            RadialGradientPaint rg=new RadialGradientPaint(
                    (float)(cx-NODE_R*0.3f),(float)(cy-NODE_R*0.3f),(float)(NODE_R*2.2f),
                    new float[]{0f,1f},new Color[]{fill.brighter(),fill.darker().darker()});
            g2.setPaint(rg);
            g2.fill(new Ellipse2D.Double(cx-NODE_R,cy-NODE_R,NODE_R*2,NODE_R*2));
            g2.setPaint(NODE_RING); g2.setStroke(new BasicStroke(0.8f));
            g2.draw(new Ellipse2D.Double(cx-NODE_R,cy-NODE_R,NODE_R*2,NODE_R*2));
            g2.setColor(LABEL_COL);
            float fs=(float)Math.max(6.0,Math.min(10.0,9.0/zoom));
            g2.setFont(new Font("Consolas",Font.PLAIN,(int)fs));
            FontMetrics fm=g2.getFontMetrics();
            String lbl=n.getLabel()!=null?n.getLabel():"?";
            AffineTransform old=g2.getTransform();
            g2.translate(cx,cy-NODE_R-3.0/zoom); g2.scale(1.0/zoom,1.0/zoom);
            g2.drawString(lbl,-fm.stringWidth(lbl)/2f,0);
            g2.setTransform(old);
        }
    }

    // ── overlays ──────────────────────────────────────────────────────────────
    private void drawIntersections(Graphics2D g2){
        if (intersectArea != null) {
            g2.setColor(new Color(255,220,50,90));
            g2.fill(intersectArea);
            g2.setColor(INTERSECT_C);
            g2.setStroke(new BasicStroke(2.0f));
            g2.draw(intersectArea);
        }
        for(Point2D pt:intersectPts){
            double r=5.0;
            g2.setColor(new Color(255,220,50,50));
            g2.fill(new Ellipse2D.Double(pt.getX()-r*2,pt.getY()-r*2,r*4,r*4));
            g2.setColor(INTERSECT_C); g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(pt.getX()-r,pt.getY()-r,r*2,r*2));
            g2.draw(new Line2D.Double(pt.getX()-r*1.5,pt.getY(),pt.getX()+r*1.5,pt.getY()));
            g2.draw(new Line2D.Double(pt.getX(),pt.getY()-r*1.5,pt.getX(),pt.getY()+r*1.5));
        }
    }

    private void drawExtendPredict(Graphics2D g2){
        if(extendPt==null) return;
        float[]dash={4f,4f};
        g2.setStroke(new BasicStroke(0.8f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,dash,0f));
        g2.setColor(PREDICT_COL);
        for(Edge e:extendEdges){Node a=findById(e.getNodeAId()),b=findById(e.getNodeBId());
            if(a!=null&&b!=null) g2.draw(new Line2D.Double(b.getX(),b.getY(),extendPt.getX(),extendPt.getY()));}
        double r=6.0; g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Ellipse2D.Double(extendPt.getX()-r,extendPt.getY()-r,r*2,r*2));
        g2.draw(new Line2D.Double(extendPt.getX()-r*1.6,extendPt.getY(),extendPt.getX()+r*1.6,extendPt.getY()));
        g2.draw(new Line2D.Double(extendPt.getX(),extendPt.getY()-r*1.6,extendPt.getX(),extendPt.getY()+r*1.6));
    }

    // ── HUD ───────────────────────────────────────────────────────────────────
    private void drawHUD(Graphics2D g2){
        g2.setFont(new Font("Consolas",Font.PLAIN,11));
        g2.setColor(new Color(40,80,140,210));
        g2.drawString(String.format("zoom %.2fx  rot %.1f°  nodes %d  edges %d  shapes %d",
                zoom,rotateDeg,nodes.size(),edges.size(),shapes.size()),12,getHeight()-12);

        String banner=null; Color bc=Color.WHITE;
        if(activeMode==Mode.MULTI_SELECT){ banner="MULTI-SELECT — click nodes/edges  •  Del=delete  •  click button again to exit"; bc=NODE_MSEL; }
        else if(activeMode==Mode.SHAPE_SELECT){ banner="SHAPE-SELECT — click nodes in order ("+shapeNodes.size()+" selected)  •  click button again to exit"; bc=NODE_SHAPE; }
        else if(activeMode==Mode.EXTEND){ banner="EXTEND — click 2 edges ("+extendEdges.size()+"/2)  •  click button again to exit"; bc=PREDICT_COL; }
        else if(activeMode==Mode.CIRCLE){ banner=circleCenter==null?"CIRCLE — click & drag to set center and radius":"CIRCLE — release to place circle"; bc=CIRCLE_COL; }
        else if(edgeStart!=null){ banner="Click second node to draw edge  •  RMB=cancel"; bc=NODE_SEL1; }
        if(banner!=null){ g2.setColor(bc); g2.setFont(new Font("Consolas",Font.BOLD,11)); g2.drawString(banner,12,22); }

        String ud=undoManager.peekUndo(),rd=undoManager.peekRedo();
        g2.setFont(new Font("Consolas",Font.PLAIN,10)); g2.setColor(new Color(50,100,160,180));
        if(ud!=null) g2.drawString("Ctrl+Z: undo "+ud,12,getHeight()-26);
        if(rd!=null) g2.drawString("Ctrl+Y: redo "+rd,ud!=null?240:12,getHeight()-26);
    }

    // ── tooltip ───────────────────────────────────────────────────────────────
    @Override
    public String getToolTipText(MouseEvent e){
        Point2D w=toWorld(e.getX(),e.getY());
        Node n=findNodeAt(w);
        if(n!=null) return String.format(
            "<html><b style='color:#00c8ff'>%s</b> id=%d<br>Pos:(%.0f,%.0f)<br>Degree:%d/4<br>Adj:%s</html>",
            n.getLabel(),n.getId(),n.getX(),n.getY(),n.degree(),adjLabels(n));
        Edge edge=findEdgeAt(w);
        if(edge!=null){Node a=findById(edge.getNodeAId()),b=findById(edge.getNodeBId());
            if(a!=null&&b!=null) return String.format(
                "<html><b style='color:#ff8020'>Edge</b> id=%d<br>%s\u2194%s<br>Length:%.1f</html>",
                edge.getId(),a.getLabel(),b.getLabel(),edge.getLength());}
        Shape sh=findShapeAt(w);
        if(sh!=null) return String.format(
            "<html><b style='color:#60ff96'>%s</b> id=%d<br>Type:%s<br>Nodes:%d<br>Area:%.1f<br>Perimeter:%.1f</html>",
            sh.getLabel(),sh.getId(),sh.getShapeType(),sh.getNodeIds().length,sh.getArea(),sh.getPerimeter());
        return null;
    }

    private String adjLabels(Node n){
        List<String> out=new ArrayList<>();
        for(long id:n.adjacentIds()){Node nb=findById(id);out.add(nb!=null?nb.getLabel():"#"+id);}
        return out.isEmpty()?"none":String.join(", ",out);
    }

    // ── mouse listeners ───────────────────────────────────────────────────────
    private void attachListeners(){
        MouseAdapter ma=new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){
                requestFocusInWindow();
                if(SwingUtilities.isRightMouseButton(e)){ rmbStartX=e.getX(); rmbStartRot=rotateDeg; rmbDragging=false; }
                if(SwingUtilities.isLeftMouseButton(e)){
                    lmbPressX=e.getX(); lmbPressY=e.getY(); lmbDragged=false;
                    if(activeMode==Mode.CIRCLE&&!circlePressed){
                        circleCenter=toWorld(e.getX(),e.getY());
                        circleCurrent=circleCenter;
                        circlePressed=true;
                        repaint();
                    }
                }
            }
            @Override public void mouseDragged(MouseEvent e){
                if(SwingUtilities.isRightMouseButton(e)){
                    int dx=e.getX()-rmbStartX;
                    if(Math.abs(dx)>4) rmbDragging=true;
                    if(rmbDragging){ rotateDeg=rmbStartRot+dx*0.30; repaint(); }
                }
                if(SwingUtilities.isLeftMouseButton(e)){
                    int dx=e.getX()-lmbPressX, dy=e.getY()-lmbPressY;
                    if(Math.abs(dx)>4||Math.abs(dy)>4) lmbDragged=true;
                    if(activeMode==Mode.CIRCLE&&circlePressed){
                        circleCurrent=toWorld(e.getX(),e.getY()); repaint();
                    }
                }
            }
            @Override public void mouseReleased(MouseEvent e){
                if(SwingUtilities.isRightMouseButton(e)&&!rmbDragging) showContextMenu(e);
                rmbDragging=false;
                if(SwingUtilities.isLeftMouseButton(e)){
                    if(activeMode==Mode.CIRCLE&&circlePressed){
                        circleCurrent=toWorld(e.getX(),e.getY());
                        commitCircle(); repaint();
                    } else if(!lmbDragged){
                        // Fire click logic on release — avoids mouseClicked being
                        // suppressed by tiny mouse movement between press and release
                        handleLeftClick(e);
                    }
                }
            }
            @Override public void mouseClicked(MouseEvent e){
                // Intentionally empty — all LMB logic moved to mouseReleased above
                // to fix the "click not registering if mouse moves slightly" bug.
            }
            @Override public void mouseMoved(MouseEvent e){
                Point2D w=toWorld(e.getX(),e.getY());
                Node pn=hoveredNode; Edge pe=hoveredEdge; Shape ps=hoveredShape;
                hoveredNode=findNodeAt(w);
                hoveredEdge=hoveredNode==null?findEdgeAt(w):null;
                hoveredShape=hoveredNode==null&&hoveredEdge==null?findShapeAt(w):null;
                if(hoveredNode!=pn||hoveredEdge!=pe||hoveredShape!=ps) repaint();
                if(activeMode==Mode.CIRCLE&&circleCenter!=null){ circleCurrent=w; repaint(); }
                setCursor((hoveredNode!=null||hoveredEdge!=null||hoveredShape!=null)
                        ?Cursor.getPredefinedCursor(Cursor.HAND_CURSOR):Cursor.getDefaultCursor());
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e){
                double amt=e.getPreciseWheelRotation(); if(amt==0) return;
                double factor=amt<0?1.12:0.89,prev=zoom;
                zoom=Math.max(0.002,Math.min(300.0/GRID_STEP,zoom*factor));
                if(zoom!=prev){Point2D w1=toWorldZ(e.getX(),e.getY(),prev),w2=toWorld(e.getX(),e.getY());
                    camX+=w1.getX()-w2.getX(); camY+=w1.getY()-w2.getY(); repaint();}
            }
        };
        addMouseListener(ma); addMouseMotionListener(ma); addMouseWheelListener(ma);

        // Key bindings — WHEN_IN_FOCUSED_WINDOW so they fire without click-to-focus
        InputMap im=getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am=getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK),"undo");
        am.put("undo",new AbstractAction(){ public void actionPerformed(ActionEvent e){ undoManager.undo();loadFromDB();repaint(); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y,InputEvent.CTRL_DOWN_MASK),"redo");
        am.put("redo",new AbstractAction(){ public void actionPerformed(ActionEvent e){ undoManager.redo();loadFromDB();repaint(); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0),"del");
        am.put("del",new AbstractAction(){ public void actionPerformed(ActionEvent e){ deleteMultiSelection(); }});
    }

    // ── left click ────────────────────────────────────────────────────────────
    private void handleLeftClick(MouseEvent e){
        Point2D w=toWorld(e.getX(),e.getY());
        boolean shift=(e.getModifiersEx()&InputEvent.SHIFT_DOWN_MASK)!=0;
        Node  hn=findNodeAt(w);
        Edge  he=hn==null?findEdgeAt(w):null;
        Shape hs=hn==null&&he==null?findShapeAt(w):null;

        // SHIFT+click anywhere = multi-select toggle regardless of mode
        if(shift){
            if(hn!=null)      toggleNode(multiNodes,hn);
            else if(he!=null) toggleEdge(multiEdges,he);
            else if(hs!=null){ toggleShape(boolShapes,hs); if(rightPanel!=null) rightPanel.updateBoolCount(boolShapes.size()); }
            repaint(); return;
        }

        if(activeMode==Mode.MULTI_SELECT){
            if(hn!=null) toggleNode(multiNodes,hn);
            else if(he!=null) toggleEdge(multiEdges,he);
            repaint(); return;
        }

        if(activeMode==Mode.SHAPE_SELECT){
            if(hn!=null){
                toggleNode(shapeNodes,hn); notifyShapeCount();
            } else if(hs!=null){
                // Clicking INSIDE a shape's region loads all its nodes at once
                // (also toggles it into the boolean selection so both workflows share state)
                shapeNodes.clear();
                for (long id : hs.getNodeIds()) {
                    Node nb = findById(id);
                    if (nb != null) shapeNodes.add(nb);
                }
                toggleShape(boolShapes, hs);
                notifyShapeCount();
                if (rightPanel != null) rightPanel.updateBoolCount(boolShapes.size());
            }
            repaint(); return;
        }

        if(activeMode==Mode.EXTEND){
            if(he!=null&&extendEdges.stream().noneMatch(ex->ex.getId()==he.getId())){
                extendEdges.add(he);
                if(extendEdges.size()==2) computeExtend();
            }
            repaint(); return;
        }

        // Boolean shape selection — clicking inside a shape when bool tab active
        if(hs!=null&&rightPanel!=null&&rightPanel.isBoolTabActive()){
            toggleShape(boolShapes,hs);
            rightPanel.updateBoolCount(boolShapes.size());
            repaint(); return;
        }

        // Normal mode
        try{
            if(hn==null&&he==null&&hs==null){
                double wx=snapD(w.getX()),wy=snapD(w.getY());
                String label="N"+(nodes.size()+1);
                Node n=nodeDAO.insert(new Node(wx,wy,label)); nodes.add(n);
                edgeStart=null;
                final long nid=n.getId(); final double fnx=wx,fny=wy; final String fl=label;
                undoManager.push(new Operation("place "+label,
                    ()->{ try{nodeDAO.delete(nid);loadFromDB();repaint();}catch(Exception ex){ex.printStackTrace();} },
                    ()->{ try{nodeDAO.insertWithId(nid,fnx,fny,fl);loadFromDB();repaint();}catch(Exception ex){ex.printStackTrace();} }));
            } else if(hn!=null){
                if(edgeStart==null) edgeStart=hn;
                else if(edgeStart.getId()==hn.getId()) edgeStart=null;
                else{ buildEdge(edgeStart,hn); edgeStart=null; }
            }
        }catch(Exception ex){
            JOptionPane.showMessageDialog(this,ex.getMessage(),"Error",JOptionPane.WARNING_MESSAGE);
        }
        repaint();
    }

    // ── circle commit ─────────────────────────────────────────────────────────
    private void commitCircle(){
        circlePressed=false;
        if(circleCenter==null||circleCurrent==null){ circleCenter=null; return; }
        double r=Math.hypot(circleCurrent.getX()-circleCenter.getX(),circleCurrent.getY()-circleCenter.getY());
        if(r<2){ circleCenter=null; circleCurrent=null; return; }
        double cx=circleCenter.getX(),cy=circleCenter.getY();

        // Ask whether this is a full circle or a semicircle — both now use
        // the same center+radius parameters from the drag gesture.
        Object[] options = {"Full Circle", "Semi-circle"};
        int choice = JOptionPane.showOptionDialog(this,
            String.format("Center=(%.0f, %.0f)  Radius≈%.1f\n\nCreate as:", cx, cy, r),
            "Confirm Shape", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);

        if (choice == JOptionPane.CLOSED_OPTION) {
            circleCenter=null; circleCurrent=null; activeMode=null;
            if(rightPanel!=null) rightPanel.clearModeButtons();
            repaint(); return;
        }

        try {
            if (choice == 1) {
                // Semi-circle: store start-angle too so we know which half to draw.
                // start-angle is taken from the drag direction (angle from center to release point).
                double startAngle = Math.toDegrees(Math.atan2(circleCurrent.getY()-cy, circleCurrent.getX()-cx));
                String extra = cx+","+cy+","+r+","+startAngle;
                Shape s = new Shape("Semi-circle"+(shapes.size()+1), "Semi-circle", new long[0],
                        (Math.PI*r*r)/2, Math.PI*r + 2*r, extra);
                shapeDAO.insert(s);
                System.out.printf("[SHAPE] Semi-circle cx=%.1f cy=%.1f r=%.1f startAngle=%.1f%n", cx, cy, r, startAngle);
            } else {
                String extra = cx+","+cy+","+r;
                Shape s = new Shape("Circle"+(shapes.size()+1), "Circle", new long[0],
                        Math.PI*r*r, 2*Math.PI*r, extra);
                shapeDAO.insert(s);
                System.out.printf("[SHAPE] Circle cx=%.1f cy=%.1f r=%.1f%n", cx, cy, r);
            }
            loadFromDB();
        } catch(Exception ex){ ex.printStackTrace(); }

        circleCenter=null; circleCurrent=null;
        activeMode=null;
        if(rightPanel!=null) rightPanel.clearModeButtons();
        repaint();
    }

    // ── extend ────────────────────────────────────────────────────────────────
    private void computeExtend(){
        Edge e1=extendEdges.get(0),e2=extendEdges.get(1);
        Node a1=findById(e1.getNodeAId()),b1=findById(e1.getNodeBId());
        Node a2=findById(e2.getNodeAId()),b2=findById(e2.getNodeBId());
        if(a1==null||b1==null||a2==null||b2==null) return;
        double x1=a1.getX(),y1=a1.getY(),x2=b1.getX(),y2=b1.getY();
        double x3=a2.getX(),y3=a2.getY(),x4=b2.getX(),y4=b2.getY();
        double denom=(x1-x2)*(y3-y4)-(y1-y2)*(x3-x4);
        if(Math.abs(denom)<1e-10){ JOptionPane.showMessageDialog(this,"Lines are parallel.","Extend",JOptionPane.INFORMATION_MESSAGE);
            extendEdges.clear(); extendPt=null; return; }
        double t=((x1-x3)*(y3-y4)-(y1-y3)*(x3-x4))/denom;
        extendPt=new Point2D.Double(x1+t*(x2-x1),y1+t*(y2-y1));
    }
    public void triggerExtend(){ if(extendEdges.size()==2) computeExtend(); repaint(); }

    // ── boolean ops ───────────────────────────────────────────────────────────
    // ── region-based boolean ops using java.awt.geom.Area ─────────────────────
    // intersectArea/subtractArea hold the actual overlapping/result region so it
    // can be drawn as a highlighted overlay (not just a scatter of crossing points).
    private Area intersectArea = null;

    public void boolIntersect(){
        if(boolShapes.size()<2){ warn("SHIFT+click inside 2 shapes on the canvas first."); return; }
        Area a=shapeToArea(boolShapes.get(0));
        Area b=shapeToArea(boolShapes.get(1));
        if(a==null||b==null){ warn("Could not build a region for one of the selected shapes."); return; }
        Area result=new Area(a);
        result.intersect(b);
        if(result.isEmpty()){
            intersectArea=null;
            JOptionPane.showMessageDialog(this,"The two shapes don't overlap — no intersection region.","Intersect",JOptionPane.INFORMATION_MESSAGE);
        } else {
            intersectArea=result;
        }
        repaint();
    }

    public void boolSubtract(){
        if(boolShapes.size()<2){ warn("SHIFT+click inside 2 shapes first."); return; }
        if(intersectArea==null||intersectArea.isEmpty()){
            warn("Run INTERSECT first to highlight the overlapping region,\nthen SUBTRACT will remove that region.");
            return;
        }

        Shape sA=boolShapes.get(0), sB=boolShapes.get(1);
        Object[] options = { "Remove overlap from "+sA.getLabel(), "Remove overlap from "+sB.getLabel(), "Remove from BOTH" };
        int choice = JOptionPane.showOptionDialog(this,
            "The highlighted region will be permanently erased\nfrom whichever shape(s) you choose.\n\nBoth shapes remain in the DB — only their overlapping part is deleted.",
            "Subtract — choose target", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);
        if(choice==JOptionPane.CLOSED_OPTION) return;

        // Determine which shapes to trim
        List<Shape> targets = new ArrayList<>();
        if(choice==0) targets.add(sA);
        else if(choice==1) targets.add(sB);
        else { targets.add(sA); targets.add(sB); }

        boolean anyChanged = false;
        for(Shape target : targets){
            Area targetArea = shapeToArea(target);
            if(targetArea==null) continue;
            Area overlap = new Area(intersectArea); // the highlighted intersection
            overlap.intersect(targetArea);
            if(overlap.isEmpty()) continue;

            // Find and delete edges of target whose MIDPOINT falls inside the overlap region
            long[] nodeIds = target.getNodeIds();
            if(nodeIds.length < 2) continue;
            for(int i=0;i<nodeIds.length;i++){
                Node na = findById(nodeIds[i]);
                Node nb = findById(nodeIds[(i+1)%nodeIds.length]);
                if(na==null||nb==null) continue;
                double mx=(na.getX()+nb.getX())/2, my=(na.getY()+nb.getY())/2;
                if(overlap.contains(mx,my)){
                    try{
                        edgeDAO.delete(na.getId(),nb.getId());
                        nodeDAO.removeAdjacency(na.getId(),nb.getId());
                        anyChanged=true;
                    }catch(Exception ex){ ex.printStackTrace(); }
                }
            }
        }

        if(!anyChanged){
            JOptionPane.showMessageDialog(this,
                "No edges of the selected shape(s) pass through the intersection region.",
                "Subtract",JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Clear intersection highlight — both original shapes remain in DB
        intersectArea=null; intersectPts.clear();
        boolShapes.clear();
        if(rightPanel!=null) rightPanel.updateBoolCount(0);
        loadFromDB(); repaint();
    }

    public void boolAdd(){
        if(boolShapes.size()<2){ warn("SHIFT+click inside 2 shapes first."); return; }
        Shape sA=boolShapes.get(0),sB=boolShapes.get(1);
        Node c1=null,c2=null; double minD=Double.MAX_VALUE;
        for(long idA:sA.getNodeIds()){ Node nA=findById(idA); if(nA==null) continue;
            for(long idB:sB.getNodeIds()){ Node nB=findById(idB); if(nB==null) continue;
                double d=Math.hypot(nA.getX()-nB.getX(),nA.getY()-nB.getY());
                if(d<minD){minD=d;c1=nA;c2=nB;} } }
        if(c1==null||c2==null) return;
        try{ buildEdge(c1,c2);
            JOptionPane.showMessageDialog(this,"Connected "+c1.getLabel()+"\u2194"+c2.getLabel()+".","Add/Union",JOptionPane.INFORMATION_MESSAGE);
        }catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Add failed",JOptionPane.WARNING_MESSAGE);}
        boolShapes.clear(); repaint();
    }

    public void clearIntersections(){ intersectPts.clear(); intersectArea=null; repaint(); }

    /** Builds a java.awt.geom.Area for any supported shape type — polygon, circle, or semicircle. */
    private Area shapeToArea(Shape s) {
        String type = s.getShapeType();
        if ("Circle".equals(type)) {
            String[] parts = s.getExtraData().split(",");
            if (parts.length<3) return null;
            try {
                double cx=Double.parseDouble(parts[0].trim()), cy=Double.parseDouble(parts[1].trim()), r=Double.parseDouble(parts[2].trim());
                return new Area(new Ellipse2D.Double(cx-r,cy-r,r*2,r*2));
            } catch(NumberFormatException ex){ return null; }
        }
        if ("Semi-circle".equals(type)) {
            String[] parts = s.getExtraData().split(",");
            if (parts.length<4) return null;
            try {
                double cx=Double.parseDouble(parts[0].trim()), cy=Double.parseDouble(parts[1].trim());
                double r=Double.parseDouble(parts[2].trim()), startAngle=Double.parseDouble(parts[3].trim());
                Arc2D arc=new Arc2D.Double(cx-r,cy-r,r*2,r*2,-startAngle,-180,Arc2D.CHORD);
                return new Area(arc);
            } catch(NumberFormatException ex){ return null; }
        }
        Path2D path = buildPolygonPath(s);
        return path!=null ? new Area(path) : null;
    }

    /** Flattens an Area's outline into an ordered list of DB-persisted nodes. */
    private List<long[]> shapeEdgePairs(Shape s){
        List<long[]>pairs=new ArrayList<>();long[]ids=s.getNodeIds();
        if(ids.length<2) return pairs;
        for(int i=0;i<ids.length;i++) pairs.add(new long[]{ids[i],ids[(i+1)%ids.length]});
        return pairs;
    }

    // ── shape creation ────────────────────────────────────────────────────────
    public void createShape(ShapeType shapeType, List<Node> selected){
        ValidationResult r=ShapeValidator.validate(shapeType,selected);
        if(!r.valid){JOptionPane.showMessageDialog(this,r.message,"Validation Error",JOptionPane.WARNING_MESSAGE);return;}

        int[][]pairs=ShapeValidator.edgePairs(shapeType,selected.size());
        List<String>errors=new ArrayList<>();
        for(int[]pair:pairs){try{buildEdge(selected.get(pair[0]),selected.get(pair[1]));}catch(Exception ex){errors.add(ex.getMessage());}}

        // Persist shape to DB
        if(shapeType!=ShapeType.LINE){
            long[]nodeIds=selected.stream().mapToLong(Node::getId).toArray();
            double area=0,perim=0;
            if(shapeType==ShapeType.TRIANGLE||shapeType==ShapeType.SQUARE||shapeType==ShapeType.PENTAGON||shapeType==ShapeType.HEXAGON){
                area=computeArea(selected); perim=computePerimeter(selected,true);
            } else if(shapeType==ShapeType.ARC||shapeType==ShapeType.SEMICIRCLE){
                perim=computePerimeter(selected,false);
            }
            String extra="";
            if(shapeType==ShapeType.SEMICIRCLE&&selected.size()==3){
                Node ctr=selected.get(1);
                double rad=(Math.hypot(selected.get(0).getX()-ctr.getX(),selected.get(0).getY()-ctr.getY())
                           +Math.hypot(selected.get(2).getX()-ctr.getX(),selected.get(2).getY()-ctr.getY()))/2;
                extra=ctr.getX()+","+ctr.getY()+","+rad;
            }
            String label=shapeType.displayName+" "+(shapes.size()+1);
            try{ shapeDAO.insert(new Shape(label,shapeType.displayName,nodeIds,area,perim,extra)); }
            catch(Exception ex){ex.printStackTrace();}
        }

        shapeNodes.clear(); notifyShapeCount(); loadFromDB(); repaint();
        if(!errors.isEmpty()) JOptionPane.showMessageDialog(this,"Some edges skipped:\n"+String.join("\n",errors),"Partial",JOptionPane.WARNING_MESSAGE);
    }

    private double computeArea(List<Node>pts){
        double a=0; int n=pts.size();
        for(int i=0;i<n;i++){Node p=pts.get(i),q=pts.get((i+1)%n);a+=p.getX()*q.getY()-q.getX()*p.getY();}
        return Math.abs(a)/2;
    }
    private double computePerimeter(List<Node>pts,boolean closed){
        double p=0; int n=pts.size(); int lim=closed?n:n-1;
        for(int i=0;i<lim;i++){Node a=pts.get(i),b=pts.get((i+1)%n);p+=Math.hypot(b.getX()-a.getX(),b.getY()-a.getY());}
        return p;
    }

    // ── import ────────────────────────────────────────────────────────────────
    public void importImage(){ new ImageTracer().run(this,()->{ loadFromDB(); repaint(); }); }

    // ── bulk delete ───────────────────────────────────────────────────────────
    private void deleteMultiSelection(){
        if(multiNodes.isEmpty()&&multiEdges.isEmpty()) return;
        if(JOptionPane.showConfirmDialog(this,"Delete "+multiNodes.size()+" node(s) and "+multiEdges.size()+" edge(s)?","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
        final List<Node>snapN=List.copyOf(multiNodes); final List<Edge>snapE=List.copyOf(multiEdges);
        for(Edge e:snapE){try{edgeDAO.delete(e.getNodeAId(),e.getNodeBId());nodeDAO.removeAdjacency(e.getNodeAId(),e.getNodeBId());}catch(Exception ex){ex.printStackTrace();}}
        for(Node n:snapN){try{nodeDAO.delete(n.getId());}catch(Exception ex){ex.printStackTrace();}}
        undoManager.push(new Operation("bulk delete",
            ()->{ for(Node n:snapN){try{nodeDAO.insertWithId(n.getId(),n.getX(),n.getY(),n.getLabel());}catch(Exception ex){ex.printStackTrace();}}
                  for(Edge e:snapE){try{edgeDAO.insertWithId(e.getId(),e.getNodeAId(),e.getNodeBId(),e.getLength());nodeDAO.addAdjacency(e.getNodeAId(),e.getNodeBId());}catch(Exception ex){ex.printStackTrace();}} loadFromDB();repaint(); },
            ()->{ for(Edge e:snapE){try{edgeDAO.delete(e.getNodeAId(),e.getNodeBId());nodeDAO.removeAdjacency(e.getNodeAId(),e.getNodeBId());}catch(Exception ex){ex.printStackTrace();}}
                  for(Node n:snapN){try{nodeDAO.delete(n.getId());}catch(Exception ex){ex.printStackTrace();}} loadFromDB();repaint(); }
        ));
        multiNodes.clear(); multiEdges.clear(); activeMode=null; loadFromDB(); repaint();
    }

    // ── context menu ──────────────────────────────────────────────────────────
    private void showContextMenu(MouseEvent e){
        Point2D w=toWorld(e.getX(),e.getY());
        Node hn=findNodeAt(w); Edge he=hn==null?findEdgeAt(w):null; Shape hs=hn==null&&he==null?findShapeAt(w):null;
        if(hn==null&&he==null&&hs==null){edgeStart=null;repaint();return;}
        JPopupMenu menu=new JPopupMenu();
        menu.setBackground(new Color(12,16,28));
        menu.setBorder(BorderFactory.createLineBorder(new Color(30,55,95)));
        if(hn!=null){final Node t=hn;JMenuItem d=item("Delete node: "+t.getLabel(),new Color(215,65,65));d.addActionListener(ev->deleteNode(t));menu.add(d);}
        if(he!=null){final Edge t=he;Node a=findById(t.getNodeAId()),b=findById(t.getNodeBId());String lb=(a!=null&&b!=null)?a.getLabel()+"\u2194"+b.getLabel():"Edge#"+t.getId();JMenuItem d=item("Delete edge: "+lb,new Color(215,65,65));d.addActionListener(ev->deleteEdge(t));menu.add(d);}
        if(hs!=null){final Shape t=hs;JMenuItem d=item("Delete shape: "+t.getLabel(),new Color(215,65,65));d.addActionListener(ev->{try{shapeDAO.delete(t.getId());loadFromDB();repaint();}catch(Exception ex){ex.printStackTrace();}});menu.add(d);}
        menu.show(this,e.getX(),e.getY());
    }
    private JMenuItem item(String t,Color fg){JMenuItem i=new JMenuItem(t);i.setFont(new Font("Consolas",Font.PLAIN,11));i.setForeground(fg);i.setBackground(new Color(12,16,28));i.setOpaque(true);return i;}

    // ── delete with full undo ─────────────────────────────────────────────────
    private void deleteNode(Node n){
        final List<Edge>conn=edges.stream().filter(e->e.getNodeAId()==n.getId()||e.getNodeBId()==n.getId()).collect(Collectors.toList());
        try{
            nodeDAO.delete(n.getId());
            if(edgeStart!=null&&edgeStart.getId()==n.getId()) edgeStart=null;
            undoManager.push(new Operation("delete "+n.getLabel(),
                ()->{ try{nodeDAO.insertWithId(n.getId(),n.getX(),n.getY(),n.getLabel());
                         for(Edge e:conn){edgeDAO.insertWithId(e.getId(),e.getNodeAId(),e.getNodeBId(),e.getLength());try{nodeDAO.addAdjacency(e.getNodeAId(),e.getNodeBId());}catch(Exception ig){}}
                     }catch(Exception ex){ex.printStackTrace();} loadFromDB();repaint(); },
                ()->{ try{nodeDAO.delete(n.getId());}catch(Exception ex){ex.printStackTrace();} loadFromDB();repaint(); }
            ));
            loadFromDB(); repaint();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void deleteEdge(Edge edge){
        try{
            edgeDAO.delete(edge.getNodeAId(),edge.getNodeBId());
            nodeDAO.removeAdjacency(edge.getNodeAId(),edge.getNodeBId());
            final Edge snap=edge;
            undoManager.push(new Operation("delete edge",
                ()->{ try{edgeDAO.insertWithId(snap.getId(),snap.getNodeAId(),snap.getNodeBId(),snap.getLength());nodeDAO.addAdjacency(snap.getNodeAId(),snap.getNodeBId());}catch(Exception ex){ex.printStackTrace();} loadFromDB();repaint(); },
                ()->{ try{edgeDAO.delete(snap.getNodeAId(),snap.getNodeBId());nodeDAO.removeAdjacency(snap.getNodeAId(),snap.getNodeBId());}catch(Exception ex){ex.printStackTrace();} loadFromDB();repaint(); }
            ));
            loadFromDB(); repaint();
        }catch(Exception ex){ex.printStackTrace();}
    }

    private void buildEdge(Node a,Node b)throws Exception{
        double len=Math.hypot(b.getX()-a.getX(),b.getY()-a.getY());
        Edge edge=edgeDAO.insert(new Edge(a.getId(),b.getId(),len));
        nodeDAO.addAdjacency(a.getId(),b.getId());
        final long eid=edge.getId(),eA=a.getId(),eB=b.getId(); final double el=len;
        undoManager.push(new Operation("draw edge "+a.getLabel()+"-"+b.getLabel(),
            ()->{ try{edgeDAO.delete(eA,eB);nodeDAO.removeAdjacency(eA,eB);}catch(Exception ex){ex.printStackTrace();} loadFromDB();repaint(); },
            ()->{ try{edgeDAO.insertWithId(eid,eA,eB,el);nodeDAO.addAdjacency(eA,eB);}catch(Exception ex){ex.printStackTrace();} loadFromDB();repaint(); }
        ));
        loadFromDB();
    }

    // ── coordinate + hit testing ──────────────────────────────────────────────
    private double snapD(double v){return Math.round(v/GRID_STEP)*GRID_STEP;}
    private Point2D toWorld(int sx,int sy){return toWorldZ(sx,sy,zoom);}
    private Point2D toWorldZ(int sx,int sy,double z){
        int cx=getWidth()/2,cy=getHeight()/2;
        double tx=sx-cx,ty=sy-cy,rad=-Math.toRadians(rotateDeg);
        double cos=Math.cos(rad),sin=Math.sin(rad);
        return new Point2D.Double(camX+(cos*tx-sin*ty)/z,camY+(sin*tx+cos*ty)/z);
    }
    private Node findNodeAt(Point2D w){double t=HIT_R/zoom;for(Node n:nodes)if(Math.hypot(n.getX()-w.getX(),n.getY()-w.getY())<=t)return n;return null;}
    private Edge findEdgeAt(Point2D w){double t=EDGE_HIT/zoom;for(Edge e:edges){Node a=findById(e.getNodeAId()),b=findById(e.getNodeBId());if(a!=null&&b!=null&&ptSeg(w.getX(),w.getY(),a.getX(),a.getY(),b.getX(),b.getY())<=t)return e;}return null;}
    private Shape findShapeAt(Point2D w){
        // Check circles by radius
        for(Shape s:shapes) if("Circle".equals(s.getShapeType())){
            String[]parts=s.getExtraData().split(",");
            if(parts.length<3) continue;
            try{double cx=Double.parseDouble(parts[0].trim()),cy=Double.parseDouble(parts[1].trim()),r=Double.parseDouble(parts[2].trim());
                if(Math.hypot(w.getX()-cx,w.getY()-cy)<=r) return s;}catch(NumberFormatException ignored){}
        }
        // Check semicircles — within radius AND on the correct half (angle within 180° of startAngle)
        for(Shape s:shapes) if("Semi-circle".equals(s.getShapeType())){
            String[]parts=s.getExtraData().split(",");
            if(parts.length<4) continue;
            try{
                double cx=Double.parseDouble(parts[0].trim());
                double cy=Double.parseDouble(parts[1].trim());
                double r =Double.parseDouble(parts[2].trim());
                double startAngle=Double.parseDouble(parts[3].trim());
                double dx=w.getX()-cx, dy=w.getY()-cy;
                double dist=Math.hypot(dx,dy);
                if(dist>r) continue;
                double pointAngle=Math.toDegrees(Math.atan2(dy,dx));
                double rel=((pointAngle-startAngle)%360+360)%360;
                if(rel<=180) return s;
            }catch(NumberFormatException ignored){}
        }
        // Check polygons
        for(Shape s:shapes){Path2D p=buildPolygonPath(s);if(p!=null&&p.contains(w))return s;}
        return null;
    }
    private Node findById(long id){for(Node n:nodes)if(n.getId()==id)return n;return null;}
    private double ptSeg(double px,double py,double ax,double ay,double bx,double by){
        double dx=bx-ax,dy=by-ay; if(dx==0&&dy==0)return Math.hypot(px-ax,py-ay);
        double t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(px-(ax+t*dx),py-(ay+t*dy));
    }
    private void toggleNode(List<Node>list,Node n){if(!list.removeIf(x->x.getId()==n.getId()))list.add(n);}
    private void toggleEdge(List<Edge>list,Edge e){if(!list.removeIf(x->x.getId()==e.getId()))list.add(e);}
    private void toggleShape(List<Shape>list,Shape s){if(!list.removeIf(x->x.getId()==s.getId()))list.add(s);}
    private void warn(String msg){JOptionPane.showMessageDialog(this,msg,"Warning",JOptionPane.WARNING_MESSAGE);}
}