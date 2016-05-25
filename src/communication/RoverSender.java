package communication;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import common.Coord;

/** Used to send message to other ROVERS. Currently, it only sends Coordinates
 * of science to other ROVERS.
 *  */
public class RoverSender implements Sender {

    @Override
    public void shareScience(List<DataOutputStream> output_streams, Coord coord) throws IOException {

        for (DataOutputStream dos : output_streams) {
            dos.writeBytes(coord.toProtocol() + "\n");
        }
    }
}
