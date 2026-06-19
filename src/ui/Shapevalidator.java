package ui;

import model.Node;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

public class ShapeValidator {

    public enum ShapeType {
        LINE        ("Line",         2, "Requires exactly 2 nodes"),
        TRIANGLE    ("Triangle",     3, "Requires exactly 3 nodes (any non-collinear triangle)"),
        RECTANGLE   ("Rectangle",    4, "Requires 4 nodes: right angles, opposite sides equal"),
        SQUARE      ("Square",       4, "Requires 4 nodes: equal sides, right angles"),
        PENTAGON    ("Pentagon",     5, "Requires 5 nodes: equal sides, no crossings"),
        HEXAGON     ("Hexagon",      6, "Requires 6 nodes: equal sides, no crossings"),
        ARC         ("Arc",          3, "Requires exactly 3 nodes: start, control, end"),
        SEMICIRCLE  ("Semi-circle",  0, "Click a center, then drag to set radius"),
        FREE_POLYGON("Free Polygon", 0, "Select 3+ nodes — any closed, non-self-intersecting shape");

        public final String displayName, hint;
        public final int requiredNodes;  // 0 = variable / handled outside the node-count model
        ShapeType(String n, int r, String h){ displayName=n; requiredNodes=r; hint=h; }
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String  message;
        ValidationResult(boolean v, String m){ valid=v; message=m; }
    }

    public static ValidationResult validate(ShapeType shape, List<Node> sel) {
        if (shape == ShapeType.FREE_POLYGON) return validateFreePolygon(sel);
        if (shape == ShapeType.SEMICIRCLE)
            return fail("Semi-circle is created via the Circle tool — click center, drag radius, then choose Semi-circle in the prompt.");

        int n=sel.size(), req=shape.requiredNodes;
        if (n==0) return fail("No nodes selected. Select "+req+" nodes in order.");
        if (n<req) return fail(shape.displayName+" needs "+req+" nodes — "+n+" selected. Pick "+(req-n)+" more.");
        if (n>req) return fail(shape.displayName+" needs exactly "+req+" — deselect "+(n-req)+".");
        for (int i=0;i<n;i++) for (int j=i+1;j<n;j++)
            if (sel.get(i).getId()==sel.get(j).getId()) return fail("Duplicate nodes in selection.");

        switch (shape) {
            case TRIANGLE   -> { return validateTriangle(sel); }
            case RECTANGLE  -> { return validateRectangle(sel); }
            case SQUARE     -> { return validateSquare(sel); }
            case PENTAGON   -> { return validatePolygon(sel, 5, "Pentagon"); }
            case HEXAGON    -> { return validatePolygon(sel, 6, "Hexagon"); }
            case ARC        -> { return validateArc(sel); }
            default         -> { return ok("Valid Line!"); }
        }
    }

    // ── Triangle: not collinear ───────────────────────────────────────────────
    private static ValidationResult validateTriangle(List<Node> s) {
        Node a=s.get(0),b=s.get(1),c=s.get(2);
        double area=Math.abs((b.getX()-a.getX())*(c.getY()-a.getY())-(c.getX()-a.getX())*(b.getY()-a.getY()));
        if (area<1e-2) return fail("The 3 nodes are collinear — not a valid triangle.");
        return ok(String.format("Valid Triangle. Area≈%.1f", area/2));
    }

    // ── Rectangle: right angles, opposite sides equal (NOT all sides equal) ──
    private static ValidationResult validateRectangle(List<Node> s) {
        String cross=checkEdgeIntersections(s);
        if (cross!=null) return fail(cross);
        double[] sides=new double[4];
        for (int i=0;i<4;i++) sides[i]=dist(s.get(i),s.get((i+1)%4));
        double tolOpp = Math.max(sides[0],sides[2]) * 0.18;
        double tolOpp2 = Math.max(sides[1],sides[3]) * 0.18;
        if (Math.abs(sides[0]-sides[2])>tolOpp)
            return fail(String.format("Opposite sides 1 & 3 differ too much (%.1f vs %.1f).", sides[0], sides[2]));
        if (Math.abs(sides[1]-sides[3])>tolOpp2)
            return fail(String.format("Opposite sides 2 & 4 differ too much (%.1f vs %.1f).", sides[1], sides[3]));
        for (int i=0;i<4;i++){
            Node prev=s.get((i+3)%4), cur=s.get(i), next=s.get((i+1)%4);
            double cosA=dotProduct(prev,cur,next);
            if (Math.abs(cosA)>0.30)
                return fail(String.format("Corner %d angle is not close enough to 90° for a rectangle.", i+1));
        }
        return ok(String.format("Valid Rectangle! Sides≈%.1f × %.1f", sides[0], sides[1]));
    }

    // ── Square: equal sides + right angles ────────────────────────────────────
    private static ValidationResult validateSquare(List<Node> s) {
        String cross=checkEdgeIntersections(s);
        if (cross!=null) return fail(cross);
        double[] sides=new double[4];
        for (int i=0;i<4;i++) sides[i]=dist(s.get(i),s.get((i+1)%4));
        double avg=(sides[0]+sides[1]+sides[2]+sides[3])/4;
        double tol=avg*0.18;
        for (int i=0;i<4;i++)
            if (Math.abs(sides[i]-avg)>tol)
                return fail(String.format(
                    "Side %d length (%.1f) differs too much from average (%.1f).\n"
                    +"For a square, all 4 sides must be roughly equal.",
                    i+1, sides[i], avg));
        for (int i=0;i<4;i++){
            Node prev=s.get((i+3)%4), cur=s.get(i), next=s.get((i+1)%4);
            double cosA=dotProduct(prev,cur,next);
            if (Math.abs(cosA)>0.30)
                return fail(String.format("Corner %d angle is not close enough to 90° for a square.", i+1));
        }
        return ok(String.format("Valid Square! Side≈%.1f", avg));
    }

