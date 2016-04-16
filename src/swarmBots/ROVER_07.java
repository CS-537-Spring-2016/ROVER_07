package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.Terrain;

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
		String line;

		boolean goingSouth = false;
		boolean blocked = false;

		Coord currentLoc = null;

		// start Rover controller process
		while (true) {
			// currently the requirements allow sensor calls to be made with no
			// simulated resource cost

			// **** location call ****
			out.println("LOC");
			line = in.readLine();
            if (line == null) {
            	System.out.println("ROVER_07 check connection to server");
            	line = "";
            }
			if (line.startsWith("LOC")) {
				currentLoc = extractLOC(line);
			}
			System.out.println("ROVER_07 currentLoc at start: " + currentLoc);



			// **** get equipment listing ****			
			ArrayList<String> equipment = new ArrayList<String>();
			equipment = getEquipment();
			System.out.println("ROVER_07 equipment list results " + equipment + "\n");



			// ***** do a SCAN *****
			//System.out.println("ROVER_07 sending SCAN request");
			this.doScan();
			scanMap.debugPrintMap();



			// ***** MOVING *****
			// try moving east 5 block if blocked
			if (blocked) {
				for (int i = 0; i < 5; i++) {
					out.println("MOVE E");
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
						out.println("MOVE S");
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
						out.println("MOVE N");
						//System.out.println("ROVER_07 request move N");
					}					
				}
			}

			// another call for current location
			out.println("LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println("ROVER_07 check connection to server");
				line = "";
			}
			if (line.startsWith("LOC")) {
				currentLoc = extractLOC(line);
			}

			//System.out.println("ROVER_07 currentLoc after recheck: " + currentLoc);

			//System.out.println("ROVER_07 stuck test " + stuck);
			System.out.println("ROVER_07 blocked test " + blocked);

			// TODO - logic to calculate where to move next



			Thread.sleep(sleepTime);

			System.out.println("ROVER_07 ------------ bottom process control --------------"); 
		}
	}

	// ################ Support Methods ###########################

	private void clearReadLineBuffer() throws IOException{
		while (in.ready()) {
			//System.out.println("ROVER_07 clearing readLine()");
			String garbage = in.readLine();	
		}
	}

	// method to retrieve a list of the rover's equipment from the server
	private ArrayList<String> getEquipment() throws IOException {
		//System.out.println("ROVER_07 method getEquipment()");
		out.println("EQUIPMENT");

		String jsonEqListIn = in.readLine(); // grabs the string that was returned first
		if (jsonEqListIn == null) {
			jsonEqListIn = "";
		}

		StringBuilder jsonEqList = new StringBuilder();
		//System.out.println("ROVER_07 incomming EQUIPMENT result - first readline: " + jsonEqListIn);

		if (jsonEqListIn.startsWith("EQUIPMENT")) {
			while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
				//System.out.println("ROVER_07 incomming EQUIPMENT result: " + jsonEqListIn);
				jsonEqList.append(jsonEqListIn);
				jsonEqList.append("\n");
				//System.out.println("ROVER_07 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return null; // server response did not start with "EQUIPMENT"
		}

		String jsonEqListString = jsonEqList.toString();		
		ArrayList<String> returnList;		
		returnList = gson.fromJson(jsonEqListString, new TypeToken<ArrayList<String>>(){}.getType());		
		//System.out.println("ROVER_07 returnList " + returnList);

		return returnList;
	}

	// sends a SCAN request to the server and puts the result in the scanMap array
	public void doScan() throws IOException {
		//System.out.println("ROVER_07 method doScan()");
		out.println("SCAN");

		String jsonScanMapIn = in.readLine(); // grabs the string that was returned first
		if (jsonScanMapIn == null){
			System.out.println("ROVER_07 check connection to server");
			jsonScanMapIn = "";
		}
		System.out.println("ROVER_07 incomming SCAN result - first readline: " + jsonScanMapIn);

		StringBuilder jsonScanMap = new StringBuilder();
		if (jsonScanMapIn.startsWith("SCAN")) {	
			while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
				jsonScanMap.append(jsonScanMapIn);
				jsonScanMap.append("\n");
				//System.out.println("ROVER_07 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return; // server response did not start with "SCAN"
		}
		//System.out.println("ROVER_07 finished scan while");

		String jsonScanMapString = jsonScanMap.toString();
		// debug print json object to a file
		//new MyWriter( jsonScanMapString, 0);  //gives a strange result - prints the \n instead of newline character in the file

		// convert from the json string back to a ScanMap object
		scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);		
	}

	// this takes the LOC response string, parses out the x and x values and
	// returns a Coord object
	public static Coord extractLOC(String sStr) {
		sStr = sStr.substring(4); // consume "LOC "
		int spaceIdx = sStr.lastIndexOf(" ");
		if (spaceIdx != -1) {
			String xStr = sStr.substring(0, spaceIdx);
			String yStr = sStr.substring(spaceIdx + 1);
			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
		}
		return null;
	}



	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_07 client = new ROVER_07();
		client.run();
	}
}
