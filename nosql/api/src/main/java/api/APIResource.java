package api ;


import org.restlet.representation.* ;
import org.restlet.data.* ;
import org.restlet.ext.json.* ;
import org.restlet.resource.* ;
import org.restlet.ext.jackson.* ;

import org.json.* ;
import nojava.* ;
import java.io.IOException ;


public class APIResource extends ServerResource {


    @Post
    public Representation post_action (Representation rep) throws IOException {
    	String doc_key = getAttribute("key") ;
    	//There is no key mentioned in the post request
    	if ( doc_key == null || doc_key.equals("") ) {
            setStatus( org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST ) ;
            Status status = new Status() ;
            status.status = "Error!" ;
            status.message = "Missing Document Key." ;
            return new JacksonRepresentation<Status>(status) ;
        } else {
        	
            try {
            	//Document exists
                String doc = API.get_document(doc_key);
                
                //Check tombstone if document exists
                if(API.checkDocForTombstone(doc_key))
                {
                	//Creating an already deleted document TS = T
                	//update doc( including vclock and message), set TS = F --> update document does all these
                	
                	JsonRepresentation represent = new JsonRepresentation(rep);
                    JSONObject jsonobject = represent.getJsonObject();
                    String doc_json = jsonobject.toString();
                	
                	API.update_document(doc_key, doc_json );
                	
                	Status status = new Status() ;
                	status.status = "Ok!" ;
                	status.message = "Document Updated: " + doc_key ;
                	return new JacksonRepresentation<Status>(status) ; 
                	
                	
                } else {
                	//Document is already existing with TS = F
                	setStatus( org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST ) ;
                	Status status = new Status() ;
	                status.status = "Error!" ;
	                status.message = "Document Exists." ;
	                return new JacksonRepresentation<Status>(status) ;
                	
                }	//end if
	                
                
            } catch ( DocumentException d ) { 
            	//get_document will throw an exception if document does not exist and come here
            	//Create a new entry in the DB here

            	JsonRepresentation represent = new JsonRepresentation(rep);
                JSONObject jsonobject = represent.getJsonObject();
                String doc_json = jsonobject.toString();
                // HashMap Document
                Document doc = new Document() ;
                doc.key = doc_key ;
                doc.json = "" ; // don't store in cache
                doc.message = "Document Queued for Storage." ;
                
                doc.setTombstone(false);
                
                // Store to DB
                try {
                	
                	//DON'T UPDATE TS HERE..It's set to false in create_document
                	API.create_document( doc_key, doc_json ) ;
                	return new JacksonRepresentation<Document>(doc) ;
                } catch (Exception e) {
                	setStatus( org.restlet.data.Status.SERVER_ERROR_INTERNAL ) ;
                	Status status = new Status() ;
                	status.status = "Server Error!" ;
                	status.message = e.toString() ;
                	return new JacksonRepresentation<Status>(status) ;  
                }
            	
            } 	//end catch

		}	//end if
    }


