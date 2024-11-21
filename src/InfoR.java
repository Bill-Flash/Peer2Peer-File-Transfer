import java.io.Serializable;
import java.util.List;

/**
 * As a DHRT in the peer
 */
public class InfoR implements Serializable {
    String resourceName;
    List<String> peers;
    public InfoR(String name, List<String> peers){
        this.resourceName = name;
        this.peers = peers;
    }
}
