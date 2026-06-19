package ui;

import db.dao.EdgeDAO;
import db.dao.NodeDAO;
import model.Edge;
import model.Node;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * AerofoilMesher
 * ==============
 * Dedicated CFD-style pipeline for aerofoil images, separate from ImageTracer:
 *
 * 1. Load image → greyscale + Otsu threshold → find largest closed contour
 * 2. Smooth & resample the contour to N evenly-spaced boundary points
 * 3. Build a structured O-grid mesh around it via transfinite interpolation
 * with geometric clustering near the surface
 * 4. Diffuse the interior grid via Laplacian relaxation passes
 * 5. Live preview dialog with adjustable resolution + RE-MESH
 * 6. COMMIT → writes all nodes + edges to the H2 database
 *
 * Requires OpenCV on classpath (already required by ImageTracer).
 */
public class AerofoilMesher {

    // ── pipeline parameters (user-adjustable via sliders) ─────────────────────
    private int    boundaryPts  = 120;   // points around the aerofoil
    private int    radialLayers = 30;    // mesh layers away from surface
    private double farfieldR    = 3.0;   // far-field radius as multiple of chord
    private int    smoothPasses = 80;    // Laplacian relaxation iterations

    private final NodeDAO nodeDAO = new NodeDAO();
    private final EdgeDAO edgeDAO = new EdgeDAO();

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

