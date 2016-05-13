package rover07Util.Communications;

import enums.RoverName;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class SocketIO {
    private RoverName rover;
    private StringBuilder in;
    private List<ByteBuffer> out;

    SocketIO() {
        rover = null;
        in = new StringBuilder();
        out = Collections.synchronizedList(new ArrayList<>());
    }

    SocketIO(RoverName rover) {
        this();
        this.rover = rover;
    }

    public RoverName getRover() {
        return rover;
    }

    public StringBuilder getIn() {
        return in;
    }

    public List<ByteBuffer> getOut() {
        return out;
    }
}
