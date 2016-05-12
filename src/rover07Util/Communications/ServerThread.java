package rover07Util.Communications;

import enums.RoverName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

/**
 * Runs the server socket and handles all socket I/O.
 *
 * Strings sent via send() are broadcast to all currently connected sockets, and all lines received from any socket
 * will be added to a buffer that is returned and cleared on calling getReceiveQueue().
 *
 * @author Michael Fong (meishuu)
 */
public class ServerThread extends Thread {
    private Selector selector;
    private ByteBuffer buffer;
    private Set<String> received;
    private final Set<SelectionKey> keysToWrite;
    private final Set<RoverSocket> socketsToRegister;
    private final Set<RoverName> roversToConnect;

    private final int DEBUG_LEVEL = 1;
    private final String DEBUG_PREFIX = "[ServerThread] ";
    private void log(int level, String str) {
        if (level <= DEBUG_LEVEL) {
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

        final ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", serverPort));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        log(1, "listening on " + serverSocket.getLocalAddress());

        final Thread connectionThread = new ConnectionThread(selector, socketsToRegister, roversToConnect);
        connectionThread.start();
    }

    @Override
    public void run() {
        while (true) {
            // register new outgoing sockets with OP_CONNECT
            synchronized (socketsToRegister) {
                for (RoverSocket roverSocket : socketsToRegister) {
                    final SocketChannel socket = roverSocket.getSocket();
                    final RoverName rover = roverSocket.getRover();
                    try {
                        socket.register(selector, SelectionKey.OP_CONNECT, new SocketIO(rover));
                    } catch (ClosedChannelException e) {
                        err(rover + " ClosedChannelException: " + e.getMessage());
                    }
                }
                socketsToRegister.clear();
            }

            // switch selected keys from OP_READ to OP_WRITE
            synchronized (keysToWrite) {
                for (SelectionKey key : keysToWrite) {
                    key.interestOps(SelectionKey.OP_WRITE);
                }
                keysToWrite.clear();
            }

            // select
            try {
                selector.select(); // blocks until wakeup()
            } catch (IOException e) {
                err("IOException in select(): " + e.getMessage());
                break; // TODO how to handle this?
            }

            // iterate over selected keys
            // we don't need to synchronize this since other threads only call selector.wakeup()
            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove(); // remove to avoid reprocessing

                // if the key was invalidated, do not bother processing
                if (!key.isValid()) continue;

                try {
                    // delegate to first matching appropriate action
                    // note that there can only be one action anyway, since we only have one op at a time on all keys
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
                    // if we had an IOException, close the connection and invalidate the key
                    log(1, "closing socket " + key.channel());
                    log(1, "- reason: " + e.getMessage());
                    try {
                        key.channel().close(); // TODO is this needed?
                    } catch (IOException e2) {
                    }
                    key.cancel();

                    // if there was a rover attached to this key, re-add it to the roversToConnect set so we know to
                    // retry a connection to that rover
                    SocketIO io = (SocketIO)key.attachment();
                    if (io.getRover() != null) {
                        roversToConnect.add(io.getRover());
                    }
                }
            }

            // wake connectionThread if we still have rovers to attempt connections to;
            // synchronization is needed to obtain ownership of the object's monitor
            synchronized (roversToConnect) {
                if (!roversToConnect.isEmpty()) {
                    roversToConnect.notify(); // wakes connectionThread if it's asleep
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel)key.channel();
        SocketChannel client = serverSocketChannel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new SocketIO());
        log(1, "accepted connection from " + client);
    }

    private void connect(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel)key.channel();
        if (client.finishConnect()) { // should always be true after OP_CONNECT; throws IOException on fail
            key.interestOps(SelectionKey.OP_READ);
            log(1, "successfully connected to " + client);
        }
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
                log(2, "received data: " + line);

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
            // as long as we have lines to send...
            while (!queue.isEmpty()) {
                // get the first line in the queue
                ByteBuffer buf = (ByteBuffer)queue.get(0);

                // write it to the socket
                client.write(buf);

                // if we couldn't send all of it, break out of the loop so we can try to finish transmitting later
                if (buf.remaining() > 0) break;

                // otherwise, pop the first line off the queue
                queue.remove(0);
            }

            // if we finished flushing the queue, go back to read mode
            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    /**
     * Adds a line to the output queue of all current sockets.
     * @param data The string to send without a trailing newline.
     */
    public void send(String data) {
        // turn input string into a ByteBuffer of a line (ending with \n)
        byte[] byteData = (data + "\n").getBytes();
        ByteBuffer buf = ByteBuffer.wrap(byteData);

        log(2, "queueing data to write: " + data);

        // add it to the output queue of all current keys
        synchronized (keysToWrite) {
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() != null) {
                    SocketIO io = (SocketIO)key.attachment();
                    io.getOut().add(buf);
                    keysToWrite.add(key); // mark this key as needing to be switched to OP_WRITE
                }
            }
        }

        selector.wakeup(); // force the select() to return
    }

    /**
     * Returns and flushes an unordered collection of all lines received over all sockets.
     * @return All lines received since the last call.
     */
    public Set<String> popReceiveQueue() { // TODO better name?
        Set<String> queue;
        synchronized(received) {
            queue = new HashSet<>(received);
            received.clear();
        }
        return queue;
    }

    /**
     * ONLY FOR TESTING - Spawns an instance of ServerThread.
     */
    public static void main(String[] args) {
        try {
            ServerThread server = new ServerThread(RoverName.ROVER_07);
            server.start();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
