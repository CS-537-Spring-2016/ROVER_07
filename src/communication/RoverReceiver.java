package communication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import common.Coord;
import enums.Science;
import enums.Terrain;

/** When any ROVER discovered science, it will write a message to your all
 * ROVERS. That message will be "sent" here.
 *  */
public class RoverReceiver implements Receiver {

    private ServerSocket listenSocket;
    private List<Coord> sharedScienceCoords;
    private List<Terrain> ignoredTerrains;
    private int roversConnectedToMe;

    public RoverReceiver() throws IOException {
        sharedScienceCoords = new ArrayList<Coord>();
        roversConnectedToMe = 0;
    }

    @Override
    public int getRoversConnectedToMe() {
        return roversConnectedToMe;
    }

    @Override
    public void startServer(ServerSocket serverSocket) throws IOException {
        listenSocket = serverSocket;
        
        // create a thread that waits for client to connect to
        new Thread(() -> {
            while (true) {
                try {
                    // wait for a connection
                    Socket connectionSocket = listenSocket.accept();

                    // once there is a connection, serve them on a separate thread
                    new Thread(new RoverHandler(connectionSocket)).start();
                    roversConnectedToMe++;

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public List<Coord> getSharedCoords() {
        return sharedScienceCoords;
    }

    @Override
    public void ignoreTerrains(Terrain... terrains) {
        ignoredTerrains = Arrays.asList(terrains);
    }

    @Override
    public List<Terrain> getIgnoredTerrains() {
        return ignoredTerrains;
    }

    /** Handle ROVER'S incoming message. Filtered and parse the data.
     * 
     * @author Shay */
    class RoverHandler implements Runnable {

        Socket roverSocket;

        public RoverHandler(Socket socket) {
            this.roverSocket = socket;
        }

        @Override
        public void run() {

            try {
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(roverSocket.getInputStream()));

                while (true) {

                    String[] line = input.readLine().split(" ");
                    // protocol: ROCK CRYSTAL 25 30

                    try {
                        if (line.length == 4) {
                            Terrain terrain = Terrain.valueOf(line[0]);
                            Science science = Science.valueOf(line[1]);
                            int xpos = Integer.valueOf(line[2]);
                            int ypos = Integer.valueOf(line[3]);

                            if (!ignoredTerrains.contains(terrain))
                                sharedScienceCoords.add(new Coord(terrain, science, xpos, ypos));
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
