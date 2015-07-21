package multiscratch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * this class will start the webserver. From the webserver, commands will arrive to register a new player or check current status (poll)
 * this class will then
 * @author Bart Huylebroeck, CoderDojo Antwerpen Centrum
 *
 */
public class ScratchMPModel {
	
	public static final int PORTNUMBER = 8000;
	
	private List<ScratchCommandListener> eventListeners;
	private HttpServer webserver;
	private Map<String, String> messages;
	private URL upstreamServer = null;
	
	public ScratchMPModel(int listenPort) throws IOException {
		
		messages = new HashMap<String, String>(50);
		eventListeners = new LinkedList<ScratchCommandListener>();
		webserver = HttpServer.create(new InetSocketAddress(PORTNUMBER), 0);
		webserver.createContext("/", new HttpAdapter());
		webserver.setExecutor(new ThreadPoolExecutor(5, 5, 1, TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>(10)));
        webserver.start();
        log("webserver listening to " + webserver.getAddress().toString());

		
	}
	
	/**
	 * this method will set a URL of the upstream server. All messages set from this server will also be forwarded to the upstream server.
	 * Once this method has set a server URL, a thread is created to poll the upstream server for updates.
	 * @TODO If you call this method several times, each with a valid server address, you will have created multiple threads that each poll
	 * the most recent upstream server specified. Not sure how to avoid that somewhat elegantly.
	 * Hack for now: try to connect to an invalid server; this will set the instance variable to null and stop the thread.
	 */
	public boolean connectToServer(String server) {
		try {
			URL dest = new URL("http", server, PORTNUMBER, "/");
			upstreamServer = dest;
			Thread t = new Thread(new Runnable() {public void run() {try {while(upstreamPoll()) Thread.sleep(30);} catch (InterruptedException ie) {}}});
			t.start();
			return true;
		}
		catch (IOException ioe) {
			log("error: connecting to " + server + ": " + ioe.getMessage());
			upstreamServer = null;
			return false;
		}
	}

	public void addListener(ScratchCommandListener l) {
		if (l != this) {
			eventListeners.add(l);
			log("Added 1 listener\n");
		}
	}
	
	public void log(String msg) {
		for (Iterator<ScratchCommandListener> i=eventListeners.iterator(); i.hasNext(); i.next().log(msg));
	}
	
	/**
	 * this class will take URI's from the HttpServer and divert them to the correct protected method in our own class
	 */
	private class HttpAdapter implements HttpHandler {
		public void handle(HttpExchange t) throws IOException {
			String method[] = t.getRequestURI().toString().split("/");
			String command = method[1];
			
			if (command.equals("writemessage")) {
				writeMessage(t, method);
			}
			else if (command.equals("poll")) {
				poll(t);
			}
			else if (command.equals("reset_all")) {
				resetAll(t);
			}
			else if (command.equals("crossdomain.xml")) {
				crossDomain(t);
			}
			else if (command.equals("nrmessages")) {
				nrMessages(t);
			}
			else {
				log("incorrect call: " + t.getRequestURI());
				byte[] response = "<HTML><BODY>message not found</BODY></HTML>".getBytes();
		        t.sendResponseHeaders(404, response.length);
		        OutputStream os = t.getResponseBody();
		        os.write(response);
		        os.close();
			}
			
			t.close();
		}
	}
	
	/**
	 * if connected to another server, this method will poll that server in order to get the latest values for all messages in that server.
	 * The method is called from a thread which was created during the connectToServer method call
	 * @return
	 */
	protected boolean upstreamPoll() {
		if (upstreamServer == null) return false;
		
		try {
			URL polly = new URL(upstreamServer, "/poll");
			URLConnection polcon = polly.openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(polcon.getInputStream()));
			String line = in.readLine();
			synchronized (messages) {
				while (line != null) {
					String[] msg = line.split(" ");
					if (msg.length != 2) log ("couldn't parse this: " + line);
					messages.put(msg[0], msg[1]);
				}
				//I might have parsed one line that isn't really a message, so I'm deleting that one again:
				messages.remove("nrmessages");
			}
			return true;
		}
		catch (IOException ioe) {
			log("error while polling upstream (poll is cancelled): " + ioe.getMessage());
			return false;
		}
	}

	/**
	 * according to the Scratch extension document, flash will check this file to make sure it is allowed to make extra http connections
	 */
	protected void crossDomain(HttpExchange t) throws IOException {
		log("returning cross domain policy");
		String response = "<cross-domain-policy> <allow-access-from domain=\"*\" to-ports=\"" + ScratchMPModel.PORTNUMBER + "\"/> </cross-domain-policy>\0";
        t.sendResponseHeaders(200, response.getBytes().length);
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
	}
	
	/**
	 * scratch reported block to count the messages currently in the server
	 */
	protected void nrMessages(HttpExchange t) throws IOException {
		log("incorrect call: " + t.getRequestURI());
		byte[] response = Integer.toString(messages.size()).getBytes();
        t.sendResponseHeaders(200, response.length);
        OutputStream os = t.getResponseBody();
        os.write(response);
        os.close();
	}

	/**
	 * Scratch will call this method at 30fps to update all its reporter blocks.
	 * downstream servers will also poll our server to get updates from everyone.
	 */
	protected void poll(HttpExchange t) throws IOException {
		StringBuffer res = new StringBuffer();
		synchronized(messages) {
			res.append("nrmessages ");
			res.append(messages.size());
			res.append("\n");
			Iterator<String> it = messages.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next();
				res.append("readmessage/");
				res.append(key);
				res.append(" ");
				res.append(messages.get(key));
				res.append("\n");
			}

		}
		byte response[] = res.toString().getBytes();
		t.sendResponseHeaders(200, response.length);
		t.getResponseBody().write(response);
		t.close();
	}
	
	/**
	 * called by Scratch whenever the "stop" button is pushed. I'm not sure that we should take action on it, because during multiplayer some
	 * players might start & stop their machines at inconvenient times.
	 */
	protected void resetAll(HttpExchange t) throws IOException {
		messages.clear();
        t.sendResponseHeaders(200, 0);
        t.close();
	}

	/**
	 * This scratch command block will create a new or overwrite an existing message.
	 * Once created, there is no real way to delete a message.
	 */
	protected void writeMessage(HttpExchange t, String method[]) throws IOException {
		log("writing a message");
		if (method.length == 4) {
			String msg = method[2];
			String msgBody = method[3];
			if (!msgBody.equals(messages.get(msg))) {
				//put locally
				synchronized (messages) {
					messages.put(msg, msgBody);
				}
				//push to upstreamserver
				if (upstreamServer != null) {
					try {
						log("going upstream ...");
						URL writeURL = new URL(this.upstreamServer, t.getRequestURI().toString());
						URLConnection conn = writeURL.openConnection();
						conn.getInputStream().skip(conn.getContentLengthLong());
						log("done upstream ...");
					}
					catch (IOException ioe) {
						log("error pushing upstream: " + ioe.getMessage());
						upstreamServer = null;
					}
					
				}
			}
			t.sendResponseHeaders(200, 0);
		}
		else {
			byte[] response = ("<HTML><BODY>incorrect (" + method.length + ") number of arguments for readmessage (please supply message name and message)</BODY></HTML>").getBytes();
	        t.sendResponseHeaders(400, response.length);
	        OutputStream os = t.getResponseBody();
	        os.write(response);
	        os.close();
		}

	}
	
}
