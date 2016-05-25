package communication;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import common.Coord;

/** Used to send messages to other ROVERS
 * 
*/
public interface Sender {

    /** @param outputStreams
     *            A list of all the ROVER's output stream. Will used this to
     *            write a message to each individual ROVER's socket
     * @param coord
     *            The coordinate that you want to share to other ROVERS
     * @throws IOException */
    void shareScience(List<DataOutputStream> outputStreams, Coord coord) throws IOException;

}
