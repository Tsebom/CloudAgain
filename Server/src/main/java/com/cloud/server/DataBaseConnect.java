package com.cloud.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DataBaseConnect {
    private static DataBaseConnect dataBaseConnect = null;
    private final DataBaseAuthService dataBaseAuthService;

    private final Logger logger = Server.logger;

    private  Connection connection;
    private Statement statement;

    private DataBaseConnect() {
        dataBaseConnect = this;
        connectDataBase();
        dataBaseAuthService = new DataBaseAuthService(connection);
    }

    public static DataBaseConnect getDataBaseConnection() {
        if (dataBaseConnect == null) {
            dataBaseConnect = new DataBaseConnect();
        }
        return dataBaseConnect;
    }

    public Statement getStatement() {
        return statement;
    }

    public DataBaseAuthService getDataBaseAuthService() {
        return dataBaseAuthService;
    }

    /**
     * Set connect to RegBase
     */
    private void connectDataBase(){
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:../Server/src/main/resources/RegBase.db");
            statement = connection.createStatement();
            logger.info(statement.toString());
            logger.info("server has connected to RegBase");
        } catch (ClassNotFoundException e) {
           logger.log(Level.SEVERE, "class \"org.sqlite.JDBC\" hasn't be find", e);
        } catch (SQLException sql) {
            logger.log(Level.SEVERE, "registration database access occurs while connect", sql);
        }
    }

    /**
     * Close connect to RegBase
     */
    public void disconnectDataBase(){
        try {
            statement.close();
            connection.close();
            logger.info("server has disconnected to RegBase");
        } catch (SQLException sql) {
            logger.log(Level.SEVERE, "registration database access occurs while disconnect", sql);
        }
    }
}
