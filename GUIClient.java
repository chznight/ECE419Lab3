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

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.util.Random;
import java.util.Vector;
import java.lang.Runnable;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * An implementation of {@link LocalClient} that is controlled by the keyboard
 * of the computer on which the game is being run.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: GUIClient.java 343 2004-01-24 03:43:45Z geoffw $
 */

public class GUIClient extends LocalClient implements KeyListener, Runnable {

        private ObjectOutputStream [] out;
        private String name;
        private int num_players;

        private final Thread thread;
        /** 
         * Flag to say whether the control thread should be
         * running.
         */
        public BlockingQueue<MazewarPacket> clientCommandQueue;
        public BlockingQueue<MazewarPacket> clientBroadcastQueue;
        private boolean active = false;
        /**
         * Create a GUI controlled {@link LocalClient}.  
         */
        public GUIClient(String name_, ObjectOutputStream [] out_, BlockingQueue<MazewarPacket> clientCommandQueue_, BlockingQueue<MazewarPacket> clientBroadcastQueue_, int num_players_) {
                super(name_);
                name = name_;
                num_players = num_players_;
                out = out_;
                clientCommandQueue = clientCommandQueue_;
                clientBroadcastQueue = clientBroadcastQueue_;
                thread = new Thread(this);
        }
        
        public void multicastPacket (MazewarPacket packetToOthers) throws IOException {
            int i = 0;
            try {
                for (i=0; i<num_players; i++) {
                    if (out[i] != null)
                        out[i].writeObject(packetToOthers);
                }                        
            } catch (IOException e) {
                            
            }
        }

        /**
         * Handle a key press.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyPressed(KeyEvent e) {
                MazewarPacket packetToOthers = new MazewarPacket();
                packetToOthers.name = name;
                // If the user pressed Q, invoke the cleanup code and quit. 
                if((e.getKeyChar() == 'q') || (e.getKeyChar() == 'Q')) {
                        packetToOthers.type = MazewarPacket.MAZEWAR_BYE;
                        //try {
                        // //   multicastPacket (packetToOthers);
                        //} catch (IOException e2) {
                            
                        //}
                        //Mazewar.unregisterClient (name);
                        Mazewar.quit();
                // Up-arrow moves forward.
                } else if(e.getKeyCode() == KeyEvent.VK_UP) {
                        packetToOthers.type = MazewarPacket.MAZEWAR_REQ;
                        packetToOthers.command = MazewarPacket.MAZEWAR_MOVEFORWARD;
                        clientBroadcastQueue.add (packetToOthers);
                        //forward();
                // Down-arrow moves backward.
                } else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                        packetToOthers.type = MazewarPacket.MAZEWAR_REQ;
                        packetToOthers.command = MazewarPacket.MAZEWAR_MOVEBACKWARD;
                        clientBroadcastQueue.add (packetToOthers);
                        //backup();
                // Left-arrow turns left.
                } else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
                        packetToOthers.type = MazewarPacket.MAZEWAR_REQ;
                        packetToOthers.command = MazewarPacket.MAZEWAR_ROTATELEFT;
                        clientBroadcastQueue.add (packetToOthers);
                        //turnLeft();
                // Right-arrow turns right.
                } else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
                        packetToOthers.type = MazewarPacket.MAZEWAR_REQ;
                        packetToOthers.command = MazewarPacket.MAZEWAR_ROTATERIGHT;
                        clientBroadcastQueue.add (packetToOthers);
                // Spacebar fires.
                } else if(e.getKeyCode() == KeyEvent.VK_SPACE) {
                        packetToOthers.type = MazewarPacket.MAZEWAR_REQ;
                        packetToOthers.command = MazewarPacket.MAZEWAR_FIRE;
                        clientBroadcastQueue.add (packetToOthers);
                        //fire();
                }
        }
        
        /**
         * Handle a key release. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyReleased(KeyEvent e) {
        }
        
        /**
         * Handle a key being typed. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyTyped(KeyEvent e) {
        }
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
                                //forward();
                                //thread.sleep(2000);
                                //backup();
                                //thread.sleep(2000);
                        }
                                
                } catch(Exception e) {
                                // Shouldn't happen.
                }
        }

}
