package db.dao;

import db.DBConnection;
import model.Edge;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EdgeDAO {

    public Edge insert(Edge edge) throws Exception {
        String sql = "INSERT INTO edges (node_a_id, node_b_id, length) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, edge.getNodeAId());
            ps.setLong(2, edge.getNodeBId());
            ps.setDouble(3, edge.getLength());

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) edge.setId(rs.getLong(1));
        }
        return edge;
    }

    public List<Edge> getAll() throws Exception {
        List<Edge> edges = new ArrayList<>();
        String sql = "SELECT * FROM edges ORDER BY id";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                edges.add(new Edge(
                        rs.getLong("id"),
                        rs.getLong("node_a_id"),
                        rs.getLong("node_b_id"),
                        rs.getDouble("length")
                ));
            }
        }
        return edges;
    }

    public void delete(long edgeId) throws Exception {
        String sql = "DELETE FROM edges WHERE id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, edgeId);
            ps.executeUpdate();
        }
    }

    public void deleteByNode(long nodeId) throws Exception {
        String sql = "DELETE FROM edges WHERE node_a_id=? OR node_b_id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, nodeId);
            ps.setLong(2, nodeId);
            ps.executeUpdate();
        }
    }
}