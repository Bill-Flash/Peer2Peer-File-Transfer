import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;


/**
 * The server supports multiple peers to connect.
 */
public class Server {
    private static Socket client;
    private static ServerSocket serverSocket;
    private static Message msg =  new Message();
    private static List<Integer> p2pPorts = new ArrayList<>();
    private static List<Socket> clients = new ArrayList<>();
    private static List<String> nameList = new ArrayList<>();
    private static Map<String, InfoP> UHPT= new HashMap<>();
    private static Map<String, InfoR> UHRT= new HashMap<>();
    // use main thread to listen the connection
    public static void main(String[] args) {
        try {
            System.out.println("The server begin to running...");
            // set the server-client port as 5000, a localhost machine with local peers
            serverSocket = new ServerSocket(5000);
            System.out.println("The main Server: "+serverSocket);
            while (true){
                // waiting for the new client to connect.
                client = serverSocket.accept();
                System.out.println("A client is connecting...");

                // the client send its name, resource, add it into the UHRT (now store name & message)
                ObjectInputStream ois = new ObjectInputStream(client.getInputStream());
                msg = (Message) ois.readObject();
                // the metric of peer is randomly set
                int metric = new Random().nextInt(100);
                // in case the same name peer --> output the same GUID
                if (UHPT.containsKey(DigestUtils.sha1Hex(msg.name))){
                    System.out.println("Fatal connection.");
                    msg.setType("invalidGenerate");
                    ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
                    oos.writeObject(msg);
                    client.close();
                }else {
                    clients.add(client);
                    if (msg.type.equals("connect")){
                        msg.GUID = DigestUtils.sha1Hex(msg.name);
                    }
                    ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
                    if (msg.type.equals("connect")){
                        // send the message shows they connect successfully, convert it to the DHRT
                        msg.setType("generate");
                        msg.DHRT = new HashMap<>();
                        List<String> peers = new ArrayList<>();
                        peers.add(msg.GUID);
                        Iterator iterator = msg.resources.iterator();
                        while (iterator.hasNext()){
                            String str = (String) iterator.next();
                            // prevent other side already has the resource
                            // there I according to the absolute path to check
                            if (UHRT.containsKey(DigestUtils.sha1Hex(str))){
                                InfoR entry = UHRT.get(DigestUtils.sha1Hex(str));
                                entry.peers.add(msg.GUID);
                                List<String> p = new ArrayList<>();
                                for (String s : entry.peers) {
                                    p.add(s);
                                }
                                msg.DHRT.put(DigestUtils.sha1Hex(str), new InfoR(str, p));
                            }else{
                                msg.DHRT.put(DigestUtils.sha1Hex(str), new InfoR(str, peers));
                                List<String> p = new ArrayList<>();
                                for (String s : peers) {
                                    p.add(s);
                                }
                                UHRT.put(DigestUtils.sha1Hex(str), new InfoR(str, p));
                            }
                        }
                        oos.writeObject(msg);
                        oos.flush();
                        // peerGUID, InfoP(resPath, metric, index)
                        nameList.add(msg.name);
                        UHPT.put(msg.GUID, new InfoP(msg.name, metric, UHPT.size()));

                    }
                    new clientHandler(serverSocket, clients, client, msg.name).start();
                    printUHPT();
                    printUHRT();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
        }
    }
    // get the peer that owns the resource and the fastest
    // The routing overlay I set according to the metric
    // the metric is randomly get
    static String overlay(String resGUID){
        InfoR res = UHRT.get(resGUID);
        if (res != null) {
            String currentPeerGUID;
            String bestPeerGUID = res.peers.get(0);
            InfoP bestPeer = UHPT.get(bestPeerGUID);
            for (int i = 0; i < res.peers.size(); i++) {
                currentPeerGUID = res.peers.get(i);
                if (UHPT.get(currentPeerGUID).metric < bestPeer.metric){
                    bestPeerGUID = currentPeerGUID;
                    bestPeer = UHPT.get(currentPeerGUID);
                }
            }
            return bestPeerGUID;
        }else {
            return null;
        }
    }
    static Map<String, InfoR> getUHRT(){
        return UHRT;
    }
    static Map<String, InfoP> getUHPT(){
        return UHPT;
    }
    static int getClient(String peerGUID){
        return UHPT.get(peerGUID).index;
    }
    static String getResName(String resGUID){
        return UHRT.get(resGUID).resourceName;
    }
    static List<Integer> getP2pPorts(){return p2pPorts;}
    public static void printUHRT(){
        System.out.println("UHRT: ");
        System.out.println("|                                            The file name "+
                "                              "+
                "             |              The file GUID          "+
                "    |                  The peers                |");
        for(Map.Entry<String, InfoR> entry : UHRT.entrySet()){
            System.out.printf("|%100s | %s| ", entry.getValue().resourceName, entry.getKey());
            String peers = "";
            for (String s : entry.getValue().peers) {
                peers+=s+", ";
            }
            System.out.println(peers+"|");
        }
    }
    public static void printUHPT(){
        System.out.println("UHPT: Updated because of new Peer");
        System.out.println("|                 The peer name "+
                "             |              The peer GUID          "+
                "    |The metric (0-100)|");
        for(Map.Entry<String, InfoP> entry : UHPT.entrySet()){
            System.out.printf("|     %20s                   | %s|        %02d        |\n", entry.getValue().peerName, entry.getKey(), entry.getValue().metric);
        }
    }
}

/**
 * every thread to control the thread's request
 */
class clientHandler extends Thread{
    ServerSocket server;
    List<Socket> clients;
    Socket peer = null;
    ObjectInputStream ois;
    ObjectOutputStream oos;
    Message message;
    String id;
    clientHandler(ServerSocket server, List<Socket> clients, Socket peer, String id){
        this.server = server;
        this.clients = clients;
        this.peer = peer;
        this.id = id;
    }

    @Override
    public void run() {
        while (true){
            try {
                if (peer.isClosed()){
                    oos.close();
                    ois.close();
                }

                // get the message (name=resGUID)
                ois = new ObjectInputStream(peer.getInputStream());
                message = (Message) ois.readObject();
                // for the getRes
                if (message.type.equals("getRes")){
                    System.out.println("Someone requests resource!");
                    String res = Server.overlay(message.name);
                    if (res == null) {
                        invalidRequest();
                    }else{
                        //distribute port nunmber from 5001
                        int j = 5001;
                        List<Integer> ports = Server.getP2pPorts();
                        while (ports.contains(j)){
                            j++;
                        }
                        ports.add(Integer.valueOf(j));
                        message.port = j;
                        // for target peer: ServerSocket
                        try {
                            int index = Server.getClient(res);
                            Socket targetPeer = clients.get(index);
                            ObjectOutputStream oosT = new ObjectOutputStream(targetPeer.getOutputStream());
                            oosT.writeObject(message);
                            oosT.flush();
                        }catch (IOException e){
                            System.out.println(Server.getUHPT().get(res).peerName+" can't be found!");
                        }
                        // for requesting peer: Socket
                        oos = new ObjectOutputStream(peer.getOutputStream());
                        message.setType("recRes");
                        message.name = (new File(Server.getResName(message.name))).getName();
                        System.out.println("The file is "+message.name);
                        oos.writeObject(message);
                        oos.flush();
                    }
                }
                //message.GUID--requestGUID, message.name--resGUID
                if (message.type.equals("update")){
                    // remove the port after p2p connection close
                    List<Integer> ports = Server.getP2pPorts();
                    ports.remove(Integer.valueOf(message.port));
                    System.out.println("Update UHRT and DHRTs.");
                    Map<String, InfoR> UHRT = Server.getUHRT();
                    UHRT.get(message.name).peers.add(message.GUID);
                    int i = Server.getUHPT().get(message.GUID).index;
                    ObjectOutputStream oosT = new ObjectOutputStream(clients.get(i).getOutputStream());

                    message.infoR = UHRT.get(message.name);
                    oosT.writeObject(message);
                    oosT.flush();
                    message = new Message();
                    Server.printUHRT();
                }
            } catch (IOException e) {
                System.out.println("Disconnect -->>"+id);
                try {
                    peer.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                break;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    // tell the requesting client that there is no such resource
    private void invalidRequest() {
        try {
            oos = new ObjectOutputStream(peer.getOutputStream());
            message.setType("invalid");
            oos.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
