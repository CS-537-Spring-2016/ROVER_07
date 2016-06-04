package rover07Util.Communications;

import enums.RoverName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;

/**
 * Periodically attempts to spawn outgoing connections to all other rovers.
 *
 * @author Michael Fong (G7) [@meishuu]
 */
class ConnectionThread extends Thread {
    private final Selector selector;
    private final Set<RoverSocket> socketsToRegister;
    private final Set<RoverName> roversToConnect;

    private int retryDelay = 250;
    private int retryAttempts = 0;

    private final int DEBUG_LEVEL = 0;
    private final String DEBUG_PREFIX = "[ConnectionThread] ";
    private void log(int level, String str) {
        if (level <= DEBUG_LEVEL) {
            System.out.println(DEBUG_PREFIX + str);
        }
    }
    private void err(String str) {
        System.err.println(DEBUG_PREFIX + str);
    }

    ConnectionThread(Selector selector, Set<RoverSocket> socketsToRegister, Set<RoverName> roversToConnect) {
        this.selector = selector;
        this.socketsToRegister = socketsToRegister;
        this.roversToConnect = roversToConnect;
    }

    @Override
    public void run() {
        while (true) {
            log(1, "trying to connect to rovers...");

            // attempt to make connections to all rovers in roversToConnect
            synchronized (roversToConnect) {
                // if the set is empty, go to sleep until ServerThread wakes us
                while (roversToConnect.isEmpty()) {
                    try {
                        roversToConnect.wait(); // releases lock on roversToConnect until awoken
                    } catch (InterruptedException e) {
                        err("thread interrupted while waiting: " + e.getMessage());
                        return;
                    }
                }

                // iterate over rovers
                final Iterator<RoverName> iter = roversToConnect.iterator();
                while (iter.hasNext()) {
                    final RoverName rover = iter.next();
                    final InetSocketAddress address = new InetSocketAddress("127.0.0.1", RoverPorts.getPort(rover));
                    SocketChannel socket;
                    try {
                        // try to make connection
                        socket = SocketChannel.open();
                        socket.configureBlocking(false);
                        socket.connect(address);
                        log(1, "attempting " + address);
                    } catch (IOException e) {
                        err("failed to connect socket to " + rover.name() + ": " + e.getMessage());
                        continue;
                    }

                    // queue this socket to be registered in ServerThread since we cannot register across threads
                    socketsToRegister.add(new RoverSocket(rover, socket));

                    // remove this rover from the list of rovers that we still need to attempt a connection to
                    iter.remove();
                }
            }

            // if we're here, we queued up sockets for registration; interrupt select() to add them asap
            selector.wakeup();

            // wait before another attempt
            try {
                log(2, "sleeping for " + retryDelay);
                Thread.sleep(retryDelay);

                // exponential backoff
                if (retryAttempts < 5) {
                    retryDelay *= 2;
                    retryAttempts++;
                }
            } catch (InterruptedException e) {
                err("thread interrupted while sleeping: " + e.getMessage());
                return;
            }
        }
    }
}
