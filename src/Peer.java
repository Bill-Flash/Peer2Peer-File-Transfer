import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;

public class Peer {
    private static Scanner scn;
    private static Socket socket;
    static String name, path, GUID, resPath;
    static Map<String,InfoR> DHRT;
    static boolean connection_state = false;

    // use main to start the connection
    public static void main(String[] args) {
        while (!connection_state){
            connectServer();
        }
    }
    // get all absolute filenames under the peer's resource directory
    public static void getFile(Message msg, File file){
        if (file != null) {
            File[] fn = file.listFiles();
            if (fn != null) {
                for (int i = 0; i < fn.length; i++) {
                    getFile(msg, fn[i]);
                }
            }else {
                msg.resources.add(file.getAbsolutePath());
//                System.out.println(file.getAbsolutePath());
            }
        }
    }
    // connect to the Main Server and set the resource path (No same names are allowed)
    public static void connectServer(){
        scn = new Scanner(System.in);
        // localhost: 127.0.0.1
        try {
            // get the peer's information
            Message msg = new Message();
            msg.type = "connect";
            InetAddress ip = InetAddress.getByName("localhost");
            System.out.println("The client has been generated...");
            // get name
            System.out.print("Please enter your name:  ");
            name = scn.nextLine();
            // get resource path
            File f = null;
            System.out.print("Please enter your resource:  ");
            while (true){
                msg.name = name;
                String input = scn.nextLine();
                path = System.getProperty("user.dir")+"/Resource/"+input;
                f = new File(path);
                // in case invalid resource path
                if (f.exists()&&!input.equals("")){
                    break;
                }else {
                    System.out.print("Invalid resource. Please reenter it:  ");
                }
            }
            getFile(msg, f);
            System.out.print("Start to connect to the server...\n");
            socket = new Socket(ip, 5000);
            System.out.println("Connect successfully");
            connection_state = true;
            // create the stream of sending and listening to the Server
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            // send message
            oos.writeObject(msg);
            oos.flush();
            // two threads to listen&send messages
            new Thread(new Peer_listen(socket, msg), "Listen").start();
        } catch (Exception e){
            System.out.println("Connection fails...");
        }

    }
    public static void printDHRT(){
        System.out.println("Receive the DHRT: ");
        System.out.println("|                                            The file name "+
                "                              "+
                "             |              The file GUID          "+
                "    |                    The peers              |");
        for(Map.Entry<String, InfoR> entry : Peer.DHRT.entrySet()){
            System.out.printf("|%100s | %s| ", entry.getValue().resourceName, entry.getKey());
            String peers = "";
            for (String s : entry.getValue().peers) {
                peers+=s+", ";
            }
            System.out.println(peers+"|");
        }
    }
}

// thread to listen to the server
class Peer_listen implements Runnable{
    private Socket socket;
    private ObjectInputStream ois;
    private Message msg;
    Peer_listen(Socket socket, Message msg){
        this.socket = socket;
        this.msg = msg;
    }

