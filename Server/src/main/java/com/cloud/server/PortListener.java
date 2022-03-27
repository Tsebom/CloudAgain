package com.cloud.server;

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
import java.util.logging.Logger;

public class PortListener implements Runnable {
    private final Logger logger = Server.logger;

    private final int PORT;
    private final String IP_ADDRESS;
    private final int BUFFER_SIZE;

    private final Map<SocketChannel, User> socketUser = new HashMap<>();
    private final Map<SocketChannel, byte[]> messageForSend = new HashMap<>();

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
                            SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
                            if (socketChannel != null) {
                                logger.info("Accept client: " + socketChannel.getRemoteAddress());
                                socketChannel.configureBlocking(false);
                                socketChannel.register(selector, SelectionKey.OP_READ,
                                        ByteBuffer.allocate(BUFFER_SIZE));
                                socketUser.put(socketChannel, new User(this, socketChannel));
                            }
                        } else if (key.isReadable()) {
                            key.interestOps(SelectionKey.OP_WRITE);
                            handler(key);
                        } else if (key.isWritable() || !messageForSend.isEmpty()) {
                            SocketChannel socketChannel = (SocketChannel) key.channel();
                            if (messageForSend.get(socketChannel) != null) {
                                write((ByteBuffer) key.attachment(), socketChannel);
                            }
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                selector.close();
                serverSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Map<SocketChannel, User> getSocketUsers() {
        return socketUser;
    }

    public void disturb() {
        selector.wakeup();
    }

    public synchronized Map<SocketChannel, byte[]> getMessageForSend() {
        return messageForSend;
    }

    /**
     * Create Thread for processing messages those get from chanel
     * @param key - token of chanel
     */
    private void handler(SelectionKey key) {
        try {
            SocketChannel socketChannel = (SocketChannel) key.channel();
            logger.info("handler from: " + socketChannel.getRemoteAddress());
            if (socketUser.containsKey(socketChannel)) {
                service.execute(new ProcessingMessages(socketUser.get(socketChannel),//get user from Map
                        read(key)));//get message
            } else {
                key.cancel();
                socketChannel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Pick a message up from channel
     * @param key - token of chanel
     * @return - String of message
     * @throws IOException -
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
        logger.info("!!!!!!!!" + message);
        return message;
    }

    /**
     *
     * @param buf
     * @param socketChannel
     * @throws IOException
     */
    private void  write(ByteBuffer buf, SocketChannel socketChannel) throws IOException {
        byte[] data = messageForSend.remove(socketChannel);

        if (data == null) {
            return;
        }

        int i = 0;
        buf.clear();

        while (i < data.length) {

            while (buf.hasRemaining() && i < data.length) {
                buf.put(data[i]);
                i++;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                try {
                    socketChannel.write(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            buf.compact();
        }

        logger.info("message was sent to the client: " + socketChannel.getRemoteAddress() ) ;
    }
}