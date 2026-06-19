package db;

import java.sql.Connection;
import java.sql.Statement;

public class SchemaInitializer {
    public static void initialize() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS adjacency");

            // Use IF NOT EXISTS so existing data is preserved across restarts
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nodes (
                    id    IDENTITY PRIMARY KEY,
                    x     DOUBLE NOT NULL,
                    y     DOUBLE NOT NULL,
                    label VARCHAR(100),
                    adj1  BIGINT DEFAULT NULL,
                    adj2  BIGINT DEFAULT NULL,
                    adj3  BIGINT DEFAULT NULL,
                    adj4  BIGINT DEFAULT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS edges (
                    id        IDENTITY PRIMARY KEY,
                    node_a_id BIGINT NOT NULL,
                    node_b_id BIGINT NOT NULL,
                    length    DOUBLE NOT NULL,
                    FOREIGN KEY (node_a_id) REFERENCES nodes(id) ON DELETE CASCADE,
                    FOREIGN KEY (node_b_id) REFERENCES nodes(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shapes (
                    id         IDENTITY PRIMARY KEY,
                    label      VARCHAR(100),
                    shape_type VARCHAR(50),
                    node_ids   VARCHAR(500),
                    area       DOUBLE DEFAULT 0,
                    perimeter  DOUBLE DEFAULT 0,
                    extra_data VARCHAR(500) DEFAULT ''
                )
            """);

            System.out.println("[DB] Schema ready: nodes, edges, shapes.");
        }
    }
}