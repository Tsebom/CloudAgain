package com.cloud.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Server {
    protected static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final LogManager logmanager = LogManager.getLogManager();

    private static final ExecutorService service = Executors.newSingleThreadExecutor();

    DataBaseConnect dataBaseConnect;

    public Server() {
        try {
            logmanager.readConfiguration(new FileInputStream("../Server/src/main/resources/logging.properties"));

            dataBaseConnect = DataBaseConnect.getDataBaseConnection();

            service.execute(new PortListener(5679, "localhost", 1460));
        } catch (IOException e) {
            if (dataBaseConnect != null) {
                dataBaseConnect.disconnectDataBase();
            }
            e.printStackTrace();
        }
    }
}