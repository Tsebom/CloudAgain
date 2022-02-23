import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PortListener implements Runnable {
    private final int PORT;
    private final String IP_ADDRESS;
    private final int BUFFER_SIZE;

    private final Map<SocketChannel, User> socketUser = new HashMap<>();

    private final ExecutorService service = Executors.newCachedThreadPool();

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    public PortListener(int port, String ip_address, int buf) throws IOException {
        this.PORT = port;
        this.IP_ADDRESS = ip_address;
        this.BUFFER_SIZE = buf;
        this.serverSocketChannel = ServerSocketChannel.open();
        this.selector = Selector.open();
    }

    @Override
    public void run() {
        try{
            serverSocketChannel.socket().bind(new InetSocketAddress(IP_ADDRESS, PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (serverSocketChannel.isOpen()) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                for (SelectionKey key : keys) {
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            SocketChannel socketChannel = serverSocketChannel.accept();
                            socketChannel.configureBlocking(false);
                            socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
                            socketUser.put(socketChannel, new User());
                        } else if (key.isReadable()) {
                            handler(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create Thread for processing messages those get from chanel
     * @param key - token of chanel
     */
    private void handler(SelectionKey key) {
        try {
            service.execute(new ProcessingMessages(
                    socketUser.get((SocketChannel) key.channel()),//get user from Map
                    read(key)));//get message
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Pick the message up from channel
     * @param key - token of chanel
     * @return - String of message
     * @throws IOException
     */
    private String read (SelectionKey key) throws IOException {
        String message = null;
        SocketChannel socketChannel = (SocketChannel) key.channel();
        if (socketChannel.isOpen()) {
            ByteBuffer buf = (ByteBuffer) key.attachment();

            int bytesRead = socketChannel.read(buf);
            if (bytesRead < 0 || bytesRead == 0) {
                return null;
            }

            buf.flip();
            StringBuilder stringBuilder = new StringBuilder();

            while (buf.hasRemaining()) {
                stringBuilder.append((char) buf.get());
            }
            buf.clear();

            message = stringBuilder.toString();
        }
        return message;
    }
}