    @Override
    public void run() {
        try {
            while (true){
                if (socket.isClosed()) {
                    Peer.connectServer();
                    break;
                }
                // print the message from the Server
                ois = new ObjectInputStream(socket.getInputStream());
                msg = (Message) ois.readObject();
                // success generate a client
                if (msg.type.equals("generate")){
                    Peer.GUID = msg.GUID;
                    System.out.println(Peer.name+" has GUID---->>>"+Peer.GUID);
                    Peer.DHRT = msg.DHRT;
                    Peer.printDHRT();
                    // in case the block and peer could send message if valid name
                    new Thread(new Peer_send(socket), "Send").start();
                }
                // fail to generate a client
                if (msg.type.equals("invalidGenerate")){
                    ois.close();
                    socket.close();
                    socket = null;
                    System.out.println("Invalid name, retry a new one!\n\n");
                    Peer.connectServer();
                }

                // P2P connection use port4000
                // target peer
                if (msg.type.equals("getRes")){
                    new TargetThread(msg, socket).start();
                }

                // requesting peer
                if (msg.type.equals("recRes")){
                    new RequestThread(msg).start();
                }

                // updated peer -- requesting peer
                if (msg.type.equals("update")){
                    System.out.println(Peer.name+" ("+Peer.GUID+") updates  --->>DHRT");
                    msg.infoR.resourceName = Peer.resPath;
                    Peer.DHRT.put(msg.name, msg.infoR);
                    Peer.printDHRT();
                    System.out.println("What do you want? ('exit','getResource')");
                }

                // invalid request
                if (msg.type.equals("invalid")){
                    System.out.println("Please re-enter your command!");
                }

            }
        }catch (Exception e){

        }
    }
}
// thread to send request to server (exit, getRe)
class Peer_send implements Runnable{
    private Socket socket;
    private ObjectOutputStream oos;
    private String input;
    private Message msg;
    Peer_send(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (true){
            msg = new Message();
            // to ask again
            if (socket.isClosed()){
                break;
            }else {
                System.out.println("What do you want? ('exit','getResource')");
                input = scanner.nextLine();
            }
            // disconnect with server
            if (input.equals("exit")){
                try {
                    // to throw exception for let server knows it exits
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    oos.writeObject(msg);
                    oos.close();
                    socket.close();
                    socket = null;
                    Peer.connectServer();
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // search resource
            if (input.equals("getResource")){
                try {
                    oos = new ObjectOutputStream(socket.getOutputStream());
                    msg.setType("getRes");
                    input = scanner.nextLine();
                    msg.name = input;
                    msg.GUID = Peer.GUID;
                    oos.writeObject(msg);
                    oos.flush();
                } catch (IOException e) {
                    System.out.println("Lose connection! Please re-connect until server start!");
                    Peer.connectServer();
                    break;
                }
            }
        }
    }
}
// Target -- SocketServer
class TargetThread extends Thread{
    private Message msg;
    private String path;
    private Socket socket;
    private int port;
    TargetThread(Message msg, Socket socket){
        this.msg = msg;
        this.socket = socket;
        this.port = msg.port;
    }
    @Override
    public void run() {
        System.out.println("\n\nReceive the getResource command!");
        ServerSocket serverSocket = null;
        Socket request = null;
        FileInputStream fileFIS = null;
        DataOutputStream dosT = null;
        try {
            // use DHRT's local resource name
            path = Peer.DHRT.get(msg.name).resourceName;
            System.out.println("Find the file in "+path);
            serverSocket = new ServerSocket(port);
            System.out.println("P2P Server: "+serverSocket);

            request = serverSocket.accept();
            System.out.println("The target peer has already connected with the requesting peer");
            File requestedFile = new File(path);
            // file and client stream
            fileFIS = new FileInputStream(requestedFile);
            dosT = new DataOutputStream(request.getOutputStream());
            byte[] buf = new byte[1024];
            int len = 0;
            while ( (len = fileFIS.read(buf)) != -1 ){
                dosT.write(buf, 0, len);// write data stream
            }
            dosT.flush();
            request.shutdownOutput();
            // this while loop to wait the requesting peer to close
            while (true){
                try {
                    ObjectInputStream objectInputStream = new ObjectInputStream(request.getInputStream());
                }catch (EOFException eofException){
                    break;
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            // close resource and socket
            try {
                if (request != null){
                    request.close();
                }
                if (fileFIS != null){
                    fileFIS.close();
                }
                if (dosT != null){
                    dosT.close();
                }
                if (serverSocket != null){
                    serverSocket.close();
                    System.out.println("Finish Transferring...");
                    System.out.println("Close the P2P connection\n\n");

                    // update the DHRT, UHRT and the other peer's DHRT
                    Peer.DHRT.get(msg.name).peers.add(msg.GUID);
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    msg.setType("update");
                    msg.port = port;
                    oos.writeObject(msg);
                    oos.flush();
                    System.out.println("What do you want? ('exit','getResource')");
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}
// requesting -- Socket
class RequestThread extends Thread{
    private Message msg;
    private String name;
    private int port;
    private boolean connection_state;
    RequestThread(Message msg){
        this.msg = msg;
        this.port = msg.port;
        this.name = Peer.name;
        this.connection_state = false;
    }

    @Override
    public void run() {
        Socket socketPeer = null;
        FileOutputStream fileFOS = null;
        DataInputStream dis = null;
        try {
            // all peers in the local machine
            InetAddress ip = InetAddress.getByName("localhost");
            // in case the server doesn't set up!
            int i = 0;
            while (!connection_state){
                try {
                    socketPeer = new Socket(ip, port);
                    connection_state = true;
                }catch (ConnectException e){
                    System.out.println("Connecting....");
                    // to cancel this request
                    i++;
                    if (i>=20){
                        break;
                    }
                    try {
                        sleep(300);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            // timeout!!
            if (!connection_state){
                System.out.println("Sorry Time Out!");
                return;}
            System.out.println("Connect successfully with the target peer.");
            File file = new File(Peer.path+"/Download-"+name);

            // for the same directory
            if (file.exists()){
                System.out.println("There is no need to create a Directory called Download-"+name);
            }else{
                if (file.mkdir()) System.out.println("Successfully create a Directory!");
            }

            Peer.resPath = Peer.path+"/Download-"+name+"/"+msg.name;
            file = new File(Peer.resPath);
            // in case the same name
            if (file.exists()){file = new File(Peer.resPath+"_1");}
            // for file sharing
            fileFOS = new FileOutputStream(file);
            dis = new DataInputStream(socketPeer.getInputStream());
            byte[] buf = new byte[1024];
            int len = 0;
            System.out.println(System.currentTimeMillis());
            while ( (len = dis.read(buf)) != -1 ){
                fileFOS.write(buf, 0, len);// write data stream
            }
            fileFOS.flush();
            socketPeer.shutdownInput();
            System.out.println(System.currentTimeMillis());
            Peer.resPath = file.getAbsolutePath();
            System.out.println(Peer.resPath);
            System.out.println("Finish... \nThe resource '"+msg.name+"' is under the directory:"+Peer.resPath);
            System.out.println("Close the P2P Connection\n\n");
//            System.out.println("What do you want? ('exit','getResource')");
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                // close the resource
                if (dis != null){
                    dis.close();
                }
                if (fileFOS != null){
                    fileFOS.close();
                }
                if (socketPeer != null){
                    socketPeer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}