package ui;

import db.dao.EdgeDAO;
import db.dao.NodeDAO;
import db.dao.ShapeDAO;
import model.Edge;
import model.Node;
import model.Shape;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Image auto-tracer powered by OpenCV.
 * Commit opens a brand-new WorkspaceFrame (separate DB-backed workspace window)
 * rather than touching whatever the user already has open.
 */
public class ImageTracer {

    private static final int    HOUGH_MIN_LINE_LEN      = 30;
    private static final int    HOUGH_MAX_LINE_GAP      = 12;
    private static final double SNAP_DIST               = 20.0;
    private static final double COLLINEAR_ANGLE_TOL      = 8.0;
    private static final double COLLINEAR_DIST_TOL       = 8.0;
    private static final int    CIRCLE_MIN_R             = 12;
    private static final int    CIRCLE_MAX_R             = 500;
    private static final double POLY_APPROX_EPSILON_PCT  = 0.04;
    private static final int    GRID                     = 40;
    private static final double ON_EDGE_DIST_TOL         = 8.0;

    private final NodeDAO  nodeDAO  = new NodeDAO();
    private final EdgeDAO  edgeDAO  = new EdgeDAO();
    private final ShapeDAO shapeDAO = new ShapeDAO();

    public interface StatusCallback { void update(String msg); }

