package db.dao;

import db.DBConnection;
import model.Node;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NodeDAO {

    public Node insert(Node node) throws Exception {
        String sql = "INSERT INTO nodes (x, y, label) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setDouble(1, node.getX());
            ps.setDouble(2, node.getY());
            ps.setString(3, node.getLabel());
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) node.setId(rs.getLong(1));
        }
        return node;
    }

    public List<Node> getAll() throws Exception {
        List<Node> nodes = new ArrayList<>();
        String sql = "SELECT * FROM nodes";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                nodes.add(new Node(
                        rs.getLong("id"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getString("label")
                ));
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

            if (rs.next()) {
                return new Node(
                        rs.getLong("id"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getString("label")
                );
            }
        }
        return null;
    }
}
