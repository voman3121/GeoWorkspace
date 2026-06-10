package ui;

import model.Node;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

public class ShapeValidator {

    public enum ShapeType {
        LINE     ("Line",     2, "Requires exactly 2 nodes"),
        TRIANGLE ("Triangle", 3, "Requires exactly 3 nodes"),
        SQUARE   ("Square",   4, "Requires exactly 4 nodes"),
        PENTAGON ("Pentagon", 5, "Requires exactly 5 nodes"),
        HEXAGON  ("Hexagon",  6, "Requires exactly 6 nodes");

        public final String displayName;
        public final int    requiredNodes;
        public final String hint;

        ShapeType(String name, int n, String hint) {
            this.displayName = name;
            this.requiredNodes = n;
            this.hint = hint;
        }
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String  message;
        ValidationResult(boolean v, String m) { valid = v; message = m; }
    }

    public static ValidationResult validate(ShapeType shape, List<Node> selected) {
        int n   = selected.size();
        int req = shape.requiredNodes;

        if (n == 0)   return new ValidationResult(false, "No nodes selected.");
        if (n < req)  return new ValidationResult(false,
            shape.displayName + " needs " + req + " nodes — you have " + n
            + ". Select " + (req - n) + " more.");
        if (n > req)  return new ValidationResult(false,
            shape.displayName + " needs exactly " + req + " nodes — deselect " + (n - req) + ".");

        // Duplicate check
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (selected.get(i).getId() == selected.get(j).getId())
                    return new ValidationResult(false, "Duplicate nodes in selection.");

        // For shapes with 3+ nodes, check that no two non-adjacent edges cross
        if (shape != ShapeType.LINE && shape != ShapeType.TRIANGLE) {
            String crossMsg = checkEdgeIntersections(selected);
            if (crossMsg != null)
                return new ValidationResult(false, crossMsg);
        }

        // For TRIANGLE specifically: just check they're not collinear
        if (shape == ShapeType.TRIANGLE) {
            if (collinear(selected.get(0), selected.get(1), selected.get(2)))
                return new ValidationResult(false, "The 3 nodes are collinear — not a valid triangle.");
        }

        return new ValidationResult(true, "Valid " + shape.displayName + "!");
    }

    /**
     * Checks if any two non-adjacent edges of the closed polygon (formed by
     * connecting nodes in order) intersect. Returns an error string, or null if clean.
     */
    private static String checkEdgeIntersections(List<Node> nodes) {
        int n = nodes.size();
        // Build edge list for closed polygon
        Line2D[] edges = new Line2D[n];
        for (int i = 0; i < n; i++) {
            Node a = nodes.get(i);
            Node b = nodes.get((i + 1) % n);
            edges[i] = new Line2D.Double(a.getX(), a.getY(), b.getX(), b.getY());
        }

        // Check every pair of non-adjacent edges
        for (int i = 0; i < n; i++) {
            for (int j = i + 2; j < n; j++) {
                // Skip adjacent edges (share an endpoint)
                if (i == 0 && j == n - 1) continue;
                if (edges[i].intersectsLine(edges[j])) {
                    Node a1 = nodes.get(i),     a2 = nodes.get((i + 1) % n);
                    Node b1 = nodes.get(j),     b2 = nodes.get((j + 1) % n);
                    return "Edge " + a1.getLabel() + "→" + a2.getLabel()
                         + " intersects edge " + b1.getLabel() + "→" + b2.getLabel()
                         + ".\nReorder node selection so edges don't cross.";
                }
            }
        }
        return null;
    }

    private static boolean collinear(Node a, Node b, Node c) {
        double area = (b.getX() - a.getX()) * (c.getY() - a.getY())
                    - (c.getX() - a.getX()) * (b.getY() - a.getY());
        return Math.abs(area) < 1e-6;
    }

    /**
     * Returns ordered edge index pairs for the shape (closed polygon, or open line).
     */
    public static int[][] edgePairs(ShapeType shape, int nodeCount) {
        if (shape == ShapeType.LINE) return new int[][]{{0, 1}};
        int[][] pairs = new int[nodeCount][2];
        for (int i = 0; i < nodeCount; i++)
            pairs[i] = new int[]{i, (i + 1) % nodeCount};
        return pairs;
    }

    /**
     * Finds the intersection point of two line segments, or null if they don't intersect.
     */
    public static Point2D segmentIntersection(double x1, double y1, double x2, double y2,
                                               double x3, double y3, double x4, double y4) {
        double d1x = x2 - x1, d1y = y2 - y1;
        double d2x = x4 - x3, d2y = y4 - y3;
        double cross = d1x * d2y - d1y * d2x;
        if (Math.abs(cross) < 1e-10) return null; // parallel
        double t = ((x3 - x1) * d2y - (y3 - y1) * d2x) / cross;
        double u = ((x3 - x1) * d1y - (y3 - y1) * d1x) / cross;
        if (t < 0 || t > 1 || u < 0 || u > 1) return null;
        return new Point2D.Double(x1 + t * d1x, y1 + t * d1y);
    }
}