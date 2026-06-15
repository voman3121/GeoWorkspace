package db.dao;
import db.DBConnection; import model.Edge;
import java.sql.*; import java.util.*;
public class EdgeDAO {
    public Edge insert(Edge edge)throws Exception{
        try(Connection c=DBConnection.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO edges(node_a_id,node_b_id,length)VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            ps.setLong(1,edge.getNodeAId());ps.setLong(2,edge.getNodeBId());ps.setDouble(3,edge.getLength());ps.executeUpdate();
            ResultSet rs=ps.getGeneratedKeys();if(rs.next())edge.setId(rs.getLong(1));}return edge;}
    public void insertWithId(long id,long aId,long bId,double len)throws Exception{
        try(Connection c=DBConnection.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO edges(id,node_a_id,node_b_id,length)VALUES(?,?,?,?)")){
            ps.setLong(1,id);ps.setLong(2,aId);ps.setLong(3,bId);ps.setDouble(4,len);ps.executeUpdate();}
        System.out.println("[DB] Edge restored id="+id);}
    public List<Edge> getAll()throws Exception{
        List<Edge>list=new ArrayList<>();
        try(Connection c=DBConnection.getConnection();Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT*FROM edges")){
            while(rs.next())list.add(new Edge(rs.getLong("id"),rs.getLong("node_a_id"),rs.getLong("node_b_id"),rs.getDouble("length")));}return list;}
    public void delete(long aId,long bId)throws Exception{
        try(Connection c=DBConnection.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM edges WHERE(node_a_id=? AND node_b_id=?)OR(node_a_id=? AND node_b_id=?)")){
            ps.setLong(1,aId);ps.setLong(2,bId);ps.setLong(3,bId);ps.setLong(4,aId);ps.executeUpdate();}}
    public void deleteById(long id)throws Exception{
        try(Connection c=DBConnection.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM edges WHERE id=?")){ps.setLong(1,id);ps.executeUpdate();}}
}