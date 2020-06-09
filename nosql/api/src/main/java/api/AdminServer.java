package api;

import org.restlet.*;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;

import org.restlet.resource.*;
import org.restlet.representation.*;
import org.restlet.ext.json.*;
import org.restlet.data.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class AdminServer extends Application {

	// Only one instance of the class is created--Singleton
	private static AdminServer _adminServer;

	// Node Sync Queues
	private static ConcurrentLinkedQueue<SyncRequest> node1_sync_queue;
	private static ConcurrentLinkedQueue<SyncRequest> node2_sync_queue;
	private static ConcurrentLinkedQueue<SyncRequest> node3_sync_queue;
	private static ConcurrentLinkedQueue<SyncRequest> node4_sync_queue;
	private static ConcurrentLinkedQueue<SyncRequest> node5_sync_queue;

	// Instance Variables
	private ConcurrentHashMap<String, Node> nodes = new ConcurrentHashMap<String, Node>(); // Holds (hostname,Node) data

	private ConcurrentHashMap<String, String> nodesNameToId = new ConcurrentHashMap<String, String>();

	private ConcurrentHashMap<String, ClientResource> clients = new ConcurrentHashMap<String, ClientResource>();
	private String my_ip = "";
	private String my_host = "";
	
	private int leaderNodeIndex; 
	private int myNodeIndex;

	// Main create an instance (new for the first time0
	public synchronized static AdminServer getInstance() {
		if (_adminServer == null) {
			_adminServer = new AdminServer();
			_adminServer.initConfig();
		}
		return _adminServer;
	}

	// Main starts AdminServer
	public static void startup() {
		try {

			Component server = new Component();
			server.getServers().add(Protocol.HTTP, 8888);
			server.getDefaultHost().attach(AdminServer.getInstance());
			server.start();

			// start Ping Checks Thread to monitor cluster status
			PingChecks pings = new PingChecks();
			new Thread(pings).start();

			node1_sync_queue = new ConcurrentLinkedQueue<SyncRequest>();
			node2_sync_queue = new ConcurrentLinkedQueue<SyncRequest>();
			node3_sync_queue = new ConcurrentLinkedQueue<SyncRequest>();
			node4_sync_queue = new ConcurrentLinkedQueue<SyncRequest>();
			node5_sync_queue = new ConcurrentLinkedQueue<SyncRequest>();
			
			

			// start Sync Threads to Sync Changes in cluster
			Sync sync1 = new Sync("api_node_1", node1_sync_queue);
			new Thread(sync1).start();
			Sync sync2 = new Sync("api_node_2", node2_sync_queue);
			new Thread(sync2).start();
			Sync sync3 = new Sync("api_node_3", node3_sync_queue);
			new Thread(sync3).start();
			Sync sync4 = new Sync("api_node_4", node4_sync_queue);
			new Thread(sync4).start();
			Sync sync5 = new Sync("api_node_5", node5_sync_queue);
			new Thread(sync5).start();

		} catch (Exception e) {
			System.out.println(e);
		}
	}

    private static boolean isTargetOrReplica(String key, int node) {
    	int target = (key.hashCode()%5) + 1;
    	if (target == node) return true;

    	for (int i = (target+1)%5 , count = 0; count < 2; i = (i+1)%5, count++) {
            if (i == node) {
            	return true;
            }
        }
        return false;

    }
	public static void syncDocument(String key, String command) {

		try {
			AdminServer server = AdminServer.getInstance();
			SyncRequest syncObject = API.get_sync_request(key);
			syncObject.command = command;
			int my_index = server.nodeIndex(server.getMyHostname());
			System.out.println(
					"Sync Document: Key = " + key + " Value : " + syncObject.json + " Command = " + command + " " + "[host:" + server.getMyHostname()
							+ " index:" + Integer.toString(my_index) + "]" + Arrays.deepToString(syncObject.vclock));
			switch (my_index) {
			case 1:
				// if (isTargetOrReplica(key, 1)) node1_sync_queue.add( syncObject ) ;
				if (isTargetOrReplica(key, 2)) node2_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 3)) node3_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 4)) node4_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 5)) node5_sync_queue.add(syncObject);
				break;
			case 2:
				if (isTargetOrReplica(key, 1)) node1_sync_queue.add( syncObject ) ;
				//if (isTargetOrReplica(key, 2)) node2_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 3)) node3_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 4)) node4_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 5)) node5_sync_queue.add(syncObject);
				break;
			case 3:
				if (isTargetOrReplica(key, 1)) node1_sync_queue.add( syncObject ) ;
				if (isTargetOrReplica(key, 2)) node2_sync_queue.add(syncObject);
				//if (isTargetOrReplica(key, 3)) node3_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 4)) node4_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 5)) node5_sync_queue.add(syncObject);
				break;
			case 4:
			    if (isTargetOrReplica(key, 1)) node1_sync_queue.add( syncObject ) ;
				if (isTargetOrReplica(key, 2)) node2_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 3)) node3_sync_queue.add(syncObject);
				//if (isTargetOrReplica(key, 4)) node4_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 5)) node5_sync_queue.add(syncObject);
				break;
			case 5:
				if (isTargetOrReplica(key, 1)) node1_sync_queue.add( syncObject ) ;
				if (isTargetOrReplica(key, 2)) node2_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 3)) node3_sync_queue.add(syncObject);
				if (isTargetOrReplica(key, 4)) node4_sync_queue.add(syncObject);
				//if (isTargetOrReplica(key, 5)) node5_sync_queue.add(syncObject);
				break;
			}

		} catch (Exception e) {
			System.out.println(e);
		}

	}

	public static ClientResource getSyncClient(String node) {
		
		//Change this to sync with leader node at its app server port (9001-9005)
		String URL = "http://" + node + ":8888/sync";
		System.out.println( URL ) ;
		ClientResource resource = new ClientResource(URL);
		/*
		 * Create a Client with the socketTimout parameter for HttpClient and "attach"
		 * it to the ClientResource.
		 */
		Context context = new Context();
		context.getParameters().add("readTimeout", "1000");
		context.getParameters().add("idleTimeout", "1000");
		context.getParameters().add("socketTimeout", "1000");
		context.getParameters().add("socketConnectTimeoutMs", "1000");
		resource.setNext(new Client(context, Protocol.HTTP));
		// Set the client to not retry on error. Default is true with 2 attempts.
		resource.setRetryOnError(false);
		return resource;
	}
	
	
	//this stays the same, substitute leaderNodeIndex with targetIndex
	public static ClientResource getForwardingClient(int targetNodeIndex, String urlpath) {
		
		//Change this to sync with leader node at its app server port (9001-9005)
		String targetNode = nodeNameFromIndex(targetNodeIndex);
		
		String URL = "http://" + targetNode + ":9090" + urlpath;
		System.out.println( "Forwarding request to " + URL ) ;
		ClientResource resource = new ClientResource(URL);
		/*
		 * Create a Client with the socketTimout parameter for HttpClient and "attach"
		 * it to the ClientResource.
		 */
		Context context = new Context();
		context.getParameters().add("readTimeout", "1000");
		context.getParameters().add("idleTimeout", "1000");
		context.getParameters().add("socketTimeout", "1000");
		context.getParameters().add("socketConnectTimeoutMs", "1000");
		resource.setNext(new Client(context, Protocol.HTTP));
		// Set the client to not retry on error. Default is true with 2 attempts.
		resource.setRetryOnError(false);
		return resource;
	}

	/* Instance Methods */

	public void initConfig() {
		InetAddress ip;
		String hostname;
		try {

			ip = InetAddress.getLocalHost();
			hostname = ip.getHostName();
			System.out.println("IP address : " + ip);
			System.out.println("Hostname : " + hostname);
			my_ip = ip.toString();
			my_host = hostname.toString();
			Node node = new Node();
			node.id = my_host;
			node.name = "localhost";
			nodes.put(my_host, node);

			nodesNameToId.put(node.name, node.id);
			
			//Start with node 1 as partition leader
			setLeaderNodeIndex(1);
			System.out.println("SETTING LEADER AS NODE 1");
			
			

		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public String getMyHostname() {
		return this.my_host;
	}

	public int nodeIndex(String id) {

		Node n = nodes.get(id);
		String name = n.name;
		int index = 0;
		switch (name) {
		case "api_node_1":
			index = 1;
			break;
		case "api_node_2":
			index = 2;
			break;
		case "api_node_3":
			index = 3;
			break;
		case "api_node_4":
			index = 4;
			break;
		case "api_node_5":
			index = 5;
			break;
		default:
			index = 0;
		}

		return index;

	}

	
	public static String nodeNameFromIndex(int nodeIndex) {
		switch(nodeIndex) {
		case 1:
			return "api_node_1";
		case 2:
			return "api_node_2";
		case 3: 
			return "api_node_3";
		case 4:
			return "api_node_4";
		case 5:
			return "api_node_5";
		default:
			return "";
		}
	}
	
	public void registerNode(String id, String name, String admin_port, String api_port) {
		// register node name
		Node node = new Node();
		node.id = id;
		node.name = name;
		node.admin_port = admin_port;
		node.api_port = api_port;
		nodes.put(id, node);
		nodesNameToId.put(name, id);
		// System.out.println( "Register Node: " + id + " as: " + name ) ;
		
		if(node.id.contentEquals(my_host)) {
			//System.out.println("*******My node");
			setMyNodeIndex(nodeIndex(id));
		}
		
		//System.out.println("*****" + node.id + " " + my_host + " " + node.name + " " + nodeIndex(id) + " " + getMyNodeIndex());

		System.out.println("Register Node: " + id + " as: " + name + " admin port: " + admin_port + " api port: " + api_port);

	}

	public Collection<Node> getNodes() {
		return nodes.values();
	}

	public void nodeUp(String id) {
		Node node = nodes.get(id);
		node.status = "up";
		
		//Node is up, check if new leader is appointed--Lowest id will be the winner
		//Only check with the leader--if my node ID is lower, I'll be the new leader
		//System.out.println("nodeIndex(id) " + nodeIndex(id));
		//System.out.println("getLeaderNode()" + getLeaderNode());
		
		
		if(nodeIndex(id) < getLeaderNodeIndex()) {
			System.out.println("NODE " + nodeIndex(id) + " UP : NEW PARTITION LEADER");
			setLeaderNodeIndex(nodeIndex(id));
		}
		
	}

	public void nodeDown(String id) {
		Node node = nodes.get(id);
		node.status = "down";
		int leaderIndex = 6;
		//Reevaluate partition leader
		//Find lowest node number that is up for leader selection
		for(Map.Entry<String, Node> it : nodes.entrySet()){ 

			int index = nodeIndex(it.getKey());
			String status = getStatus(it.getKey());
			
			//partition1: 2 selects itself as the leader/1 selects itself as the leader
			if(index < leaderIndex && (status.equals("up") || status.contentEquals("self")))
			{
				leaderIndex = index;
			}
			
		}


		if(leaderIndex < 6) {
			if(getLeaderNodeIndex() != leaderIndex)
				System.out.println("NEW LEADER IS: " + leaderIndex ); 
			setLeaderNodeIndex(leaderIndex);
			
		}
		
	}
	

	public void nodeSelf(String id) {
		Node node = nodes.get(id);
		node.status = "self";
	}

	public String getStatus(String id) {
		Node node = nodes.get(id);
		return node.status;
	}

	//Checks if a node is reachable
	public boolean isNodeUp(String name) {
		String n_id = nodesNameToId.get(name);
		Node node = nodes.get(n_id);
		return node.status.equals("up");
	}
	
	public boolean isNodeUp(int nodeIndex) {
		String nodeName = nodeNameFromIndex(nodeIndex);
		String n_id = nodesNameToId.get(nodeName);
		Node node = nodes.get(n_id);
		return node.status.equals("up");
	}
	

	public int getLeaderNodeIndex() {
		return leaderNodeIndex;
	}

	public void setLeaderNodeIndex(int leaderNodeIndex) {
		this.leaderNodeIndex = leaderNodeIndex;
	}
	
	//Dont need this
	public int selectLeaderNode() {
		return 1;
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach("/", PingResource.class);
		router.attach("/node", NodeResource.class);
		router.attach("/sync", SyncResource.class);
		router.attach("/sync/{key}", SyncResource.class);
		return router;
	}

	public int getMyNodeIndex() {
		return myNodeIndex;
	}

	public void setMyNodeIndex(int myNodeIndex) {
		this.myNodeIndex = myNodeIndex;
	}

	public int getNodeIdForKey(String key) {
        int keyNum = Math.abs(key.hashCode()) ;
        return (keyNum % 5) + 1;
    }
    
    public int getNextNode(int nodeIndex) {
        return (nodeIndex + 1) % 5;
    }



}
