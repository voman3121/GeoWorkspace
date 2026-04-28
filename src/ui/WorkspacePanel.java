package ui;

import db.dao.AdjacencyDAO;
import db.dao.EdgeDAO;
import db.dao.NodeDAO;
import model.Edge;
import model.Node;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

public class WorkspacePanel extends JPanel {

    // ── palette ───────────────────────────────────────────────────────────────
    private static final Color BG          = new Color(10,  12,  18);
    private static final Color GRID_MAJOR  = new Color(18,  26,  42);
    private static final Color GRID_MINOR  = new Color(14,  18,  28);
    private static final Color EDGE_COL    = new Color(0,   160, 140);
    private static final Color EDGE_HOVER  = new Color(0,   230, 190);
    private static final Color NODE_FILL   = new Color(15,  20,  35);
    private static final Color NODE_RING   = new Color(0,   200, 170);
    private static final Color NODE_SEL    = new Color(255, 200,  50);
    private static final Color NODE_HOVER  = new Color(80,  220, 200);
    private static final Color LABEL_COL   = new Color(180, 220, 210);

    // ── geometry ──────────────────────────────────────────────────────────────
    private static final int   R           = 7;    // node radius
    private static final int   HIT         = 12;   // click hit radius
    private static final float EDGE_HIT    = 7f;   // edge hover threshold px

    // ── state ─────────────────────────────────────────────────────────────────
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    private final NodeDAO      nodeDAO      = new NodeDAO();
    private final EdgeDAO      edgeDAO      = new EdgeDAO();
    private final AdjacencyDAO adjacencyDAO = new AdjacencyDAO();

    private Node selectedNode = null;
    private Node hoveredNode  = null;
    private Edge hoveredEdge  = null;

    // ── constructor ───────────────────────────────────────────────────────────
    public WorkspacePanel() {
        setBackground(BG);
        setFocusable(true);
        loadData();
        setupMouse();
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setInitialDelay(250);
        ToolTipManager.sharedInstance().setDismissDelay(8000);
        System.out.println("[UI] WorkspacePanel ready. Click canvas to place nodes.");
    }

    // ── DB load ───────────────────────────────────────────────────────────────
    private void loadData() {
        try {
            nodes.clear();
            edges.clear();
            nodes.addAll(nodeDAO.getAll());
            edges.addAll(edgeDAO.getAll());
            System.out.printf("[DB] Loaded %d nodes, %d edges%n", nodes.size(), edges.size());
        } catch (Exception e) {
            System.err.println("[DB] Load error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── mouse ─────────────────────────────────────────────────────────────────
    private void setupMouse() {

        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                System.out.printf("[MOUSE] Click at (%d, %d) button=%d%n",
                        e.getX(), e.getY(), e.getButton());

                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e.getX(), e.getY());
                    return;
                }

                Node hit = findNodeAt(e.getX(), e.getY());
                try {
                    if (hit == null) {
                        // ── place new node ────────────────────────────────
                        String label = "N" + (nodes.size() + 1);
                        Node newNode = nodeDAO.insert(new Node(e.getX(), e.getY(), label));
                        nodes.add(newNode);
                        System.out.printf("[NODE] Created %s id=%d at (%d,%d)%n",
                                label, newNode.getId(), e.getX(), e.getY());

                    } else if (selectedNode == null) {
                        // ── first selection ───────────────────────────────
                        selectedNode = hit;
                        System.out.printf("[SELECT] Node #%d selected as source%n", hit.getId());

                    } else if (selectedNode.getId() == hit.getId()) {
                        // ── deselect ──────────────────────────────────────
                        selectedNode = null;
                        System.out.println("[SELECT] Deselected");

                    } else {
                        // ── draw edge ─────────────────────────────────────
                        Edge edge = edgeDAO.insert(new Edge(selectedNode.getId(), hit.getId()));
                        edges.add(edge);
                        adjacencyDAO.insertBidirectional(selectedNode.getId(), hit.getId());
                        System.out.printf("[EDGE] Created edge #%d: %d <-> %d%n",
                                edge.getId(), selectedNode.getId(), hit.getId());
                        selectedNode = null;
                    }
                } catch (Exception ex) {
                    System.err.println("[ERROR] " + ex.getMessage());
                    ex.printStackTrace();
                }
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Node prevNode = hoveredNode;
                Edge prevEdge = hoveredEdge;

                hoveredNode = findNodeAt(e.getX(), e.getY());
                hoveredEdge = (hoveredNode == null) ? findEdgeAt(e.getX(), e.getY()) : null;

                if (hoveredNode != prevNode || hoveredEdge != prevEdge) repaint();

                setCursor(hoveredNode != null || hoveredEdge != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });
    }

