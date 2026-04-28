package ui;

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

    private static final Color BG = new Color(10,12,18);
    private static final Color GRID_MAJOR = new Color(18,26,42);
    private static final Color GRID_MINOR = new Color(14,18,28);
    private static final Color EDGE_COL = new Color(0,160,140);
    private static final Color EDGE_HOVER = new Color(0,230,190);
    private static final Color NODE_FILL = new Color(15,20,35);
    private static final Color NODE_RING = new Color(0,200,170);
    private static final Color NODE_SEL = new Color(255,200,50);
    private static final Color NODE_HOVER = new Color(80,220,200);
    private static final Color LABEL_COL = new Color(180,220,210);

    private static final int GRID = 30;
    private static final int R = 7;
    private static final int HIT = 12;
    private static final float EDGE_HIT = 7f;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    private final NodeDAO nodeDAO = new NodeDAO();
    private final EdgeDAO edgeDAO = new EdgeDAO();

    private Node selectedNode = null;
    private Node hoveredNode = null;
    private Edge hoveredEdge = null;

    public WorkspacePanel() {
        setBackground(BG);
        loadData();
        setupMouse();
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    private void loadData() {
        try {
            nodes.clear();
            edges.clear();
            nodes.addAll(nodeDAO.getAll());
            edges.addAll(edgeDAO.getAll());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupMouse() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleDelete(e.getX(), e.getY());
                    return;
                }

                int gx = Math.round((float)e.getX() / GRID);
                int gy = Math.round((float)e.getY() / GRID);
                int sx = gx * GRID;
                int sy = gy * GRID;

                try {
                    Node hit = findNodeAt(e.getX(), e.getY());

                    if (hit == null) {
                        if (nodeDAO.findByGrid(gx, gy) != null) return;
                        Node n = nodeDAO.insert(new Node(gx, gy, sx, sy, "N" + (nodes.size()+1)));
                        nodes.add(n);
                    } else if (selectedNode == null) {
                        selectedNode = hit;
                    } else if (selectedNode.getId() == hit.getId()) {
                        selectedNode = null;
                    } else {
                        if (selectedNode.getDegree() >= 4 || hit.getDegree() >= 4) {
                            JOptionPane.showMessageDialog(null, "Max degree (4) reached.");
                            return;
                        }

                        double len = Math.hypot(
                                hit.getScreenX() - selectedNode.getScreenX(),
                                hit.getScreenY() - selectedNode.getScreenY()
                        );

                        Edge edge = edgeDAO.insert(new Edge(selectedNode.getId(), hit.getId(), len));
                        edges.add(edge);

                        updateAdjacency(selectedNode, hit);
                        updateAdjacency(hit, selectedNode);

                        selectedNode = null;
                    }
                    repaint();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoveredNode = findNodeAt(e.getX(), e.getY());
                hoveredEdge = hoveredNode == null ? findEdgeAt(e.getX(), e.getY()) : null;
                repaint();
            }
        });
    }

    private void updateAdjacency(Node a, Node b) throws Exception {
        String adj = a.getAdjacentNodes();
        adj = adj.isBlank() ? String.valueOf(b.getId()) : adj + "," + b.getId();
        a.setAdjacentNodes(adj);
        a.setDegree(a.getDegree() + 1);
        nodeDAO.updateAdjacency(a.getId(), a.getAdjacentNodes(), a.getDegree());
    }

    private void handleDelete(int x, int y) {
        try {
            Node n = findNodeAt(x, y);
            if (n != null) {
                edgeDAO.deleteByNode(n.getId());
                nodeDAO.delete(n.getId());
                loadData();
                repaint();
                return;
            }

            Edge e = findEdgeAt(x, y);
            if (e != null) {
                edgeDAO.delete(e.getId());
                loadData();
                repaint();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Node n = findNodeAt(e.getX(), e.getY());
        if (n != null) return HoverTooltipManager.buildNodeTooltip(n);

        Edge ed = findEdgeAt(e.getX(), e.getY());
        if (ed != null) {
            Node a = findById(ed.getNodeAId());
            Node b = findById(ed.getNodeBId());
            return HoverTooltipManager.buildEdgeTooltip(ed, a, b);
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        drawGrid(g2);
        drawEdges(g2);
        drawNodes(g2);
    }

    private void drawGrid(Graphics2D g2) {
        for (int x = 0; x < getWidth(); x += GRID) {
            g2.setColor(x % (GRID * 5) == 0 ? GRID_MAJOR : GRID_MINOR);
            g2.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += GRID) {
            g2.setColor(y % (GRID * 5) == 0 ? GRID_MAJOR : GRID_MINOR);
            g2.drawLine(0, y, getWidth(), y);
        }
    }

    private void drawEdges(Graphics2D g2) {
        for (Edge e : edges) {
            Node a = findById(e.getNodeAId());
            Node b = findById(e.getNodeBId());
            if (a == null || b == null) continue;
            g2.setColor(hoveredEdge != null && hoveredEdge.getId() == e.getId() ? EDGE_HOVER : EDGE_COL);
            g2.draw(new Line2D.Double(a.getScreenX(), a.getScreenY(), b.getScreenX(), b.getScreenY()));
        }
    }

    private void drawNodes(Graphics2D g2) {
        for (Node n : nodes) {
            int x = (int) n.getScreenX(), y = (int) n.getScreenY();
            Color ring = selectedNode != null && selectedNode.getId() == n.getId() ? NODE_SEL
                    : hoveredNode != null && hoveredNode.getId() == n.getId() ? NODE_HOVER : NODE_RING;

            g2.setColor(NODE_FILL);
            g2.fill(new Ellipse2D.Double(x - R, y - R, R * 2, R * 2));
            g2.setColor(ring);
            g2.draw(new Ellipse2D.Double(x - R, y - R, R * 2, R * 2));

            g2.setColor(LABEL_COL);
            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            g2.drawString(n.getLabel(), x - 8, y - 12);
        }
    }

    private Node findNodeAt(int x, int y) {
        for (Node n : nodes) {
            if (Math.hypot(n.getScreenX() - x, n.getScreenY() - y) <= HIT) return n;
        }
        return null;
    }

    private Edge findEdgeAt(int x, int y) {
        for (Edge e : edges) {
            Node a = findById(e.getNodeAId());
            Node b = findById(e.getNodeBId());
            if (a == null || b == null) continue;
            if (ptSegDist(x, y, a.getScreenX(), a.getScreenY(), b.getScreenX(), b.getScreenY()) <= EDGE_HIT) return e;
        }
        return null;
    }

    private Node findById(long id) {
        for (Node n : nodes) if (n.getId() == id) return n;
        return null;
    }

    private double ptSegDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
}