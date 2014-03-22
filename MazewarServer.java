import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/*Please let this server run on ug140:1111 by default*/
public class MazewarServer {	

  public static int NUM_PLAYER_SUPPORTED = 2;

	public static void main(String[] args) throws IOException {
       	ServerSocket serverSocket = null;
       	boolean listening = true;
        int number_of_players = 0;

        //Client list is storing the currently connected clients
       	LinkedList<ClientInfo> ClientList = new LinkedList<ClientInfo>();
        //using blocking queue to solve concurrency and also save cpu
       	BlockingQueue<MazewarPacket> QueueList = new ArrayBlockingQueue<MazewarPacket>(1024);

        try {
        	if(args.length == 2) {
        		serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        		NUM_PLAYER_SUPPORTED = Integer.parseInt(args[1]);
        	} else {
        		System.err.println("ERROR: Invalid arguments!");
        		System.exit(-1);
        	}
        } catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }


        	MazewarServerSendThread serverSendThread;
        	MazewarServerReceiveThread[] serverReceiveThread = new MazewarServerReceiveThread[NUM_PLAYER_SUPPORTED];
        	
          serverSendThread = new MazewarServerSendThread(ClientList, QueueList);
          serverSendThread.start();

	     while (number_of_players < NUM_PLAYER_SUPPORTED) {
          		serverReceiveThread[number_of_players] = new MazewarServerReceiveThread(serverSocket.accept(), ClientList, QueueList, NUM_PLAYER_SUPPORTED);
              serverReceiveThread[number_of_players].start();
              number_of_players++;
        	}

          try {
            for (int i = 0; i < NUM_PLAYER_SUPPORTED; i++) {
              serverReceiveThread[i].join();
            }
            serverSendThread.stop_game();
            serverSendThread.join();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }



	        serverSocket.close();
    	}
}



