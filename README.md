# GeoWorkspace

A 2D geometric workspace built in Java Swing with an H2 relational database backend.  
Place nodes, draw edges, form shapes, run boolean operations, and inspect all data live via the H2 web console.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Java Swing |
| Database | H2 2.4.240 (TCP server mode) |
| Language | Java 23 (OpenJDK Temurin) |
| Build | Manual `javac` + `@sources.txt` |

---

## Prerequisites

- **Java 17+** — download from [adoptium.net](https://adoptium.net)
- **H2 JAR** — download `h2-2.4.240.jar` and place it in the `lib/` folder of the project

Verify your Java version:
```
java -version
```

---

## Project Structure

```
GeoWorkspace/
├── lib/
│   └── h2-2.4.240.jar        <- H2 database JAR (download separately, not in repo)
├── out/                       <- compiled .class files (generated on build, not in repo)
├── src/
│   ├── app/Main.java
│   ├── db/
│   │   ├── DBConnection.java
│   │   ├── DBServer.java
│   │   ├── SchemaInitializer.java
│   │   └── dao/
│   │       ├── NodeDAO.java
│   │       └── EdgeDAO.java
│   ├── model/
│   │   ├── Node.java
│   │   └── Edge.java
│   └── ui/
│       ├── WorkspaceFrame.java
│       ├── WorkspacePanel.java
│       ├── ShapePanel.java
│       ├── ShapeValidator.java
│       ├── BooleanPanel.java
│       ├── UndoManager.java
│       └── HoverTooltipManager.java
├── sources.txt                <- list of all .java files for javac
└── README.md
```


---

## Running the App

Three steps, each in its own terminal. **Do them in this order.**

---

### Step 1 — Start the H2 Database Server

Open **Terminal 1** and run:

**Windows:**
```
java -jar "C:\Users\USER\Desktop\h2-2.4.240.jar" -tcp -tcpPort 9092 -tcpAllowOthers -web -webPort 8082 -webAllowOthers
```

**Mac / Linux:**
```
java -jar ~/Downloads/h2-2.4.240.jar -tcp -tcpPort 9092 -tcpAllowOthers -web -webPort 8082 -webAllowOthers
```

You should see:
```
TCP server running at tcp://localhost:9092 (others can connect)
Web Console server running at http://localhost:8082
```

**Keep this terminal open for the whole session.** Closing it kills the database.

---

### Step 2 — Compile

Open **Terminal 2**, go to the project root, and compile:

**Windows:**
```
cd C:\Users\USER\eclipse-workspace\GeoWorkspace
rmdir /s /q out
mkdir out
javac -cp "lib/h2-2.4.240.jar" -d out @sources.txt
```

**Mac / Linux:**
```
cd ~/path/to/GeoWorkspace
rm -rf out && mkdir out
javac -cp "lib/h2-2.4.240.jar" -d out @sources.txt
```

No output means success. Errors will be printed inline.

---

### Step 3 — Run

In the same **Terminal 2**:

**Windows:**
```
java -cp "out;lib/h2-2.4.240.jar" app.Main
```

**Mac / Linux:**
```
java -cp "out:lib/h2-2.4.240.jar" app.Main
```

The GeoWorkspace window opens. The terminal will print DB activity as you interact.

---

### Step 4 (Optional) — Inspect the Database in the Browser

Open: `http://localhost:8082`

Connect with:

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:tcp://localhost:9092/~/geodb` |
| User Name | `sa` |
| Password | *(leave blank)* |

You can view and edit the `nodes` and `edges` tables live. The workspace syncs changes within ~1.5 seconds automatically.

---

## Controls

| Action | Input |
|---|---|
| Place node | Left-click on empty canvas |
| Draw edge | Left-click node A, then node B |
| Cancel edge / deselect | Right-click on empty space |
| Delete node or edge | Right-click on it → Delete |
| Zoom in / out | Scroll wheel or touchpad pinch |
| Rotate workspace | Right-click + drag left or right |
| Multi-select for bulk delete | Switch to Multi-sel mode, click items, press `Delete` |
| Select nodes for shape | Switch to Shape-sel mode, click nodes |
| Undo | `Ctrl+Z` (up to 10 steps) |
| Redo | `Ctrl+Y` |

---

## Toolbar Modes

| Button | What it does |
|---|---|
| ✦ Normal | Place nodes and draw edges (default) |
| ⊞ Multi-sel | Click nodes and edges to select them, then press Delete to remove all at once |
| ⬡ Shape-sel | Click nodes to build a shape selection, then use the Shapes panel to connect them |
| ⟋ Extend | Click 2 edges to predict their intersection point if extended |

---

## Side Panel Tabs

### Shapes
Pick a shape type, switch to Shape-sel mode, click the required number of nodes, then press **CREATE SHAPE**.  
The validator checks:
- Correct node count
- No duplicate nodes
- No self-intersecting edges (rejects shapes like the one in the image above)

### Boolean
- **Intersect** — highlights all points where edges cross (yellow markers)
- **Subtract** — removes intersecting edge pairs from the canvas
- **Add** — merge geometry (placeholder, full topology coming soon)

### Extend
Click 2 edges on the canvas, then press **PREDICT INTERSECTION** to see where the lines would meet if extended infinitely. Parallel lines show a "no intersection" message.

---

## Database Schema

Two tables:

```sql
-- Nodes: adjacency list stored inline (max 4 neighbours)
CREATE TABLE nodes (
    id    IDENTITY PRIMARY KEY,
    x     DOUBLE NOT NULL,
    y     DOUBLE NOT NULL,
    label VARCHAR(100),
    adj1  BIGINT DEFAULT NULL,
    adj2  BIGINT DEFAULT NULL,
    adj3  BIGINT DEFAULT NULL,
    adj4  BIGINT DEFAULT NULL
);

-- Edges: includes computed Euclidean length
CREATE TABLE edges (
    id        IDENTITY PRIMARY KEY,
    node_a_id BIGINT NOT NULL,
    node_b_id BIGINT NOT NULL,
    length    DOUBLE NOT NULL,
    FOREIGN KEY (node_a_id) REFERENCES nodes(id) ON DELETE CASCADE,
    FOREIGN KEY (node_b_id) REFERENCES nodes(id) ON DELETE CASCADE
);
```

The DB file is saved to `~/geodb.mv.db` (your OS home folder) and persists across sessions.

---

## Resetting the Database

Run this in the H2 web console at `http://localhost:8082` to wipe all data:

```sql
DROP TABLE IF EXISTS edges;
DROP TABLE IF EXISTS nodes;
```

Restart the app — schema will be recreated automatically.

---

## .gitignore

Add this to your `.gitignore`:

```
out/
lib/
*.mv.db
*.trace.db
```

---

## Known Issues

- Mouse scroll wheel zoom may not work on some hardware; touchpad pinch-to-zoom works instead
- Undo tracking for delete operations is incomplete (re-insert with original ID not yet implemented)
- Boolean Add is a placeholder pending full polygon topology support
