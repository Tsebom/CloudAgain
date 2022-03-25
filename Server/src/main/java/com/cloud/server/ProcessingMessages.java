package com.cloud.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProcessingMessages implements Runnable{
    private final PortListener portListener;
    private final SocketChannel socketChanel;

    private final Logger logger = Server.logger;

    private final User user;
    private final String messages;
    private volatile int downloadPort;

    public ProcessingMessages(User user, String messages) {
        this.user = user;
        this.portListener = user.getPortListener();
        this.socketChanel = user.getSocketChanel();
        this.messages = messages;
    }

    @Override
    public void run() {
        StringBuilder sb = new StringBuilder();
        sb.append(user.getStorageMessage()).append(messages);

        String msg = sb.toString().trim();
        String[] s = msg.split("&");

        if (s[0].equals("start") & s[s.length - 1].equals("end")) {
            logger.info(s[1]);
            processing(s[1].trim());
            user.setStorageMessage("");
        } else {
            user.setStorageMessage(msg);
        }
    }

    /**
     * Perform commands coming from client
     * @param command - the command coming from client
     */
    public void processing(String command) {
        logger.info("get command: " + command);
        try {
            if (user.isAuth() & user.isReg()) {
                if (command.startsWith("getUpdateFileTable")) {
                    serializeData(updateFileTable(user.getCurrentPath()));
                }else if (command.equals("getPathField")) {
                    String s = user.getCurrentPath().toString();
                    serializeData(s.replace(user.getRoot() + File.separator, ""));
                } else if (command.startsWith("download")) {
                    downloadFile(command);
                } else if (command.startsWith("upload")) {
                    uploadFile(command);
                } else if (command.startsWith("moveTo")) {
                    moveTo(command);
                } else if (command.equals("moveBack")) {
                    moveBack();
                } else if (command.startsWith("copy")) {
                    setSelectCopyFile(command);
                } else if (command.equals("past")) {
                    pastFileOrDir();
                } else if (command.startsWith("cut")) {
                    setSelectCutFile(command);
                } else if (command.startsWith("create")) {
                    createFileOrDirectory(command);
                } else if (command.startsWith("rename")) {
                    renameFile(command);
                } else if (command.startsWith("delete")) {
                    deleteFileOrDirectory(user.getCurrentPath().resolve(command.substring("delete ".length())));
                } else if (command.equals("disconnect")) {
                    breakConnect();
                }
            } else {
                if (command.startsWith("auth")) {
                    authorization(command);
                } else if (command.startsWith("reg")) {
                    registration(command);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementing a command to move a selected directory
     * @param command - the name selected directory
     */
    private void moveTo(String command) {
        Path currentPath = user.getCurrentPath().resolve(command.substring("moveTo ".length()));
        if (Files.isDirectory(currentPath)) {
            serializeData(updateFileTable(currentPath));
        } else {
            currentPath = currentPath.getParent();
            serializeData("alert This is not directory");
        }
        user.setCurrentPath(currentPath);
    }

    /**
     * Implementing a command to move back along directory
     */
    private void moveBack() {
        if (!user.getCurrentPath().equals(user.getRoot().resolve(user.getName()))) {
            Path newCurrentPath = user.getCurrentPath().getParent();
            serializeData(updateFileTable(newCurrentPath));
            user.setCurrentPath(newCurrentPath);

        } else {
            serializeData("ok");
        }
    }

    /**
     * Implementing a request for update the list of server files
     * @param path - the target a directory
     * @return - the list of FileInfo class contenting information about the files inside this directory
     */
    private List<FileInfo> updateFileTable (Path path) {
        try {
            return Files.list(path).map(FileInfo::new).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            serializeData("alert Can not update the list of files");
        }
        return null;
    }

    /**
     * Seting path of selected file for copy
     * @param command - the name of selected file
     */
    private void setSelectCopyFile(String command) {
        Path selectFileForCopy = user.getCurrentPath().resolve(command.substring("copy ".length()));
        logger.info("selectFileForCopy is " +  selectFileForCopy);
        user.setSelectFileForCopy(selectFileForCopy);
    }

    /**
     * Seting path of selected file for cut
     * @param command - the name of selected file
     */
    private void setSelectCutFile(String command) {
        Path selectFileForCut = user.getCurrentPath().resolve(command.substring("cut ".length()));
        user.setSelectFileForCut(selectFileForCut);
        logger.info("selectFileForCut is " +  selectFileForCut);
    }

    /**
     * Defines operation (cut or copy) requested by client
     */
    private void pastFileOrDir() {
        logger.info("past");
        if (user.getSelectFileForCopy() != null && user.getSelectFileForCut() == null) {
            pastCopyFileOrDir(user.getSelectFileForCopy());
            user.setSelectFileForCopy(null);
        } else if (user.getSelectFileForCopy() == null && user.getSelectFileForCut() != null) {
            pastCopyFileOrDir(user.getSelectFileForCut());
            deleteFileOrDirectory(user.getSelectFileForCut());
            user.setSelectFileForCut(null);
            return; //to don't invoke twice serializeData("ok")
        }

        serializeData("ok");
    }

    /**
     * Ending the command of copy
     * @param source - the path to the copying file
     */
    private void pastCopyFileOrDir(Path source) {
        logger.info("past fod");
        Path target = user.getCurrentPath().resolve(source.getFileName());

        if (!Files.isDirectory(source)) {
            copyFile(source, target);
        }else if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        }
    }

    /**
     * implementing copying of directory
     * @param source - the path to source directory
     * @param target - the path to target coping directory
     */
    private void copyDirectory(Path source, Path target) {
        logger.info("start copy dir");
        try {
            if (!Files.exists(target)) {
                Files.createDirectory(target);
            }
            List<Path> list = walkDirectory(source);
            logger.info(list.toString());
            if (list.isEmpty()) {
                return;
            }
            Collections.sort(list);

            for (Path p : list) {
                Path s = source.resolve(p);
                Path t = target.resolve(p);
                if (Files.isDirectory(s)) {
                    if (!Files.exists(t)) {
                        Files.createDirectory(t);
                    }
                } else if (!Files.isDirectory(s)) {
                    copyFile(s, t);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Coping file
     * @param source - the path to source file
     * @param target - the path to target of copy
     */
    private void copyFile(Path source, Path target) {
        try {
            if (Files.exists(target)) {
                serializeData("alert The file name is exist");
            } else {
                Files.copy(source, target);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create a fle or a directory
     * @param command - the name of the file or directory
     */
    private void createFileOrDirectory(String command) {
        try {
            if (command.startsWith("create_file")) {
                Files.createFile(Paths.get(user.getCurrentPath().resolve(
                        command.substring("create_file ".length())).toString()));
                serializeData("ok");
            } else if (command.startsWith("create_dir")) {
                Files.createDirectory(Paths.get(user.getCurrentPath().resolve(
                        command.substring("create_dir ".length())).toString()));
                serializeData("ok");
            }
        } catch (IOException e) {
            e.printStackTrace();
            serializeData("alert File cannot be create");
        }
    }

    /**
     * Renaming selected a file or a directory
     * @param command - the old and the new name of the file or directory
     */
    private void renameFile(String command) {
        String[] token = command.split(" ");
        File currentName = new File(user.getCurrentPath().resolve(token[1]).toString());
        File newName = new File(user.getCurrentPath().resolve(token[2]).toString());
        currentName.renameTo(newName);
        serializeData("ok");
    }

    /**
     * Implementing a request to delete a directory or file
     * @param target - the target file or directory
     */
    private void deleteFileOrDirectory(Path target) {
        try {
            if (!Files.isDirectory(target)) {
                Files.delete(target);
            } else if (Files.isDirectory(target)) {
                deleteDirectory(target);
            }
            serializeData("ok");
        } catch (IOException e) {
            e.printStackTrace();
            serializeData("alert File cannot be deleted");
        }
    }

    /**
     * Implementing a request delete a directory or file
     * @param path - the path to the target directory
     */
    private void deleteDirectory(Path path) {
        try {
            List<Path> list = walkDirectory(path);

            if (!list.isEmpty()) {
                Collections.sort(list);
                Collections.reverse(list);
            } else {
                Files.delete(path);
                return;
            }

            while (!list.isEmpty()) {
                Iterator<Path> iterator = list.iterator();
                while (iterator.hasNext()) {
                    Path p = iterator.next();
                    Path t = path.resolve(p);
                    if (!Files.isDirectory(t)) {
                        Files.delete(t);
                    } else if (Files.isDirectory(t) && walkDirectory(t).isEmpty()) {
                        Files.delete(t);
                    }
                    iterator.remove();
                    list.remove(p);
                }
            }
            Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Detouring content of target directory
     * @param path - the path to the target directory
     * @return - the list of path the files consisting into directory
     */
    private List<Path> walkDirectory(Path path) {
        List<Path> list = new ArrayList<>();
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    list.addAll(Files.list(dir).map(p -> truncationPath(p, path)).
                            filter(p -> p != null).collect(Collectors.toList()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Cutting redundant part the path of file for further processing
     * @param path - the previous path
     * @param source - the redundant path
     * @return - the required path
     */
    private Path truncationPath(Path path, Path source) {
        return  path.subpath(source.getNameCount(), path.getNameCount());

    }

    /**
     * Implementing a request disconnect client
     */
    private void breakConnect() {
        try {
            serializeData("disconnect");

            while (portListener.getMessageForSend().get(socketChanel) != null) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            portListener.getSocketUser().remove(socketChanel);
            socketChanel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Authorization user
     * @param command
     */
    private void authorization(String command) {
        String[] token = command.split(" ");

        DataBaseConnect dataBaseConnect = DataBaseConnect.getDataBaseConnection();

        if (dataBaseConnect.getDataBaseAuthService().isRegistration(token[1], token[2])) {
            user.setName(token[1]);
            user.setReg(true);
            user.setAuth(true);
            user.setCurrentPath(user.getRoot().resolve(token[1]));
            serializeData("auth_ok");
        } else {
            serializeData("alert_fail_auth The login or the password is not correct");
        }
    }

    /**
     *
     * @param command
     * @throws IOException
     */
    private void registration(String command) throws IOException {
        String[] token = command.split(" ");

        DataBaseConnect dataBaseConnect = DataBaseConnect.getDataBaseConnection();

        if (dataBaseConnect.getDataBaseAuthService().isRegistration(token[1], token[2])) {
            serializeData("alert_fail_reg This user already exist");
        } else {
            dataBaseConnect.getDataBaseAuthService().setRegistration(token[1], token[2]);
            user.setName(token[1]);
            Files.createDirectory(user.getRoot().resolve(token[1]));
            user.setReg(true);
            user.setAuth(true);
            user.setName(token[1]);
            user.setCurrentPath(user.getRoot().resolve(token[1]));
            serializeData("reg_ok");
        }
    }

    /**
     * Serialize a data for writing the data to the channel
     * @param ob - an object is serialized
     */
    private void serializeData(Object ob) {
        logger.info("start serial");
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(ob);
            oos.flush();
            sendMessage(baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("stop serial");
    }

    /**
     *
     * @param bytes - byte array of data
     */
    private void sendMessage(byte[] bytes) {
        while (portListener.getMessageForSend().get(socketChanel) != null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        portListener.getMessageForSend().put(socketChanel, bytes);

    }

    /////////////////////////DOWNLOAD----UPLOAD////////////////////////////////

    /**
     * This method prepared server for sending the file to the client
     * @param command - the information about the requested file
     */
    private void downloadFile(String command) {
        String[] token = command.split(" ");
        logger.info("take command to download " + token[1]);
        new Thread(() -> {
            try(ServerSocket serverSocket = new ServerSocket(0)) {
                downloadPort = serverSocket.getLocalPort();
                Socket socket = serverSocket.accept();

                writeFile(user.getCurrentPath().resolve(token[1]), socket);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        while(downloadPort == 0) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        serializeData("ready_for_send_file " + downloadPort);
    }

    /**
     * Writing the file  when user download file from server
     * @param path - the path to target file
     * @param socket -
     */
    private void  writeFile(Path path, Socket socket) {
        logger.info("start sending " + path);
        byte[] b = new byte[1460];

        try (InputStream in = new FileInputStream(path.toString());
        OutputStream out = socket.getOutputStream()) {
            while (in.available() != 0) {
                out.write(b, 0 , in.read(b));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            serializeData("alert This file is not exist");
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info("end sending " + path);
    }

    /**
     *  This method prepared server for accept the file
     * @param command - the command with information about the file
     */
    private void uploadFile(String command) {
        String[] token = command.split(" ");

        if (Files.exists(user.getCurrentPath().resolve(token[1]))) {
            serializeData("alert This file is exist");
        } else {
            try {
                Files.createFile(user.getCurrentPath().resolve(token[1]));
                long size = Long.parseLong(token[2]);
                logger.info("size downloading file: " + size);

                new Thread(() -> {
                    try(ServerSocket serverSocket = new ServerSocket(0)) {
                        downloadPort = serverSocket.getLocalPort();
                        Socket socket = serverSocket.accept();

                        readFile(user.getCurrentPath().resolve(token[1]), socket, size);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();

                while(downloadPort == 0) {
                    Thread.sleep(200);
                }
                serializeData("ready_for_get_file " + downloadPort);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Reading the data when user send file to server
     * @param pathFile - the path to file where data writing from channel
     * @param socket -
     * @param sizeFile - the size of file
     * @throws IOException
     */
    private void readFile(Path pathFile, Socket socket, long sizeFile) throws IOException {
        logger.info("start download file");
        byte[] b = new byte[1460];

        try(OutputStream out = new FileOutputStream(pathFile.toString());
            InputStream in = socket.getInputStream()) {

            long size = 0L;

            while (size < sizeFile) {
                int i = in.read(b);
                if (i != -1) {
                    size += i;
                    out.write(b, 0, i);
                }
            }

            serializeData("ok");
        } catch (FileNotFoundException e) {
            serializeData("Error");
            e.printStackTrace();
        }
    }
}
