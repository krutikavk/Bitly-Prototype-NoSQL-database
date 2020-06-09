package api ;

import nojava.* ;
import nojava.SM.CannotDeleteException;
import nojava.SM.NotFoundException;

import java.util.* ;
import java.io.* ;

import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.LinkedBlockingQueue ;
import java.util.concurrent.ConcurrentHashMap ;
import java.util.Collection ;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import org.json.* ;
import org.restlet.resource.*;

import api.Status;

import org.restlet.representation.* ;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.ext.json.* ;
import org.restlet.data.* ;




public class API implements Runnable {

	// queue of new documents
    private static BlockingQueue<Document> CREATE_QUEUE = new LinkedBlockingQueue<Document>() ;						

    // key to record map
    private static ConcurrentHashMap<String,Document> KEYMAP_CACHE = new ConcurrentHashMap<String,Document>() ; 	


    // Background Thread
	@Override
	public void run() {
		while (true) {
			try {
				// sleep for 5 seconds
				try { Thread.sleep( 5000 ) ; } catch ( Exception e ) {}  

				// process any new additions to database
				Document doc = CREATE_QUEUE.take();
  				SM db = SMFactory.getInstance() ;
        		SM.OID record_id  ;
        		String record_key ;
        		SM.Record record  ;
        		String jsonText = doc.json ;
            	int size = jsonText.getBytes().length ;
            	record = new SM.Record( size ) ;
            	record.setBytes( jsonText.getBytes() ) ;
            	record_id = db.store( record ) ;
            	record_key = new String(record_id.toBytes()) ;
            	doc.record = record_key ;
            	doc.json = "" ;
            	KEYMAP_CACHE.put( doc.key, doc ) ;    
                System.out.println( "Created Document: " + doc.key ) ;
                
                // sync nodes
                AdminServer.syncDocument( doc.key, "create" ) ; 

			} catch (InterruptedException ie) {
				ie.printStackTrace() ;
			} catch (Exception e) {
				System.out.println( e ) ;
			}			
		}
	}    


    public static Document[] get_hashmap() {
    	return (Document[]) KEYMAP_CACHE.values().toArray(new Document[0]) ;
    }


    public static void save_hashmap() {
		try {
		  FileOutputStream fos = new FileOutputStream("index.db");
		  ObjectOutputStream oos = new ObjectOutputStream(fos);
		  oos.writeObject(KEYMAP_CACHE);
		  oos.close();
		  fos.close();
		} catch(IOException ioe) {
		  ioe.printStackTrace();
		}
    }


    public static void load_hashmap() {
		 try {
		     FileInputStream fis = new FileInputStream("index.db") ;
		     ObjectInputStream ois = new ObjectInputStream(fis) ;
		     KEYMAP_CACHE = (ConcurrentHashMap) ois.readObject();
		     ois.close() ;
		     fis.close() ;
		  } catch(IOException ioe) {
		     ioe.printStackTrace() ;
		  } catch(ClassNotFoundException c) {
		     System.out.println("Class not found");
		     c.printStackTrace() ;
		  }
    }
    
   //Conflict Detection
	/* 
	 * In order for vclock incoming to be considered a descendant of vclock local, each marker in vclock incoming 
	 * must revision number greater than or equal to the marker in vclock local.
	 */
    
   public static boolean isConflict(String[] local, String[] incoming) {
	   boolean isConflict = false;
	   int localVClockTag, incomingVClockTag;

	   for(int i = 1; i < 6; i++) {
		   localVClockTag = getVClockTag(local[i]);
		   incomingVClockTag = getVClockTag(incoming[i]);
		   
		   if(incomingVClockTag < localVClockTag) {
			   isConflict = true;
			   break;
		   }	   
	   }
	   
	   return isConflict;
   }
   
   
   //Merge 2 vclocks and get the conflict winner--largest revision number for each marker in incoming/local vclocks
   public static int mergeVClocks(String[] local, String[] incoming) {
	   int localVClockTag, incomingVClockTag, newValue;
	   int winner = 0;
	   for(int i = 1; i < 6; i++) {
		  localVClockTag = getVClockTag(local[i]);
		  incomingVClockTag = getVClockTag(incoming[i]);
		  
		  if(localVClockTag != incomingVClockTag) {
			  winner = i;
		  }
		  
		  if(localVClockTag == 0) {
			  
			  if(incoming[i] != null)
				  local[i] = incoming[i];
		  } else {
			  newValue = (localVClockTag >= incomingVClockTag) ? localVClockTag : incomingVClockTag;
			  local[i] = local[i].split(":")[0] + ":" + Integer.toString(newValue);
		  }
		  
	   }
	   return winner;  
   }
   
