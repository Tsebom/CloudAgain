package com.cloud.server;

import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 *  Class for storage data about user
 */
public class User {
    private final Logger logger = Server.logger;

    private final PortListener portListener;
    private final SocketChannel socketChannel;
    private boolean isReg;
    private boolean isAuth;

    private String name;

    private Path root;
    private Path currentPath;
    private Path selectFileForCopy;
    private Path selectFileForCut;

    private String storageMessage = "";

    public User(PortListener portListener, SocketChannel socketChannel) {
        this.portListener = portListener;
        this.socketChannel = socketChannel;
        this.isReg = false;
        this.isAuth = false;
        this.root = Paths.get("src/main/resources/serverDir");
        this.name = "stranger";
    }

    public void setReg(boolean reg) {
        isReg = reg;
    }

    public void setAuth(boolean auth) {
        isAuth = auth;
        logger.info(name + " was authorize");
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public void setStorageMessage(String storageMessage) {
        this.storageMessage = storageMessage;
    }

    public void setCurrentPath(Path currentPath) {
        this.currentPath = currentPath;
    }

    public void setSelectFileForCut(Path selectFileForCut) {
        this.selectFileForCut = selectFileForCut;
    }

    public void setSelectFileForCopy(Path selectFileForCopy) {
        this.selectFileForCopy = selectFileForCopy;
    }

    public PortListener getPortListener() {
        return portListener;
    }

    public SocketChannel getSocketChanel() {
        return socketChannel;
    }

    public boolean isReg() {
        return isReg;
    }

    public boolean isAuth() {
        return isAuth;
    }

    public String getName() {
        return name;
    }

    public Path getRoot() {
        return root;
    }

    public String getStorageMessage() {
        return storageMessage;
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    public Path getSelectFileForCut() {
        return selectFileForCut;
    }

    public Path getSelectFileForCopy() {
        return selectFileForCopy;
    }
}
