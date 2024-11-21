import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * multiple functionalities
 * send DHRT
 * set port
 * ...
 */
class Message implements Serializable {
    String name = "";
    List<String> resources = new ArrayList<>();
    InfoR infoR ;
    String GUID = "";
    String type = "";
    int port = 5001;
    Map<String,InfoR> DHRT = new HashMap<>();
    public Message(){
    }

    public void setType(String type) {
        this.type = type;
    }
}
