package rover07Util;

import enums.RoverName;
import rover07Util.Communications.Protocol;
import rover07Util.Communications.ScienceInfo;
import rover07Util.Communications.ServerThread;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class RoverComms {
    private final ServerThread server;

    public RoverComms(RoverName rover) throws IOException {
        server = new ServerThread(rover);
        server.start();
    }

    public void sendScience(ScienceInfo info) {
        server.send(Protocol.stringify(info));
    }

    public Set<ScienceInfo> getScience() {
        Set<String> queue = server.popReceiveQueue();
        if (queue.isEmpty()) return null;

        Set<ScienceInfo> infos = new HashSet<>();
        for (String line : queue) {
            infos.add(Protocol.parse(line));
        }
        return infos;
    }
}
