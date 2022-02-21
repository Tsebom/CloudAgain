import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final ExecutorService service = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        try {
            service.execute(new PortListener(5679, "localhost", 1460));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
