package db.dao;

import db.DBConnection;
import model.Node;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AdjacencyDAO {

    public void insertBidirectional(long a, long b) throws Exception {
        insert(a, b);
        insert(b, a);
    }

    private void insert(long nodeId, long adjacentId) throws Exception {
        String sql = "MERGE INTO adjacency (node_id, adjacent_node_id) VALUES (?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, nodeId);
            ps.setLong(2, adjacentId);
            ps.executeUpdate();
        }
    }

    public List<Node> getAdjacentNodes(long nodeId) throws Exception {
        List<Node> list = new ArrayList<>();

        String sql = """
            SELECT n.* FROM adjacency a
            JOIN nodes n ON a.adjacent_node_id = n.id
            WHERE a.node_id = ?
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, nodeId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new Node(
                        rs.getLong("id"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getString("label")
                ));
            }
        }
        return list;
    }
}

