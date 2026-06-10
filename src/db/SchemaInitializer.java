package db;

import java.sql.Connection;
import java.sql.Statement;

public class SchemaInitializer {
    public static void initialize() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Drop old adjacency table if it exists from previous runs
            stmt.execute("DROP TABLE IF EXISTS adjacency");

            // NODES: adjacency list stored inline as adj1..adj4 columns
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nodes (
                    id     IDENTITY PRIMARY KEY,
                    x      DOUBLE NOT NULL,
                    y      DOUBLE NOT NULL,
                    label  VARCHAR(100),
                    adj1   BIGINT DEFAULT NULL,
                    adj2   BIGINT DEFAULT NULL,
                    adj3   BIGINT DEFAULT NULL,
                    adj4   BIGINT DEFAULT NULL
                )
            """);

            // EDGES: includes computed length
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS edges (
                    id         IDENTITY PRIMARY KEY,
                    node_a_id  BIGINT NOT NULL,
                    node_b_id  BIGINT NOT NULL,
                    length     DOUBLE NOT NULL,
                    FOREIGN KEY (node_a_id) REFERENCES nodes(id) ON DELETE CASCADE,
                    FOREIGN KEY (node_b_id) REFERENCES nodes(id) ON DELETE CASCADE
                )
            """);

            System.out.println("[DB] Schema ready: 2 tables (nodes, edges).");
        }
    }
}