    // ── entry point ────────────────────────────────────────────────────────────
    public void run(WorkspacePanel canvas, Runnable onCommit) {
        ensureOpenCvLoaded();

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Aerofoil Image");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Images (png, jpg, bmp, tif)", "png", "jpg", "jpeg", "bmp", "tif", "tiff"));
        if (fc.showOpenDialog(canvas) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();

        JDialog progress = makeProgressDialog(canvas);
        JLabel  progLbl  = (JLabel) ((JPanel) progress.getContentPane()).getComponent(1);
        progress.setVisible(true);

        SwingWorker<MeshResult, String> worker = new SwingWorker<>() {
            @Override protected MeshResult doInBackground() throws Exception {
                publish("Loading image…");
                Mat src = Imgcodecs.imread(file.getAbsolutePath());
                if (src.empty()) throw new RuntimeException("Cannot load image: " + file.getName());

                publish("Extracting aerofoil boundary…");
                List<org.opencv.core.Point> boundary = AerofoilMesher.this.extractBoundary(src);
                if (boundary.size() < 10)
                    throw new RuntimeException("No closed aerofoil boundary found in image.");

                publish("Resampling boundary (" + boundaryPts + " pts)…");
                List<double[]> bPts = AerofoilMesher.this.resampleBoundary(boundary, boundaryPts, src.cols(), src.rows());

                publish("Building O-grid mesh (layers=" + radialLayers + ")…");
                double[][][] grid = AerofoilMesher.this.buildOGrid(bPts, radialLayers, farfieldR);

                publish("Smoothing mesh (" + smoothPasses + " passes)…");
                AerofoilMesher.this.smoothGrid(grid, smoothPasses);

                publish("Rendering preview…");
                BufferedImage preview = AerofoilMesher.this.renderPreviewInternal(bPts, grid);
                src.release();

                MeshResult r = new MeshResult(bPts, grid, preview);
                r.rawBoundary = boundary;
                r.imgW = src.cols(); r.imgH = src.rows();
                return r;
            }
            @Override protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) progLbl.setText(chunks.get(chunks.size()-1));
            }
            @Override protected void done() {
                progress.dispose();
                try {
                    MeshResult result = get();
                    AerofoilMesher.this.showPreviewDialog(canvas, result, onCommit);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(canvas,
                        "Meshing failed:\n" + cause.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                    cause.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    // ── STEP 1: boundary extraction ────────────────────────────────────────────
    private List<org.opencv.core.Point> extractBoundary(Mat src) {
        Mat grey = new Mat(), blur = new Mat(), thresh = new Mat();

        if (src.channels() == 3) Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGR2GRAY);
        else grey = src.clone();

        Imgproc.GaussianBlur(grey, blur, new Size(5, 5), 0);

        // Otsu auto-threshold — handles aerofoil white-on-dark or dark-on-white
        Imgproc.threshold(blur, thresh, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(7, 7));
        Mat closed = new Mat();
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

        MatOfPoint largest = null;
        double   maxArea   = 0;
        for (MatOfPoint c : contours) {
            double a = Imgproc.contourArea(c);
            if (a > maxArea) { maxArea = a; largest = c; }
        }

        grey.release(); blur.release(); thresh.release(); closed.release();
        kernel.release(); hierarchy.release();

        if (largest == null) return new ArrayList<>();
        return largest.toList();
    }

    // ── STEP 2: resample boundary to N equally-spaced points ──────────────────
    private List<double[]> resampleBoundary(List<org.opencv.core.Point> raw, int N, int imgW, int imgH) {
        int M = raw.size();
        double[] arc = new double[M + 1];
        arc[0] = 0;
        for (int i = 0; i < M; i++) {
            org.opencv.core.Point a = raw.get(i), b = raw.get((i + 1) % M);
            arc[i + 1] = arc[i] + Math.hypot(b.x - a.x, b.y - a.y);
        }
        double totalLen = arc[M];
        double step = totalLen / N;

        double scale = 600.0 / imgW;
        double cx = imgW / 2.0, cy = imgH / 2.0;

        List<double[]> pts = new ArrayList<>(N);
        int j = 0;
        for (int i = 0; i < N; i++) {
            double target = i * step;
            while (j < M - 1 && arc[j + 1] < target) j++;
            double t = (arc[j + 1] - arc[j]) < 1e-9 ? 0 :
                       (target - arc[j]) / (arc[j + 1] - arc[j]);
            org.opencv.core.Point a = raw.get(j), b = raw.get((j + 1) % M);
            double px = a.x + t * (b.x - a.x);
            double py = a.y + t * (b.y - a.y);
            pts.add(new double[]{ (px - cx) * scale, (py - cy) * scale });
        }
        return pts;
    }

    // ── STEP 3: O-grid mesh via transfinite interpolation ─────────────────────
    private double[][][] buildOGrid(List<double[]> bPts, int R, double farR) {
        int N = bPts.size();
        double[][][] g = new double[N][R + 1][2];

        double cx = 0, cy = 0;
        for (double[] p : bPts) { cx += p[0]; cy += p[1]; }
        cx /= N; cy /= N;

        double maxDist = 0;
        for (double[] p : bPts) maxDist = Math.max(maxDist, Math.hypot(p[0]-cx, p[1]-cy));
        double R_far = maxDist * farR * 2.5;

        for (int i = 0; i < N; i++) {
            g[i][0][0] = bPts.get(i)[0];
            g[i][0][1] = bPts.get(i)[1];
        }

        for (int i = 0; i < N; i++) {
            double angle = 2.0 * Math.PI * i / N;
            g[i][R][0] = cx + R_far * Math.cos(angle);
            g[i][R][1] = cy + R_far * Math.sin(angle);
        }

        for (int i = 0; i < N; i++) {
            double[] p0 = g[i][0];
            double[] pR = g[i][R];
            for (int j = 1; j < R; j++) {
                double t = geomStretch(j, R, 1.15);
                g[i][j][0] = p0[0] + t * (pR[0] - p0[0]);
                g[i][j][1] = p0[1] + t * (pR[1] - p0[1]);
            }
        }
        return g;
    }

    private double geomStretch(int j, int R, double ratio) {
        if (Math.abs(ratio - 1.0) < 1e-6) return (double) j / R;
        double num = Math.pow(ratio, j) - 1.0;
        double den = Math.pow(ratio, R) - 1.0;
        return num / den;
    }

    // ── STEP 4: Laplacian (diffusion) smoothing — boundaries frozen ───────────
    private void smoothGrid(double[][][] g, int passes) {
        int N = g.length, R = g[0].length - 1;
        double[][][] tmp = new double[N][R + 1][2];

        for (int pass = 0; pass < passes; pass++) {
            for (int i = 0; i < N; i++) {
                tmp[i][0][0] = g[i][0][0]; tmp[i][0][1] = g[i][0][1];
                tmp[i][R][0] = g[i][R][0]; tmp[i][R][1] = g[i][R][1];

                int iL = (i - 1 + N) % N;
                int iR = (i + 1) % N;
                for (int j = 1; j < R; j++) {
                    tmp[i][j][0] = 0.25 * (g[iL][j][0] + g[iR][j][0] + g[i][j-1][0] + g[i][j+1][0]);
                    tmp[i][j][1] = 0.25 * (g[iL][j][1] + g[iR][j][1] + g[i][j-1][1] + g[i][j+1][1]);
                }
            }
            for (int i = 0; i < N; i++)
                for (int j = 0; j <= R; j++) {
                    g[i][j][0] = tmp[i][j][0];
                    g[i][j][1] = tmp[i][j][1];
                }
        }
    }

    // ── STEP 5: preview dialog with live controls ──────────────────────────────
    private void showPreviewDialog(WorkspacePanel canvas, MeshResult result, Runnable onCommit) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(canvas),
                                   "Aerofoil Mesh Preview", true);
        dlg.setSize(960, 680);
        dlg.setLocationRelativeTo(canvas);
        dlg.setLayout(new BorderLayout(6, 6));
        dlg.getContentPane().setBackground(new Color(8, 10, 20));

        JLabel previewLabel = new JLabel(new ImageIcon(result.preview));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBackground(new Color(6, 8, 16));
        previewLabel.setOpaque(true);
        JScrollPane scroll = new JScrollPane(previewLabel);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(20, 40, 80)));
        dlg.add(scroll, BorderLayout.CENTER);

        JPanel ctrl = new JPanel();
        ctrl.setBackground(new Color(10, 13, 24));
        ctrl.setLayout(new BoxLayout(ctrl, BoxLayout.Y_AXIS));
        ctrl.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        ctrl.setPreferredSize(new Dimension(220, 0));

        ctrl.add(ctrlLabel("◈ MESH PARAMETERS"));
        ctrl.add(Box.createVerticalStrut(10));

        JSlider bSlider = slider(40, 300, boundaryPts,  "Boundary pts");
        JSlider rSlider = slider(5,  80,  radialLayers, "Radial layers");
        JSlider sSlider = slider(10, 200, smoothPasses, "Smooth passes");

        JLabel bVal = valLabel(boundaryPts  + " pts");
        JLabel rVal = valLabel(radialLayers + " layers");
        JLabel sVal = valLabel(smoothPasses + " passes");

        addSliderRow(ctrl, "Boundary pts",  bSlider, bVal);
        addSliderRow(ctrl, "Radial layers", rSlider, rVal);
        addSliderRow(ctrl, "Smooth passes", sSlider, sVal);

        ctrl.add(Box.createVerticalStrut(10));
        JLabel infoLbl = new JLabel("<html><span style='color:#1a3060'>Adjust and click<br>RE-MESH to preview.</span></html>");
        infoLbl.setFont(new Font("Consolas", Font.PLAIN, 10));
        infoLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        ctrl.add(infoLbl);
        ctrl.add(Box.createVerticalStrut(12));

        JButton remeshBtn = bigBtn("↺  RE-MESH", new Color(0, 70, 130));
        JButton commitBtn = bigBtn("✔  COMMIT TO DB", new Color(0, 110, 60));
        JButton cancelBtn = bigBtn("✖  CANCEL", new Color(80, 20, 20));
        commitBtn.setForeground(new Color(100, 255, 150));

        ctrl.add(remeshBtn); ctrl.add(Box.createVerticalStrut(6));
        ctrl.add(commitBtn); ctrl.add(Box.createVerticalStrut(4));
        ctrl.add(cancelBtn);
        ctrl.add(Box.createVerticalGlue());
        dlg.add(ctrl, BorderLayout.EAST);

        bSlider.addChangeListener(e -> bVal.setText(bSlider.getValue() + " pts"));
        rSlider.addChangeListener(e -> rVal.setText(rSlider.getValue() + " layers"));
        sSlider.addChangeListener(e -> sVal.setText(sSlider.getValue() + " passes"));

        // ── Re-mesh: re-resample from the ORIGINAL raw boundary every time ─────
        // (fixes the bug where boundary-point-count changes were silently ignored)
        remeshBtn.addActionListener(e -> {
            boundaryPts  = bSlider.getValue();
            radialLayers = rSlider.getValue();
            smoothPasses = sSlider.getValue();
            remeshBtn.setEnabled(false);
            remeshBtn.setText("…working");

            SwingWorker<MeshResult, Void> w = new SwingWorker<>() {
                @Override protected MeshResult doInBackground() {
                    List<double[]> bPts = AerofoilMesher.this.resampleBoundary(
                        result.rawBoundary, boundaryPts, result.imgW, result.imgH);
                    double[][][] grid = AerofoilMesher.this.buildOGrid(bPts, radialLayers, farfieldR);
                    AerofoilMesher.this.smoothGrid(grid, smoothPasses);
                    BufferedImage prev = AerofoilMesher.this.renderPreviewInternal(bPts, grid);
                    MeshResult r2 = new MeshResult(bPts, grid, prev);
                    r2.rawBoundary = result.rawBoundary;
                    r2.imgW = result.imgW; r2.imgH = result.imgH;
                    return r2;
                }
                @Override protected void done() {
                    try {
                        MeshResult r2 = get();
                        previewLabel.setIcon(new ImageIcon(r2.preview));
                        result.bPts = r2.bPts;
                        result.grid = r2.grid;
                    } catch (Exception ex) { ex.printStackTrace(); }
                    remeshBtn.setEnabled(true);
                    remeshBtn.setText("↺  RE-MESH");
                }
            };
            w.execute();
        });

        commitBtn.addActionListener(e -> {
            commitBtn.setEnabled(false);
            commitBtn.setText("Writing to DB…");
            SwingWorker<Void, Void> w = new SwingWorker<>() {
                @Override protected Void doInBackground() throws Exception {
                    AerofoilMesher.this.commitMesh(result.bPts, result.grid);
                    return null;
                }
                @Override protected void done() {
                    dlg.dispose();
                    onCommit.run();
                    int N = result.bPts.size(), R = result.grid[0].length - 1;
                    JOptionPane.showMessageDialog(canvas,
                        String.format("Mesh committed:\n%d nodes, %d edges",
                            N * (R + 1), N * R + N * (R + 1)),
                        "Done", JOptionPane.INFORMATION_MESSAGE);
                }
            };
            w.execute();
        });

        cancelBtn.addActionListener(e -> dlg.dispose());
        dlg.setVisible(true);
    }

    // ── STEP 6: commit mesh to DB ──────────────────────────────────────────────
    private void commitMesh(List<double[]> bPts, double[][][] grid) throws Exception {
        int N = grid.length, R = grid[0].length - 1;
        long[][] ids = new long[N][R + 1];

        for (int i = 0; i < N; i++) {
            for (int j = 0; j <= R; j++) {
                String lbl = (j == 0 ? "B" : (j == R ? "F" : "M")) + i + "_" + j;
                Node n = nodeDAO.insert(new Node(grid[i][j][0], grid[i][j][1], lbl));
                ids[i][j] = n.getId();
            }
        }

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < R; j++) {
                double len = Math.hypot(
                    grid[i][j+1][0] - grid[i][j][0],
                    grid[i][j+1][1] - grid[i][j][1]);
                edgeDAO.insert(new Edge(ids[i][j], ids[i][j+1], len));
            }
        }

        for (int j = 0; j <= R; j++) {
            for (int i = 0; i < N; i++) {
                int iN = (i + 1) % N;
                double len = Math.hypot(
                    grid[iN][j][0] - grid[i][j][0],
                    grid[iN][j][1] - grid[i][j][1]);
                edgeDAO.insert(new Edge(ids[i][j], ids[iN][j], len));
            }
        }
    }

    // ── preview rendering (single canonical implementation) ───────────────────
    private BufferedImage renderPreviewInternal(List<double[]> bPts, double[][][] grid) {
        int PW = 880, PH = 480;
        BufferedImage img = new BufferedImage(PW, PH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(6, 8, 16));
        g2.fillRect(0, 0, PW, PH);

        int N = grid.length, R = grid[0].length - 1;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < N; i++) for (int j = 0; j <= R; j++) {
            minX = Math.min(minX, grid[i][j][0]); maxX = Math.max(maxX, grid[i][j][0]);
            minY = Math.min(minY, grid[i][j][1]); maxY = Math.max(maxY, grid[i][j][1]);
        }
        double gW = maxX - minX, gH = maxY - minY;
        if (gW < 1 || gH < 1) { g2.dispose(); return img; }
        double scale = Math.min((PW - 40) / gW, (PH - 40) / gH) * 0.9;
        double ox = PW / 2.0 - (minX + gW / 2) * scale;
        double oy = PH / 2.0 - (minY + gH / 2) * scale;

        // circumferential lines
        for (int j = 0; j <= R; j++) {
            float frac = (float) j / R;
            int alpha = j == 0 ? 255 : (j == R ? 140 : 100);
            g2.setColor(new Color(0, (int)(60 + 140 * frac), (int)(140 + 80 * frac), alpha));
            g2.setStroke(new BasicStroke(j == 0 ? 2f : 0.5f));
            for (int i = 0; i < N; i++) {
                int iN = (i + 1) % N;
                // FIXED: Restored variable 'grid', added loop iterators 'i' and 'iN', and fixed parentheses
                g2.drawLine(
                	    (int) (grid[i][j][0] * scale + ox),
                	    (int) (grid[i][j][1] * scale + oy),
                	    (int) (grid[iN][j][0] * scale + ox),
                	    (int) (grid[iN][j][1] * scale + oy)
                	);
            }
        }

        // radial lines
        g2.setStroke(new BasicStroke(0.4f));
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < R; j++) {
                float frac = (float) j / R;
                g2.setColor(new Color(0, (int)(60 + 140 * frac), (int)(140 + 80 * frac), 90));
                // FIXED: Restored variable 'grid', added loop iterator 'i', and fixed parentheses
                g2.drawLine(
                	    (int) (grid[i][j][0] * scale + ox),
                	    (int) (grid[i][j][1] * scale + oy),
                	    (int) (grid[i][j + 1][0] * scale + ox),
                	    (int) (grid[i][j + 1][1] * scale + oy)
                	);
            }
        }

        // aerofoil boundary highlight
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(0, 220, 255));
        Path2D path = new Path2D.Double();
        for (int i = 0; i < bPts.size(); i++) {
            double px = bPts.get(i)[0] * scale + ox;
            double py = bPts.get(i)[1] * scale + oy;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.closePath();
        g2.draw(path);

        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        g2.setColor(new Color(40, 80, 140));
        g2.drawString(String.format("Boundary pts: %d   Layers: %d   Total cells: %d   Nodes: %d",
            N, R, N * R, N * (R + 1)), 8, PH - 8);

        g2.dispose();
        return img;
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private JDialog makeProgressDialog(WorkspacePanel canvas) {
        JDialog d = new JDialog((Frame) SwingUtilities.getWindowAncestor(canvas), "Meshing…", false);
        d.setSize(360, 90);
        d.setLocationRelativeTo(canvas);
        d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBackground(new Color(10, 13, 24));
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBackground(new Color(16, 20, 36));
        bar.setForeground(new Color(0, 185, 255));
        JLabel lbl = new JLabel("Initialising…");
        lbl.setFont(new Font("Consolas", Font.PLAIN, 11));
        lbl.setForeground(new Color(100, 160, 210));
        p.add(bar, BorderLayout.NORTH);
        p.add(lbl, BorderLayout.CENTER);
        d.setContentPane(p);
        return d;
    }

    private JSlider slider(int min, int max, int val, String tip) {
        JSlider s = new JSlider(min, max, val);
        s.setBackground(new Color(10, 13, 24));
        s.setForeground(new Color(0, 185, 255));
        s.setToolTipText(tip);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return s;
    }

    private void addSliderRow(JPanel p, String name, JSlider slider, JLabel val) {
        JLabel lbl = new JLabel(name);
        lbl.setFont(new Font("Consolas", Font.PLAIN, 10));
        lbl.setForeground(new Color(70, 120, 170));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(lbl); p.add(val); p.add(slider);
        p.add(Box.createVerticalStrut(8));
    }

    private JLabel valLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Consolas", Font.BOLD, 10));
        l.setForeground(new Color(0, 185, 255));
        return l;
    }

    private JLabel ctrlLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Consolas", Font.BOLD, 11));
        l.setForeground(new Color(0, 185, 255));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JButton bigBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Consolas", Font.BOLD, 11));
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ── data container ─────────────────────────────────────────────────────────
    private static class MeshResult {
        List<double[]>  bPts;
        double[][][]    grid;
        BufferedImage   preview;
        List<org.opencv.core.Point>     rawBoundary;
        int imgW, imgH;

        MeshResult(List<double[]> bPts, double[][][] grid, BufferedImage preview) {
            this.bPts    = bPts;
            this.grid    = grid;
            this.preview = preview;
        }
    }
}