import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.ServerSocket;
/*
 * Parts of code are taken from http://www.ase.md/~aursu/ClientServerThreads.html
 * All credit for the basic chat function goes to them.
 */

/*
 * A chat server that delivers public and private messages.
 */
public class MultiThreadChatServerSync {

    // The server socket.
    private static ServerSocket serverSocket = null;
    private static ServerSocket transferSocket = null;
    // The client socket.
    private static Socket clientSocket = null;

    // This chat server can accept up to maxClientsCount clients' connections.
    private static final int maxClientsCount = 10;
    private static final clientThread[] threads = new clientThread[maxClientsCount];

    public static void main(String args[]) {

        // The default port number.
        int portNumber = 5556;
        if (args.length < 1) {
            System.out.println("Now using port number=" + portNumber);
        } else {
            portNumber = Integer.valueOf(args[0]).intValue();
        }

        /*
         * Open a server socket on the portNumber (default 2222). Note that we can
         * not choose a port less than 1023 if we are not privileged users (root).
         */
        try {
            serverSocket = new ServerSocket(portNumber);
        } catch (IOException e) {
            System.out.println(e);
        }

        /*
         * Create a client socket for each connection and pass it to a new client
         * thread.
         */
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                int i = 0;
                for (i = 0; i < maxClientsCount; i++) {
                    if (threads[i] == null) {
                        (threads[i] = new clientThread(clientSocket, threads)).start();
                        break;
                    }
                }
                if (i == maxClientsCount) {
                    PrintStream os = new PrintStream(clientSocket.getOutputStream());
                    os.println("Server too busy. Try later.");
                    os.close();
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }
}

/*
 * The chat client thread. This client thread opens the input and the output
 * streams for a particular client, ask the client's name, informs all the
 * clients connected to the server about the fact that a new client has joined
 * the chat room, and as long as it receive data, echos that data back to all
 * other clients. The thread broadcast the incoming messages to all clients and
 * routes the private message to the particular client. When a client leaves the
 * chat room this thread informs also all the clients about that and terminates.
 */
class clientThread extends Thread {

    private String clientName = null;
    private DataInputStream is = null;
    private PrintStream os = null;
    private Socket clientSocket = null;
    private final clientThread[] threads;
    private int maxClientsCount;

    public clientThread(Socket clientSocket, clientThread[] threads) {
        this.clientSocket = clientSocket;
        this.threads = threads;
        maxClientsCount = threads.length;
    }

