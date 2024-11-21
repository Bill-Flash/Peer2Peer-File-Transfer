import java.io.Serializable;

/**
 * a UHPT value in the UHRT
 */
public class InfoP implements Serializable {
    String peerName;
    int metric;
    int index;
    public InfoP(String name, int metric, int index){
        this.peerName = name;
        this.metric = metric;
        this.index = index;
    }
}
