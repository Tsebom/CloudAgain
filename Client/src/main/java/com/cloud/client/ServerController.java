package com.cloud.client;

import com.cloud.server.FileInfo;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.swing.*;
import java.net.URL;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class ServerController implements Initializable {
    private Logger logger = ClientConnect.logger;

    private static final int PORT = 5679;
    private static final String IP_ADRESS = "localhost";
    private static Stage stage;

    private ClientConnect connect;

    List<FileInfo> listFile;
    private String selected;

    @FXML
    public TextField pathField;
    @FXML
    public VBox manager_box;
    @FXML
    public VBox auth_box;
    @FXML
    public Button sign_in;
    @FXML
    public Button sign_up;
    @FXML
    public Button registration;
    @FXML
    public Button back_sign_in;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public TableView fileTable;

    private boolean isRegistration = true;
    private boolean isTryRegistration = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        TableColumn<FileInfo, String> nameFileColumn = new TableColumn<>("Name");
        nameFileColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFilename()));
        nameFileColumn.setPrefWidth(150);

        TableColumn<FileInfo, Long> sizeFileColumn = new TableColumn<>("Size");
        sizeFileColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        sizeFileColumn.setPrefWidth(150);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        TableColumn<FileInfo, String> dateFileColumn = new TableColumn<>("Modified");
        dateFileColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getLastTimeModify()
                .format(dtf)));
        dateFileColumn.setPrefWidth(150);

        fileTable.getColumns().addAll(nameFileColumn, sizeFileColumn, dateFileColumn);

        sizeFileColumn.setCellFactory(column -> {
            return new TableCell<FileInfo, Long>() {
                @Override
                protected void updateItem(Long item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        String text = String.format("%,d bytes", item);
                        if (item == -1L) {
                            text = "[DIR]";
                        }
                        setText(text);
                    }
                }
            };
        });

        fileTable.getSortOrder().add(sizeFileColumn);
        fileTable.getSortOrder().add(nameFileColumn);

        Platform.runLater(() -> {
            stage.setOnCloseRequest((event) -> {
                if (connect == null) {
                    Platform.exit();
                } else if (connect != null) {
                    connect.getQueue().add("disconnect");
                }
            });
        });
    }

    public List<FileInfo> getListFile() {
        return listFile;
    }

    public String getSelected() {
        return selected;
    }

    public void setSelected(String selected) {
        this.selected = selected;
    }

    public static void setStage(Stage stage) {
        ServerController.stage = stage;
    }

    public boolean isRegistration() {
        return isRegistration;
    }

    /**
     * Open registration form
     * @param actionEvent - click to button Sign up
     */
    public void signUp(ActionEvent actionEvent) {
        regOrAuth(isTryRegistration);
        setTitle("Cloud Registration");
    }

    /**
     * Commence process of registration and open connection with server
     * @param actionEvent - click to button Registration
     */
    public void registration(ActionEvent actionEvent) {
        connect = ClientConnect.getInstance();
        connect.setNameUser(loginField.getText());
        connect.setServerController(this);

        connect.getQueue().add("reg ".concat(loginField.getText().trim() + " ").concat(passwordField.getText().trim()));
    }

    /**
     * Commence process of authorization
     * @param actionEvent - click to button Sign in
     */
    public void signIn(ActionEvent actionEvent) {
        connect = ClientConnect.getInstance();
        connect.setNameUser(loginField.getText());
        connect.setServerController(this);
        connect.getQueue().add("auth ".concat(loginField.getText().trim() + " ")
                .concat(passwordField.getText().trim()));
    }

    /**
     *  Set title for window
     * @param title - title
     */
    public void setTitle(String title) {
        Platform.runLater(() -> {
            stage.setTitle(title);
        });
    }

    /**
     *  Display list file from server
     * @param list - list of file
     */
    public void updateFileTable(List<FileInfo> list) {
        listFile = list;
        fileTable.getItems().clear();
        fileTable.getItems().addAll(list);
        fileTable.sort();
    }

    /**
     * Creating new folder or file inside current directory on the server side
     * @param actionEvent - click to button +
     */
    public void createNewFolderOrFile(ActionEvent actionEvent) {
        String name = JOptionPane.showInputDialog("Type the name folder");
        if (name != null && !name.equals("")) {
            if (name.contains(".")) {
                connect.getQueue().add("create_file ".concat(name));
            }
            else {
                connect.getQueue().add("create_dir ".concat(name));
            }
        }
    }

    /**
     * Selection files or directories from list representing in the server part of window
     * @param mouseEvent - clicking to line of table of files corresponding a file or a directory
     */
    public void selectDirectory(MouseEvent mouseEvent) {
        FileInfo fileInfo= (FileInfo)fileTable.getSelectionModel().getSelectedItem();
        if (mouseEvent.getClickCount() == 1) {
            if (fileInfo != null) {
                selected = Paths.get(fileInfo.getFilename()).toString();
                logger.info(selected);
            }
        } else if (mouseEvent.getClickCount() == 2) {
            if (fileInfo != null) {
                connect.getQueue().add("moveTo ".concat(
                        ((FileInfo)fileTable.getSelectionModel().getSelectedItem()).getFilename()));
                selected = null;
            }
        }
    }

    /**
     * Move to a parent directory
     * @param actionEvent - click to button Up
     */
    public void toParentPathAction(ActionEvent actionEvent) {
        connect.getQueue().add("moveBack");
    }

    /**
     * Delete a file on the server side
     * @param actionEvent - click to button delete
     */
    public void deleteFile(ActionEvent actionEvent) {
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "You are going to delete " + selected + " from server! You are sure?",
                    ButtonType.YES, ButtonType.NO);
            Optional<ButtonType> option = alert.showAndWait();
            if (option.get() == ButtonType.YES) {
                connect.getQueue().add("delete ".concat(selected));
            }
            selected = null;
        } else {
            Platform.runLater(() -> ClientController.
                    alertWarning("No one file was selected"));
        }
    }

    /**
     * Rename a file on the server side
     * @param actionEvent - click to button Rename
     */
    public void renameFile(ActionEvent actionEvent) {
        if (selected != null) {
            String rename = JOptionPane.showInputDialog("Type the new name");
            if (rename != null && !rename.equals("")) {
                if (isNameFile(rename)) {
                    Alert alert = new Alert(Alert.AlertType.WARNING,
                            "The file's name already exist!", ButtonType.CANCEL);
                    alert.showAndWait();
                } else {
                    connect.getQueue().add("rename ".concat(selected + " " +rename));
                }
            }
            selected = null;
        } else {
            Platform.runLater(() -> ClientController.
                    alertWarning("No one file was selected"));
        }
    }

    /**
     * Set selected a file or directory like source for copy
     * @param actionEvent - click to button Copy
     */
    public void copyFile(ActionEvent actionEvent) {
        if (selected != null) {
            connect.getQueue().add("copy ".concat(selected));
            selected = null;
        } else {
            Platform.runLater(() -> ClientController.
                    alertWarning("No one file was selected"));
        }
    }

    /**
     * Commence process of copy or cut if appropriate process was selected
     * @param actionEvent - click to button Paste
     */
    public void pasteFile(ActionEvent actionEvent) {
        connect.getQueue().add("past");
    }

    /**
     * Set selected a file or directory like source for cut
     * @param actionEvent - click to button Cut
     */
    public void cutFile(ActionEvent actionEvent) {
        if (selected != null) {
            connect.getQueue().add("cut ".concat(selected));
            selected = null;
        } else {
            Platform.runLater(() -> ClientController.
                    alertWarning("No one file was selected"));
        }
    }

    /**
     * Turn to registration form
     * @param isRegistration -
     */
    protected void switchServerWindow(boolean isRegistration) {
        auth_box.setVisible(!isRegistration);
        auth_box.setManaged(!isRegistration);
        manager_box.setVisible(isRegistration);
        manager_box.setManaged(isRegistration);
        this.isRegistration = !isRegistration;
    }

    /**
     *
     * @param isTryRegistration -
     */
    private void regOrAuth (boolean isTryRegistration) {
        sign_in.setVisible(isTryRegistration);
        sign_in.setManaged(isTryRegistration);
        sign_up.setVisible(isTryRegistration);
        sign_up.setManaged(isTryRegistration);
        registration.setVisible(!isTryRegistration);
        registration.setManaged(!isTryRegistration);
        back_sign_in.setVisible(!isTryRegistration);
        back_sign_in.setManaged(!isTryRegistration);
        this.isTryRegistration = !isTryRegistration;
    }

    /**
     * Check existing a file
     * @param nameFile - a name file
     * @return - true if file exist false if not
     */
    private boolean isNameFile(String nameFile) {
        for (FileInfo f : listFile) {
            if (f.getFilename().equals(nameFile)) {
                return true;
            }
        }
        return false;
    }
}
