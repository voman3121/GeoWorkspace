# GeoWorkspace
A 2D geometric workspace built in Java Swing with an H2 relational database backend and OpenCV-powered image processing.

Place nodes, draw edges, create shapes, run boolean operations, auto-trace hand-drawn sketches, and generate structured CFD meshes around aerofoil profiles — all backed by a live H2 database you can inspect in the browser.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Java Swing |
| Database | H2 2.4.240 (TCP server mode) |
| Image Processing | OpenCV 4.9.0 (Java bindings) |
| Language | Java 17+ (OpenJDK Temurin recommended) |
| Build | Manual `javac` + `@sources.txt` |

---

## Prerequisites

| Requirement | Where to get it |
|---|---|
| **Java 17+** | [adoptium.net](https://adoptium.net) |
| **H2 JAR** (`h2-2.4.240.jar`) | [h2database.com](https://h2database.com/html/download.html) |
| **OpenCV 4.9.0** (Windows self-extracting archive) | [github.com/opencv/opencv/releases/tag/4.9.0](https://github.com/opencv/opencv/releases/tag/4.9.0) |

Verify Java:
```bash
java -version
```

---

## One-time Setup — OpenCV

1. Download **`opencv-4.9.0-windows.exe`** from the link above and run it  
2. Extract to `C:\opencv\` (just unpacks files — no installer)  
3. Copy two files into your project's `lib/` folder:

```
C:\opencv\build\java\opencv-490.jar          → GeoWorkspace\lib\opencv-490.jar
C:\opencv\build\java\x64\opencv_java490.dll  → GeoWorkspace\lib\opencv_java490.dll
```

Also place `h2-2.4.240.jar` in `lib\` if you haven't already.

Your `lib\` folder should look like:
```
lib/
├── h2-2.4.240.jar
├── opencv-490.jar
└── opencv_java490.dll
```

---

## Project Structure

```
GeoWorkspace/
├── lib/
│   ├── h2-2.4.240.jar            ← H2 database JAR
│   ├── opencv-490.jar            ← OpenCV Java API
│   └── opencv_java490.dll        ← OpenCV native library (Windows x64)
├── out/                          ← compiled .class files (generated on build)
├── src/
│   ├── app/
│   │   └── Main.java
│   ├── db/
│   │   ├── DBConnection.java
│   │   ├── DBServer.java
│   │   ├── SchemaInitializer.java
│   │   └── dao/
│   │       ├── NodeDAO.java
│   │       ├── EdgeDAO.java
│   │       └── ShapeDAO.java
│   ├── model/
│   │   ├── Node.java
│   │   ├── Edge.java
│   │   └── Shape.java
│   └── ui/
│       ├── WorkspaceFrame.java
│       ├── WorkspacePanel.java
│       ├── RightPanel.java
│       ├── ShapeValidator.java
│       ├── UndoManager.java
│       ├── HoverTooltipManager.java
│       ├── ImageTracer.java
│       └── AerofoilMesher.java
├── sources.txt                   ← file list for javac
└── README.md
```

---

## Running the App

Clone the repo and enter the project directory:

```bash
git clone https://github.com/voman3121/GeoWorkspace.git
cd GeoWorkspace
```

Three steps. Do them in order.

---

### Step 1 — Start the H2 Database Server

Open **Terminal 1** and run:

**Windows:**
```bash
java -jar "lib\h2-2.4.240.jar" -tcp -tcpPort 9092 -tcpAllowOthers -web -webPort 8082 -webAllowOthers
```

**Mac / Linux:**
```bash
java -jar lib/h2-2.4.240.jar -tcp -tcpPort 9092 -tcpAllowOthers -web -webPort 8082 -webAllowOthers
```

You should see:
```
TCP server running at tcp://localhost:9092 (others can connect)
Web Console server running at http://localhost:8082
```

**Keep this terminal open** — closing it stops the database.

---

### Step 2 — Compile

Open **Terminal 2**, navigate to the project folder, and compile with both JARs on the classpath:

**Windows:**
```bash
rmdir /s /q out
mkdir out
javac -cp "lib/h2-2.4.240.jar;lib/opencv-490.jar" -d out @sources.txt
```

**Mac / Linux:**
```bash
rm -rf out && mkdir out
javac -cp "lib/h2-2.4.240.jar:lib/opencv-490.jar" -d out @sources.txt
```

---

### Step 3 — Run

**Windows:**
```bash
java -Djava.library.path=lib -cp "out;lib/h2-2.4.240.jar;lib/opencv-490.jar" app.Main
```

**Mac / Linux:**
```bash
java -Djava.library.path=lib -cp "out:lib/h2-2.4.240.jar:lib/opencv-490.jar" app.Main
```

> `-Djava.library.path=lib` tells the JVM where to find `opencv_java490.dll`.  
> The app will launch and print `[OpenCV] Native library loaded: 4.9.0` if setup is correct.  
> If the DLL is missing the app still launches — only the ⤓ Trace and ⬡ Mesh features will fail.

---

### Step 4 (Optional) — Live Database Console

Open `http://localhost:8082` in a browser and connect with:

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:tcp://localhost:9092/~/geodb` |
| User Name | `sa` |
| Password | *(leave blank)* |

The workspace syncs all changes within ~1.5 seconds automatically.

---

## Features

### Workspace canvas
- **Place nodes** — left-click on empty space (snapped to 40px grid)
- **Draw edges** — left-click a node, then left-click a second node
- **Pan / rotate** — right-click drag
- **Zoom** — scroll wheel (zoom toward cursor)
- **Delete** — right-click any node/edge/shape → context menu; or multi-select + Del key
- **Undo / Redo** — `Ctrl+Z` / `Ctrl+Y` (10-step history, full DB-level undo with original IDs)
- **Hover tooltips** — hover over any node, edge, or shape for DB details

### Modes (left bar)
| Button | What it does |
|---|---|
| ⊞ Multi-Select | Click nodes/edges to accumulate a selection; `Del` deletes all at once |
| ⬡ Shape-Select | Click nodes in order on the canvas to build a shape selection |
| ● Circle | Click + drag to set center and radius; release to choose Full Circle or Semi-circle |

`SHIFT + left-click` works as multi-select toggle in any mode.

### Shapes (right panel → SHAPES tab)
Select a shape type, enter **Shape-Select** mode, click nodes on the canvas in order, then press **CREATE SHAPE**.

| Shape | Nodes | Validation |
|---|---|---|
| Line | 2 | Any 2 distinct nodes |
| Triangle | 3 | Non-collinear |
| Rectangle | 4 | Right angles, opposite sides equal (±18%) |
| Square | 4 | All sides equal (±18%), right angles |
| Pentagon | 5 | Equal sides (±25%), no crossing edges |
| Hexagon | 6 | Equal sides (±25%), no crossing edges |
| Arc | 3 | Node 1 = start, node 2 = control point, node 3 = end; draws a quadratic Bézier |
| Semi-circle | — | Use the **● Circle** tool → choose Semi-circle on release |
| Free Polygon | 3+ | Any closed, non-self-intersecting polygon |

Shapes are stored in the `shapes` table with label, type, node IDs, area, and perimeter.  
Hovering inside a shape's filled region shows its DB record in a tooltip.

### Boolean operations (right panel → BOOLEAN tab)
`SHIFT + click inside` a shape's filled region to select it (shown as A and B).

| Operation | Behaviour |
|---|---|
| **INTERSECT** | Highlights the actual overlapping region between A and B in yellow |
| **SUBTRACT** | Deletes edges of the chosen shape(s) that pass through the highlighted overlap zone. Requires INTERSECT to be run first. Both shapes stay in the DB — only the overlapping edges are removed. |
| **ADD / UNION** | Connects the nearest endpoints of the two shapes with a new edge |

### Line Extend (right panel → EXTEND tab)
Click 2 edges on the canvas, then press **PREDICT INTERSECTION** to see where they'd meet if extended (shown as a crosshair overlay). Works on non-parallel lines only.

### Import — ⤓ Trace (left bar)
Auto-traces a hand-drawn sketch photo using an OpenCV pipeline:

1. Greyscale + contrast stretch (CLAHE)  
2. Bilateral filter (denoise, preserve edges)  
3. Adaptive Canny edge detection (thresholds computed from image median)  
4. Morphological closing (bridges small gaps in strokes)  
5. Probabilistic Hough line transform (`HoughLinesP`)  
6. Hough circle transform (`HoughCircles`)  
7. Contour detection + polygon approximation (`findContours` + `approxPolyDP`)  
8. Collinear line merging + endpoint snapping  
9. Interior ghost-node filtering  

A preview dialog shows detected shapes (green), lines (blue), circles (orange), and nodes (yellow) overlaid on the source image. Press **COMMIT → NEW WORKSPACE** to write results to the database and open them in a fresh workspace window — your existing workspace is never touched.

**Best input:** clear black pen on white paper, photographed flat from directly above, no shadows.

### Import — ⬡ Mesh (left bar)
Generates a structured CFD O-grid mesh around an aerofoil image:

1. Otsu threshold → largest closed contour = aerofoil silhouette  
2. Resample boundary to N evenly-spaced points  
3. Transfinite interpolation with geometric clustering (denser near surface)  
4. Laplacian diffusion smoothing (iterative relaxation, surface and far-field frozen)  

The preview dialog has three sliders:

| Slider | Effect |
|---|---|
| Boundary pts | How many points trace the aerofoil outline |
| Radial layers | How many mesh layers extend away from the surface |
| Smooth passes | How many Laplacian relaxation iterations are applied |

Press **↺ RE-MESH** to regenerate with new settings, **✔ COMMIT TO DB** to write all nodes and edges.  
Boundary nodes are labelled `B`, far-field nodes `F`, interior nodes `M`.

---

## Database Schema

```sql
CREATE TABLE nodes (
    id    IDENTITY PRIMARY KEY,
    x     DOUBLE NOT NULL,
    y     DOUBLE NOT NULL,
    label VARCHAR(100),
    adj1  BIGINT DEFAULT NULL,   -- adjacency (up to 4 neighbours per node)
    adj2  BIGINT DEFAULT NULL,
    adj3  BIGINT DEFAULT NULL,
    adj4  BIGINT DEFAULT NULL
);

CREATE TABLE edges (
    id        IDENTITY PRIMARY KEY,
    node_a_id BIGINT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    node_b_id BIGINT NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    length    DOUBLE NOT NULL
);

CREATE TABLE shapes (
    id         IDENTITY PRIMARY KEY,
    label      VARCHAR(100),
    shape_type VARCHAR(50),
    node_ids   VARCHAR(500),    -- comma-separated node IDs
    area       DOUBLE DEFAULT 0,
    perimeter  DOUBLE DEFAULT 0,
    extra_data VARCHAR(500)     -- cx,cy,r[,startAngle] for circles/semicircles
);
```

Data persists across restarts (`CREATE TABLE IF NOT EXISTS`). Drop tables in the H2 console only if you want a clean slate:

```sql
DROP TABLE IF EXISTS shapes;
DROP TABLE IF EXISTS edges;
DROP TABLE IF EXISTS nodes;
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `UnsatisfiedLinkError: no opencv_java490 in java.library.path` | Make sure `opencv_java490.dll` is in `lib\` and you're using `-Djava.library.path=lib` in the run command |
| `Cannot read image` on import | OpenCV loaded but the file format isn't supported — use PNG or JPG |
| `Port 9092 already in use` | Another H2 server is running; the app auto-connects to it, no action needed |
| Shapes not being created | Check the validation message — nodes must be selected in order with no self-crossing edges |
| Ctrl+Z not working | Click on the canvas once to give it focus, or use the ↩ Undo button in the left bar |
| No boundary found in aerofoil image | Image may have low contrast — try increasing brightness/contrast before importing, or use a cleaner scan |
