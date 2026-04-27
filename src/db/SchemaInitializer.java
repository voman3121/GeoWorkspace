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
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    label VARCHAR(100)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS edges (
                    id IDENTITY PRIMARY KEY,
                    node_a_id BIGINT NOT NULL,
                    node_b_id BIGINT NOT NULL,
                    FOREIGN KEY (node_a_id) REFERENCES nodes(id),
                    FOREIGN KEY (node_b_id) REFERENCES nodes(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS adjacency (
                    node_id BIGINT NOT NULL,
                    adjacent_node_id BIGINT NOT NULL,
                    PRIMARY KEY (node_id, adjacent_node_id),
                    FOREIGN KEY (node_id) REFERENCES nodes(id),
                    FOREIGN KEY (adjacent_node_id) REFERENCES nodes(id)
                )
            """);
        }
    }
}