import java.io.File;
import java.nio.file.Path;

public class User {
    private boolean isReg;
    private boolean isAuth;

    private String name;

    private Path root;
    private Path currentPath;
    private Path selectFileForCopy;
    private Path selectFileForCut;

    private String storageMessage;

    public User() {
        this.isReg = false;
        this.isAuth = false;
        this.name = "stranger";
    }

/////////////SETTERS///////////////////////
    public void setReg(boolean reg) {
        isReg = reg;
    }

    public void setAuth(boolean auth) {
        isAuth = auth;
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

//////////////GETTERS////////////////////
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

    /**
     * Perform commands coming from client
     * @param command - the command coming from client
     */
    public void processing(String command) {

    }
}
