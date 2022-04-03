package com.cloud.client;

import com.cloud.server.FileInfo;
import javafx.application.Platform;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Set connection with server
 */
public class ClientConnect implements Runnable{
    private static ClientConnect instance;

    protected static final Logger logger = Logger.getLogger(ClientConnect.class.getName());
    private static final LogManager logmanager = LogManager.getLogManager();

    private static final int PORT = 5679;
    private static final String IP_ADDRESS = "localhost";
    private static final int BUFFER_SIZE = 1460;

    private List<Byte> messageStorage;

    private static final List<Byte> END = new ArrayList<>();

    static {
        END.add((byte) 60);
        END.add((byte) 69);
        END.add((byte) 78);
        END.add((byte) 68);
        END.add((byte) 62);
    }

    private Selector selector;
    private SocketChannel channel;
    private SocketAddress serverAddress;
    private Thread downloadFileThread = null;

    private ServerController serverController;
    private ClientController clientController;

    private final Queue<String> queue = new LinkedBlockingQueue<>();
    private boolean breakClientConnect;
    private String nameUser;
    private volatile int portForDownload = 0;


    private ClientConnect() {
        logger.info("client instance created");
        try {
            logmanager.readConfiguration(new FileInputStream("../Client/src/main/resources/logging.properties"));
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_CONNECT, ByteBuffer.allocate(BUFFER_SIZE));
            channel.connect(new InetSocketAddress(IP_ADDRESS, PORT));
            serverAddress = channel.getRemoteAddress();
        } catch (IOException e) {
            e.printStackTrace();
        }
        instance = this;
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (breakClientConnect) {
                    channel.close();
                    selector.close();
                    logger.info("break client");
                    break;
                }
                if (selector.isOpen()) {
                    selector.select();
                    Set<SelectionKey> keys = selector.selectedKeys();
                    for (SelectionKey key : keys) {
                        if (key.isConnectable()) {
                            channel.finishConnect();
                            logger.info("Connection to the server");
                            key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
                        }
                        if (key.isWritable() && !queue.isEmpty()) {
                            String line = queue.poll();
                            if (line != null && line.equals("download")) {
                                downloadFile();
                                continue;
                            }
                            if (line != null) {
                                logger.info("send command to the server: " + line);
                                channel.write(ByteBuffer.wrap(wrapMessage(line).getBytes(StandardCharsets.UTF_8)));
                            }
                        }
                        if (key.isReadable()) {
                            read(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static ClientConnect getInstance() {
        if (instance == null) {
            new Thread(new ClientConnect()).start();
        }
        return instance;
    }

    public Queue<String> getQueue() {
        return queue;
    }

    public void setServerController(ServerController serverController) {
        this.serverController = serverController;
    }

    public void setClientController(ClientController clientController) {
        this.clientController = clientController;
    }

    public void setNameUser(String nameUser) {
        this.nameUser = nameUser;
    }

    /**
     * Reading messages from server
     * @param key - token of chanel
     */
    public void read(SelectionKey key) {
        logger.info("the start reading data from the channel: " + serverAddress);
        ByteBuffer buf = (ByteBuffer) key.attachment();
        try {
            List<Byte> list = new ArrayList<>();
            while (channel.read(buf) > 0) {
                buf.flip();
                for (int i = 0; i < buf.limit(); i++) {
                    list.add(buf.get(i));
                }
                buf.clear();
            }

            if (list.size() > 4) {
                logger.info(list.toString());
                List<Byte> endSubList = new ArrayList<>();

                for (int i = list.size()-5; i < list.size(); i++) {
                    endSubList.add(list.get(i));
                }

                if (endSubList.equals(END)) {
                    logger.info("END");
                    if (messageStorage == null) {
                        messageStorage = list;
                    } else {
                        messageStorage.addAll(list);
                    }

                    byte[] b = new byte[messageStorage.size() - 5];
                    for (int i = 0; i < messageStorage.size() - 5; i++) {
                        b[i] = messageStorage.get(i);
                    }
                    messageStorage = null;
                    convertData(b);
                } else if (messageStorage != null) {
                    messageStorage.addAll(list);
                } else {
                    messageStorage = list;
                }
            } else if (messageStorage != null) {
                messageStorage.addAll(list);
            } else {
                messageStorage = list;
            }

            logger.info("the end read data from the channel: " + serverAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reading file data from server
     * @param pathFile - A path to a file
     * @param fileInfo - Instance of FileInfo class a file
     */
    private void readFile(Path pathFile, FileInfo fileInfo) {
        logger.info("start download file");
        byte[] b = new byte[1460];

        try (Socket socket = new Socket(IP_ADDRESS, portForDownload);
             InputStream in = socket.getInputStream();
             OutputStream out = new FileOutputStream(pathFile.toString())) {

            portForDownload = 0;

            long size = 0L;
            while (size < fileInfo.getSize()) {

                int i = in.read(b);
                if (i != -1) {
                    size += i;
                    out.write(b, 0, i);
                }
            }
            clientController.updateFileTable(pathFile.getParent());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sending file to server
     * @param path - The path to file is writing
     */
    private void  writeFile(Path path, String command) {
        logger.info("start sending " + path);
        byte[] b = new byte[1460];

        String[] token = command.split(" ");
        int port = Integer.parseInt(token[1]);

        try (Socket socket = new Socket(IP_ADDRESS, port);
             InputStream in = new FileInputStream(path.toString());
             OutputStream out = socket.getOutputStream()) {

            while (in.available() != 0) {
                out.write(b, 0, in.read(b));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("end sending " + path);
    }

    /**
     *  Prepare to download file from server
     */
    private void downloadFile() {
        try {
            String filename = serverController.getSelected();
            if (filename == null) {
                Platform.runLater(() -> ClientController.
                        alertWarning("No one file was selected"));
                return;
            }
            for (FileInfo f: serverController.getListFile()) {
                if (f.getFilename().equals(filename) && f.getSize() != -1L) {
                    Path pathFile = Paths.get(clientController.pathField.getText()).resolve(f.getFilename());
                    if (!Files.exists(pathFile)) {
                        logger.info(f.getFilename());
                        String command = wrapMessage("download " + f.getFilename());

                        downloadFileThread = new Thread(() -> {
                            try {
                                Files.createFile(pathFile);
                                readFile(pathFile, f);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        channel.write(ByteBuffer.wrap(command.getBytes(StandardCharsets.UTF_8)));
                        logger.info("command to download was sent");
                    } else {
                        Platform.runLater(() -> ClientController.alertWarning("This file is exist"));
                    }
                    serverController.setSelected(null);
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            serverController.setSelected(null);
        }
    }

    /**
     * Processing command getting from server
     * @param command - getting command
     */
    private void processingCommand(String command) {
        logger.info(command);
        //warning about fail
        if (command.startsWith("alert")) {
            logger.info("alert");
            if (command.startsWith("alert_fail_reg")) {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert_fail_reg ", "")));
            } else if (command.startsWith("alert_fail_auth")) {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert_fail_auth ", "")));
            } else if (command.startsWith("alert_fail_data")) {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert_fail_data ", "")));
            } else {
                Platform.runLater(() -> ClientController.
                        alertWarning(command.replace("alert ", "")));
            }
        }
        //relevant commands
        if (command.equals("ok")) {
            queue.add("getUpdateFileTable");
        } else if (command.equals("auth_ok")) {
            logger.info("authorization has been confirmed");
            serverController.switchServerWindow(serverController.isRegistration());
            serverController.setTitle("Cloud");
            queue.add("getUpdateFileTable");
        } else if (command.startsWith(nameUser)) {
            serverController.pathField.setText(command.replace(
                    command.substring(0, nameUser.length()), nameUser + ":~" ));
        } else if (command.equals("reg_ok")) {
            logger.info("registration has been confirmed");
            serverController.switchServerWindow(serverController.isRegistration());
            serverController.setTitle("Cloud");
            queue.add(wrapMessage("getPathField"));
        } else if (command.startsWith("ready_for_get_file")) {
            new Thread(() -> writeFile(clientController.getSelectedFileForUpload(), command)).start();
        } else if (command.startsWith("ready_for_send_file")) {
            portForDownload = Integer.parseInt(command.split(" ")[1]);
            logger.info("port for download: " + portForDownload);

            if (downloadFileThread != null && portForDownload != 0) {
                logger.info("start thread download");
                downloadFileThread.start();
                downloadFileThread = null;
            }
        }

        if (command.equals("disconnect")) {
            logger.info("disconnect confirmed");
            breakClientConnect = true;
            Platform.runLater(Platform::exit);
        }
    }

    /**
     * Deserialization Object getting from server
     * @param b - set of byte getting from server
     */
    private void convertData(byte[] b) {
        logger.info(b.toString());
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object ob = ois.readObject();
            if (ob instanceof String) {
                logger.info("string");
                processingCommand((String) ob);
            }
            if (ob instanceof ArrayList) {
                logger.info("arraylist");
                castFileInfo(ob);
            }
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Casting object getting after deserialization
     * @param ob - casting object
     */
    private void castFileInfo(Object ob) {
        List<FileInfo> fl = new ArrayList<>();
        for (Object o : (ArrayList) ob) {
            if (o instanceof FileInfo) {
                fl.add((FileInfo) o);
            }
        }
        serverController.updateFileTable(fl);
        queue.add("getPathField");
    }

    /**
     * Wrap message for server by start/end markers
     * @param message - wrapping message
     * @return - resulting message
     */
    private String wrapMessage(String message) {
        return "start&" + message + "&end";
    }
}