    private static boolean openCvLoaded = false;
    private static synchronized void ensureOpenCvLoaded() {
        if (openCvLoaded) return;
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            openCvLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException(
                "OpenCV native library not found.\n"
                + "Make sure opencv_java*.dll is in your lib/ folder and you launched with:\n"
                + "  java -Djava.library.path=lib -cp \"...\" app.Main", e);
        }
    }

    /** parentCanvas is only used to anchor dialogs — its workspace is never touched. */
    public void run(Component parentCanvas, Runnable onDone) {
        ensureOpenCvLoaded();
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select hand-drawn sketch");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Images (PNG, JPG, BMP)", "png", "jpg", "jpeg", "bmp"));
        if (fc.showOpenDialog(parentCanvas) != JFileChooser.APPROVE_OPTION) return;
        runTrace(parentCanvas, fc.getSelectedFile(), onDone);
    }

    private void runTrace(Component parent, File file, Runnable onDone) {
        JDialog prog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent),
                "Tracing sketch…", false);
        JLabel statusLbl = new JLabel("Loading…", SwingConstants.CENTER);
        statusLbl.setFont(new Font("Consolas", Font.PLAIN, 12));
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true);
        prog.setLayout(new BorderLayout(8, 8));
        prog.add(statusLbl, BorderLayout.CENTER);
        prog.add(bar, BorderLayout.SOUTH);
        prog.setSize(380, 90);
        prog.setLocationRelativeTo(parent);
        prog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        prog.setVisible(true);

        SwingWorker<TraceResult, String> worker = new SwingWorker<>() {
            @Override protected void process(List<String> chunks) {
                statusLbl.setText(chunks.get(chunks.size() - 1));
            }
            @Override protected TraceResult doInBackground() throws Exception {
                return trace(file, msg -> publish(msg));
            }
            @Override protected void done() {
                prog.dispose();
                try {
                    showPreview(parent, file, get(), onDone);
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(parent,
                        "Trace failed: " + cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    cause.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    // ── pipeline ──────────────────────────────────────────────────────────────
    private TraceResult trace(File file, StatusCallback cb) throws Exception {
        cb.update("Loading image…");
        Mat original = Imgcodecs.imread(file.getAbsolutePath());
        if (original.empty()) throw new Exception("Cannot read image — unsupported format or corrupt file.");
        int w = original.cols(), h = original.rows();

        cb.update("Converting to greyscale…");
        Mat grey = new Mat();
        Imgproc.cvtColor(original, grey, Imgproc.COLOR_BGR2GRAY);

        cb.update("Denoising (bilateral filter)…");
        Mat denoised = new Mat();
        Imgproc.bilateralFilter(grey, denoised, 9, 50, 50);

        cb.update("Enhancing contrast (CLAHE)…");
        Mat enhanced = new Mat();
        Imgproc.createCLAHE(2.5, new Size(8, 8)).apply(denoised, enhanced);

        cb.update("Computing adaptive Canny thresholds…");
        double medianVal = median(enhanced);
        double lower = Math.max(0, 0.66 * medianVal);
        double upper = Math.min(255, 1.33 * medianVal);
        if (upper - lower < 20) { lower = 30; upper = 90; }

        cb.update("Canny edge detection…");
        Mat edges = new Mat();
        Imgproc.Canny(enhanced, edges, lower, upper, 3, true);

        cb.update("Closing small gaps (morphology)…");
        Mat closed = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel);

        cb.update("Detecting lines (probabilistic Hough)…");
        Mat linesMat = new Mat();
        double houghThresh = estimateHoughThreshold(w, h);
        Imgproc.HoughLinesP(closed, linesMat, 1, Math.PI / 180, (int) houghThresh,
                HOUGH_MIN_LINE_LEN, HOUGH_MAX_LINE_GAP);
        List<double[]> rawLines = matToLines(linesMat);

        cb.update("Detecting circles (Hough gradient)…");
        Mat circlesMat = new Mat();
        Imgproc.HoughCircles(enhanced, circlesMat, Imgproc.HOUGH_GRADIENT, 1.0,
                Math.min(w, h) / 16.0, 80, 35, CIRCLE_MIN_R, CIRCLE_MAX_R);
        List<double[]> circles = matToCircles(circlesMat);

        cb.update("Finding contours & corners…");
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        List<List<double[]>> polygons = approximatePolygons(contours);

        cb.update("Merging duplicate & collinear lines…");
        rawLines = mergeCollinear(rawLines);
        rawLines = removeLinesInsideCircles(rawLines, circles);

        cb.update("Fusing contour corners with line endpoints…");
        List<double[]> nodePoints = fusePoints(rawLines, polygons, circles);
        nodePoints = snapPoints(nodePoints);

        cb.update("Removing interior ghost nodes…");
        nodePoints = removeInteriorPoints(nodePoints, rawLines);

        cb.update("Building shape candidates…");
        List<ShapeCandidate> shapeCandidates = buildShapeCandidates(polygons, nodePoints);

        original.release(); grey.release(); denoised.release(); enhanced.release();
        edges.release(); closed.release(); linesMat.release(); circlesMat.release();
        hierarchy.release();

        BufferedImage previewImg = matToBufferedImage(Imgcodecs.imread(file.getAbsolutePath()));
        return new TraceResult(previewImg, w, h, rawLines, circles, nodePoints, shapeCandidates);
    }

    private double estimateHoughThreshold(int w, int h) {
        double diag = Math.hypot(w, h);
        return Math.max(25, Math.min(80, diag / 14.0));
    }

    private double median(Mat grey) {
        Mat hist = new Mat();
        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat range = new MatOfFloat(0, 256);
        Imgproc.calcHist(List.of(grey), new MatOfInt(0), new Mat(), hist, histSize, range);
        double total = grey.rows() * grey.cols();
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += hist.get(i, 0)[0];
            if (sum >= total / 2.0) return i;
        }
        return 128;
    }

    private List<double[]> matToLines(Mat linesMat) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < linesMat.rows(); i++) {
            double[] v = linesMat.get(i, 0);
            out.add(new double[]{v[0], v[1], v[2], v[3]});
        }
        return out;
    }

    private List<double[]> matToCircles(Mat circlesMat) {
        List<double[]> out = new ArrayList<>();
        if (circlesMat.cols() == 0) return out;
        float[] data = new float[(int) (circlesMat.total() * circlesMat.channels())];
        circlesMat.get(0, 0, data);
        for (int i = 0; i < data.length; i += 3)
            out.add(new double[]{data[i], data[i + 1], data[i + 2]});
        return out;
    }

    private List<List<double[]>> approximatePolygons(List<MatOfPoint> contours) {
        List<List<double[]>> polys = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < 200) continue;
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, POLY_APPROX_EPSILON_PCT * perimeter, true);
            org.opencv.core.Point[] pts = approx.toArray();
            if (pts.length >= 3 && pts.length <= 12) {
                List<double[]> poly = new ArrayList<>();
                for (org.opencv.core.Point p : pts) poly.add(new double[]{p.x, p.y});
                polys.add(poly);
            }
            contour2f.release(); approx.release();
        }
        return polys;
    }

    private List<double[]> mergeCollinear(List<double[]> lines) {
        List<double[]> merged = new ArrayList<>(lines);
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < merged.size(); i++) {
                for (int j = i + 1; j < merged.size(); j++) {
                    double[] a = merged.get(i), b = merged.get(j);
                    if (isCollinearAndClose(a, b)) {
                        merged.set(i, combineLine(a, b));
                        merged.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        return merged;
    }

    private boolean isCollinearAndClose(double[] a, double[] b) {
        double angA = Math.toDegrees(Math.atan2(a[3] - a[1], a[2] - a[0]));
        double angB = Math.toDegrees(Math.atan2(b[3] - b[1], b[2] - b[0]));
        double dAng = Math.abs(angA - angB) % 180;
        if (dAng > COLLINEAR_ANGLE_TOL && dAng < 180 - COLLINEAR_ANGLE_TOL) return false;
        double d1 = ptLineDist(b[0], b[1], a);
        double d2 = ptLineDist(b[2], b[3], a);
        return Math.min(d1, d2) < COLLINEAR_DIST_TOL && segmentsOverlapOrClose(a, b);
    }

    private boolean segmentsOverlapOrClose(double[] a, double[] b) {
        double maxGap = SNAP_DIST * 2.5;
        return dist(a[0],a[1],b[0],b[1]) < maxGap || dist(a[0],a[1],b[2],b[3]) < maxGap ||
               dist(a[2],a[3],b[0],b[1]) < maxGap || dist(a[2],a[3],b[2],b[3]) < maxGap ||
               (ptLineDist(b[0],b[1],a) < COLLINEAR_DIST_TOL && ptLineDist(b[2],b[3],a) < COLLINEAR_DIST_TOL);
    }

    private double[] combineLine(double[] a, double[] b) {
        double[][] pts = {{a[0],a[1]},{a[2],a[3]},{b[0],b[1]},{b[2],b[3]}};
        double maxD = -1; int bi=0, bj=0;
        for (int i=0;i<4;i++) for (int j=i+1;j<4;j++) {
            double d = dist(pts[i][0],pts[i][1],pts[j][0],pts[j][1]);
            if (d>maxD) { maxD=d; bi=i; bj=j; }
        }
        return new double[]{pts[bi][0],pts[bi][1],pts[bj][0],pts[bj][1]};
    }

    private List<double[]> removeLinesInsideCircles(List<double[]> lines, List<double[]> circles) {
        if (circles.isEmpty()) return lines;
        List<double[]> kept = new ArrayList<>();
        for (double[] l : lines) {
            double mx=(l[0]+l[2])/2, my=(l[1]+l[3])/2;
            boolean onCircle=false;
            for (double[] c : circles) {
                double dCenter = dist(mx,my,c[0],c[1]);
                if (Math.abs(dCenter - c[2]) < c[2]*0.15) { onCircle=true; break; }
            }
            if (!onCircle) kept.add(l);
        }
        return kept;
    }

    private List<double[]> fusePoints(List<double[]> lines, List<List<double[]>> polygons,
                                       List<double[]> circles) {
        List<double[]> pts = new ArrayList<>();
        for (double[] l : lines) {
            pts.add(new double[]{l[0], l[1]});
            pts.add(new double[]{l[2], l[3]});
        }
        for (List<double[]> poly : polygons) pts.addAll(poly);
        for (double[] c : circles) pts.add(new double[]{c[0], c[1]});
        return pts;
    }

    private List<double[]> removeInteriorPoints(List<double[]> pts, List<double[]> lines) {
        List<double[]> kept = new ArrayList<>();
        for (double[] p : pts) {
            boolean interior = false;
            for (double[] l : lines) {
                double segLen = dist(l[0], l[1], l[2], l[3]);
                if (segLen < 1e-3) continue;
                double perp = ptLineDist(p[0], p[1], l);
                if (perp > ON_EDGE_DIST_TOL) continue;
                double dA = dist(p[0], p[1], l[0], l[1]);
                double dB = dist(p[0], p[1], l[2], l[3]);
                if (dA > SNAP_DIST && dB > SNAP_DIST && (dA + dB) < segLen + SNAP_DIST) {
                    interior = true;
                    break;
                }
            }
            if (!interior) kept.add(p);
        }
        return kept;
    }

    private List<double[]> snapPoints(List<double[]> pts) {
        List<double[]> result = new ArrayList<>();
        for (double[] p : pts) {
            boolean merged=false;
            for (double[] r : result) {
                if (dist(p[0],p[1],r[0],r[1]) < SNAP_DIST) {
                    r[0]=(r[0]+p[0])/2; r[1]=(r[1]+p[1])/2; merged=true; break;
                }
            }
            if (!merged) result.add(new double[]{p[0],p[1]});
        }
        return result;
    }

    private List<ShapeCandidate> buildShapeCandidates(List<List<double[]>> polygons, List<double[]> nodePoints) {
        List<ShapeCandidate> candidates = new ArrayList<>();
        for (List<double[]> poly : polygons) {
            List<Integer> nodeIdx = new ArrayList<>();
            for (double[] corner : poly) {
                int idx = nearestIdx(nodePoints, corner[0], corner[1]);
                if (idx >= 0 && !nodeIdx.contains(idx)) nodeIdx.add(idx);
            }
            if (nodeIdx.size() >= 3) {
                String type = switch (nodeIdx.size()) {
                    case 3 -> "Triangle";
                    case 4 -> "Square";
                    case 5 -> "Pentagon";
                    case 6 -> "Hexagon";
                    default -> "Free-Polygon";
                };
                candidates.add(new ShapeCandidate(type, nodeIdx));
            }
        }
        return candidates;
    }

    private double ptLineDist(double px,double py,double[]l) {
        double dx=l[2]-l[0], dy=l[3]-l[1], len2=dx*dx+dy*dy;
        if (len2==0) return dist(px,py,l[0],l[1]);
        double t=Math.max(0,Math.min(1,((px-l[0])*dx+(py-l[1])*dy)/len2));
        return dist(px,py,l[0]+t*dx,l[1]+t*dy);
    }
    private double dist(double x1,double y1,double x2,double y2){ return Math.hypot(x1-x2,y1-y2); }
    private int nearestIdx(List<double[]> pts, double x, double y) {
        int best=-1; double bestD=SNAP_DIST*3;
        for (int i=0;i<pts.size();i++) {
            double d=dist(pts.get(i)[0],pts.get(i)[1],x,y);
            if (d<bestD){bestD=d;best=i;}
        }
        return best;
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        Mat rgb = new Mat();
        Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_BGR2RGB);
        int w = rgb.cols(), h = rgb.rows();
        byte[] data = new byte[w * h * (int) rgb.elemSize()];
        rgb.get(0, 0, data);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[w * h];
        for (int i = 0, j = 0; i < px.length; i++, j += 3) {
            int r = data[j] & 0xff, g = data[j+1] & 0xff, b = data[j+2] & 0xff;
            px[i] = (r << 16) | (g << 8) | b;
        }
        img.setRGB(0, 0, w, h, px, 0, w);
        rgb.release();
        return img;
    }

    // ── preview dialog ────────────────────────────────────────────────────────
    private void showPreview(Component parent, File file, TraceResult result, Runnable onDone) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent),
                "Trace Preview — OpenCV pipeline result", true);
        dlg.setSize(Math.min(result.w + 120, 1280), Math.min(result.h + 200, 880));
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(6, 6));
        dlg.getContentPane().setBackground(new Color(8, 10, 18));

        JPanel canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double sx = (double)(getWidth()-20)/result.w, sy=(double)(getHeight()-20)/result.h;
                double s = Math.min(sx, sy);
                int ox=(int)((getWidth()-result.w*s)/2), oy=(int)((getHeight()-result.h*s)/2);

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
                g2.drawImage(result.src, ox, oy, (int)(result.w*s), (int)(result.h*s), null);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

                g2.setColor(new Color(60, 220, 120, 200));
                g2.setStroke(new BasicStroke(2.2f));
                for (ShapeCandidate sc : result.shapes) {
                    Path2D p = new Path2D.Double();
                    boolean first=true;
                    for (int idx : sc.nodeIndices) {
                        double[] pt = result.points.get(idx);
                        if (first) { p.moveTo(ox+pt[0]*s, oy+pt[1]*s); first=false; }
                        else p.lineTo(ox+pt[0]*s, oy+pt[1]*s);
                    }
                    p.closePath();
                    g2.draw(p);
                }

                g2.setColor(new Color(0, 160, 255, 110));
                g2.setStroke(new BasicStroke(1.2f));
                for (double[] l : result.lines)
                    g2.drawLine((int)(ox+l[0]*s),(int)(oy+l[1]*s),(int)(ox+l[2]*s),(int)(oy+l[3]*s));

                g2.setColor(new Color(255, 140, 0, 220));
                g2.setStroke(new BasicStroke(2f));
                for (double[] c : result.circles) {
                    int cx=(int)(ox+c[0]*s), cy=(int)(oy+c[1]*s), r=(int)(c[2]*s);
                    g2.drawOval(cx-r, cy-r, r*2, r*2);
                }

                g2.setColor(new Color(255, 210, 30));
                for (double[] p : result.points) {
                    int px=(int)(ox+p[0]*s), py=(int)(oy+p[1]*s);
                    g2.fillOval(px-5, py-5, 10, 10);
                }
            }
        };
        canvas.setBackground(new Color(8, 10, 18));
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        dlg.add(scroll, BorderLayout.CENTER);

        JLabel info = new JLabel(String.format(
            "  Detected: %d shapes  •  %d lines  •  %d circles  •  %d nodes   (OpenCV pipeline)",
            result.shapes.size(), result.lines.size(), result.circles.size(), result.points.size()));
        info.setFont(new Font("Consolas", Font.PLAIN, 12));
        info.setForeground(new Color(0, 185, 255));
        info.setOpaque(true); info.setBackground(new Color(6, 8, 16));
        info.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        dlg.add(info, BorderLayout.NORTH);

        JPanel south = new JPanel(new BorderLayout(8, 4));
        south.setBackground(new Color(8, 10, 18));
        south.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        JLabel legend = new JLabel(
            "<html><span style='color:#3cdc78'>■</span> shapes &nbsp;"
            + "<span style='color:#00a0ff'>■</span> lines &nbsp;"
            + "<span style='color:#ff8c00'>■</span> circles &nbsp;"
            + "<span style='color:#ffd21e'>●</span> nodes &nbsp; "
            + "<span style='color:#888'>→ opens in a NEW workspace window</span></html>");
        legend.setFont(new Font("Consolas", Font.PLAIN, 10));
        south.add(legend, BorderLayout.WEST);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setBackground(new Color(8, 10, 18));
        JButton cancel = dlgBtn("CANCEL", new Color(35, 35, 50));
        JButton commit = dlgBtn("COMMIT → NEW WORKSPACE", new Color(0, 110, 190));
        btnRow.add(cancel); btnRow.add(commit);
        south.add(btnRow, BorderLayout.EAST);
        dlg.add(south, BorderLayout.SOUTH);

        cancel.addActionListener(e -> dlg.dispose());
        commit.addActionListener(e -> { dlg.dispose(); commitToNewWorkspace(result, parent, onDone); });
        dlg.setVisible(true);
    }

    // ── commit to a BRAND NEW workspace window (separate DB-backed frame) ─────
    private void commitToNewWorkspace(TraceResult result, Component parent, Runnable onDone) {
        double targetW=1600.0, targetH=1200.0;
        double scale=Math.min(targetW/result.w, targetH/result.h);
        double offX=-result.w*scale/2.0, offY=-result.h*scale/2.0;

        try {
            Map<Integer,Long> ptIdMap = new HashMap<>();
            List<Node> insertedNodes = new ArrayList<>();
            for (int i=0;i<result.points.size();i++) {
                double[] p = result.points.get(i);
                double wx=snap(offX+p[0]*scale), wy=snap(offY+p[1]*scale);
                Node n = nodeDAO.insert(new Node(wx, wy, "T"+(i+1)));
                ptIdMap.put(i, n.getId());
                insertedNodes.add(n);
            }

            int edgeCount=0;
            for (double[] line : result.lines) {
                int i1=nearestIdx(result.points, line[0], line[1]);
                int i2=nearestIdx(result.points, line[2], line[3]);
                if (i1<0||i2<0||i1==i2) continue;
                Long idA=ptIdMap.get(i1), idB=ptIdMap.get(i2);
                if (idA==null||idB==null) continue;
                Node na=nodeDAO.findById(idA), nb=nodeDAO.findById(idB);
                if (na==null||nb==null||na.hasNeighbour(idB)) continue;
                if (na.firstFreeSlot()<0||nb.firstFreeSlot()<0) continue;
                double wx1=offX+line[0]*scale, wy1=offY+line[1]*scale;
                double wx2=offX+line[2]*scale, wy2=offY+line[3]*scale;
                try {
                    edgeDAO.insert(new Edge(idA, idB, Math.hypot(wx2-wx1, wy2-wy1)));
                    nodeDAO.addAdjacency(idA, idB);
                    edgeCount++;
                } catch (Exception ignore) {}
            }

            int shapeCount=0;
            for (ShapeCandidate sc : result.shapes) {
                long[] ids = new long[sc.nodeIndices.size()];
                boolean ok=true;
                for (int k=0;k<sc.nodeIndices.size();k++) {
                    Long id = ptIdMap.get(sc.nodeIndices.get(k));
                    if (id==null) { ok=false; break; }
                    ids[k]=id;
                }
                if (!ok) continue;
                shapeDAO.insert(new Shape(sc.type+" "+(shapeCount+1), sc.type, ids, 0, 0, ""));
                shapeCount++;
            }

            int circleCount=0;
            for (double[] c : result.circles) {
                double wcx=offX+c[0]*scale, wcy=offY+c[1]*scale, wr=c[2]*scale;
                shapeDAO.insert(new Shape("Circle"+(++circleCount), "Circle", new long[0],
                        Math.PI*wr*wr, 2*Math.PI*wr, wcx+","+wcy+","+wr));
            }

            final int fn=insertedNodes.size(), fe=edgeCount, fs=shapeCount, fc=circleCount;
            SwingUtilities.invokeLater(() -> {
                // Open a completely separate workspace window — the caller's
                // existing canvas/state is never touched.
                WorkspaceFrame newWindow = new WorkspaceFrame();
                newWindow.setTitle("GeoWorkspace — Imported: " + result.points.size() + " nodes");
                newWindow.setVisible(true);

                JOptionPane.showMessageDialog(newWindow,
                    "Import complete!\n\n"+fn+" nodes  •  "+fe+" edges  •  "+fs+" shapes  •  "+fc+" circles\n\n"
                    +"Opened in this new workspace window.\nYour previous workspace is untouched.",
                    "Done", JOptionPane.INFORMATION_MESSAGE);

                onDone.run();
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent,
                "DB error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
        }
    }

    private double snap(double v){ return Math.round(v/GRID)*GRID; }
    private JButton dlgBtn(String t, Color bg) {
        JButton b=new JButton(t); b.setFont(new Font("Consolas",Font.BOLD,11));
        b.setForeground(Color.WHITE); b.setBackground(bg);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }

    private static class ShapeCandidate {
        final String type; final List<Integer> nodeIndices;
        ShapeCandidate(String t, List<Integer> n){ type=t; nodeIndices=n; }
    }
    private static class TraceResult {
        final BufferedImage src; final int w, h;
        final List<double[]> lines, circles, points;
        final List<ShapeCandidate> shapes;
        TraceResult(BufferedImage s,int w,int h,List<double[]>l,List<double[]>c,List<double[]>p,List<ShapeCandidate>sh){
            src=s; this.w=w; this.h=h; lines=l; circles=c; points=p; shapes=sh;
        }
    }
}