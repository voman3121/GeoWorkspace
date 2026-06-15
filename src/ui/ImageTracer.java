package ui;

import db.dao.EdgeDAO;
import db.dao.NodeDAO;
import db.dao.ShapeDAO;
import model.Edge;
import model.Node;
import model.Shape;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Image auto-tracer.
 *
 * Pipeline:
 *  1. Load image
 *  2. Greyscale + contrast boost
 *  3. Gaussian blur (noise reduction)
 *  4. Canny edge detection (Sobel gradient + NMS + hysteresis)
 *  5. Probabilistic Hough line transform → line segments
 *  6. Circular Hough transform → circles/arcs
 *  7. Merge near-duplicate lines / snap endpoints
 *  8. Place nodes at endpoints + intersections
 *  9. Draw edges between connected nodes
 * 10. Write everything to the DB
 */
public class ImageTracer {

    // ── tuning constants ──────────────────────────────────────────────────────
    private static final int    CANNY_LOW        = 30;
    private static final int    CANNY_HIGH       = 90;
    private static final int    HOUGH_THRESHOLD  = 60;   // min votes for a line
    private static final int    MIN_LINE_LEN     = 25;   // px
    private static final int    MAX_LINE_GAP     = 12;   // px gap still joined
    private static final double SNAP_DIST        = 14.0; // px — merge near endpoints
    private static final int    CIRCLE_MIN_R     = 15;
    private static final int    CIRCLE_MAX_R     = 300;
    private static final int    CIRCLE_THRESHOLD = 40;

    // ── world-space scale ─────────────────────────────────────────────────────
    // Map image pixels → world units so nodes land on grid multiples of 40
    private static final int GRID = 40;

    private final NodeDAO  nodeDAO  = new NodeDAO();
    private final EdgeDAO  edgeDAO  = new EdgeDAO();
    private final ShapeDAO shapeDAO = new ShapeDAO();

    // ── public entry point ────────────────────────────────────────────────────

