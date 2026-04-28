package ui;

import javax.swing.*;
import java.awt.*;

public class WorkspaceFrame extends JFrame {

    public WorkspaceFrame() {
        setTitle("GeoWorkspace");
        setSize(1200, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setBackground(new Color(10, 12, 18));

        // ── header bar ────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(10, 12, 18));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 40, 60)));
        header.setPreferredSize(new Dimension(0, 42));

        JLabel title = new JLabel("  ◈  GEOWORKSPACE");
        title.setFont(new Font("Courier New", Font.BOLD, 13));
        title.setForeground(new Color(0, 210, 180));

        JLabel hint = new JLabel(
            "CLICK → place node   ·   CLICK node→node → draw edge   ·   HOVER → inspect   ·   RIGHT-CLICK → delete   ");
        hint.setFont(new Font("Courier New", Font.PLAIN, 11));
        hint.setForeground(new Color(80, 100, 130));

        header.add(title, BorderLayout.WEST);
        header.add(hint,  BorderLayout.EAST);

        // ── canvas ────────────────────────────────────────────────────────────
        WorkspacePanel canvas = new WorkspacePanel();

        // ── status bar ────────────────────────────────────────────────────────
        JLabel status = new JLabel("  ○  CONNECTED  ·  H2 TCP localhost:9092  ·  Console → http://localhost:8082");
        status.setFont(new Font("Courier New", Font.PLAIN, 11));
        status.setForeground(new Color(0, 180, 120));
        status.setOpaque(true);
        status.setBackground(new Color(8, 10, 16));
        status.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(20, 35, 55)));
        status.setPreferredSize(new Dimension(0, 28));

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(status,  BorderLayout.SOUTH);
    }
}