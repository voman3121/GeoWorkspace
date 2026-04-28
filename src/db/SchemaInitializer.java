package db;

import java.sql.Connection;
import java.sql.Statement;

public class SchemaInitializer {

    public static void initialize() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nodes (
                    id IDENTITY PRIMARY KEY,
                    grid_x INT NOT NULL,
                    grid_y INT NOT NULL,
                    screen_x DOUBLE NOT NULL,
                    screen_y DOUBLE NOT NULL,
                    label VARCHAR(100) NOT NULL,
                    degree INT DEFAULT 0,
                    adjacent_nodes VARCHAR(100) DEFAULT ''
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS edges (
                    id IDENTITY PRIMARY KEY,
                    node_a_id BIGINT NOT NULL,
                    node_b_id BIGINT NOT NULL,
                    length DOUBLE NOT NULL,
                    FOREIGN KEY (node_a_id) REFERENCES nodes(id) ON DELETE CASCADE,
                    FOREIGN KEY (node_b_id) REFERENCES nodes(id) ON DELETE CASCADE
                )
            """);
        }
    }
}