   //Process null vclock tags as 0
   private static int getVClockTag(String vClock) {
	    if(vClock == null) {
	    	return 0;
	    } else {
	    	return (Integer.parseInt(vClock.split(":")[1]));
	    }
   }

   public static void sync_document(SyncRequest sync) throws DocumentException {

        String key = sync.key ;
        String value = sync.json ;
        String[] vclock = sync.vclock ;
        String command = sync.command ;
        
        boolean conflict;

        try {

            AdminServer server = AdminServer.getInstance() ;
            String my_host = server.getMyHostname() ;
            int my_index = server.nodeIndex( my_host ) ;
            int incoming_index = server.nodeIndex(vclock[0]);
            
            SM db = SMFactory.getInstance() ;
            SM.OID record_id  ;
            SM.Record record  ;
            String jsonText = value ;
            int size = jsonText.getBytes().length ;
            System.out.println("From     : "     + server.nodeIndex(vclock[0])
                               + " Key   :"    + key
                               + " Value :"  + value
                               + " Comand :" + command);
            
            
            switch ( command ) {
                case "create":
                    
                    Document doc = KEYMAP_CACHE.get(key);
                    if (doc == null)
                    {
                    	//Doc doesnt exist--create a new one
                    	doc = new Document() ;
                        doc.vclock[0] = my_host ;
                        doc.vclock[1] = vclock[1] ;
                        doc.vclock[2] = vclock[2] ;
                        doc.vclock[3] = vclock[3] ;
                        doc.vclock[4] = vclock[4] ;
                        doc.vclock[5] = vclock[5] ;
                        
                        doc.vclock[my_index] = my_host + ":" + Integer.toString(0) ;
                        
                        //Add tombstine value for the document
                        doc.setTombstone(false);
                        
                        record = new SM.Record( size ) ;
                        record.setBytes( jsonText.getBytes() ) ;
                        record_id = db.store( record ) ;
                        
                        
                        String record_key;
                        record_id = db.store( record ) ;
                        record_key = new String(record_id.toBytes()) ;
                        record = new SM.Record( size ) ;
                        record.setBytes( jsonText.getBytes() ) ;
                         
                        doc.record = record_key ;
                        doc.json = "" ;
                        doc.key = key ;
                        KEYMAP_CACHE.put( key, doc ) ;
                        System.out.println( "SYNC: Created Document Key: " + key 
                                            + " Record: " + record_key  + " incoming value: " + value
                                            + " vClock: " + Arrays.toString(doc.vclock) 
                                        ) ;
                        break ;
                    		
                    } else {
                    	//Document exists, proceed to check the TS value
                    	
                    	conflict = isConflict(doc.getVclock(), vclock);
                    	int winnerNode = mergeVClocks(doc.getVclock(), vclock);
                       
                    	//conflict, i am not winner but message from winner
                    	// if conflict and message from winner -> store
                    	System.out.println("Conflict : " + conflict
                    			          + " winner : " + winnerNode
                    			          + " incoming from : " + incoming_index);
                    	if (conflict && incoming_index != winnerNode) {
                    		break;
                    	} else {
                    		//No conflict or conflict and message from winnerNode 
                    		SM.OID update_id ;
                    		doc.setTombstone(false);
                        	record = new SM.Record( size ) ;
                            record.setBytes( jsonText.getBytes() ) ;
                    		record_id = db.getOID( doc.record.getBytes() ) ;
                            update_id = db.update( record_id, record ); 
                            
                            System.out.println( "Incoming from(kachra update): " + server.nodeIndex(vclock[0]) + "SYNC CREATE No Conflict/Winner: Updated Document Key: " + key 
                                    + " vClock: " + Arrays.toString(doc.vclock) 
                                ) ;
                    		
                    	}	//end if
                    	
                    }	//end if
 
                    break ;
                case "update":
                	/*
						vClock Counters Calculations
					Only Increment Local Counters when User makes a Change (i.e. from API calls on port 9XXX)
					For Sync Calls on port 8XXX, Only merge vClock Counters (i.e. do not increment local counters)
					For Null vClock Counter for a Node, assume the Counter is Zero         
                	 */
                	
                	//Check first if there is a conflict
                	//Resolve conflict
                	
                	SM.OID update_id ;
                	record = new SM.Record( size ) ;
                    record.setBytes( jsonText.getBytes() ) ;
                    Document doc1 = KEYMAP_CACHE.get(key);
                    assert(doc1 != null);
                    
                    conflict = isConflict(doc1.getVclock(), vclock); 
                    int winnerNode = mergeVClocks(doc1.getVclock(), vclock);
                    
                	System.out.println("Conflict : " + conflict
      			          + " winner : " + winnerNode
      			          + " incoming from : " + incoming_index);

                	if (conflict && incoming_index != winnerNode) {
                		//Reject updates coming from loser node
                		break;
                	} else {
                		//No conflict or conflict and incoming message is winner : accept

                		doc1.setTombstone(false);
                    	record = new SM.Record( size ) ;
                        record.setBytes( jsonText.getBytes() ) ;
                		record_id = db.getOID( doc1.record.getBytes() ) ;
                        update_id = db.update( record_id, record ); 
                        
                        System.out.println( "Incoming from(kachra update): " + server.nodeIndex(vclock[0]) + "SYNC UPDATE No Conflict/Winner: Updated Document Key: " + key 
                                + " vClock: " + Arrays.toString(doc1.vclock) 
                            ) ;
                		
                	}
                    
                    break ;
                case "delete":

                	System.out.println("----------------DELETE DOCUMENT----------------------");
                	SM.OID delete_id ;
                    Document doc2 = KEYMAP_CACHE.get(key);
                    assert(doc2 != null);
                    
                    conflict = isConflict(doc2.getVclock(), vclock);
                    
                    int winner = mergeVClocks(doc2.getVclock(), vclock);
                	System.out.println("Conflict : " + conflict
      			          + " winner : " + winner
      			          + " incoming from : " + incoming_index);

                    
                	if (conflict && incoming_index != winner) {
                		//Reject updates coming from loser node
                		break;
                	} else {
                		//No conflict or conflict and incoming message is winner : accept

                		doc2.setTombstone(true);                        
                        System.out.println( "Incoming from(kachra update): " + server.nodeIndex(vclock[0]) + "SYNC DELETE No Conflict/Winner: Updated Document Key: " + key 
                                + " vClock: " + Arrays.toString(doc2.vclock) 
                            ) ;
                		
                	}	//end if
                	
                    break ;
                    
            }   //end switch 

        } catch (Exception e) {
            throw new DocumentException( e.toString() ) ;
        }

    }


