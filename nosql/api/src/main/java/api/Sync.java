package api ;

import java.io.* ;
import java.net.InetAddress;
import java.util.* ;

import org.json.* ;
import org.restlet.resource.*;
import org.restlet.representation.* ;
import org.restlet.ext.jackson.* ;
import org.restlet.ext.json.* ;
import org.restlet.data.* ;
import org.restlet.* ;

import java.util.concurrent.ConcurrentLinkedQueue ;


public class Sync implements Runnable {

	//Get adminserver instance--same object returned every time
	private AdminServer server = AdminServer.getInstance() ;
	private ConcurrentLinkedQueue<SyncRequest> sync_queue ;
	private String sync_node ;

	public Sync( String node, ConcurrentLinkedQueue<SyncRequest> queue ) {
		sync_node = node ;						//This is "api_node_x"
		sync_queue = queue ;	
	}

    // Background Thread
	@Override
	public void run() {

		SyncRequest syncObject = null ;

		while (true) {
			try {
				// sleep for 5 seconds
				try { Thread.sleep( 5000 ) ; } catch ( Exception e ) {}  

				//System.out.println( "CHECK SYNC QUEUE: " + sync_node + "..." ) ;

				if ( !sync_queue.isEmpty() ) {
					
					System.out.println( "Node : " +server.getMyHostname() + " To : "
							 + sync_node + " Size :" + sync_queue.size()) ;
					
					// Check node status before it is dequed
					if(!server.isNodeUp(sync_node)) {
						continue;
					}
					
						
					// check sync queue for work
					syncObject = sync_queue.peek() ;	

					// try to sync to peer node...
					System.out.println ( 	  "SYNC: " + "[" + server.getMyHostname() + "]" + " -> " + sync_node  
											+ " Document Key: " + syncObject.key 
											+ " vClock: " + Arrays.toString(syncObject.vclock) + "sync command" + syncObject.command
											+ " sync message value : " + syncObject.json) ;

					//Ensuring post is successful, node has not gone down after post began
					
					ClientResource client = AdminServer.getSyncClient( sync_node ) ;
					try {
						Representation rep = client.post( new JacksonRepresentation<SyncRequest>(syncObject), MediaType.APPLICATION_JSON);
						String json = rep.getText();
						if (!client.getStatus().isSuccess()) {
							
							System.out.println("Retry later! :" + json);
							continue;
						}

					} catch (Exception e) {
						System.out.println("Error during post, retry later");
						continue;
					}
					//client.post( new JacksonRepresentation<SyncRequest>(syncObject), MediaType.APPLICATION_JSON);

					// remove head of queue if successful
					syncObject = sync_queue.poll() ;

					// if sync error, leave in queue for retry
				}				

			} catch (Exception e) {
				e.printStackTrace(); 
				System.out.println( e ) ;
			}			
		}
	}    

}


