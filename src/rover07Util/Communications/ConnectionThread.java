package rover07Util.Communications;

import enums.RoverName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;

public class ConnectionThread extends Thread {
    private final Selector selector;
    private final Set<RoverSocket> socketsToRegister;
    private final Set<RoverName> roversToConnect;

    private int retryDelay = 250;
    private int retryAttempts = 0;

    private final boolean DEBUG = true;
    private final String DEBUG_PREFIX = "[ConnectionThread] ";
    private void log(String str) {
        if (DEBUG) {
            System.out.println(DEBUG_PREFIX + str);
        }
    }
    private void err(String str) {
        System.err.println(DEBUG_PREFIX + str);
    }

    public ConnectionThread(Selector selector, Set<RoverSocket> socketsToRegister, Set<RoverName> roversToConnect) {
        this.selector = selector;
        this.socketsToRegister = socketsToRegister;
        this.roversToConnect = roversToConnect;
    }

    @Override
    public void run() {
        while (true) {
            log("trying to connect to rovers...");
            synchronized (roversToConnect) {
                while (roversToConnect.isEmpty()) {
                    try {
                        roversToConnect.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                final Iterator<RoverName> iter = roversToConnect.iterator();
                while (iter.hasNext()) {
                    final RoverName rover = iter.next();
                    final InetSocketAddress address = new InetSocketAddress("127.0.0.1", RoverPorts.getPort(rover));
                    SocketChannel socket;
                    try {
                        socket = SocketChannel.open();
                        socket.configureBlocking(false);
                        socket.connect(address);
                        log("attempting " + address);
                    } catch (IOException e) {
                        err("failed to connect to " + rover.name() + ": " + e.getMessage());
                        continue;
                    }

                    socketsToRegister.add(new RoverSocket(rover, socket));
                    iter.remove();
                }
            }
            selector.wakeup();
            try {
                log("sleeping for " + retryDelay);
                Thread.sleep(retryDelay);
                if (retryAttempts < 5) {
                    retryDelay *= 2;
                    retryAttempts++;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