    public static void create_document(String key, String value) throws DocumentException {
    	try {
	    	System.out.println( "Create Document: Key = " + key + " Value = " + value ) ;
	    	Document doc = new Document() ;
	    	doc.key = key ;
	    	
	    	//Set TS value too
	    	doc.setTombstone(false);
	    	
            AdminServer server = AdminServer.getInstance() ;
            String my_host = server.getMyHostname() ;
            System.out.println( "My Host Name: " + my_host ) ;
            doc.vclock[0] = my_host ;
            
            
            String my_version = my_host + ":" + Integer.toString(1) ;
            int my_index = server.nodeIndex( my_host ) ;
            System.out.println( "Node Index: " + my_index ) ;
            doc.vclock[my_index] = my_version ;
	    	KEYMAP_CACHE.put( key, doc ) ;
	    	doc.json = value ;
	        CREATE_QUEUE.put( doc ) ; 
	    	System.out.println( "New Document Queued: " + key ) ;    		
	    } catch (Exception e) {
	    	throw new DocumentException( e.toString() ) ;
	    }

    }


    public static String get_document(String key) throws DocumentException {
    	System.out.println( "Get Document: " + key ) ;
    	Document doc = KEYMAP_CACHE.get( key ) ;
    	if ( doc == null || doc.record == null )
    		throw new DocumentException( "Document Not Found: " + key ) ;
    	String record_key = doc.record ;
    	SM db = SMFactory.getInstance() ;
    	SM.OID record_id ;
        SM.Record found ;
		record_id = db.getOID( record_key.getBytes() ) ;
        try {
            found = db.fetch( record_id ) ;
            byte[] bytes = found.getBytes() ;
            String jsonText = new String(bytes) ;
            System.out.println( "Document Found: " + key ) ;    
            return jsonText ;
        } catch (SM.NotFoundException nfe) {
        	System.out.println( "Document Found: " + key ) ;    
			throw new DocumentException( "Document Not Found: " + key ) ;   
		} catch (Exception e) {
			throw new DocumentException( e.toString() ) ;                 
        }   	
    }