    public void run() {
        int maxClientsCount = this.maxClientsCount;
        clientThread[] threads = this.threads;

        try {
            /*
             * Create input and output streams for this client.
             */
            is = new DataInputStream(clientSocket.getInputStream());
            os = new PrintStream(clientSocket.getOutputStream());
            String name;
            while (true) {
                os.println("Enter your name.");
                name = is.readLine().trim();
                if (name.indexOf('@') == -1) {
                    break;
                } else {
                    os.println("The name should not contain '@' character.");
                }
            }

            /* Welcome the new the client. */
            os.println("Welcome " + name
                    + " to our chat room.\n");
            synchronized (this) {
                for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] != null && threads[i] == this) {
                        clientName = "@" + name;
                        break;
                    }
                }
                for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] != null && threads[i] != this) {
                        threads[i].os.println("*** A new user " + name
                                + " entered the chat room !!! ***");
                    }
                }
                for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] != null) {
                        threads[i].os.println("/users");
                        for(int j = 0; j < maxClientsCount; j++){
                            if ((threads[j] != null) && (threads[j].clientName != null) && (i != j)) {
                                threads[i].os.println(threads[j].clientName);
                            }
                        }
                        threads[i].os.println("/usersEnd");
                    }
                }
            }
            /* Start the conversation. */
            while (true) {

                /* Get line from client */
                String line = is.readLine();

                /* Parse commands */
                if (line.startsWith("/quit")) {
                    break;
                }

                if (line.startsWith("/send")) {
                    /* Listen on port 5556 */
                    System.out.print("Opening port...");
                    ServerSocket server = new ServerSocket(5557);
                    System.out.println("Done");

                    long sstart, scost, sspeed, stotal;
                    Socket[] sk = new Socket[2];
                    
                    /* Accept the sender */
                    System.out.print("Accepting first client...");
                    sk[0] = server.accept();
                    System.out.println("Done");

                    /* Set variables for sender */
                    InputStream input = sk[0].getInputStream();
                    BufferedReader inReader = new BufferedReader(new InputStreamReader(sk[0].getInputStream()));
                    BufferedWriter outReader = new BufferedWriter(new OutputStreamWriter(sk[0].getOutputStream()));

                    /* Get the receiver */
                    System.out.print("Retrieving the receiver...");
                    String destination = inReader.readLine();
                    System.out.println("Done");

                    /* Tell the transfer port to destination client */
                    System.out.print("Sending transfer command to receiver...");
                    synchronized (this) {
                        for (int i = 0; i < maxClientsCount; i++) {
                            if (threads[i] != null && threads[i] != this
                                    && threads[i].clientName != null
                                    && threads[i].clientName.contains(destination)) {
                                threads[i].os.println("/send");
                            }
                        }
                    }
                    System.out.println("Done");

                    /* Accept the receiver */
                    System.out.print("Accepting receiver...");
                    sk[1] = server.accept();
                    System.out.println("Done");

                    /* Set variables for receiver */
                    OutputStream output = sk[1].getOutputStream();
                    BufferedReader inWriter = new BufferedReader(new InputStreamReader(sk[1].getInputStream()));
                    BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(sk[1].getOutputStream()));

                    /* Read the filename */
                    System.out.print("Retrieving the filename...");
                    String filename = inReader.readLine();
                    System.out.println("Done");

                    System.out.print("Handshaking...");
                    if (!filename.equals("")) {
                        System.out.print("Sending filename to receiver...");
                        outWriter.write(filename + "\n");
                        outWriter.flush();
                        
                        System.out.print("Getting receiver status...");
                        String status = inWriter.readLine();
                        if (status.contains("ABORD")){
                            System.out.print("ABORDING...");
                            outReader.write("ABORD\n");
                            outReader.flush();
                            output.close();
                            server.close();
                            continue;
                        }
                        System.out.print("READY...Sending READY to sender...");
                        /* Reply back to client with READY status */
                        outReader.write("READY\n");
                        outReader.flush();                  
                        
                    }
                    System.out.println("Done");

                    /* Set the best buffer to use and send the length to both clients */
                    System.out.print("Calculating best buffer size...");
                    byte[] buffer1 = new byte[sk[0].getReceiveBufferSize()];
                    byte[] buffer2 = new byte[sk[1].getReceiveBufferSize()];
                    byte[] buffer;

                    if (buffer1.length > buffer2.length) {
                        buffer = buffer2;
                    } else {
                        buffer = buffer1;
                    }
                    outReader.write(buffer.length + "\n");
                    outReader.flush();
                    outWriter.write(buffer.length + "\n");
                    outWriter.flush();
                    System.out.println("Done");

                    /* Send the file */
                    System.out.print("Sending " + filename + " to " + destination + "...");
                    int bytesReceived;

                    while ((bytesReceived = input.read(buffer)) > 0) {                        
                        output.write(buffer, 0, bytesReceived);                        
                    }
                    System.out.println("Done");
                    
                    output.close();
                    server.close();
                }

                if (line.startsWith("@")) { // If the message is private sent it to the given client.
                    String[] words = line.split("\\s", 2);
                    if (words.length > 1 && words[1] != null) {
                        words[1] = words[1].trim();
                        if (!words[1].isEmpty()) {
                            synchronized (this) {
                                for (int i = 0; i < maxClientsCount; i++) {
                                    if (threads[i] != null && threads[i] != this
                                            && threads[i].clientName != null
                                            && threads[i].clientName.equals(words[0])) {
                                        threads[i].os.println("<" + name + "> " + words[1]);
                                        /*
                                         * Echo this message to let the client know the private
                                         * message was sent.
                                         */
                                        this.os.println(">" + name + "> " + words[1]);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    /* The message is public, broadcast it to all other clients. */
                    synchronized (this) {
                        for (int i = 0; i < maxClientsCount; i++) {
                            if (threads[i] != null && threads[i].clientName != null) {
                                threads[i].os.println("<" + name + "> " + line);
                            }
                        }
                    }
                }
            }

            /* Convesation ended */
            synchronized (this) {
                for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] != null && threads[i] != this
                            && threads[i].clientName != null) {
                        threads[i].os.println("*** The user " + name
                                + " is leaving the chat room !!! ***");
                    }
                }                
            }
            os.println("*** Bye " + name + " ***");

            /*
             * Clean up. Set the current thread variable to null so that a new client
             * could be accepted by the server.
             */
            synchronized (this) {
                for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] == this) {
                        threads[i] = null;
                    }
                }
                for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] != null) {
                        threads[i].os.println("/users");
                        for(int j = 0; j < maxClientsCount; j++){
                            if ((threads[j] != null) && (threads[j].clientName != null)) {
                                threads[i].os.println(threads[j].clientName);
                            }
                        }
                        threads[i].os.println("/usersEnd");
                    }
                }
            }

            /*
             * Close the output stream, close the input stream, close the socket.
             */
            is.close();
            os.close();
            clientSocket.close();
        } catch (IOException e) {
        }
    }
}
