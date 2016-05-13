package rover07Util.Communications;

import enums.RoverName;

import java.nio.channels.SocketChannel;

class RoverSocket {
    private RoverName rover;
    private SocketChannel socket;

    RoverSocket(RoverName rover, SocketChannel socket) {
        this.rover = rover;
        this.socket = socket;
    }

    public RoverName getRover() {
        return rover;
    }

    public SocketChannel getSocket() {
        return socket;
    }
}