    @Get
    public Representation get_action (Representation rep) throws IOException {
    	String doc_key = getAttribute("key") ;
    	if ( doc_key == null || doc_key.equals("") ) {
    		return new JacksonRepresentation<Document[]>(API.get_hashmap()) ;  
        } else {
        	try {
        		//Document exists
        		String doc = API.get_document( doc_key ) ;	
        		//Check for tombstone 
        		if(API.checkDocForTombstone(doc_key))
                {
                	//Get a document with TS = T --doesnt exist for the user
        			System.out.println("\n Trying to GET a document with TS=T ");
        			setStatus( org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST ) ;
    	            Status status = new Status() ;
    	            status.status = "Error!" ;
    	            status.message = ("Trying to GET a document with TS=T") ;      	
    	            return new JacksonRepresentation<Status>(status);
                	
                } else {
                	//Document exists with TS = F--return the valid document
                	return new StringRepresentation(doc, MediaType.APPLICATION_JSON);
                	
                }

        	} catch ( Exception e ) {
        		//Document doesnt exist--Nothing to do
	            setStatus( org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST ) ;
	            Status status = new Status() ;
	            status.status = "Error!" ;
	            status.message = e.toString() ;      	
	            return new JacksonRepresentation<Status>(status) ;
        	}
		}
    }


   
    @Put
    public Representation update_action (Representation rep) throws IOException {
    	String doc_key = getAttribute("key") ;
    	
    	if ( doc_key == null || doc_key.equals("") ) {
    		
            setStatus( org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST ) ;
            Status status = new Status() ;
            status.status = "Error!" ;
            status.message = "Missing Document Key." ;
            return new JacksonRepresentation<Status>(status) ;
            
        } else {
        	
        	JsonRepresentation represent = new JsonRepresentation(rep);
            JSONObject jsonobject = represent.getJsonObject();
            String doc_json = jsonobject.toString();
            
            try {
            	//check if document exists
                String exists = API.get_document(doc_key);
                
                //Document exists
                if(API.checkDocForTombstone(doc_key))
                {
                	//Trying to update a document with TS = T --doesnt exist for the user
                	System.out.println("\n Trying to PUT a document with TS=T ");
                	setStatus( org.restlet.data.Status.SERVER_ERROR_INTERNAL ) ;
                	Status status = new Status() ;
                	status.status = "Server Error!" ;
                	status.message = "Trying to PUT a document with TS = T ";
                	return new JacksonRepresentation<Status>(status) ;
                	
                } else {
                	
                	//Document exists with TS = F--update document
                	API.update_document( doc_key, doc_json ) ;
                	Status status = new Status() ;
                	status.status = "Ok!" ;
                	status.message = "Document Updated: " + doc_key ;
                	return new JacksonRepresentation<Status>(status) ; 
                	
                }     
                
            } catch ( Exception e ) { 
            	
            	//Document does not exist in the DB
            	
            	setStatus( org.restlet.data.Status.SERVER_ERROR_INTERNAL ) ;
            	Status status = new Status() ;
            	status.status = "Server Error!" ;
            	status.message = e.toString() ;
            	return new JacksonRepresentation<Status>(status); 
            }
            

		}
    }

    @Delete
    public Representation delete_action (Representation rep) throws IOException {
    	String doc_key = getAttribute("key") ;
    	if ( doc_key == null || doc_key.equals("") ) {
            setStatus( org.restlet.data.Status.CLIENT_ERROR_BAD_REQUEST ) ;
            Status status = new Status() ;
            status.status = "Error!" ;
            status.message = "Missing Document Key." ;
            return new JacksonRepresentation<Status>(status) ;
        } else {
        	
        	
        	try {
            	//Document exists
                String exists = API.get_document(doc_key);
                
                
                if(API.checkDocForTombstone(doc_key))
                {
                	//Trying to delete a document with TS = T --doesnt exist for the user
                	System.out.println("\n Trying to DELETE a document with TS=T ");
                	setStatus( org.restlet.data.Status.SERVER_ERROR_INTERNAL ) ;
                	Status status = new Status() ;
                	status.status = "Server Error!" ;
                	status.message = "Trying to DELETE a document with TS = T ";
                	return new JacksonRepresentation<Status>(status) ;
                	
                } else {
                	//Document exists with TS = F--delete document
                	//Delete the document, update vclocks with owner as current node, TS=T
                	
                	
                	API.delete_document( doc_key ) ;	
                	Status status = new Status() ;
                	status.status = "Ok!" ;
                	status.message = "Document Deleted: " + doc_key ;
                	return new JacksonRepresentation<Status>(status) ;  
                	
                }   
                
            } catch ( Exception e ) { 
            	
            	setStatus( org.restlet.data.Status.SERVER_ERROR_INTERNAL ) ;
            	Status status = new Status() ;
            	status.status = "Server Error!" ;
            	status.message = "Trying to DELETE a document with TS = T ";
            	return new JacksonRepresentation<Status>(status) ; 
            }	
		}
    }
}

