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

