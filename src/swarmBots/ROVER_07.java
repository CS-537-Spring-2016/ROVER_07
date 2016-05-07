package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.Terrain;

import rover07Util.Query;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_07 {
	// connection settings
	final String ROVER_NAME = "ROVER_07";
	String SERVER_ADDRESS = "localhost";
	final int PORT_ADDRESS = 9537;

	// io
	BufferedReader in;
	PrintWriter out;
	Query q;

	// rover vars
	Gson gson;
	ScanMap scanMap;
	int sleepTime;

	/**
	 * Constructors
	 */
	public ROVER_07() {
		System.out.println("ROVER_07 rover object constructed");
		SERVER_ADDRESS = "localhost";
		sleepTime = 300; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}

	public ROVER_07(String serverAddress) {
		System.out.println("ROVER_07 rover object constructed");
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException {
		gson = new GsonBuilder().setPrettyPrinting().create();

		// Make connection and initialize streams
		// TODO - need to close this socket
		Socket socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		out = new PrintWriter(socket.getOutputStream(), true);
		
		q = new Query(in, out, gson);

		// Process all messages from server, wait until server requests Rover ID name
		while (true) {
			String line = in.readLine();
			if (line.startsWith("SUBMITNAME")) {
				// This sets the name of this instance of a swarmBot for identifying the thread to the server
				out.println(ROVER_NAME);
				break;
			}
		}
		
		// Enter main loop
		try {
			mainLoop();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (socket != null) socket.close();
		}
	}

	/**
	 * Main rover logic
	 */
	private void mainLoop() throws IOException, InterruptedException {
		boolean goingSouth = true;
		boolean blocked = false;
		Coord currentLoc = null;

		ArrayList<String> equipment;
		Coord startLoc;
		Coord targetLoc;

		/**
		 *  Get initial values that won't change
		 */
		// get EQUIPMENT			
		equipment = q.getEquipment();
		System.out.println(ROVER_NAME + " equipment list results " + equipment + "\n");
		
		// get START_LOC
		startLoc = q.getLoc(Query.LocType.START);
		System.out.println(ROVER_NAME + " START_LOC " + startLoc);
		
		// get TARGET_LOC
		targetLoc = q.getLoc(Query.LocType.TARGET);
		System.out.println(ROVER_NAME + " TARGET_LOC " + targetLoc);
		
		while (true) {
			// currently the requirements allow sensor calls to be made with no
			// simulated resource cost

			// **** location call ****
			currentLoc = q.getLoc();
			System.out.println("ROVER_07 currentLoc at start: " + currentLoc);



			// ***** do a SCAN *****
			//System.out.println("ROVER_07 sending SCAN request");
			scanMap = q.getScan();
			scanMap.debugPrintMap();



			// ***** MOVING *****
			// try moving east 5 block if blocked
			if (blocked) {
				for (int i = 0; i < 5; i++) {
					q.doMove("E");
					//System.out.println("ROVER_07 request move E");
					Thread.sleep(300);
				}
				blocked = false;
				//reverses direction after being blocked
				goingSouth = !goingSouth;
			} else {

				// pull the MapTile array out of the ScanMap object
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				int centerIndex = (scanMap.getEdgeSize() - 1)/2;
				// tile S = y + 1; N = y - 1; E = x + 1; W = x - 1

				if (goingSouth) {
					// check scanMap to see if path is blocked to the south
					// (scanMap may be old data by now)
					if (scanMapTiles[centerIndex][centerIndex +1].getHasRover() 
							|| scanMapTiles[centerIndex][centerIndex +1].getTerrain() == Terrain.ROCK
							|| scanMapTiles[centerIndex][centerIndex +1].getTerrain() == Terrain.NONE) {
						blocked = true;
					} else {
						// request to server to move
						q.doMove("S");
						//System.out.println("ROVER_07 request move S");
					}
					
				} else {
					// check scanMap to see if path is blocked to the north
					// (scanMap may be old data by now)
					//System.out.println("ROVER_07 scanMapTiles[2][1].getHasRover() " + scanMapTiles[2][1].getHasRover());
					//System.out.println("ROVER_07 scanMapTiles[2][1].getTerrain() " + scanMapTiles[2][1].getTerrain().toString());
					
					if (scanMapTiles[centerIndex][centerIndex -1].getHasRover() 
							|| scanMapTiles[centerIndex][centerIndex -1].getTerrain() == Terrain.ROCK
							|| scanMapTiles[centerIndex][centerIndex -1].getTerrain() == Terrain.NONE) {
						blocked = true;
					} else {
						// request to server to move
						q.doMove("N");
						//System.out.println("ROVER_07 request move N");
					}					
				}
			}

			// another call for current location
			currentLoc = q.getLoc();

			//System.out.println("ROVER_07 currentLoc after recheck: " + currentLoc);

			//System.out.println("ROVER_07 stuck test " + stuck);
			System.out.println("ROVER_07 blocked test " + blocked);

			// TODO - logic to calculate where to move next



			Thread.sleep(sleepTime);
//			q.doGather()
			System.out.println("ROVER_07 ------------ bottom process control --------------");
		}
	}

	// ################ Support Methods ###########################

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_07 client = new ROVER_07();
		client.run();
	}
}
