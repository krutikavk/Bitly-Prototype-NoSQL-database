package api ;

import nojava.* ;
import java.util.Map ;

//Test Commit-CP mode
public class Main {

	private static SM db ;

    public static void main(String[] args) throws Exception {

    	db = SMFactory.getInstance() ;          // startup DB Instance
        AppServer.startup() ;                   // startup App REST Service on port 9090
        AdminServer.startup() ;                 // startup Admin REST Service on port 8888
        
        
        /*kk-add
        API api = new API();
        new Thread(api).start();
        PingChecks pings = new PingChecks();
        new Thread(pings).start();
        Sync syncs = new Sync()
        new Thread(syncs).start();
        */
        
 
        // dump out environment variables
        Map<String, String> env = System.getenv();
        System.out.println( "CLUSTER_NAME = " + env.get("CLUSTER_NAME") ) ;
        System.out.println( "CAP_MODE = " + env.get("CAP_MODE") ) ;
        System.out.println( "VERSION = " + env.get("VERSION") ) ;

    }

}



        