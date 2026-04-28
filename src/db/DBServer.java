package db;

import org.h2.tools.Server;
import java.sql.SQLException;

public class DBServer {
    private static Server tcpServer;

    public static void start() {
        try {
            tcpServer = Server.createTcpServer(
                    "-tcp", "-tcpAllowOthers", "-ifNotExists", "-tcpPort", "8082"
            ).start();
            System.out.println("H2 TCP Server started: " + tcpServer.getURL());
        } catch (SQLException e) {
            // Port already in use = H2 jar is already running externally, that's fine
            System.out.println("Port 8082 already in use — connecting to existing H2 server.");
        }
    }

    public static void stop() {
        if (tcpServer != null) tcpServer.stop();
    }
}