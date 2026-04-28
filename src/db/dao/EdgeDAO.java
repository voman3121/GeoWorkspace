package db.dao;

import db.DBConnection;
import model.Edge;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EdgeDAO {
    public Edge insert(Edge edge) throws Exception {
        String sql = "INSERT INTO edges (node_a_id, node_b_id) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, edge.getNodeAId());
            ps.setLong(2, edge.getNodeBId());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) edge.setId(rs.getLong(1));
        }
        return edge;
    }
    public List<Edge> getAll() throws Exception {
        List<Edge> edges = new ArrayList<>();
        String sql = "SELECT * FROM edges";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                edges.add(new Edge(
                        rs.getLong("id"),
                        rs.getLong("node_a_id"),
                        rs.getLong("node_b_id")
                ));
            }
        }
        return edges;
    }
}