    public static SyncRequest get_sync_request(String key) throws DocumentException {
        System.out.println( "Get Document to sync : " + key ) ;
        Document doc = KEYMAP_CACHE.get( key ) ;
        if ( doc == null || doc.record == null )
            throw new DocumentException( "Document Not Found: " + key ) ;
        String record_key = doc.record ;
        SM db = SMFactory.getInstance() ;
        SM.OID record_id ;
        SM.Record found ;
        record_id = db.getOID( record_key.getBytes() ) ;
        try {
            found = db.fetch( record_id ) ;
            byte[] bytes = found.getBytes() ;
            String jsonText = new String(bytes) ;
            System.out.println( "Document Found: " + key ) ;    
            SyncRequest sync = new SyncRequest() ;
            sync.key = doc.key ;
            sync.json = jsonText ;
            
            //Deep copy
            //sync.vclock = doc.vclock ;
            
            //Shallow copy
            sync.vclock[0] = doc.vclock[0]; 
            sync.vclock[1] = doc.vclock[1] ;
            sync.vclock[2] = doc.vclock[2] ;
            sync.vclock[3] = doc.vclock[3] ;
            sync.vclock[4] = doc.vclock[4] ;
            sync.vclock[5] = doc.vclock[5] ;
            
            sync.command = "" ; // set by caller
            System.out.println( "Send Document to sync : " + key ) ;
                
            return sync ;
        } catch (SM.NotFoundException nfe) {
            System.out.println( "Document Found: " + key ) ;    
            throw new DocumentException( "Document Not Found: " + key ) ;   
        } catch (Exception e) {
            throw new DocumentException( e.toString() ) ;                 
        }       
    }

    
    //Check for tombstone behavior--caller will do that
    public static void update_document( String key, String value ) throws DocumentException {
    	System.out.println( "Get Document to update: " + key ) ;
    	Document doc = KEYMAP_CACHE.get( key ) ;
    	if ( doc == null || doc.record == null )
    		throw new DocumentException( "Document Not Found: " + key ) ;
    	String record_key = doc.record ;
    	SM db = SMFactory.getInstance() ;
        SM.Record found ;
        SM.Record record ;
        SM.OID update_id ;        
		SM.OID record_id = db.getOID( record_key.getBytes() ) ;
		String jsonText = value ;
		int size = jsonText.getBytes().length ;
 		try {
            // store json to db
            record = new SM.Record( size ) ;
            record.setBytes( jsonText.getBytes() ) ;
            update_id = db.update( record_id, record ) ;
            System.out.println( "Document Updated: " + key ) ;
            // update vclock
            AdminServer server = AdminServer.getInstance() ;
            String my_host = server.getMyHostname() ;
            doc.vclock[0] = my_host ;
            
            //set tombstone
            doc.setTombstone(false);
            
            int my_index = server.nodeIndex( my_host ) ;
            System.out.println(my_index);
            String old_version = doc.vclock[my_index] ;
            System.out.println(old_version);
            
            String[] splits = old_version.split(":") ;
            int version = Integer.parseInt(splits[1])+1 ;
            String new_version = my_host + ":" + Integer.toString(version) ;
            System.out.println(new_version);
            
            doc.vclock[my_index] = new_version ;
            // sync nodes
            AdminServer.syncDocument( key, "update" ) ; 
			return ;             
        } catch (SM.NotFoundException nfe) {
			throw new DocumentException( "Document Not Found: " + key ) ;
       	} catch (Exception e) {
           	throw new DocumentException( e.toString() ) ;           
        }
    }

    
    /* Original delete-Document without tombstone
    public static void delete_document( String key ) throws DocumentException {
    	System.out.println( "Delete Document: " + key ) ;
    	Document doc = KEYMAP_CACHE.get( key ) ;
    	if ( doc == null || doc.record == null )
    		throw new DocumentException( "Document Not Found: " + key ) ;
    	String record_key = doc.record ;
    	SM db = SMFactory.getInstance() ;
        SM.Record found ;
        SM.Record record ;     
		SM.OID record_id = db.getOID( record_key.getBytes() ) ;
       	try {
       		//Sync delete before the document is deleted!! else we're trying to sync (get document) after its already removed!
            //db.delete( record_id ) ;
       		//doc.vclock[0] = AdminServer.getInstance().getMyHostname(); --> now done in updateVClockForDelete function
       		
       		//Increment vclock, make current node owner of updated vclock  
       		updateVClockForDelete(doc.getVclock());
       		//Set tombstone to true
       		doc.setTombstone(true);
       		
            AdminServer.syncDocument( key, "delete" ) ; 
            db.delete( record_id ) ;
            // remove key map
            KEYMAP_CACHE.remove( key ) ;
			System.out.println( "Document Deleted: " + key ) ;
        } catch (SM.NotFoundException nfe) {
           throw new DocumentException( "Document Not Found: " + key ) ;
        } catch (Exception e) {
         	throw new DocumentException( e.toString() ) ;            
        }		
    }
    
    */
    
    
    //New delete_document with tombstone functionality
    public static void delete_document( String key ) throws DocumentException {
    	System.out.println( "Delete Document: " + key ) ;
    	Document doc = KEYMAP_CACHE.get( key ) ;
    	if ( doc == null || doc.record == null )
    		throw new DocumentException( "Document Not Found: " + key ) ;
    	
    	String record_key = doc.record ;
    	SM db = SMFactory.getInstance() ;
        SM.Record found ;
        SM.Record record ;     
		SM.OID record_id = db.getOID( record_key.getBytes() ) ;
		
       	try {
       		
       		//Increment vclock, self=owner of vclock 
       		updateVClockForDelete(doc.getVclock());
       		//Set tombstone to true
       		doc.setTombstone(true);
       		
            AdminServer.syncDocument( key, "delete" ) ; 
            
            //Do not delete anything from the database

			System.out.println( "Document Deleted: " + key ) ;
        } catch (Exception e) {
         	throw new DocumentException( e.toString() ) ;            
        }		
    }
    
    
    //Helper to increment vclock, make current node owner of updated vclock in case of delete
    //Make it generic later 
    // 1. Increment vClock
    // 2. Update owner
    public static void updateVClockForDelete (String[] vClock) {
    	
    	AdminServer server = AdminServer.getInstance() ;
        String my_host = server.getMyHostname() ;
        
        //Update owner
        vClock[0] = my_host ;
        
        int my_index = server.nodeIndex(my_host ) ;
        String old_version = vClock[my_index] ;
        String[] splits = old_version.split(":") ;
        
        //Increment vclock
        int version = Integer.parseInt(splits[1])+1 ;
        String new_version = my_host + ":" + Integer.toString(version) ; 
        vClock[my_index] = new_version ;
    	
    }

    
    public static boolean checkDocForTombstone (String key) {
    	Document doc = KEYMAP_CACHE.get( key );
    	return doc.isTombstone();
    	
    }
    

}


