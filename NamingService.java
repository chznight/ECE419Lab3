import java.net.*;
import java.io.*;
import java.util.HashMap;


class LookupTable implements Serializable {
	public String  client_name;
    public int client_id;
    public ClientLocation client_location;	
	/* constructors */
	public LookupTable() { 
		this.client_name = ""; /*name initialized to empty string*/
	}
	
}

public class NamingService {
    public static void main(String[] args) throws IOException {

        ServerSocket serverSocket = null;
        //boolean listening = true;
        int num_of_players = 2;
	int num_of_players_connected=0;

        try {
            if(args.length == 2) {
                serverSocket = new ServerSocket(Integer.parseInt(args[0]));
                num_of_players = Integer.parseInt(args[1]);
            } else {
                System.err.println("ERROR: Invalid arguments!");
                System.err.println("USE: Port Number_of_players");
                System.exit(-1);
            }
        } catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }



        LookupTable[] clientLookupTable = new LookupTable[num_of_players]; /*Created a lookup table here*/
        
        for (int i = 0; i < num_of_players; i++) {
            clientLookupTable[i] = new LookupTable();
        }

        //while (listening) {
        while(num_of_players_connected<num_of_players){
        	new NamingServiceHandlerThread(serverSocket.accept(), clientLookupTable, num_of_players).start();
        	num_of_players_connected++;
        }

        serverSocket.close();
    }
}
