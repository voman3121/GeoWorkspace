package ui;

import db.dao.EdgeDAO;
import db.dao.NodeDAO;
import model.Edge;
import model.Node;
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
    private static final Color BG         = new Color(6,   8,  16);
    private static final Color GRID_DOT   = new Color(28,  38,  62);
    private static final Color GRID_MAJOR = new Color(18,  26,  46);
    private static final Color AXIS_COL   = new Color(22,  52, 100, 160);
    private static final Color NODE_FILL  = new Color(0,  185, 255);
    private static final Color NODE_RING  = new Color(60, 210, 255);
    private static final Color NODE_SEL1  = new Color(255, 195,  25);
    private static final Color NODE_MULTI = new Color(60,  255, 150);
    private static final Color NODE_HOVER = new Color(130, 225, 255);
    private static final Color NODE_MSEL  = new Color(255, 100, 100);  // multi-delete select
    private static final Color EDGE_COL   = new Color(0,  140, 195, 190);
    private static final Color EDGE_HOVER = new Color(255, 115,  25, 235);
    private static final Color EDGE_MSEL  = new Color(255, 100, 100, 220);
    private static final Color LABEL_COL  = new Color(140, 200, 245);
    private static final Color INTERSECT  = new Color(255, 220,  50, 200);
    private static final Color PREDICT    = new Color(120, 255, 120, 180);

    private static final double NODE_R   = 3.5;
    private static final double HIT_R    = 10.0;
    private static final float  EDGE_HIT = 6f;
    private static final int    GRID_STEP = 40;

    // ── data ──────────────────────────────────────────────────────────────────
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final NodeDAO nodeDAO  = new NodeDAO();
    private final EdgeDAO edgeDAO  = new EdgeDAO();

    // ── undo ──────────────────────────────────────────────────────────────────
    private final UndoManager undoManager = new UndoManager();

    // ── interaction state ─────────────────────────────────────────────────────
    public enum InteractionMode {
        NORMAL,        // place node / draw edge
        MULTI_SELECT,  // Shift+click to multi-select nodes+edges for bulk delete
        SHAPE_SELECT,  // Shift+click for shape building
        EXTEND         // select 2 lines to predict intersection
    }

    private InteractionMode mode = InteractionMode.NORMAL;

    private Node       edgeStart     = null;
    private List<Node> shapeSelected = new ArrayList<>();

    // Multi-select (for bulk delete)
    private List<Node> multiNodes = new ArrayList<>();
    private List<Edge> multiEdges = new ArrayList<>();

    // Extend mode
    private List<Edge> extendEdges    = new ArrayList<>();
    private Point2D    extendPredict  = null;

    // Intersection highlights
    private List<Point2D> intersectPoints = new ArrayList<>();

    // Hover
    private Node hoveredNode = null;
    private Edge hoveredEdge = null;

    // Panels (injected)
    private ShapePanel shapePanel;

    // ── viewport ──────────────────────────────────────────────────────────────
    private double camX      = 0, camY = 0;
    private double zoom      = 1.0;
    private double rotateDeg = 0.0;

    private boolean rmbDragging = false;
    private int     rmbStartX   = 0;
    private double  rmbStartRot = 0.0;

    private Timer syncTimer;

    // ── constructor ───────────────────────────────────────────────────────────

    public WorkspacePanel() {
        setBackground(BG);
        setFocusable(true);
        loadFromDB();
        attachListeners();
        startSyncTimer();
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setInitialDelay(280);
        ToolTipManager.sharedInstance().setDismissDelay(7000);
    }

    public void setShapePanel(ShapePanel sp) {
        this.shapePanel = sp;
        sp.setSelectionSupplier(() -> shapeSelected);
    }

    public UndoManager getUndoManager() { return undoManager; }

    // ── DB ────────────────────────────────────────────────────────────────────

    private void loadFromDB() {
        try {
            nodes.clear(); edges.clear();
            nodes.addAll(nodeDAO.getAll());
            edges.addAll(edgeDAO.getAll());
        } catch (Exception e) {
            System.err.println("[DB] Load error: " + e.getMessage());
        }
    }

    private void startSyncTimer() {
        syncTimer = new Timer(1500, e -> { loadFromDB(); repaint(); });
        syncTimer.start();
    }

    // ── paint ─────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        AffineTransform saved = g2.getTransform();
        applyViewport(g2);

        drawGrid(g2);
        drawAxes(g2);
        drawEdges(g2);
        drawIntersections(g2);
        drawExtendPredict(g2);
        drawNodes(g2);

        g2.setTransform(saved);
        drawHUD(g2);
    }

    private void applyViewport(Graphics2D g2) {
        int scx = getWidth() / 2, scy = getHeight() / 2;
        g2.translate(scx, scy);
        g2.rotate(Math.toRadians(rotateDeg));
        g2.scale(zoom, zoom);
        g2.translate(-camX, -camY);
    }

    // ── grid ──────────────────────────────────────────────────────────────────

    private void drawGrid(Graphics2D g2) {
        double halfW = (getWidth()  / 2.0) / zoom + GRID_STEP * 3;
        double halfH = (getHeight() / 2.0) / zoom + GRID_STEP * 3;
        int x0 = (int)(((camX - halfW) / GRID_STEP) - 1) * GRID_STEP;
        int x1 = (int)(((camX + halfW) / GRID_STEP) + 1) * GRID_STEP;
        int y0 = (int)(((camY - halfH) / GRID_STEP) - 1) * GRID_STEP;
        int y1 = (int)(((camY + halfH) / GRID_STEP) + 1) * GRID_STEP;

        g2.setColor(GRID_DOT);
        g2.setStroke(new BasicStroke(0.4f));
        for (int gx = x0; gx <= x1; gx += GRID_STEP)
            for (int gy = y0; gy <= y1; gy += GRID_STEP) {
                g2.draw(new Line2D.Double(gx - 1, gy, gx + 1, gy));
                g2.draw(new Line2D.Double(gx, gy - 1, gx, gy + 1));
            }

        int major = GRID_STEP * 5;
        g2.setColor(GRID_MAJOR);
        g2.setStroke(new BasicStroke(0.5f));
        int mx0 = (int)(((camX - halfW) / major) - 1) * major;
        int my0 = (int)(((camY - halfH) / major) - 1) * major;
        for (int gx = mx0; gx <= x1; gx += major)
            g2.draw(new Line2D.Double(gx, y0, gx, y1));
        for (int gy = my0; gy <= y1; gy += major)
            g2.draw(new Line2D.Double(x0, gy, x1, gy));
    }

    private void drawAxes(Graphics2D g2) {
        g2.setColor(AXIS_COL);
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(new Line2D.Double(-99999, 0, 99999, 0));
        g2.draw(new Line2D.Double(0, -99999, 0, 99999));
        g2.setColor(new Color(0, 180, 255, 90));
        g2.fill(new Ellipse2D.Double(-2, -2, 4, 4));
    }

    // ── edges ─────────────────────────────────────────────────────────────────

    private void drawEdges(Graphics2D g2) {
        for (Edge e : edges) {
            Node a = findById(e.getNodeAId()), b = findById(e.getNodeBId());
            if (a == null || b == null) continue;

            boolean hov  = hoveredEdge != null && e.getId() == hoveredEdge.getId();
            boolean msel = multiEdges.stream().anyMatch(me -> me.getId() == e.getId());
            boolean ext  = extendEdges.stream().anyMatch(ee -> ee.getId() == e.getId());

            Color col = msel ? EDGE_MSEL : ext ? new Color(120,255,120,220) : hov ? EDGE_HOVER : EDGE_COL;
            g2.setColor(col);
            g2.setStroke(new BasicStroke(msel || ext ? 2.0f : hov ? 1.8f : 1.0f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(a.getX(), a.getY(), b.getX(), b.getY()));

            double mx = (a.getX() + b.getX()) / 2;
            double my = (a.getY() + b.getY()) / 2;
            g2.setColor(hov ? EDGE_HOVER : new Color(0, 155, 200, 100));
            g2.fill(new Ellipse2D.Double(mx - 1.2, my - 1.2, 2.4, 2.4));
        }
    }

    // ── intersection highlights ────────────────────────────────────────────────

    private void drawIntersections(Graphics2D g2) {
        for (Point2D pt : intersectPoints) {
            double r = 5.0;
            g2.setColor(new Color(255, 220, 50, 60));
            g2.fill(new Ellipse2D.Double(pt.getX() - r * 2, pt.getY() - r * 2, r * 4, r * 4));
            g2.setColor(INTERSECT);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(pt.getX() - r, pt.getY() - r, r * 2, r * 2));
            // crosshair
            g2.draw(new Line2D.Double(pt.getX() - r * 1.5, pt.getY(), pt.getX() + r * 1.5, pt.getY()));
            g2.draw(new Line2D.Double(pt.getX(), pt.getY() - r * 1.5, pt.getX(), pt.getY() + r * 1.5));
        }
    }

    // ── extend prediction ─────────────────────────────────────────────────────

    private void drawExtendPredict(Graphics2D g2) {
        if (extendPredict == null) return;
        double r = 6.0;
        // Dashed lines from each edge toward predicted intersection
        float[] dash = {4f, 4f};
        g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g2.setColor(PREDICT);
        for (Edge e : extendEdges) {
            Node a = findById(e.getNodeAId()), b = findById(e.getNodeBId());
            if (a == null || b == null) continue;
            // Draw from midpoint of edge toward predicted point
            g2.draw(new Line2D.Double(b.getX(), b.getY(),
                    extendPredict.getX(), extendPredict.getY()));
        }
        // Predicted point marker
        g2.setColor(PREDICT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Ellipse2D.Double(extendPredict.getX() - r, extendPredict.getY() - r, r * 2, r * 2));
        g2.draw(new Line2D.Double(extendPredict.getX() - r * 1.6, extendPredict.getY(),
                                  extendPredict.getX() + r * 1.6, extendPredict.getY()));
        g2.draw(new Line2D.Double(extendPredict.getX(), extendPredict.getY() - r * 1.6,
                                  extendPredict.getX(), extendPredict.getY() + r * 1.6));
    }

    // ── nodes ─────────────────────────────────────────────────────────────────

    private void drawNodes(Graphics2D g2) {
        for (Node n : nodes) {
            double cx = n.getX(), cy = n.getY();
            boolean isSel   = edgeStart != null && edgeStart.getId() == n.getId();
            boolean isShape = shapeSelected.stream().anyMatch(s -> s.getId() == n.getId());
            boolean isMSel  = multiNodes.stream().anyMatch(s -> s.getId() == n.getId());
            boolean isHov   = hoveredNode != null && hoveredNode.getId() == n.getId();

            Color fill = isMSel  ? NODE_MSEL
                       : isSel   ? NODE_SEL1
                       : isShape ? NODE_MULTI
                       : isHov   ? NODE_HOVER
                       :           NODE_FILL;

            if (isSel || isShape || isMSel || isHov) {
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 45));
                double gr = NODE_R + 4;
                g2.fill(new Ellipse2D.Double(cx - gr, cy - gr, gr * 2, gr * 2));
            }

            RadialGradientPaint rg = new RadialGradientPaint(
                    (float)(cx - NODE_R * 0.3), (float)(cy - NODE_R * 0.3),
                    (float)(NODE_R * 2.2f),
                    new float[]{0f, 1f},
                    new Color[]{fill.brighter(), fill.darker().darker()});
            g2.setPaint(rg);
            g2.fill(new Ellipse2D.Double(cx - NODE_R, cy - NODE_R, NODE_R * 2, NODE_R * 2));

            g2.setPaint(NODE_RING);
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(new Ellipse2D.Double(cx - NODE_R, cy - NODE_R, NODE_R * 2, NODE_R * 2));

            // Label
            g2.setColor(LABEL_COL);
            float fontSize = (float) Math.max(6.0, Math.min(10.0, 9.0 / zoom));
            g2.setFont(new Font("Consolas", Font.PLAIN, (int) fontSize));
            FontMetrics fm = g2.getFontMetrics();
            String lbl = n.getLabel() != null ? n.getLabel() : "?";
            AffineTransform old = g2.getTransform();
            double labelOffset = NODE_R + 3.0 / zoom;
            g2.translate(cx, cy - labelOffset);
            g2.scale(1.0 / zoom, 1.0 / zoom);
            g2.drawString(lbl, -fm.stringWidth(lbl) / 2f, 0);
            g2.setTransform(old);
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void drawHUD(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2.setColor(new Color(40, 80, 140, 210));
        g2.drawString(String.format("zoom %.2fx  rot %.1f°  nodes %d  edges %d",
                zoom, rotateDeg, nodes.size(), edges.size()), 12, getHeight() - 12);

        String modeMsg = null;
        Color  modeCol = Color.WHITE;
        switch (mode) {
            case NORMAL:
                if (edgeStart != null) {
                    modeMsg = "EDGE MODE — click a second node  [RMB = cancel]";
                    modeCol = NODE_SEL1;
                }
                break;
            case SHAPE_SELECT:
                modeMsg = "SHAPE SELECT — SHIFT+click nodes (" + shapeSelected.size() + " selected)";
                modeCol = NODE_MULTI;
                break;
            case MULTI_SELECT:
                modeMsg = "MULTI-SELECT — SHIFT+click nodes/edges  [Del = delete all selected  |  RMB = cancel]";
                modeCol = NODE_MSEL;
                break;
            case EXTEND:
                modeMsg = "EXTEND — click 2 edges to predict intersection  (" + extendEdges.size() + "/2)  [RMB = cancel]";
                modeCol = PREDICT;
                break;
        }
        if (modeMsg != null) {
            g2.setColor(modeCol);
            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            g2.drawString(modeMsg, 12, 22);
        }

        // Undo/redo hints
        String undoDesc = undoManager.peekUndoDescription();
        String redoDesc = undoManager.peekRedoDescription();
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(50, 100, 160, 180));
        if (undoDesc != null) g2.drawString("Ctrl+Z: undo " + undoDesc, 12, getHeight() - 26);
        if (redoDesc != null) g2.drawString("Ctrl+Y: redo " + redoDesc,
                12 + (undoDesc != null ? 220 : 0), getHeight() - 26);
    }

    // ── tooltip ───────────────────────────────────────────────────────────────

    @Override
    public String getToolTipText(MouseEvent e) {
        Point2D w = toWorld(e.getX(), e.getY());
        Node n = findNodeAt(w);
        if (n != null)
            return String.format(
                "<html><b style='color:#00c8ff'>%s</b> <span style='color:#555'>id=%d</span><br>"
                + "Pos: (%.0f, %.0f)<br>Degree: %d/4<br>Adj: %s</html>",
                n.getLabel(), n.getId(), n.getX(), n.getY(),
                n.degree(), neighbourLabels(n));
        Edge edge = findEdgeAt(w);
        if (edge != null) {
            Node a = findById(edge.getNodeAId()), b = findById(edge.getNodeBId());
            if (a != null && b != null)
                return String.format(
                    "<html><b style='color:#ff8020'>Edge</b> id=%d<br>"
                    + "%s \u2194 %s<br>Length: %.1f</html>",
                    edge.getId(), a.getLabel(), b.getLabel(), edge.getLength());
        }
        return null;
    }

    private String neighbourLabels(Node n) {
        List<String> out = new ArrayList<>();
        for (long aid : n.adjacentIds()) {
            Node nb = findById(aid);
            out.add(nb != null ? nb.getLabel() : "#" + aid);
        }
        return out.isEmpty() ? "none" : String.join(", ", out);
    }

    // ── mouse ─────────────────────────────────────────────────────────────────

    private void attachListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isRightMouseButton(e)) {
                    rmbStartX = e.getX(); rmbStartRot = rotateDeg; rmbDragging = false;
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int dx = e.getX() - rmbStartX;
                    if (Math.abs(dx) > 4) rmbDragging = true;
                    if (rmbDragging) { rotateDeg = rmbStartRot + dx * 0.30; repaint(); }
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) && !rmbDragging) showContextMenu(e);
                rmbDragging = false;
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) handleLeftClick(e);
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                Point2D w = toWorld(e.getX(), e.getY());
                Node prevN = hoveredNode; Edge prevE = hoveredEdge;
                hoveredNode = findNodeAt(w);
                hoveredEdge = hoveredNode == null ? findEdgeAt(w) : null;
                if (hoveredNode != prevN || hoveredEdge != prevE) repaint();
                setCursor((hoveredNode != null || hoveredEdge != null)
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double scrollAmount = e.getPreciseWheelRotation();
                if (scrollAmount == 0) return;
                double factor   = scrollAmount < 0 ? 1.12 : 0.89;
                double prevZoom = zoom;
                double maxZoom  = 300.0 / GRID_STEP;
                double minZoom  = 0.002;
                zoom = Math.max(minZoom, Math.min(maxZoom, zoom * factor));
                if (zoom != prevZoom) {
                    Point2D w1 = toWorldWithZoom(e.getX(), e.getY(), prevZoom);
                    Point2D w2 = toWorld(e.getX(), e.getY());
                    camX += w1.getX() - w2.getX();
                    camY += w1.getY() - w2.getY();
                    repaint();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);

        // Keyboard: Delete key, Ctrl+Z, Ctrl+Y
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) deleteMultiSelection();
                else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
                    undoManager.undo(); loadFromDB(); repaint();
                } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Y) {
                    undoManager.redo(); loadFromDB(); repaint();
                }
            }
        });
    }

    // ── left click dispatch ───────────────────────────────────────────────────

    private void handleLeftClick(MouseEvent e) {
        Point2D w  = toWorld(e.getX(), e.getY());
        boolean sh = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
        Node    hitNode = findNodeAt(w);
        Edge    hitEdge = hitNode == null ? findEdgeAt(w) : null;

        switch (mode) {
            case MULTI_SELECT -> handleMultiSelectClick(hitNode, hitEdge, sh);
            case SHAPE_SELECT -> handleShapeSelectClick(hitNode, sh);
            case EXTEND       -> handleExtendClick(hitEdge);
            default           -> handleNormalClick(w, hitNode, sh);
        }
        repaint();
    }

    private void handleNormalClick(Point2D w, Node hit, boolean shift) {
        if (shift) {
            // Shift in normal mode = enter MULTI_SELECT
            setMode(InteractionMode.MULTI_SELECT);
            if (hit != null) multiNodes.add(hit);
            return;
        }
        try {
            if (hit == null) {
                double wx = snapD(w.getX()), wy = snapD(w.getY());
                String label = "N" + (nodes.size() + 1);
                Node n = nodeDAO.insert(new Node(wx, wy, label));
                nodes.add(n);
                edgeStart = null;
                // Undo: delete the node we just created
                final long nid = n.getId();
                undoManager.push(new Operation("place node " + label,
                    () -> { try { nodeDAO.delete(nid); loadFromDB(); repaint(); } catch (Exception ex) { ex.printStackTrace(); } },
                    () -> { try { nodeDAO.insert(new Node(wx, wy, label)); loadFromDB(); repaint(); } catch (Exception ex) { ex.printStackTrace(); } }
                ));
            } else {
                if (edgeStart == null) {
                    edgeStart = hit;
                } else if (edgeStart.getId() == hit.getId()) {
                    edgeStart = null;
                } else {
                    buildEdge(edgeStart, hit);
                    edgeStart = null;
                }
            }
        } catch (Exception ex) {
            System.err.println("[ERROR] " + ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void handleMultiSelectClick(Node hitNode, Edge hitEdge, boolean shift) {
        if (hitNode != null) {
            boolean already = multiNodes.stream().anyMatch(n -> n.getId() == hitNode.getId());
            if (already) multiNodes.removeIf(n -> n.getId() == hitNode.getId());
            else         multiNodes.add(hitNode);
        } else if (hitEdge != null) {
            boolean already = multiEdges.stream().anyMatch(e -> e.getId() == hitEdge.getId());
            if (already) multiEdges.removeIf(e -> e.getId() == hitEdge.getId());
            else         multiEdges.add(hitEdge);
        }
    }

    private void handleShapeSelectClick(Node hit, boolean shift) {
        if (hit == null) return;
        boolean already = shapeSelected.stream().anyMatch(s -> s.getId() == hit.getId());
        if (already) shapeSelected.removeIf(s -> s.getId() == hit.getId());
        else         shapeSelected.add(hit);
        if (shapePanel != null) shapePanel.updateSelectionCount(shapeSelected.size());
    }

    private void handleExtendClick(Edge hitEdge) {
        if (hitEdge == null) return;
        boolean already = extendEdges.stream().anyMatch(e -> e.getId() == hitEdge.getId());
        if (already) return;
        extendEdges.add(hitEdge);
        if (extendEdges.size() == 2) computeExtendPrediction();
    }

    // ── extend: compute predicted intersection ────────────────────────────────

    private void computeExtendPrediction() {
        Edge e1 = extendEdges.get(0), e2 = extendEdges.get(1);
        Node a1 = findById(e1.getNodeAId()), b1 = findById(e1.getNodeBId());
        Node a2 = findById(e2.getNodeAId()), b2 = findById(e2.getNodeBId());
        if (a1 == null || b1 == null || a2 == null || b2 == null) return;

        // Use infinite line intersection (not just segment)
        double x1 = a1.getX(), y1 = a1.getY(), x2 = b1.getX(), y2 = b1.getY();
        double x3 = a2.getX(), y3 = a2.getY(), x4 = b2.getX(), y4 = b2.getY();
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);

        if (Math.abs(denom) < 1e-10) {
            JOptionPane.showMessageDialog(this,
                "The two lines are parallel — no intersection.", "Extend", JOptionPane.INFORMATION_MESSAGE);
            extendEdges.clear(); extendPredict = null;
            return;
        }
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        extendPredict = new Point2D.Double(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
        System.out.printf("[EXTEND] Predicted intersection at (%.1f, %.1f)%n",
                extendPredict.getX(), extendPredict.getY());
    }

    // ── boolean operations (called from BooleanPanel) ─────────────────────────

    /**
     * Finds all intersection points between all pairs of edges and highlights them.
     */
    public void computeIntersections() {
        intersectPoints.clear();
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                Edge ea = edges.get(i), eb = edges.get(j);
                Node a1 = findById(ea.getNodeAId()), a2 = findById(ea.getNodeBId());
                Node b1 = findById(eb.getNodeAId()), b2 = findById(eb.getNodeBId());
                if (a1 == null || a2 == null || b1 == null || b2 == null) continue;
                // Skip edges sharing a node
                if (ea.getNodeAId() == eb.getNodeAId() || ea.getNodeAId() == eb.getNodeBId()
                 || ea.getNodeBId() == eb.getNodeAId() || ea.getNodeBId() == eb.getNodeBId()) continue;
                Point2D pt = ShapeValidator.segmentIntersection(
                        a1.getX(), a1.getY(), a2.getX(), a2.getY(),
                        b1.getX(), b1.getY(), b2.getX(), b2.getY());
                if (pt != null) intersectPoints.add(pt);
            }
        }
        System.out.println("[BOOL] Found " + intersectPoints.size() + " intersection(s).");
        repaint();
    }

    public void clearIntersections() { intersectPoints.clear(); repaint(); }

    /**
     * Boolean SUBTRACT: removes edges that cross any other edge.
     */
    public void booleanSubtract() {
        List<Edge> toDelete = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                Edge ea = edges.get(i), eb = edges.get(j);
                Node a1 = findById(ea.getNodeAId()), a2 = findById(ea.getNodeBId());
                Node b1 = findById(eb.getNodeAId()), b2 = findById(eb.getNodeBId());
                if (a1==null||a2==null||b1==null||b2==null) continue;
                if (ea.getNodeAId()==eb.getNodeAId()||ea.getNodeAId()==eb.getNodeBId()
                 || ea.getNodeBId()==eb.getNodeAId()||ea.getNodeBId()==eb.getNodeBId()) continue;
                Point2D pt = ShapeValidator.segmentIntersection(
                        a1.getX(),a1.getY(),a2.getX(),a2.getY(),
                        b1.getX(),b1.getY(),b2.getX(),b2.getY());
                if (pt != null) { toDelete.add(ea); toDelete.add(eb); }
            }
        }
        if (toDelete.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No intersecting edges found.", "Subtract", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // Ask user which to keep
        String[] options = {"Delete ALL intersecting edges", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
            toDelete.size() / 2 + " edge pair(s) intersect. Delete all intersecting edges?",
            "Boolean Subtract", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
            null, options, options[0]);
        if (choice == 0) {
            toDelete.stream().distinct().forEach(e -> {
                try {
                    edgeDAO.delete(e.getNodeAId(), e.getNodeBId());
                    nodeDAO.removeAdjacency(e.getNodeAId(), e.getNodeBId());
                } catch (Exception ex) { ex.printStackTrace(); }
            });
            loadFromDB(); intersectPoints.clear(); repaint();
        }
    }

    /**
     * Boolean ADD: merges the two closest endpoints of any two edges
     * by inserting a connecting edge between them.
     */
    public void booleanAdd() {
        JOptionPane.showMessageDialog(this,
            "Boolean ADD: connects the nearest endpoints of separate edge groups.\n"
            + "Select two nodes and use the normal edge tool to connect them.\n"
            + "(Full topological merge coming in a future update.)",
            "Boolean Add", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── mode management (called from side panels) ─────────────────────────────

    public void setMode(InteractionMode newMode) {
        mode = newMode;
        edgeStart = null;
        if (newMode != InteractionMode.MULTI_SELECT)  { multiNodes.clear(); multiEdges.clear(); }
        if (newMode != InteractionMode.SHAPE_SELECT)  { shapeSelected.clear(); if (shapePanel != null) shapePanel.updateSelectionCount(0); }
        if (newMode != InteractionMode.EXTEND)        { extendEdges.clear(); extendPredict = null; }
        repaint();
    }

    public InteractionMode getMode() { return mode; }

    // ── bulk delete ───────────────────────────────────────────────────────────

    private void deleteMultiSelection() {
        if (multiNodes.isEmpty() && multiEdges.isEmpty()) return;
        int total = multiNodes.size() + multiEdges.size();
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete " + multiNodes.size() + " node(s) and " + multiEdges.size() + " edge(s)?",
            "Confirm bulk delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Snapshot for undo (simplified: just reload)
        final List<Long> nodeIds = multiNodes.stream().map(Node::getId).collect(Collectors.toList());
        final List<long[]> edgePairs = multiEdges.stream()
                .map(e -> new long[]{e.getNodeAId(), e.getNodeBId()}).collect(Collectors.toList());

        for (Edge e : new ArrayList<>(multiEdges)) {
            try { edgeDAO.delete(e.getNodeAId(), e.getNodeBId());
                  nodeDAO.removeAdjacency(e.getNodeAId(), e.getNodeBId()); }
            catch (Exception ex) { ex.printStackTrace(); }
        }
        for (Node n : new ArrayList<>(multiNodes)) {
            try { nodeDAO.delete(n.getId()); }
            catch (Exception ex) { ex.printStackTrace(); }
        }
        multiNodes.clear(); multiEdges.clear();
        setMode(InteractionMode.NORMAL);
        loadFromDB(); repaint();
    }

    // ── context menu ──────────────────────────────────────────────────────────

    private void showContextMenu(MouseEvent e) {
        Point2D w = toWorld(e.getX(), e.getY());
        Node hitNode = findNodeAt(w);
        Edge hitEdge = hitNode == null ? findEdgeAt(w) : null;

        if (hitNode == null && hitEdge == null) {
            setMode(InteractionMode.NORMAL); repaint(); return;
        }

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(12, 16, 28));
        menu.setBorder(BorderFactory.createLineBorder(new Color(30, 55, 95)));

        if (hitNode != null) {
            final Node t = hitNode;
            JMenuItem del = styledItem("Delete node: " + t.getLabel(), new Color(215, 65, 65));
            del.addActionListener(ev -> deleteNode(t));
            menu.add(del);
        }
        if (hitEdge != null) {
            final Edge t = hitEdge;
            Node a = findById(t.getNodeAId()), b = findById(t.getNodeBId());
            String lbl = (a != null && b != null) ? a.getLabel() + " \u2194 " + b.getLabel() : "Edge #" + t.getId();
            JMenuItem del = styledItem("Delete edge: " + lbl, new Color(215, 65, 65));
            del.addActionListener(ev -> deleteEdge(t));
            menu.add(del);
        }
        menu.show(this, e.getX(), e.getY());
    }

    private JMenuItem styledItem(String text, Color fg) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(new Font("Consolas", Font.PLAIN, 11));
        item.setForeground(fg); item.setBackground(new Color(12, 16, 28)); item.setOpaque(true);
        return item;
    }

    // ── shape creation ────────────────────────────────────────────────────────

    public void createShape(ShapeType shape, List<Node> selected) {
        ValidationResult r = ShapeValidator.validate(shape, selected);
        if (!r.valid) {
            JOptionPane.showMessageDialog(this, r.message, "Shape Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int[][] pairs = ShapeValidator.edgePairs(shape, selected.size());
        List<String> errors = new ArrayList<>();
        for (int[] pair : pairs) {
            try { buildEdge(selected.get(pair[0]), selected.get(pair[1])); }
            catch (Exception ex) { errors.add(ex.getMessage()); }
        }
        shapeSelected.clear();
        if (shapePanel != null) shapePanel.updateSelectionCount(0);
        setMode(InteractionMode.NORMAL);
        if (!errors.isEmpty())
            JOptionPane.showMessageDialog(this, "Some edges skipped:\n" + String.join("\n", errors),
                    "Partial", JOptionPane.WARNING_MESSAGE);
        repaint();
    }

    // ── DB operations ─────────────────────────────────────────────────────────

    private void buildEdge(Node a, Node b) throws Exception {
        double len = Math.hypot(b.getX() - a.getX(), b.getY() - a.getY());
        Edge edge = edgeDAO.insert(new Edge(a.getId(), b.getId(), len));
        nodeDAO.addAdjacency(a.getId(), b.getId());
        // Undo
        final long eAid = a.getId(), eBid = b.getId();
        undoManager.push(new Operation("draw edge " + a.getLabel() + "-" + b.getLabel(),
            () -> { try { edgeDAO.delete(eAid, eBid); nodeDAO.removeAdjacency(eAid, eBid); loadFromDB(); repaint(); } catch(Exception ex){ex.printStackTrace();} },
            () -> { try { double l2 = Math.hypot(b.getX()-a.getX(),b.getY()-a.getY()); edgeDAO.insert(new Edge(eAid,eBid,l2)); nodeDAO.addAdjacency(eAid,eBid); loadFromDB(); repaint(); } catch(Exception ex){ex.printStackTrace();} }
        ));
        loadFromDB();
    }

    private void deleteNode(Node n) {
        try {
            nodeDAO.delete(n.getId());
            if (edgeStart != null && edgeStart.getId() == n.getId()) edgeStart = null;
            undoManager.push(new Operation("delete node " + n.getLabel(), () -> {}, () -> {}));
            loadFromDB(); repaint();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void deleteEdge(Edge edge) {
        try {
            edgeDAO.delete(edge.getNodeAId(), edge.getNodeBId());
            nodeDAO.removeAdjacency(edge.getNodeAId(), edge.getNodeBId());
            undoManager.push(new Operation("delete edge", () -> {}, () -> {}));
            loadFromDB(); repaint();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    // ── coordinate helpers ────────────────────────────────────────────────────

    private double snapD(double v) { return Math.round(v / GRID_STEP) * GRID_STEP; }

    private Point2D toWorld(int sx, int sy) { return toWorldWithZoom(sx, sy, zoom); }

    private Point2D toWorldWithZoom(int sx, int sy, double z) {
        int scx = getWidth() / 2, scy = getHeight() / 2;
        double tx = sx - scx, ty = sy - scy;
        double rad = -Math.toRadians(rotateDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Point2D.Double(camX + (cos * tx - sin * ty) / z,
                                  camY + (sin * tx + cos * ty) / z);
    }

    private Node findNodeAt(Point2D w) {
        double t = HIT_R / zoom;
        for (Node n : nodes)
            if (Math.hypot(n.getX() - w.getX(), n.getY() - w.getY()) <= t) return n;
        return null;
    }

    private Edge findEdgeAt(Point2D w) {
        double t = EDGE_HIT / zoom;
        for (Edge e : edges) {
            Node a = findById(e.getNodeAId()), b = findById(e.getNodeBId());
            if (a==null||b==null) continue;
            if (ptSegDist(w.getX(), w.getY(), a.getX(), a.getY(), b.getX(), b.getY()) <= t) return e;
        }
        return null;
    }

    private Node findById(long id) {
        for (Node n : nodes) if (n.getId() == id) return n;
        return null;
    }

    private double ptSegDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx-ax, dy = by-ay;
        if (dx==0&&dy==0) return Math.hypot(px-ax, py-ay);
        double t = Math.max(0, Math.min(1, ((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(px-(ax+t*dx), py-(ay+t*dy));
    }
}