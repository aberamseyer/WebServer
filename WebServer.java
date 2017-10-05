/**
 * Implements a simple multi-threaded web server that will accept requests and responds to only the filename in the header lines. It will spawn a maximum of 30 threads for simultaneous connections to avoid overloading the host machine
 * This operates under the assumption that the method is always GET. 
 * @author Abe Ramseyer
 * 9/28/2017
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.NumberFormatException;

public final class WebServer {
	public static void main(String args[]) {
		int port = 0;
		ExecutorService pool = Executors.newFixedThreadPool(30);
		if(args.length != 1) {
			System.err.println("Usage: java WebServer port");
			System.exit(1);
		}
		try {
			port = Integer.parseInt(args[0]);
			if(port < 1024 || port > 65535) throw new NumberFormatException();
		} catch (NumberFormatException e) {
			System.err.println("ERR - arg 1");
			System.exit(1);
		}
		
		try {
			ServerSocket welcomeSocket = new ServerSocket(port); 
			System.out.println("\nListening for connections on port " + port + "..\n");

			while(true) {
				try {
					Callable<Void> request = new HttpRequest(welcomeSocket.accept());
					pool.submit(request);
				} catch (IOException e) {
					System.err.println("Error while creating thread");
				}
			}
		} catch (IOException e) {
			System.err.println("Couldn't start server");
		}
	
	}

	/**
	 * Encapsulates a single HTTP request sent by a browser and sends back an appropriate response. Can handle following file extensions: .txt .css .gif .jpg .png
	 * Implementing Callable<Void> allows this to be run multi-threaded
	 * @author Abe Ramseyer
	 * 9/28/2017
	 */
	private static class HttpRequest implements Callable<Void> {
		private static final String CRLF = "\r\n";
		private Socket socket;
	
		public HttpRequest(Socket socket) { 
			this.socket = socket;
		}
	
		/**
	     * processes an HttpRequest object, including sending the response
	     * @retunrs null every time
	     */
		@Override
		public Void call() {
			try {
				BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());
				
				String requestLine = inFromClient.readLine();

				// synchronized ensures the order of print statements won't mix with other threads
				synchronized(System.out) {
					System.out.println("---------- Begin client request header -----");
					System.out.println(requestLine);
	
					String headerLine = null;
					while((headerLine = inFromClient.readLine()).length() != 0) {
						System.out.println(headerLine);
					}
					System.out.println("----------- End client header------------\n\n");
				}	

				StringTokenizer tokens = new StringTokenizer(requestLine);
				String method = tokens.nextToken();
				if(!method.equals("GET")) { // verify the method is GET
					System.err.println("Unsupported method request " + method);	
					//throw new IOException(); // TODO change to return; ?
					return null;
				}  
				String fileName = tokens.nextToken();
				fileName = "." + fileName;
				if(fileName.endsWith("/")) // remove a trailing '/' character
					fileName = fileName.substring(0, fileName.length()-1);

				// Open the requested file
				FileInputStream file = null;
				boolean fileExists = true;
					
				try {
					file = new FileInputStream(fileName);
				} catch (FileNotFoundException e) {
					fileExists = false;
				} // TODO retry with a preceeding "/" or redirect to "/index.html"
				  // disallow some illgegal paths, i.e. "..." in them
				
				
				// Construct the response message
				String statusLine = null;
				String contentTypeLine = null;
				String entityBody = null;
				// normal response
				if(fileExists) {
					statusLine = "HTTP/1.1 200 OK";
					contentTypeLine = "Content-type: " + contentType(fileName);
				// document not found, constant 404 page
				} else {
					statusLine = "HTTP/1.1 404 Not Found";
					contentTypeLine = "text/html";
					entityBody = "<!DOCTYPE html>\n" +
								 "<HTML>\n" +
								 "<HEAD>\n" +
								 "<TITLE>404 Not Found</TITLE>\n" +
								 "</HEAD>\n" +
								 "<BODY>The requested file could not be found on the server. Click <a href=\"./index.html\">here</a> to go to home page</BODY>\n" +
								 "</HTML>";
				}
	
	
				// Send the responses
				outToClient.writeBytes(statusLine);
				outToClient.writeBytes(CRLF);
				outToClient.writeBytes(contentTypeLine);
				outToClient.writeBytes(CRLF + CRLF); // doesn't work without 2 for some reason

				// log the header that was sent to client
				// synchronized ensures the order of print statements won't mix with other threads
				synchronized(System.out) {
					System.out.println("---------- Begin server response header ------");
					System.out.println(statusLine);
					System.out.println(contentTypeLine);
					System.out.println("---------- End server response header --------\n\n");
				}
				if(fileExists) {
					try {
						sendBytes(file, outToClient); // handle exceptions thrown by .read() and .write()
					file.close();
					System.out.println("Sent file " + fileName + " to " + socket.getInetAddress() + ":" + socket.getPort() + "\n");
					} catch (Exception e) {
						System.err.println("Exception while reading/writing file");
					}
				} else {
					// entityBody will hold the 404 page if the file doesn't exist
					outToClient.writeBytes(entityBody);
				}
	
				// close data streams
				inFromClient.close();
				outToClient.close();
			
	
	
			} catch (IOException e) {
				System.err.println("Error while sending resposne");
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					// Give up handling exceptions, something pretty bad happened
				}
			}	
		
			// necessary for implemented method
			return null;
		}
	
		/*
	 	 * determines a the content type of the file request based on file extension
	 	 */
		private static String contentType(String file) {
			if(file.endsWith(".html") || file.endsWith(".html"))
				return "text/html";
			else if(file.endsWith(".css"))
				return "text/css";
			else if(file.endsWith(".js"))
				return "text/javascript";
			else if(file.endsWith(".gif"))
				return "image/gif";
			else if(file.endsWith(".jpg") || file.endsWith(".jpeg"))
				return "image/jpeg";
			else if(file.endsWith(".png"))
				return "image/png";
			
			return "unknown"; // TODO change this
		}		
		
		/*
	 	 * writes the contents of a specified file out to the the client
	 	 */
		private static void sendBytes(FileInputStream file, DataOutputStream outToClient) throws Exception {
			byte[] buffer = new byte[1024];
			int bytes = 0;
			// copy requested file into the socket's output stream
			while((bytes = file.read(buffer)) != -1) {
				outToClient.write(buffer, 0, bytes);
			}	
		}
	}
}
