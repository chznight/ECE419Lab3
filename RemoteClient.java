import java.util.Random;
import java.util.Vector;
import java.lang.Runnable;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/*
Copyright (C) 2004 Geoffrey Alan Washburn
   
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
   
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
   
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
*/
  
/**
 * A skeleton for those {@link Client}s that correspond to clients on other computers.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: RemoteClient.java 342 2004-01-23 21:35:52Z geoffw $
 */

public class RemoteClient extends Client implements Runnable{
        

        private BlockingQueue<MazewarPacket> clientCommandQueue;
        private String name;
        private final Thread thread;
        private boolean active = false;
        /**
         * Create a remotely controlled {@link Client}.
         * @param name Name of this {@link RemoteClient}.
         */
        public RemoteClient(String name_, BlockingQueue<MazewarPacket> clientCommandQueue_) {
                super(name_);
                name = name_;
                clientCommandQueue = clientCommandQueue_;
                thread = new Thread(this);
        }


        /**
         * Copying the structure of the RobotClient. Really, I not sure how it really works
         */ 

        public synchronized void registerMaze(Maze maze) {
                assert(maze != null);
                super.registerMaze(maze);

                // Get the control thread going.
                active = true;
                thread.start();
        }
        
        /** 
         * Override the abstract {@link Client}'s unregisterMaze method so we know when to stop the 
         * control thread. 
         */
        public synchronized void unregisterMaze() {
                // Signal the control thread to stop
                active = false; 
                // Wait half a second for the thread to complete.
                try {
                        thread.join(500);
                } catch(Exception e) {
                        // Shouldn't happen
                }
                super.unregisterMaze();
        }
        
        public void run() {
                // Put a spiffy message in the console
                try {
                // Loop while we are active
                        while(active) {
                                
                                MazewarPacket headQueuePacket;
                                headQueuePacket = clientCommandQueue.peek();
                                if (headQueuePacket == null)
                                {
                                        continue;
                                }
                                if (headQueuePacket.name.equals(name)) {
                                        clientCommandQueue.poll();
                                        if (headQueuePacket.command == MazewarPacket.MAZEWAR_MOVEFORWARD)
                                                forward();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_MOVEBACKWARD)
                                                backup();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_ROTATELEFT)
                                                turnLeft();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_ROTATERIGHT)
                                                turnRight();
                                        else if (headQueuePacket.command == MazewarPacket.MAZEWAR_FIRE)
                                                fire();
                                        else {
                                                //do nothing
                                        }       
                                }
                        }
                                
                } catch(Exception e) {
                                // Shouldn't happen.
                }
        }

}
