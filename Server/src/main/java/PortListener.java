import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

public class PortListener implements Runnable {
    private final int PORT;
    private final String IP_ADDRESS;
    private final int BUFFER_SIZE;

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
        try {
            serverSocketChannel.socket().bind(new InetSocketAddress(IP_ADDRESS, PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, ByteBuffer.allocate(BUFFER_SIZE));

            while (serverSocketChannel.isOpen()) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                for (SelectionKey key : keys) {
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            SocketChannel socketChannel = serverSocketChannel.accept();
                            socketChannel.configureBlocking(false);
                            socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
                        } else if (key.isReadable()) {
                            SocketChannel socketChannel = (SocketChannel) key.channel();
                            //something else, I just need to figure out about it
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}