package com.cloud.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Realization of interface AuthService for DataBase SQLite
 */
public class DataBaseAuthService implements AuthService {
    private final Logger logger = Server.logger;

    private final DataBaseConnect dataBaseConnect;
    private final Connection connection;
    private PreparedStatement addUser;

    public DataBaseAuthService(Connection connection) {
        dataBaseConnect = DataBaseConnect.getDataBaseConnection();
        this.connection = connection;
        try {
            addUser = setAllPrepareStatement();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getNickNameByLoginAndPassword(String login, String password) {
        ResultSet result;

        try {
            result = dataBaseConnect.getStatement().executeQuery("SELECT nickname FROM UsersOFAuthorization WHERE " +
                    "login = '" + login + "' AND password = '" + password + "'");
            if (result.next()) {
                return result.getString(1);
            }
        }catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean isRegistration(String login, String password) {
        ResultSet result;

        try {
            result = dataBaseConnect.getStatement().executeQuery(String.format("SELECT * FROM UsersOFAuthorization " +
                    "WHERE login = '%s' AND password = '%s'", login, password));
            if (result.next() && result != null) {
                return true;
            }
        }catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void setRegistration(String login, String password) {
        try {
            addUser.setString(1, login);
            addUser.setString(2, password);
            addUser.setString(3, login);
            addUser.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set a query for adding
     * @return An object that represents a precompiled SQL statement
     * @throws SQLException -
     */
    private PreparedStatement setAllPrepareStatement() throws SQLException {
        return connection.prepareStatement("INSERT INTO UsersOFAuthorization (login, password, nickname) " +
                "VALUES ( ? , ? , ? )");
    }
}