    /** Called from WorkspacePanel. Shows file picker → processing dialog → commits to DB. */
    public void run(Component parent, Runnable onDone) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select hand-drawn sketch");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Images (PNG, JPG, BMP)", "png","jpg","jpeg","bmp"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        SwingWorker<TraceResult, String> worker = new SwingWorker<>() {
            JDialog progress;
            JLabel  statusLabel;
            JProgressBar bar;

            @Override protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size()-1));
            }

            @Override protected TraceResult doInBackground() throws Exception {
            	return trace(file, msg -> publish(msg));            }

            @Override protected void done() {
                progress.dispose();
                try {
                    TraceResult result = get();
                    showPreview(parent, result, onDone);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                        "Trace failed: " + ex.getCause().getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }

            // Build progress dialog on EDT before starting
            { SwingUtilities.invokeLater(() -> {
                progress = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent),
                        "Tracing sketch…", true);
                progress.setLayout(new BorderLayout(10,10));
                statusLabel = new JLabel("Loading image…", SwingConstants.CENTER);
                statusLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
                bar = new JProgressBar(); bar.setIndeterminate(true);
                progress.add(statusLabel, BorderLayout.CENTER);
                progress.add(bar, BorderLayout.SOUTH);
                progress.setSize(340, 100);
                progress.setLocationRelativeTo(parent);
                progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                execute();   // start worker
                progress.setVisible(true);
            }); }
        };
        // Worker is started inside the Swing init block above
    }

    // ── processing pipeline ───────────────────────────────────────────────────

    private interface StatusCallback { void update(String msg); }

    private TraceResult trace(File file, StatusCallback cb) throws Exception {
        cb.update("Loading image…");
        BufferedImage src = ImageIO.read(file);
        if (src == null) throw new Exception("Cannot read image file.");

        // 1. Greyscale
        cb.update("Converting to greyscale…");
        int w = src.getWidth(), h = src.getHeight();
        int[][] grey = toGrey(src, w, h);

        // 2. Boost contrast
        cb.update("Enhancing contrast…");
        grey = stretchContrast(grey, w, h);

        // 3. Gaussian blur 5×5
        cb.update("Applying Gaussian blur…");
        grey = gaussianBlur(grey, w, h);

        // 4. Canny edge detection
        cb.update("Running Canny edge detection…");
        boolean[][] edges = canny(grey, w, h);

        // 5. Hough line transform
        cb.update("Detecting lines (Hough transform)…");
        List<int[]> lines = houghLines(edges, w, h);   // each: [x1,y1,x2,y2]

        // 6. Circular Hough
        cb.update("Detecting circles…");
        List<int[]> circles = houghCircles(grey, edges, w, h); // each: [cx,cy,r]

        // 7. Merge/snap lines
        cb.update("Merging duplicate lines…");
        lines = mergeLines(lines);

        // 8. Build nodes from endpoints + intersections
        cb.update("Placing nodes…");
        List<double[]> pts = collectPoints(lines, circles, w, h);
        pts = snapPoints(pts, SNAP_DIST);

        return new TraceResult(src, w, h, lines, circles, pts);
    }

    // ── greyscale ─────────────────────────────────────────────────────────────

    private int[][] toGrey(BufferedImage img, int w, int h) {
        int[][] g = new int[h][w];
        for (int y=0; y<h; y++)
            for (int x=0; x<w; x++) {
                Color c = new Color(img.getRGB(x,y), true);
                g[y][x] = (int)(0.299*c.getRed() + 0.587*c.getGreen() + 0.114*c.getBlue());
            }
        return g;
    }

    private int[][] stretchContrast(int[][] g, int w, int h) {
        int min=255, max=0;
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) { min=Math.min(min,g[y][x]); max=Math.max(max,g[y][x]); }
        if (max==min) return g;
        int[][] out=new int[h][w];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) out[y][x]=(g[y][x]-min)*255/(max-min);
        return out;
    }

    // ── Gaussian blur 5×5 ─────────────────────────────────────────────────────

    private static final double[] GAUSS5 = {
        2,4,5,4,2, 4,9,12,9,4, 5,12,15,12,5, 4,9,12,9,4, 2,4,5,4,2
    };
    private static final double GAUSS5_SUM = 159.0;

    private int[][] gaussianBlur(int[][] g, int w, int h) {
        int[][] out=new int[h][w];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            double sum=0;
            for (int ky=-2;ky<=2;ky++) for (int kx=-2;kx<=2;kx++) {
                int ny=Math.max(0,Math.min(h-1,y+ky));
                int nx=Math.max(0,Math.min(w-1,x+kx));
                sum+=g[ny][nx]*GAUSS5[(ky+2)*5+(kx+2)];
            }
            out[y][x]=(int)(sum/GAUSS5_SUM);
        }
        return out;
    }

    // ── Canny edge detection ──────────────────────────────────────────────────

    private boolean[][] canny(int[][] g, int w, int h) {
        // Sobel gradients
        double[][] mag=new double[h][w];
        double[][] ang=new double[h][w];
        int[] sx={-1,0,1,-2,0,2,-1,0,1};
        int[] sy={-1,-2,-1,0,0,0,1,2,1};
        for (int y=1;y<h-1;y++) for (int x=1;x<w-1;x++) {
            double gx=0,gy=0;
            for (int k=0;k<9;k++){int dy=k/3-1,dx=k%3-1;gx+=g[y+dy][x+dx]*sx[k];gy+=g[y+dy][x+dx]*sy[k];}
            mag[y][x]=Math.hypot(gx,gy);
            ang[y][x]=Math.toDegrees(Math.atan2(gy,gx));
        }

        // Non-maximum suppression
        double[][] nms=new double[h][w];
        for (int y=1;y<h-1;y++) for (int x=1;x<w-1;x++) {
            double a=ang[y][x]; double m=mag[y][x];
            double m1,m2;
            if ((a>=-22.5&&a<22.5)||(a>=157.5||a<-157.5))      { m1=mag[y][x-1]; m2=mag[y][x+1]; }
            else if ((a>=22.5&&a<67.5)||(a>=-157.5&&a<-112.5)) { m1=mag[y-1][x+1]; m2=mag[y+1][x-1]; }
            else if ((a>=67.5&&a<112.5)||(a>=-112.5&&a<-67.5)) { m1=mag[y-1][x]; m2=mag[y+1][x]; }
            else                                                  { m1=mag[y-1][x-1]; m2=mag[y+1][x+1]; }
            nms[y][x]=(m>=m1&&m>=m2)?m:0;
        }

        // Double threshold + hysteresis
        boolean[][] strong=new boolean[h][w], weak=new boolean[h][w];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            if (nms[y][x]>=CANNY_HIGH) strong[y][x]=true;
            else if (nms[y][x]>=CANNY_LOW) weak[y][x]=true;
        }
        // Connect weak to strong
        boolean[][] out=new boolean[h][w];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) if(strong[y][x]) out[y][x]=true;
        boolean changed=true;
        while (changed) {
            changed=false;
            for (int y=1;y<h-1;y++) for (int x=1;x<w-1;x++) {
                if (!out[y][x]&&weak[y][x]) {
                    boolean nb=false;
                    for (int dy=-1;dy<=1&&!nb;dy++) for(int dx=-1;dx<=1&&!nb;dx++) if(out[y+dy][x+dx])nb=true;
                    if (nb){out[y][x]=true;changed=true;}
                }
            }
        }
        return out;
    }

    // ── Probabilistic Hough line transform ───────────────────────────────────

    private List<int[]> houghLines(boolean[][] edges, int w, int h) {
        // Standard Hough: accumulate r-theta, then trace segments
        int diagLen=(int)Math.ceil(Math.hypot(w,h));
        int numAngles=180;
        int[][] acc=new int[numAngles][2*diagLen];
        double[] cosA=new double[numAngles], sinA=new double[numAngles];
        for (int t=0;t<numAngles;t++){double r=Math.toRadians(t);cosA[t]=Math.cos(r);sinA[t]=Math.sin(r);}

        // Accumulate
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) if(edges[y][x])
            for (int t=0;t<numAngles;t++){
                int r=(int)Math.round(x*cosA[t]+y*sinA[t])+diagLen;
                if(r>=0&&r<2*diagLen) acc[t][r]++;
            }

        // Find peaks above threshold
        List<int[]> segments=new ArrayList<>();
        for (int t=0;t<numAngles;t++) for (int r=0;r<2*diagLen;r++) {
            if (acc[t][r]<HOUGH_THRESHOLD) continue;
            // Trace segment along this (theta,r) line
            double theta=Math.toRadians(t);
            double rVal=r-diagLen;
            double ct=cosA[t],st=sinA[t];
            // perpendicular direction
            int[] seg=traceSegment(edges,w,h,theta,rVal,ct,st);
            if (seg!=null) segments.add(seg);
        }
        return segments;
    }

    private int[] traceSegment(boolean[][] edges,int w,int h,double theta,double rho,double ct,double st) {
        // Walk along the line and find the longest contiguous edge run
        int x0=(int)Math.round(rho*ct), y0=(int)Math.round(rho*st);
        // direction along the line
        double dx=-st, dy=ct;
        if (Math.abs(dx)<Math.abs(dy)){dx=-st;dy=ct;}else{dx=-st;dy=ct;}

        // Scan from -diagLen to +diagLen along direction
        int len=(int)Math.ceil(Math.hypot(w,h));
        int bestX1=-1,bestY1=-1,bestX2=-1,bestY2=-1,bestLen=0;
        int curX1=-1,curY1=-1,gap=0,runLen=0;

        for (int i=-len;i<=len;i++){
            int x=(int)Math.round(x0+i*dx);
            int y=(int)Math.round(y0+i*dy);
            if (x<0||x>=w||y<0||y>=h){gap++;continue;}
            if (edges[y][x]){
                gap=0; runLen++;
                if (curX1<0){curX1=x;curY1=y;}
                if (runLen>bestLen){bestLen=runLen;bestX1=curX1;bestY1=curY1;bestX2=x;bestY2=y;}
            } else {
                gap++;
                if (gap>MAX_LINE_GAP){curX1=-1;curY1=-1;runLen=0;gap=0;}
            }
        }
        if (bestLen<MIN_LINE_LEN) return null;
        return new int[]{bestX1,bestY1,bestX2,bestY2};
    }

    // ── Circular Hough transform ──────────────────────────────────────────────

    private List<int[]> houghCircles(int[][] grey, boolean[][] edges, int w, int h) {
        List<int[]> found=new ArrayList<>();
        // For each candidate radius, accumulate centre votes
        for (int r=CIRCLE_MIN_R;r<=Math.min(CIRCLE_MAX_R,Math.min(w,h)/2);r+=4) {
            int[][] acc=new int[h][w];
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) if(edges[y][x]) {
                // vote for all possible centres at distance r
                for (int angle=0;angle<360;angle+=5) {
                    double rad=Math.toRadians(angle);
                    int cx=(int)Math.round(x-r*Math.cos(rad));
                    int cy=(int)Math.round(y-r*Math.sin(rad));
                    if (cx>=0&&cx<w&&cy>=0&&cy<h) acc[cy][cx]++;
                }
            }
            // Find peaks
            for (int cy=r;cy<h-r;cy++) for (int cx=r;cx<w-r;cx++) {
                if (acc[cy][cx]<CIRCLE_THRESHOLD) continue;
                // Check not too close to existing circles
                boolean dup=false;
                for (int[] c:found) if(Math.hypot(cx-c[0],cy-c[1])<r*0.5){dup=true;break;}
                if (!dup) found.add(new int[]{cx,cy,r});
            }
        }
        return found;
    }

    // ── merge near-duplicate lines ────────────────────────────────────────────

    private List<int[]> mergeLines(List<int[]> lines) {
        List<int[]> merged=new ArrayList<>(lines);
        boolean changed=true;
        while (changed) {
            changed=false;
            outer:
            for (int i=0;i<merged.size();i++) {
                for (int j=i+1;j<merged.size();j++) {
                    int[] a=merged.get(i), b=merged.get(j);
                    if (linesParallelAndClose(a,b)) {
                        // merge into longer combined line
                        merged.set(i, combineLine(a,b));
                        merged.remove(j);
                        changed=true; break outer;
                    }
                }
            }
        }
        return merged;
    }

    private boolean linesParallelAndClose(int[] a, int[] b) {
        double angA=Math.toDegrees(Math.atan2(a[3]-a[1],a[2]-a[0]));
        double angB=Math.toDegrees(Math.atan2(b[3]-b[1],b[2]-b[0]));
        double dAng=Math.abs(angA-angB)%180;
        if (dAng>10&&dAng<170) return false;
        // Check endpoint proximity
        return ptLineDist(b[0],b[1],a[0],a[1],a[2],a[3])<SNAP_DIST*1.5 ||
               ptLineDist(b[2],b[3],a[0],a[1],a[2],a[3])<SNAP_DIST*1.5;
    }

    private int[] combineLine(int[] a, int[] b) {
        // Pick the outermost endpoints
        double[] pts={dist2(a[0],a[1],b[0],b[1]),dist2(a[0],a[1],b[2],b[3]),
                      dist2(a[2],a[3],b[0],b[1]),dist2(a[2],a[3],b[2],b[3])};
        int maxIdx=0; for(int i=1;i<4;i++) if(pts[i]>pts[maxIdx]) maxIdx=i;
        int[][] combos={{a[0],a[1],b[0],b[1]},{a[0],a[1],b[2],b[3]},
                        {a[2],a[3],b[0],b[1]},{a[2],a[3],b[2],b[3]}};
        return combos[maxIdx];
    }

    // ── collect node positions ─────────────────────────────────────────────────

    private List<double[]> collectPoints(List<int[]> lines, List<int[]> circles, int w, int h) {
        List<double[]> pts=new ArrayList<>();
        // Line endpoints
        for (int[] l:lines) {
            pts.add(new double[]{l[0],l[1]});
            pts.add(new double[]{l[2],l[3]});
        }
        // Line-line intersections
        for (int i=0;i<lines.size();i++) for (int j=i+1;j<lines.size();j++) {
            double[] pt=lineIntersect(lines.get(i),lines.get(j));
            if (pt!=null&&pt[0]>=0&&pt[0]<w&&pt[1]>=0&&pt[1]<h) pts.add(pt);
        }
        // Circle centres
        for (int[] c:circles) pts.add(new double[]{c[0],c[1]});
        return pts;
    }

    private List<double[]> snapPoints(List<double[]> pts, double snapDist) {
        List<double[]> result=new ArrayList<>();
        for (double[] p:pts) {
            boolean merged=false;
            for (double[] r:result) {
                if (Math.hypot(p[0]-r[0],p[1]-r[1])<snapDist) {
                    r[0]=(r[0]+p[0])/2; r[1]=(r[1]+p[1])/2;
                    merged=true; break;
                }
            }
            if (!merged) result.add(new double[]{p[0],p[1]});
        }
        return result;
    }

    // ── geometry helpers ──────────────────────────────────────────────────────

    private double ptLineDist(int px,int py,int ax,int ay,int bx,int by) {
        double dx=bx-ax,dy=by-ay,len2=dx*dx+dy*dy;
        if(len2==0) return Math.hypot(px-ax,py-ay);
        double t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/len2));
        return Math.hypot(px-(ax+t*dx),py-(ay+t*dy));
    }

    private double dist2(int x1,int y1,int x2,int y2){return Math.hypot(x1-x2,y1-y2);}

    private double[] lineIntersect(int[] a, int[] b) {
        double x1=a[0],y1=a[1],x2=a[2],y2=a[3];
        double x3=b[0],y3=b[1],x4=b[2],y4=b[3];
        double denom=(x1-x2)*(y3-y4)-(y1-y2)*(x3-x4);
        if (Math.abs(denom)<1) return null;
        double t=((x1-x3)*(y3-y4)-(y1-y3)*(x3-x4))/denom;
        if(t<-0.1||t>1.1) return null;
        return new double[]{x1+t*(x2-x1),y1+t*(y2-y1)};
    }

    // ── preview dialog ────────────────────────────────────────────────────────

    private void showPreview(Component parent, TraceResult result, Runnable onDone) {
        JDialog dlg = new JDialog((Frame)SwingUtilities.getWindowAncestor(parent),
                "Trace Preview — confirm to import", true);
        dlg.setSize(Math.min(result.w+100,1200), Math.min(result.h+150,800));
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(8,8));

        // Preview canvas
        JPanel preview = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2=(Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

                // Scale to fit
                double sx=(double)(getWidth()-20)/result.w;
                double sy=(double)(getHeight()-20)/result.h;
                double s=Math.min(sx,sy);
                int ox=(int)((getWidth()-result.w*s)/2);
                int oy=(int)((getHeight()-result.h*s)/2);

                // Draw original image dimmed
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.30f));
                g2.drawImage(result.src,(int)(ox),(int)(oy),(int)(result.w*s),(int)(result.h*s),null);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1f));

                // Draw detected lines
                g2.setColor(new Color(0,180,255,200));
                g2.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                for (int[] l:result.lines) {
                    g2.drawLine((int)(ox+l[0]*s),(int)(oy+l[1]*s),
                                (int)(ox+l[2]*s),(int)(oy+l[3]*s));
                }

                // Draw detected circles
                g2.setColor(new Color(0,220,160,200));
                for (int[] c:result.circles) {
                    int cx=(int)(ox+c[0]*s),cy=(int)(oy+c[1]*s),r=(int)(c[2]*s);
                    g2.drawOval(cx-r,cy-r,r*2,r*2);
                }

                // Draw detected nodes
                g2.setColor(new Color(255,200,30));
                for (double[] p:result.points) {
                    int px=(int)(ox+p[0]*s),py=(int)(oy+p[1]*s);
                    g2.fillOval(px-4,py-4,8,8);
                }
            }
        };
        preview.setBackground(new Color(8,10,18));
        preview.setPreferredSize(new Dimension(result.w, result.h));
        JScrollPane scroll=new JScrollPane(preview);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        // Info
        JLabel info=new JLabel(String.format(
            "  Detected: %d lines  •  %d circles  •  %d nodes   — click COMMIT to import into workspace",
            result.lines.size(), result.circles.size(), result.points.size()));
        info.setFont(new Font("Consolas",Font.PLAIN,11));
        info.setForeground(new Color(0,185,255));
        info.setOpaque(true); info.setBackground(new Color(6,8,16));
        info.setBorder(BorderFactory.createEmptyBorder(6,12,6,12));

        // Sensitivity slider
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,12,4));
        controls.setBackground(new Color(8,10,18));
        controls.add(lbl("Sensitivity:"));
        JSlider sens=new JSlider(10,120,HOUGH_THRESHOLD);
        sens.setBackground(new Color(8,10,18));
        sens.setForeground(new Color(0,180,255));
        controls.add(sens);
        controls.add(lbl("(lower = more lines)"));

        // Buttons
        JPanel btnRow=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,4));
        btnRow.setBackground(new Color(8,10,18));
        JButton commit=dlgBtn("COMMIT TO DB",new Color(0,100,180));
        JButton cancel=dlgBtn("CANCEL",new Color(40,40,60));
        btnRow.add(cancel); btnRow.add(commit);

        JPanel south=new JPanel(new BorderLayout());
        south.setBackground(new Color(8,10,18));
        south.add(controls,BorderLayout.CENTER);
        south.add(btnRow,BorderLayout.EAST);

        dlg.add(info,BorderLayout.NORTH);
        dlg.add(scroll,BorderLayout.CENTER);
        dlg.add(south,BorderLayout.SOUTH);

        commit.addActionListener(e->{
            dlg.dispose();
            commitToDb(result, parent, onDone);
        });
        cancel.addActionListener(e->dlg.dispose());

        dlg.setVisible(true);
    }

    // ── commit to DB ──────────────────────────────────────────────────────────

    private void commitToDb(TraceResult result, Component parent, Runnable onDone) {
        // Scale image coordinates → world coordinates
        // Fit image into a world area of ~1600x1200 world units
        double targetW = 1600.0, targetH = 1200.0;
        double scaleX = targetW / result.w;
        double scaleY = targetH / result.h;
        double scale  = Math.min(scaleX, scaleY);
        // Offset so it's centred at origin
        double offX = -result.w * scale / 2.0;
        double offY = -result.h * scale / 2.0;

        try {
            // Insert nodes (snapped to nearest grid point)
            Map<Integer,Long> ptIdMap=new HashMap<>();
            List<Node> insertedNodes=new ArrayList<>();

            for (int i=0;i<result.points.size();i++) {
                double[] p=result.points.get(i);
                double wx=snap(offX+p[0]*scale);
                double wy=snap(offY+p[1]*scale);
                String label="T"+(i+1);
                Node n=nodeDAO.insert(new Node(wx,wy,label));
                ptIdMap.put(i,(long)n.getId());
                insertedNodes.add(n);
            }

            // Insert edges for each detected line segment
            // Find nearest node to each line endpoint and connect them
            int edgesCreated=0;
            for (int[] line:result.lines) {
                double wx1=offX+line[0]*scale, wy1=offY+line[1]*scale;
                double wx2=offX+line[2]*scale, wy2=offY+line[3]*scale;
                int ni1=nearestPoint(result.points,line[0],line[1]);
                int ni2=nearestPoint(result.points,line[2],line[3]);
                if (ni1<0||ni2<0||ni1==ni2) continue;
                long idA=ptIdMap.get(ni1), idB=ptIdMap.get(ni2);
                // Check adjacency limit
                Node na=nodeDAO.findById(idA), nb=nodeDAO.findById(idB);
                if(na==null||nb==null) continue;
                if(na.hasNeighbour(idB)) continue;
                if(na.firstFreeSlot()<0||nb.firstFreeSlot()<0) continue;
                double len=Math.hypot(wx2-wx1,wy2-wy1);
                try {
                    edgeDAO.insert(new Edge(idA,idB,len));
                    nodeDAO.addAdjacency(idA,idB);
                    edgesCreated++;
                } catch(Exception ex){/* skip if already exists */}
            }

            // Insert circles as shapes
            int circlesCreated=0;
            for (int[] c:result.circles) {
                double wcx=offX+c[0]*scale;
                double wcy=offY+c[1]*scale;
                double wr=c[2]*scale;
                String label="Circle"+(circlesCreated+1);
                String extra=wcx+","+wcy+","+wr;
                Shape s=new Shape(label,"Circle",new long[0],Math.PI*wr*wr,2*Math.PI*wr,extra);
                shapeDAO.insert(s);
                circlesCreated++;
            }

            final int fn=insertedNodes.size(), fe=edgesCreated, fc=circlesCreated;
            SwingUtilities.invokeLater(()->{ 
                JOptionPane.showMessageDialog(parent,
                    "Import complete!\n"+fn+" nodes  •  "+fe+" edges  •  "+fc+" circles\nwritten to database.",
                    "Done", JOptionPane.INFORMATION_MESSAGE);
                onDone.run();
            });

        } catch(Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(parent,
                "DB error: "+ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double snap(double v){return Math.round(v/GRID)*GRID;}

    private int nearestPoint(List<double[]> pts,int x,int y){
        int best=-1; double bestD=SNAP_DIST*3;
        for(int i=0;i<pts.size();i++){
            double d=Math.hypot(pts.get(i)[0]-x,pts.get(i)[1]-y);
            if(d<bestD){bestD=d;best=i;}
        }
        return best;
    }

    private JLabel lbl(String t){JLabel l=new JLabel(t);l.setFont(new Font("Consolas",Font.PLAIN,11));l.setForeground(new Color(100,160,210));return l;}
    private JButton dlgBtn(String t,Color bg){
        JButton b=new JButton(t);b.setFont(new Font("Consolas",Font.BOLD,11));
        b.setForeground(Color.WHITE);b.setBackground(bg);b.setBorderPainted(false);b.setFocusPainted(false);b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }

    // ── result record ─────────────────────────────────────────────────────────

    private static class TraceResult {
        final BufferedImage src;
        final int w,h;
        final List<int[]> lines,circles;
        final List<double[]> points;
        TraceResult(BufferedImage src,int w,int h,List<int[]>lines,List<int[]>circles,List<double[]>pts){
            this.src=src;this.w=w;this.h=h;this.lines=lines;this.circles=circles;this.points=pts;}
    }
}