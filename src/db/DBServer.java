package db;

import org.h2.tools.Server;

import java.sql.SQLException;

public class DBServer {
    private static Server tcpServer;

    public static void start() throws SQLException {
        tcpServer = Server.createTcpServer(
                "-tcp",
                "-tcpAllowOthers",
                "-ifNotExists",
                "-tcpPort", "9092"
        ).start();

        System.out.println("H2 TCP Server started: " + tcpServer.getURL());
    }

    public static void stop() {
        if (tcpServer != null) tcpServer.stop();
    }
}