    // ── Regular polygon: equal sides, no self-intersection ───────────────────
    private static ValidationResult validatePolygon(List<Node> s, int n, String name) {
        String cross=checkEdgeIntersections(s);
        if (cross!=null) return fail(cross);
        double[] sides=new double[n];
        double sum=0;
        for (int i=0;i<n;i++){ sides[i]=dist(s.get(i),s.get((i+1)%n)); sum+=sides[i]; }
        double avg=sum/n;
        double tol=avg*0.25;
        for (int i=0;i<n;i++)
            if (Math.abs(sides[i]-avg)>tol)
                return fail(String.format(
                    "Side %d (%.1f) differs too much from average (%.1f).\n"
                    +"All sides of a regular %s should be roughly equal.",
                    i+1, sides[i], avg, name));
        return ok(String.format("Valid %s! Side≈%.1f", name, avg));
    }

    // ── Arc: 3 non-collinear points — start, control, end ────────────────────
    private static ValidationResult validateArc(List<Node> s) {
        Node a=s.get(0),b=s.get(1),c=s.get(2);
        double area=Math.abs((b.getX()-a.getX())*(c.getY()-a.getY())-(c.getX()-a.getX())*(b.getY()-a.getY()));
        if (area<1e-2) return fail("Arc nodes are collinear — cannot form a curved arc.");
        return ok("Valid Arc — will draw a curve through the 3 points.");
    }

    // ── Free Polygon: any 3+ nodes forming a closed, non-self-intersecting loop ─
    private static ValidationResult validateFreePolygon(List<Node> s) {
        int n = s.size();
        if (n < 3) return fail("Free Polygon needs at least 3 nodes — "+n+" selected.");
        for (int i=0;i<n;i++) for (int j=i+1;j<n;j++)
            if (s.get(i).getId()==s.get(j).getId()) return fail("Duplicate nodes in selection.");
        String cross = checkEdgeIntersections(s);
        if (cross != null) return fail(cross + "\n(Free Polygon allows any shape, but edges still can't cross.)");
        double area = shoelaceArea(s);
        return ok(String.format("Valid Free Polygon! %d sides, area≈%.1f", n, area));
    }

    private static double shoelaceArea(List<Node> pts) {
        double a=0; int n=pts.size();
        for (int i=0;i<n;i++){ Node p=pts.get(i), q=pts.get((i+1)%n); a+=p.getX()*q.getY()-q.getX()*p.getY(); }
        return Math.abs(a)/2;
    }

    // ── Edge intersection check (for closed polygons) ─────────────────────────
    private static String checkEdgeIntersections(List<Node> nodes) {
        int n=nodes.size();
        Line2D[] el=new Line2D[n];
        for (int i=0;i<n;i++){Node a=nodes.get(i),b=nodes.get((i+1)%n);
            el[i]=new Line2D.Double(a.getX(),a.getY(),b.getX(),b.getY());}
        for (int i=0;i<n;i++) for (int j=i+2;j<n;j++){
            if(i==0&&j==n-1) continue;
            if(el[i].intersectsLine(el[j])){
                Node a1=nodes.get(i),a2=nodes.get((i+1)%n);
                Node b1=nodes.get(j),b2=nodes.get((j+1)%n);
                return "Edge "+a1.getLabel()+"→"+a2.getLabel()+" intersects "+b1.getLabel()+"→"+b2.getLabel()
                     +".\nReorder node selection so edges don't cross.";
            }
        }
        return null;
    }

    private static double dotProduct(Node prev, Node cur, Node next) {
        double ux=prev.getX()-cur.getX(), uy=prev.getY()-cur.getY();
        double vx=next.getX()-cur.getX(), vy=next.getY()-cur.getY();
        double magU=Math.hypot(ux,uy), magV=Math.hypot(vx,vy);
        if (magU<1e-6||magV<1e-6) return 0;
        return (ux*vx+uy*vy)/(magU*magV);
    }

    public static int[][] edgePairs(ShapeType shape, int n) {
        if (shape==ShapeType.LINE)         return new int[][]{{0,1}};
        if (shape==ShapeType.ARC)          return new int[][]{};   // drawn as curve
        if (shape==ShapeType.SEMICIRCLE)   return new int[][]{};   // drawn as arc from center+radius
        int[][] p=new int[n][2];
        for (int i=0;i<n;i++) p[i]=new int[]{i,(i+1)%n};
        return p;
    }

    public static Point2D segmentIntersection(double x1,double y1,double x2,double y2,
                                               double x3,double y3,double x4,double y4){
        double d1x=x2-x1,d1y=y2-y1,d2x=x4-x3,d2y=y4-y3;
        double cross=d1x*d2y-d1y*d2x;
        if(Math.abs(cross)<1e-10) return null;
        double t=((x3-x1)*d2y-(y3-y1)*d2x)/cross;
        double u=((x3-x1)*d1y-(y3-y1)*d1x)/cross;
        if(t<0||t>1||u<0||u>1) return null;
        return new Point2D.Double(x1+t*d1x,y1+t*d1y);
    }

    private static double dist(Node a, Node b){ return Math.hypot(a.getX()-b.getX(),a.getY()-b.getY()); }
    private static ValidationResult ok(String m)  { return new ValidationResult(true,  m); }
    private static ValidationResult fail(String m) { return new ValidationResult(false, m); }
}