    private void handleRightClick(int x, int y) {
        Node hit = findNodeAt(x, y);
        if (hit == null) return;
        int opt = JOptionPane.showConfirmDialog(this,
                "Delete node " + hit.getLabel() + " and all its edges?",
                "Delete", JOptionPane.YES_NO_OPTION);
        if (opt != JOptionPane.YES_OPTION) return;
        try {
            // Remove edges that reference this node
            edges.removeIf(ed -> ed.getNodeAId() == hit.getId() || ed.getNodeBId() == hit.getId());
            nodes.remove(hit);
            // Reload from DB after cascade delete would need full DAO support;
            // for now just remove locally and reload
            loadData();
            if (selectedNode != null && selectedNode.getId() == hit.getId()) selectedNode = null;
            repaint();
            System.out.printf("[NODE] Deleted node #%d%n", hit.getId());
        } catch (Exception ex) {
            System.err.println("[ERROR] Delete: " + ex.getMessage());
        }
    }

    // ── tooltip ───────────────────────────────────────────────────────────────
    @Override
    public String getToolTipText(MouseEvent e) {
        Node n = findNodeAt(e.getX(), e.getY());
        if (n != null) {
            try {
                List<Node> adj = adjacencyDAO.getAdjacentNodes(n.getId());
                return HoverTooltipManager.buildNodeTooltip(n, adj);
            } catch (Exception ex) {
                return "DB error: " + ex.getMessage();
            }
        }
        Edge ed = findEdgeAt(e.getX(), e.getY());
        if (ed != null) {
            Node a = findById(ed.getNodeAId());
            Node b = findById(ed.getNodeBId());
            if (a != null && b != null)
                return HoverTooltipManager.buildEdgeTooltip(ed, a, b);
        }
        return null;
    }

    // ── painting ──────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g2);
        drawEdges(g2);
        drawNodes(g2);
    }

    private void drawGrid(Graphics2D g2) {
        int minorStep = 30, majorStep = 150;

        // minor grid
        g2.setColor(GRID_MINOR);
        g2.setStroke(new BasicStroke(0.4f));
        for (int x = 0; x < getWidth();  x += minorStep) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += minorStep) g2.drawLine(0, y, getWidth(), y);

        // major grid
        g2.setColor(GRID_MAJOR);
        g2.setStroke(new BasicStroke(0.8f));
        for (int x = 0; x < getWidth();  x += majorStep) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += majorStep) g2.drawLine(0, y, getWidth(), y);
    }

    private void drawEdges(Graphics2D g2) {
        for (Edge e : edges) {
            Node a = findById(e.getNodeAId());
            Node b = findById(e.getNodeBId());
            if (a == null || b == null) continue;

            boolean hov = hoveredEdge != null && hoveredEdge.getId() == e.getId();
            g2.setColor(hov ? EDGE_HOVER : EDGE_COL);
            g2.setStroke(new BasicStroke(hov ? 2.2f : 1.5f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(a.getX(), a.getY(), b.getX(), b.getY()));
        }
    }

    private void drawNodes(Graphics2D g2) {
        for (Node n : nodes) {
            int cx = (int) n.getX(), cy = (int) n.getY();

            boolean isSel = selectedNode != null && selectedNode.getId() == n.getId();
            boolean isHov = hoveredNode  != null && hoveredNode.getId()  == n.getId();

            Color ring = isSel ? NODE_SEL : (isHov ? NODE_HOVER : NODE_RING);

            // outer glow ring (selected only)
            if (isSel) {
                g2.setColor(new Color(255, 200, 50, 35));
                g2.fill(new Ellipse2D.Double(cx - R - 7, cy - R - 7, (R + 7) * 2, (R + 7) * 2));
            }

            // node body
            g2.setColor(NODE_FILL);
            g2.fill(new Ellipse2D.Double(cx - R, cy - R, R * 2, R * 2));

            // ring stroke
            g2.setColor(ring);
            g2.setStroke(new BasicStroke(isSel ? 2.0f : 1.4f));
            g2.draw(new Ellipse2D.Double(cx - R, cy - R, R * 2, R * 2));

            // crosshair dot at centre
            g2.setColor(ring);
            g2.fill(new Ellipse2D.Double(cx - 1.5, cy - 1.5, 3, 3));

            // label — small, above node
            g2.setColor(LABEL_COL);
            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            String lbl = n.getLabel() != null ? n.getLabel() : "";
            g2.drawString(lbl, cx - fm.stringWidth(lbl) / 2, cy - R - 4);
        }
    }

    // ── hit testing ───────────────────────────────────────────────────────────
    private Node findNodeAt(int x, int y) {
        for (Node n : nodes) {
            double dx = n.getX() - x, dy = n.getY() - y;
            if (Math.sqrt(dx * dx + dy * dy) <= HIT) return n;
        }
        return null;
    }

    private Edge findEdgeAt(int x, int y) {
        for (Edge e : edges) {
            Node a = findById(e.getNodeAId());
            Node b = findById(e.getNodeBId());
            if (a == null || b == null) continue;
            if (ptSegDist(x, y, a.getX(), a.getY(), b.getX(), b.getY()) <= EDGE_HIT) return e;
        }
        return null;
    }

    private Node findById(long id) {
        for (Node n : nodes) if (n.getId() == id) return n;
        return null;
    }

    private double ptSegDist(double px, double py,
                             double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
}