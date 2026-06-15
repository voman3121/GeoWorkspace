package db.dao;
import db.DBConnection; import model.Node;
import java.sql.*; import java.util.*; 
public class NodeDAO {
    public Node insert(Node node) throws Exception {
        try(Connection c=DBConnection.getConnection();
            PreparedStatement ps=c.prepareStatement("INSERT INTO nodes(x,y,label)VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            ps.setDouble(1,node.getX());ps.setDouble(2,node.getY());ps.setString(3,node.getLabel());ps.executeUpdate();
            ResultSet rs=ps.getGeneratedKeys();if(rs.next())node.setId(rs.getLong(1));}
        System.out.println("[DB] Node inserted id="+node.getId());return node;}
    public void insertWithId(long id,double x,double y,String label)throws Exception{
        try(Connection c=DBConnection.getConnection();
            PreparedStatement ps=c.prepareStatement("INSERT INTO nodes(id,x,y,label)VALUES(?,?,?,?)")) {
            ps.setLong(1,id);ps.setDouble(2,x);ps.setDouble(3,y);ps.setString(4,label);ps.executeUpdate();}
        System.out.println("[DB] Node restored id="+id);}
    public List<Node> getAll()throws Exception{
        List<Node>list=new ArrayList<>();
        try(Connection c=DBConnection.getConnection();Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT*FROM nodes")){
            while(rs.next())list.add(map(rs));}return list;}
    public Node findById(long id)throws Exception{
        try(Connection c=DBConnection.getConnection();PreparedStatement ps=c.prepareStatement("SELECT*FROM nodes WHERE id=?")){
            ps.setLong(1,id);ResultSet rs=ps.executeQuery();return rs.next()?map(rs):null;}}
    public void delete(long id)throws Exception{
        try(Connection c=DBConnection.getConnection()){
            try(PreparedStatement ps=c.prepareStatement("UPDATE nodes SET adj1=CASE WHEN adj1=? THEN NULL ELSE adj1 END,adj2=CASE WHEN adj2=? THEN NULL ELSE adj2 END,adj3=CASE WHEN adj3=? THEN NULL ELSE adj3 END,adj4=CASE WHEN adj4=? THEN NULL ELSE adj4 END WHERE adj1=? OR adj2=? OR adj3=? OR adj4=?")){
                for(int i=1;i<=8;i++)ps.setLong(i,id);ps.executeUpdate();}
            try(PreparedStatement ps=c.prepareStatement("DELETE FROM nodes WHERE id=?")){ps.setLong(1,id);ps.executeUpdate();}
            System.out.println("[DB] Node deleted id="+id);}}
    public void addAdjacency(long aId,long bId)throws Exception{
        Node a=findById(aId),b=findById(bId);
        if(a==null||b==null)throw new Exception("Node not found");
        if(a.hasNeighbour(bId))throw new Exception("Edge already exists between these nodes");
        if(a.firstFreeSlot()<0)throw new Exception("Node "+a.getLabel()+" already has 4 neighbours");
        if(b.firstFreeSlot()<0)throw new Exception("Node "+b.getLabel()+" already has 4 neighbours");
        try(Connection c=DBConnection.getConnection()){setAdj(c,aId,a.firstFreeSlot(),bId);setAdj(c,bId,b.firstFreeSlot(),aId);}}
    public void removeAdjacency(long aId,long bId)throws Exception{
        try(Connection c=DBConnection.getConnection()){clearAdj(c,aId,bId);clearAdj(c,bId,aId);}}
    private void setAdj(Connection c,long nodeId,int slot,long val)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("UPDATE nodes SET adj"+slot+"=? WHERE id=?")){ps.setLong(1,val);ps.setLong(2,nodeId);ps.executeUpdate();}}
    private void clearAdj(Connection c,long nodeId,long tgt)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("UPDATE nodes SET adj1=CASE WHEN adj1=? THEN NULL ELSE adj1 END,adj2=CASE WHEN adj2=? THEN NULL ELSE adj2 END,adj3=CASE WHEN adj3=? THEN NULL ELSE adj3 END,adj4=CASE WHEN adj4=? THEN NULL ELSE adj4 END WHERE id=?")){
            ps.setLong(1,tgt);ps.setLong(2,tgt);ps.setLong(3,tgt);ps.setLong(4,tgt);ps.setLong(5,nodeId);ps.executeUpdate();}}
    private Node map(ResultSet rs)throws SQLException{
        return new Node(rs.getLong("id"),rs.getDouble("x"),rs.getDouble("y"),rs.getString("label"),
                rs.getLong("adj1"),rs.getLong("adj2"),rs.getLong("adj3"),rs.getLong("adj4"));}
}