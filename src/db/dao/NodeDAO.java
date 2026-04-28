package db.dao;

import db.DBConnection;
import model.Node;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NodeDAO {

    public Node insert(Node node) throws Exception {
        String sql = """
            INSERT INTO nodes (grid_x, grid_y, screen_x, screen_y, label, degree, adjacent_nodes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, node.getGridX());
            ps.setInt(2, node.getGridY());
            ps.setDouble(3, node.getScreenX());
            ps.setDouble(4, node.getScreenY());
            ps.setString(5, node.getLabel());
            ps.setInt(6, node.getDegree());
            ps.setString(7, node.getAdjacentNodes());

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) node.setId(rs.getLong(1));
        }
        return node;
    }

    public List<Node> getAll() throws Exception {
        List<Node> nodes = new ArrayList<>();
        String sql = "SELECT * FROM nodes ORDER BY id";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                nodes.add(map(rs));
            }
        }
        return nodes;
    }

    public Node findById(long id) throws Exception {
        String sql = "SELECT * FROM nodes WHERE id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return map(rs);
        }
        return null;
    }

    public Node findByGrid(int gx, int gy) throws Exception {
        String sql = "SELECT * FROM nodes WHERE grid_x=? AND grid_y=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, gx);
            ps.setInt(2, gy);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);
        }
        return null;
    }

    public void updateAdjacency(long nodeId, String adjacency, int degree) throws Exception {
        String sql = "UPDATE nodes SET adjacent_nodes=?, degree=? WHERE id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, adjacency);
            ps.setInt(2, degree);
            ps.setLong(3, nodeId);
            ps.executeUpdate();
        }
    }

    public void delete(long nodeId) throws Exception {
        String sql = "DELETE FROM nodes WHERE id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, nodeId);
            ps.executeUpdate();
        }
    }

    private Node map(ResultSet rs) throws Exception {
        return new Node(
                rs.getLong("id"),
                rs.getInt("grid_x"),
                rs.getInt("grid_y"),
                rs.getDouble("screen_x"),
                rs.getDouble("screen_y"),
                rs.getString("label"),
                rs.getInt("degree"),
                rs.getString("adjacent_nodes")
        );
    }
}