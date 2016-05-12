package rover07Util.Communications;

import enums.RoverName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class ServerThread implements Runnable {
    private Selector selector;
    private ByteBuffer buffer;
    private Set<String> received;
    private final Set<SelectionKey> keysToWrite;
    private final Set<RoverSocket> socketsToRegister;
    private final Set<RoverName> roversToConnect;

    private ServerSocketChannel serverSocket;
    private final Thread connectionThread;

    private final boolean DEBUG = true;
    private final String DEBUG_PREFIX = "[ServerThread] ";
    private void log(String str) {
        if (DEBUG) {
            System.out.println(DEBUG_PREFIX + str);
        }
    }
    private void err(String str) {
        System.err.println(DEBUG_PREFIX + str);
    }

    public ServerThread(RoverName rover) throws IOException {
        final int serverPort = RoverPorts.getPort(rover);
        roversToConnect = RoverPorts.getRovers();
        roversToConnect.remove(rover);
        socketsToRegister = Collections.synchronizedSet(new HashSet<>());

        selector = Selector.open();
        buffer = ByteBuffer.allocateDirect(64);
        received = Collections.synchronizedSet(new HashSet<>());
        keysToWrite = new HashSet<>();

        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", serverPort));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        log("listening on " + serverSocket.getLocalAddress());

        connectionThread = new ConnectionThread(selector, socketsToRegister, roversToConnect);
        connectionThread.start();
    }

    @Override
    public void run() {
        while (true) {
            synchronized (socketsToRegister) {
                for (RoverSocket roverSocket : socketsToRegister) {
                    try {
                        final SocketChannel socket = roverSocket.getSocket();
                        final RoverName rover = roverSocket.getRover();
                        socket.register(selector, SelectionKey.OP_CONNECT, new SocketIO(rover));
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                    }
                }
                socketsToRegister.clear();
            }

            synchronized (keysToWrite) {
                for (SelectionKey key : keysToWrite) {
                    key.interestOps(SelectionKey.OP_WRITE);
                }
                keysToWrite.clear();
            }

            try {
                selector.select(); // blocks until wakeup()
            } catch (IOException e) {
                err("IOException in select(): " + e.getMessage());
                break; // TODO
            }

            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove(); // remove event to avoid reprocessing

                if (!key.isValid()) continue;

                try {
                    if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isConnectable()) {
                        connect(key);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                } catch (IOException e) {
                    log("closing socket " + key.channel());
                    log("- reason: " + e.getMessage());
                    try {
                        key.channel().close(); // TODO is this needed?
                    } catch (IOException e2) {
                    }
                    key.cancel();

                    SocketIO io = (SocketIO)key.attachment();
                    if (io.getRover() != null) {
                        roversToConnect.add(io.getRover());
                    }
                }
            }

            synchronized (roversToConnect) {
                if (!roversToConnect.isEmpty()) {
                    roversToConnect.notify();
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new SocketIO());
        log("accepted connection from " + client);
    }

    private void connect(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel)key.channel();
        client.finishConnect();
        key.interestOps(SelectionKey.OP_READ);
        log("successfully connected to " + client);
    }

    private void read(SelectionKey key) throws IOException {
        SocketIO io = (SocketIO)key.attachment();
        SocketChannel client = (SocketChannel)key.channel();

        // start reading
        while (true) {
            // read into buffer
            buffer.clear();
            int read = client.read(buffer);
            if (read == 0) break;

            // flip buffer for writing
            buffer.flip();

            // convert to byte array
            byte[] data = new byte[read];
            buffer.get(data);

            // add to this key's string builder
            StringBuilder in = io.getIn();
            in.append(new String(data));

            // start parsing
            while (true) {
                // look for eol
                int eol = in.indexOf("\n");
                if (eol == -1) break;

                // get first line
                String line = in.substring(0, eol);

                // add to parsed lines for later retrieval
                received.add(line);
                log("received data: " + line);

                // remove from builder
                in.delete(0, eol + 1);
            }
        }
    }

    private void write(SelectionKey key) throws IOException {
        SocketIO io = (SocketIO)key.attachment();
        SocketChannel client = (SocketChannel)key.channel();

        List queue = io.getOut();
        synchronized (queue) {
            while (!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                client.write(buf);
                if (buf.remaining() > 0) break;
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    public void send(String data) {
        byte[] byteData = (data + "\n").getBytes();
        ByteBuffer buf = ByteBuffer.wrap(byteData);

        log("queueing data to write: " + data);

        synchronized (keysToWrite) {
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() != null) {
                    SocketIO io = (SocketIO)key.attachment();
                    io.getOut().add(buf);
                    keysToWrite.add(key);
                }
            }
        }

        selector.wakeup();
    }

    public Set<String> getReceiveQueue() {
        Set<String> queue;
        synchronized(received) {
            queue = new HashSet<>(received);
            received.clear();
        }
        return queue;
    }

    public static void main(String[] args) {
        try {
            ServerThread test = new ServerThread(RoverName.ROVER_07);
            Thread task = new Thread(test);
            task.start();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
