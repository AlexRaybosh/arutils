/*
 * Copyright (c) 2009-2015, Alex Raybosh
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 * http://www.gnu.org/licenses/lgpl-3.0.html  
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 */

package arutils.db.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import arutils.async.AsyncEngine;
import arutils.util.ByteArrayHolder;
import arutils.util.ByteBufferHolder;
import arutils.util.Utils;

import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

interface ReplyOutputStream {
	ReplyOutputStream write(String str);
	ReplyOutputStream write(Object conId);
}
class BHStream implements ReplyOutputStream {
	final  ByteBufferHolder bh;
	BHStream(ByteBufferHolder bh) {this.bh=bh;}
	public ReplyOutputStream write(String str) {
		bh.putBytes(str.getBytes(Utils.UTF8));
		return this;
	}
	@Override
	public ReplyOutputStream write(Object obj) {
		if (obj!=null) write(obj.toString());
		return this;
	}
};
class GetRequest {
	enum State {START,IN};
	List<String> path=new ArrayList<>();
	Map<String,String> params=new HashMap<>();
	public GetRequest(String line) {
		State state=State.START;
		StringBuilder buf=new StringBuilder();
		int paramPos=line.length();
		BODY: for (int i=0;i<line.length();++i) {
			char c=line.charAt(i);
			switch (state) {
			case START:
				if (c=='/') continue;
				buf.append(c);
				state=State.IN;
				break;
			case IN:
				if (c=='/') {
					state=State.START;
					path.add(buf.toString());
					buf.setLength(0);
				} else if (c=='?') {
					path.add(buf.toString());
					buf.setLength(0);
					paramPos=i+1;
					break BODY;
				} else {
					buf.append(c);
				}
				break;
			default:
				break;
			}
		}
		if (buf.length()>0) path.add(buf.toString());
		if (paramPos<line.length()) {
			for (String p : line.substring(paramPos).split("&")) {
				fillParam(params,p);
			}
		}
	}
	
	private static void fillParam(Map<String, String> params, String line) {
		for (int i=0;i<line.length();++i) {
			char c=line.charAt(i);
			if (c=='=') {
				String key=line.substring(0, i);
				String val=line.substring(i+1);
				params.put(key.trim(), val.trim());
				return;
			}
		}
		params.put(line.trim(),"");
	}
}

class EndPoint {
	static final byte[] FULL_HEADER="HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ".getBytes();
	enum State {
		READING_REQUEST, WRITING_REPLY, STREAMING_REPLY, CLOSED
	}
	State state=State.READING_REQUEST;
	
	final static int N=256;
	private int MAX_STREAMING_REPLIES_BUF = 10000;
	
	ByteBuffer inBuffer = ByteBuffer.allocate(N);
	ByteArrayHolder inRawRequest;
	ByteBufferHolder outReply=new ByteBufferHolder(1024);
	ByteBuffer outHeaderBuffer = ByteBuffer.wrap(FULL_HEADER);
	ByteBuffer outContentLengthBuffer = ByteBuffer.allocate(16);
	private SelectionKey key;
	private DBProfilerWebServer server;
	
	public EndPoint(DBProfilerWebServer server, SelectionKey clientKey) {
		this.server=server;
		key=clientKey;
	}
	public void process() throws IOException {
//		System.out.println("process : "+state+" isReadable="+key.isReadable()+"\tisWritable="+key.isWritable());
		try {
			if (key.isReadable()) {
				SocketChannel client = (SocketChannel) key.channel();
				int size=client.read(inBuffer);
	//			System.out.println("READ "+size+" bytes");
				if (size==0)
					return;
				if (-1==size) {
					//System.out.println("Client cancel");
					close();
					return;
				}
				inBuffer.flip();
				
				if (state!=State.READING_REQUEST) {
					System.out.println("Swallow inbound "+size+" bytes, current state "+state);
				} else {
					if (inRawRequest==null)
						inRawRequest=new ByteArrayHolder(256);
					inRawRequest.pushBytes(inBuffer.array(), inBuffer.position(), inBuffer.limit());
					checkCompletion();
				}	
			}
			if (!key.isValid()) {close();return;}
			if (key.isWritable()) {
//				System.out.println("Client is writable : "+state);
				switch (state) {
				case WRITING_REPLY:
					writeFull();
					break;
				case STREAMING_REPLY:
					writeStream();
				default:
					break;
				}
			}
		} catch (Throwable e) {
			//e.printStackTrace();
			close();
		}
	}
	boolean streamHeaderDone=false;
	static byte[] streamHeaderBytes11=("HTTP/1.1 200 OK\r\n" + 
			"Content-Type: text/plain; charset=utf-8\r\n" + 
			"Transfer-Encoding: chunked\r\n" + 
			"\r\n").getBytes(); 
	static byte[] streamHeaderBytes10=("HTTP/1.0 200 OK\r\n" + 
			"Content-Type: text/plain; charset=utf-8\r\n" + 
			"\r\n").getBytes();
	
	ByteBuffer streamCurrent=null,streamChunkSize=null,chunkCrln=null;

	private int httpVersion=1;
	
	ArrayDeque<byte[]> streamingReplies=new ArrayDeque<byte[]>();
	boolean chunkCompleted;

	private boolean streamingShowArgs;
	private int streamingMaxArgsLength;

	private byte[] takeNextStreamChunk() {
		synchronized (server.getStreamingLock()) {
			return streamingReplies.pollFirst();
		}
	}
	public boolean addStreamMessages(TreeMap<Long, QData> buf) {
		for (Entry<Long, QData> e  : buf.entrySet()) {
			if (streamingReplies.size()>MAX_STREAMING_REPLIES_BUF) {
				System.err.println("MAX_STREAMING_REPLIES_BUF LIMIT");
				//close();
				return false;
			}
			byte[] msg=server.convertToStreamMessage(e.getKey(),e.getValue(),streamingShowArgs, streamingMaxArgsLength);
			streamingReplies.addLast(msg);	
		}
		return true;
	}
	public void makeWritable() {
		if (key.isValid())
				key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
	}
	
	private void writeStream() throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		for (;;) {
			if (!streamHeaderDone) {
				if (streamCurrent==null)
					streamCurrent=ByteBuffer.wrap(httpVersion==0?streamHeaderBytes10:streamHeaderBytes11);
				int len=client.write(streamCurrent);
				if (len<0) {close();break;}
				if (!streamCurrent.hasRemaining()) {
					streamHeaderDone=true;
					streamCurrent=null;
					chunkCompleted=true;
				}
				if (len==0) break;
				continue;
			} else {
				if (streamCurrent==null || chunkCompleted) {
					byte[] reply=takeNextStreamChunk();
					if (reply==null) {
						key.interestOps(SelectionKey.OP_READ); // suspend writeability
						break;
					}
					chunkCompleted=false;
					
					streamChunkSize=ByteBuffer.wrap((Integer.toHexString(reply.length)+"\r\n").getBytes() );
					streamCurrent=ByteBuffer.wrap(reply);
					chunkCrln=ByteBuffer.wrap("\r\n".getBytes());

				}
				long len=client.write(httpVersion==0?new ByteBuffer[]{streamCurrent}: new ByteBuffer[]{streamChunkSize, streamCurrent, chunkCrln} );
				if (len<0) {close();break;}

				
				if ( (httpVersion==0 && !streamCurrent.hasRemaining() ) || 
					 (httpVersion==1 && !chunkCrln.hasRemaining()     ) ) 
				{
					chunkCompleted=true;
					continue;
				}
				
				if (len==0) break;
			}
				
		}
		
	}


	private void writeFull() throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		for (;;) {
			ByteBuffer current=outHeaderBuffer;
			if (!current.hasRemaining()) current=outContentLengthBuffer;
			if (!current.hasRemaining()) current=outReply.getByteBuffer();
			
			//int dpos=current.position();
			
			int len=client.write(current);
			if (len<0) {
				close();
				return;
			}
			
			/*for (int i=dpos;i<dpos+len;++i) {
				byte b=current.array()[i];
				if (b==10 || b==13) System.out.println("\n0x"+Integer.toHexString((int)b));
				else System.out.print((char)b);
			}
			*/
			
			if (!outReply.getByteBuffer().hasRemaining()) {
				state=State.READING_REQUEST;
				key.interestOps(SelectionKey.OP_READ);
				return;
			}
			if (len==0)
				break;
		}
	}
	private void checkCompletion() {
		int pos=inRawRequest.position();
		if (pos>=4) {
			int last=inRawRequest.getIntAt(pos-4);
			if (last==0x0d0a0d0a) {
				processFullInRequest();
			}
		}		
	}
	private void processFullInRequest() {
		try {
			byte[] arr = inRawRequest.array();
			String line=null;
			for (int i=0;i<arr.length;++i) {
				if (arr[i]==0xd || arr[i]==0xa) {
					line=new String(arr,0,i);
					break;
				}
			}
			inRawRequest.clear();
			if (line==null) {
				close();
				return;
			}
			line=URLDecoder.decode(line,"UTF-8");
		
			outReply.clear();
			Reply reply=server.handleRequest(this,line,new BHStream(outReply));
			switch (reply.kind) {
			case ERROR:
				close();
				break;
			case FULL:
				outReply.flip();
				outHeaderBuffer.clear();
				outContentLengthBuffer.clear();
				String lengthString=""+outReply.getByteBuffer().limit();
				outContentLengthBuffer.put(lengthString.getBytes());
				outContentLengthBuffer.put("\r\n\r\n".getBytes());
				outContentLengthBuffer.flip();
				key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				state=State.WRITING_REPLY;
				break;
			case STREAM:
				server.addStreamer(key);
				key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				state=State.STREAMING_REPLY;
			default:
				break;
			}
		} catch (Throwable t) {
			t.printStackTrace();
			close();
		}
	}
	void close() {
		if (state==State.CLOSED)
			return;
		server.removeEndPoint(key);
		state=State.CLOSED;
		try {key.channel().close();} catch (IOException e) {}
		key.cancel();
	}
	public void setHttpVersion(int v) {
		httpVersion=v;
		
	}
	public void setStreamingParams(int slow_disconnect_on, boolean showArgs, int maxArgsLength) {
		MAX_STREAMING_REPLIES_BUF=slow_disconnect_on;
		streamingShowArgs=showArgs;
		streamingMaxArgsLength=maxArgsLength;
	}

}

final class Reply {
	enum Kind {ERROR, FULL, STREAM}
	Kind kind;
	String mimeType;
	public Reply(Kind kind) {
		this.kind = kind;
		mimeType="text/html";
	}
	public Reply(Kind kind,String m) {
		this.kind = kind;
		mimeType=m;
	}	
}

final class QData {
	Object conId;
	final String sql; 
	final Object[] args;
	final long start=System.currentTimeMillis();
	long completionTime;
	boolean success;
	SQLException sex;
	public QData(Object conId, String sql, Object[] args) {
		this.conId = conId;
		this.sql = sql;
		this.args = args;
	}

	public void setCompletion(boolean success,	SQLException sex) {
		this.completionTime=System.currentTimeMillis();
		this.success=success;
		this.sex=sex;
	}

	public String printArgs(boolean html) {
		if (args!=null && args.length>0) {
			return U.printRow(args);	
		} else if (sex instanceof ExtendedSQLException) {
			List<Object[]> bulk = ((ExtendedSQLException)sex).getBulkArgs();
			if (bulk!=null && bulk.size()>0) {
				StringBuilder sb=new StringBuilder();
				for (Object[] row : bulk) {
					sb.append("{");
					sb.append(U.printRow(row));
					sb.append("}").append(html?"<br>\n":"/n");
				}
				return sb.toString();
			}
		}
		return "";
	}

	public String getConnectionIdString() {
		if (conId==null) 
			return "NOT YET KNOWN";
		return conId.toString();
	}

	public void setConnectionInfo(Object conId) {
		this.conId=conId;
		
	}

	public boolean hasArgs() {
		return args!=null && args.length>0;
	}
}

public class DBProfilerWebServer implements Profiler {

	protected static volatile int MAX_HIST = 100;
	static DBProfilerWebServer server=new DBProfilerWebServer();
	


	public Object getStreamingLock() {
		return streamers;
	}

	public void removeEndPoint(SelectionKey key) {
		endPoints.remove(key);
		synchronized (getStreamingLock()) {
			streamers.remove(key);
		}
	}
	
	public void addStreamer(SelectionKey key) {
		synchronized (getStreamingLock()) {
			streamers.add(key);
//			EndPoint e = endPoints.get(key);
		}
		
	}
	Map<SelectionKey,EndPoint> endPoints=new HashMap<>();
	Set<SelectionKey> streamers=new HashSet<SelectionKey>();
	TreeMap<Long,QData> streamersBuf=new TreeMap<>();
	
	Selector selector;
	ServerSocketChannel serverSocketChannel;
	private SelectionKey serverKey;
	
	
	private void bind(int portStart, int portEnd) throws IOException {
		boolean up=portEnd>portStart;
		int currentPort=portStart;
		String shost=System.getenv("DB_PROFILER_WEB_HOST");
		if (shost==null) shost="localhost";
		for (;;) {
			try {
				InetSocketAddress hostAddress = "*".equals(shost)?new InetSocketAddress(currentPort) : new InetSocketAddress(shost, currentPort);
				serverSocketChannel.bind(hostAddress);
				printServerPort(shost,currentPort);
				return;
			} catch (IOException iox) {
				if (currentPort==portEnd)
					throw iox;
				currentPort=currentPort+(up?1:-1);
			}
		}
	}
	private void printServerPort(String host, int currentPort) {
		String method=System.getenv("DB_PROFILER_WEB_PORT_PRINT");
		if ("stdout".equalsIgnoreCase(method)) {
			System.out.println("DB_PROFILER LISTENING "+host+":"+currentPort);
		} else if ("stderr".equalsIgnoreCase(method)) {
			System.err.println("DB_PROFILER LISTENING "+host+":"+currentPort);
		}  
	}

	void init(int portStart, int portEnd) throws IOException {
		selector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		bind(portStart,portEnd);
		serverSocketChannel.configureBlocking(false);
		int ops = serverSocketChannel.validOps();
		serverKey = serverSocketChannel.register(selector, ops, null);
	}


	
	void loop() throws IOException {
		boolean hasConnected=false;
		for (;;) {
			if (hasConnected) {
				if (endPoints.size()==0) {
					hasConnected=false;
					lastDisconnected();
				}
			} else {
				if (endPoints.size()>0) {
					hasConnected=true;
					firstConnected();
				}
			}
			//System.err.println("Waiting for select : "+endPoints.size());
			//int numOfKeys = 
			if (Thread.interrupted()) {
				System.err.println("DB Profiler has been interrupted");
				return;
			}
			selector.select();
			//System.out.println("Number of selected keys: " + noOfKeys);
			for (Iterator<SelectionKey> it=selector.selectedKeys().iterator(); it.hasNext();) {
				SelectionKey key=it.next();
				if (!key.isValid()) {
					EndPoint endPoint=endPoints.get(key);
					if (endPoint!=null) {
						endPoint.close();
						continue;
					}
				}
				if (serverKey==key) {
					if (key.isAcceptable()) {
						SocketChannel ch = serverSocketChannel.accept();
						ch.configureBlocking(false);
						SelectionKey clientKey=ch.register(selector, SelectionKey.OP_READ);
						//System.out.println("Accepted new connection from client: " + ch);
						EndPoint endPoint=new EndPoint(this, clientKey);
						endPoints.put(clientKey, endPoint);
					}
				} else {
					EndPoint endPoint=endPoints.get(key);
					if (endPoint!=null) {
						endPoint.process();
					} else {
						System.err.println("Unknown key "+key);
					}
				}
				it.remove();
			}
			distibuteToStreamers();
		}
	}



	public void start(final int portStart,final int portEnd) {
		/*Thread t=new Thread() {
			public void run() {
				try {runMe(portStart,portEnd);} catch (Throwable tt) {
					tt.printStackTrace();
					
				}
			}
		};
		t.setDaemon(true);
		t.start();
		*/
		AsyncEngine.getEngineExecutorService().execute(new Runnable() {
			@Override
			public void run() {
				try {runMe(portStart,portEnd);} catch (Throwable tt) {
					tt.printStackTrace();	
				}
			}
		});
	}

	protected void runMe(int portStart,int portEnd) throws IOException {
		init(portStart,portEnd);
		loop();
	}


	public Reply handleRequest(EndPoint endPoint, String line, ReplyOutputStream ss) {
		//System.out.println(line);
		String[] args=line.split("\\s+");
		if (args.length!=3) {
			return new Reply(Reply.Kind.ERROR);
		}
		if (!"GET".equals(args[0])) {
			return new Reply(Reply.Kind.ERROR);
		}
		String rawReq=args[1];
		//System.out.println("REQUEST: "+ rawReq);
		GetRequest req=new GetRequest(rawReq);
		
		if ("HTTP/1.0".equals(args[2]))
			endPoint.setHttpVersion(0);
				
			
		if (req.path.size()==0) {
			return handleDefault(ss);
		} else if (showInFlight(req)) {
			return handleInFlight(req.params,ss);
		} else if (isShowRecent(req)) {
			return handleRecent(req.params,ss);
		} else if (isStream(req)) {
			return handleStream(endPoint,req.params,ss);
		} else if (isFavicon(req)) {
			return handleFavicon(req.params,ss);
		} else if (isTest(req)) {
			return handleFavicon(req.params,ss);
		} else {
			return handleUnknown(line,req,ss);
		}
	}

	private boolean isTest(GetRequest req) {
		return req.path.size()==1 && "test".equals(req.path.get(0));
	}
	private boolean isStream(GetRequest req) {
		return req.path.size()==1 && "stream".equals(req.path.get(0));
	}

	private boolean isFavicon(GetRequest req) {
		return req.path.size()==1 && "favicon.ico".equals(req.path.get(0));
	}

	private static boolean showInFlight(GetRequest req) {
		return req.path.size()==1 && "inflight".equals(req.path.get(0));
	}
	private static boolean isShowRecent(GetRequest req) {
		return req.path.size()==1 && "recent".equals(req.path.get(0));
	}
	
	private Reply handleStream(EndPoint endPoint, Map<String, String> params, ReplyOutputStream ss) {
		int slow_disconnect_on=10000,  maxArgsLength=100;
		if (params.containsKey("slow_disconnect_on")) {try {slow_disconnect_on=Integer.parseInt(params.get("slow_disconnect_on"));} catch (Exception e) {}}
		if (params.containsKey("maxargs")) {try {maxArgsLength=Integer.parseInt(params.get("maxargs"));} catch (Exception e) {}}
		boolean showArgs=params.containsKey("showargs");
		endPoint.setStreamingParams(slow_disconnect_on,showArgs,maxArgsLength);
		return new Reply(Reply.Kind.STREAM);
	}

	private Reply handleUnknown(String line, GetRequest req, ReplyOutputStream ss) {
		ss.write("<!DOCTYPE html><html><head><title>DB Monitor</title><style></style></head><body>");
		ss.write("Can't handle ").write(line);
		ss.write("</body></html>");
		return new Reply(Reply.Kind.FULL);
	}

	private Reply handleFavicon(Map<String, String> params, ReplyOutputStream ss) {
		ss.write("Screw You");
		return new Reply(Reply.Kind.FULL,"image/x-icon");
	}
	
	private Reply handleDefault(ReplyOutputStream ss) {
		ss.write("<!DOCTYPE html><html><head><title>DB Monitor</title><style></style></head><body>");
		ss.write("<p><a href=\"inflight\" target=\"iframe_inflight\"><b>In Flight Queries</b></a>");
		ss.write("<a href=\"inflight?showargs&maxargs=400\" target=\"iframe_inflight\">\t<b>(With Args)</b></a>");
		ss.write("<a href=\"inflight?showargs&maxargs=100&refresh=1\" target=\"iframe_inflight\">\t<b>(Autorefresh)</b></a></p>");
		ss.write("<iframe src=\"inflight\" width=\"100%\" height=\"500px\" name=\"iframe_inflight\"></iframe>");
		
		ss.write("<br><p><a href=\"stream?slow_disconnect_on=10000&showargs&maxargs=100\" target=\"_blank\"><b>Stream History</b></a><br></p>");
		
		ss.write("<p><a href=\"recent\" target=\"iframe_recent\"><b>Recent Queries</b></a>");
		ss.write("<a href=\"recent?showargs&maxargs=400\" target=\"iframe_recent\">\t<b>(With Args)</b></a>");
		ss.write("<a href=\"recent?showargs&maxargs=100&refresh=1\" target=\"iframe_recent\">\t<b>(Autorefresh)</b></a></p>");
		ss.write("<iframe src=\"recent\" width=\"100%\" height=\"800px\" name=\"iframe_recent\"></iframe>");
		
		ss.write("</body></html>");
		return new Reply(Reply.Kind.FULL);
	}
	private Reply handleInFlight(Map<String, String> params, ReplyOutputStream ss) {
		Map<Long,QData> inFlightCopy;
		synchronized (this) {
			inFlightCopy=new TreeMap<>(inFlight);
		}
		int maxSqlLength=100, maxArgsLength=10000;
		if (params.containsKey("maxsql")) {try {maxSqlLength=Integer.parseInt(params.get("maxsql"));} catch (Exception e) {}}
		if (params.containsKey("maxargs")) {try {maxArgsLength=Integer.parseInt(params.get("maxargs"));} catch (Exception e) {}}
		float refresh=-1;
		if (params.containsKey("refresh")) {try {refresh=Float.parseFloat(params.get("refresh"));} catch (Exception e) {}}
		reply(ss,false, params.containsKey("showargs"),inFlightCopy,maxSqlLength,maxArgsLength,refresh);
		return new Reply(Reply.Kind.FULL);
	}

	private Reply handleRecent(Map<String, String> params, ReplyOutputStream ss) {
		Map<Long,QData> recent=history.get();
		int maxSqlLength=100, maxArgsLength=100;
		if (params.containsKey("maxsql")) {try {maxSqlLength=Integer.parseInt(params.get("maxsql"));} catch (Exception e) {}}
		if (params.containsKey("maxargs")) {try {maxArgsLength=Integer.parseInt(params.get("maxargs"));} catch (Exception e) {}}
		float refresh=-1;
		if (params.containsKey("refresh")) {try {refresh=Float.parseFloat(params.get("refresh"));} catch (Exception e) {}}
		reply(ss,true, params.containsKey("showargs"),recent,maxSqlLength,maxArgsLength,refresh);
		return new Reply(Reply.Kind.FULL);
	}
	
	
	private void reply(ReplyOutputStream ss, boolean isHistory, boolean withArgs, Map<Long, QData> data, int maxSqlLength, int maxArgsLength, float refresh) {	
		long now=System.currentTimeMillis();
		ss.write("<!DOCTYPE html><html><head><title>DB Monitor</title>");
		if (refresh>0) ss.write("<META HTTP-EQUIV=\"refresh\" CONTENT=\""+refresh+"\">");
		ss.write("<style>\n" + 
				"table, th, td {border: 1px solid black;\nborder-collapse: collapse;}\n"
				+ "table#t01 tr:nth-child(even) {\n" + 
				"    background-color: #eee;\n" + 
				"}\n" + 
				"table#t01 tr:nth-child(odd) {\n" + 
				"   background-color:#fff;\n" + 
				"}\n" + 
				"table#t01 th	{\n" + 
				"    background-color: black;\n" + 
				"    color: white;\n" + 
				"}" + 
				"</style></head><body>");
		ss.write("<table id=\"t01\" style=\"width:100%\">\n");
		ss.write("<col style=\"width:10%\">\n" + 
				"<col style=\"width:10%\">\n" + 
				"<col style=\"width:15%\">\n" +
				"<col style=\"width:10%\">\n");
				/*(withArgs?"<col style=\"width:50%\"><col style=\"width:20%\">\n":"<col style=\"width:70%\">")
				);*/
		String th="<thead><tr>\n" + 
		"    <th>#</th>\n" + 
		"    <th>con</th>\n" + 
		"    <th>start</th> \n" + 
		"    <th>ms</th>\n" + 
		"    <th>sql</th>\n" + 
		(isHistory?"<th>Error</th>\n":"") +
		(withArgs?"    <th>Args</th>\n":"") +
		"  </tr></thead><tbody>";
		ss.write(th);
		for (Long tick : data.keySet()) {
			QData q =data.get(tick);
			String bgEnd=(isHistory && !q.success)?" bgcolor=\"#FF0000\">":">";
			ss.write("<tr>");
			ss.write("<td").write(bgEnd).write("<samp>").write(tick).write("</samp>").write("</td>");
			String c=q.getConnectionIdString();
			ss.write("<td").write(bgEnd).write(c).write("</td>");
			ss.write("<td").write(bgEnd).write(Utils.formatLocalDateTime(new Date(q.start))).write("</td>");
			ss.write("<td").write(bgEnd).write((isHistory?q.completionTime:now)-q.start).write("</td>");
			ss.write("<td").write(bgEnd).write("<samp>").write( (q.sql.length()>maxSqlLength? (q.sql.substring(0, maxSqlLength)+" ..."):q.sql)).write("</samp>").write("</td>");
			if (isHistory) {
				ss.write("<td>").write(getErrorMsg(q.success, q.sex)).write("</td>");
			}
			if (withArgs) {
				String a=q.printArgs(true);// Utils.csvList(q.args);
				//ss.write("<td>").write("<samp>").write(a.length()>maxSqlLength?(a.substring(0, maxArgsLength)+" ..."):a).write("</samp>").write("</td>");
				ss.write("<td>").write("<samp>").write(U.limit(a,maxArgsLength)).write("</samp>").write("</td>");
			}
			ss.write("</tr>");
		}
		ss.write("</tbody></table>");
		ss.write("</body></html>");
	}
	
    public static String getErrorMsg(boolean success, SQLException ex) {
    	if (success) return "";
    	if (ex==null)
    		return "NON SQL ERROR";
    	Throwable last=ex;
    	Set<Throwable> visited=new HashSet<Throwable>();
    	for (int i=0;i<100;++i) {
    		Throwable next=last.getCause();
    		if (next==null || visited.contains(next))
    			break;
    		visited.add(next);
			last=next;
    	}
    	StackTraceElement[] trace = last.getStackTrace();
    	StringBuilder sb=new StringBuilder("<p><samp>");
    	
		StringBuilder s=new StringBuilder();
		ExtendedSQLException.print(s, 100, ex);
		
    	sb.append(s.toString()).append("<br>");
    	if (trace!=null) {
    		if (last!=ex) {
    			sb.append("Caused by: ").append(last.getMessage()).append("<br>");
    		}
    		for (StackTraceElement e : trace)
    			sb.append("&nbsp;&nbsp;&nbsp;&nbsp;").append(e).append("<br>");
    		
    	}
    	sb.append("</samp></p>");
    	return sb.toString();
	}
	
	Map<Long,QData> inFlight=new HashMap<>();
	long tick=0;
	
	@Override
	public final synchronized Object onStart(Object conId, String sql, Object[] args) {
		long mytick=++tick;
		inFlight.put(mytick, new QData(conId,sql,args));
		return mytick;
	}
	@Override
	public final synchronized void onEnd(Object ticket, boolean success, SQLException sex) {
		QData completed=inFlight.remove(ticket);
		history.remember(tick, completed,success,sex);
	}
	
	interface QHistory {
		void remember(long tick, QData q,boolean success, SQLException sex);
		NavigableMap<Long, QData> get();
	}

	QHistory dummyHistory=new QHistory() {
		public void remember(long tick, QData q,boolean success, SQLException sex) {}
		public NavigableMap<Long, QData> get() {return new TreeMap<Long, QData>();}
	};
	TreeMap<Long,QData> historyData=new TreeMap<>();
	QHistory realHistory=new QHistory() {	
		public void remember(long tick, QData q,boolean success, SQLException sex) {
			synchronized (realHistory) {
				if (historyData.size()>MAX_HIST) {
					historyData.remove(historyData.firstEntry().getKey());
				}
				q.setCompletion(success,sex);
				historyData.put(tick, q);
			}
			saveStreamingMessge(tick,q);
		}
		@Override
		public TreeMap<Long, QData> get() {
			TreeMap<Long, QData> ret = new TreeMap<Long, QData>(Collections.reverseOrder());
			synchronized (realHistory) {
				ret.putAll(historyData);
			}
			return ret;
		}
	};
	
	volatile QHistory history=dummyHistory;
	void switchToDummyHistory() {
		//System.err.println("Dummy History");
		synchronized (realHistory) {historyData.clear();}
		history=dummyHistory;
	}
	void switchToRealHistory() {
		//System.err.println("Real History");
		history=realHistory;
	}
	private void saveStreamingMessge(long tick, QData q) {
		synchronized (getStreamingLock()) {
			if (streamers.isEmpty())
				return;
			streamersBuf.put(tick, q);
		}

		selector.wakeup();
		
	}

	private void distibuteToStreamers() {
		synchronized (getStreamingLock()) {
			if (streamersBuf.isEmpty())
				return;
			List<SelectionKey> invalids=new ArrayList<SelectionKey>();
			for (SelectionKey key : streamers) {
				if (!key.isValid()) {
					invalids.add(key);
					continue;
				}
				try {
					EndPoint streamer=endPoints.get(key);
					if (streamer!=null) {
						if (streamer.addStreamMessages(streamersBuf))
							streamer.makeWritable();
						else
							invalids.add(key);
					}
				} catch (Exception ex) {
					invalids.add(key);
					continue;					
				}
			}
			for (SelectionKey key : invalids) {
				close(key);
			}
			streamersBuf.clear();
		}
		
	}
	
	private void close(SelectionKey key) {
		EndPoint e=endPoints.get(key);
		if (e!=null) {
			e.close();
		}
	}

	byte[] convertToStreamMessage(long tick, QData q, boolean showArgs, int maxArgsLength) {
		long d=q.completionTime-q.start;
		StringBuilder sb=new StringBuilder();
		sb.append(q.success?'S':'F').append('\t');
		sb.append(d).append('\t');
		singleLine(sb, U.limit(q.sql,maxArgsLength));
		boolean isExtendedPrintedArgs=false;
		if (q.sex!=null) {
			sb.append('\t');
			StringBuilder s=new StringBuilder();
			isExtendedPrintedArgs=ExtendedSQLException.print(s, maxArgsLength, q.sex);
			singleLine(sb, s.toString());
		}
		if (showArgs && !isExtendedPrintedArgs && q.hasArgs()) {
			sb.append('\t');
			StringBuilder s=new StringBuilder();
			singleLine(s,q.printArgs(false));
			sb.append(U.limit(s.toString(),maxArgsLength));
		}
		sb.append('\n');
		return sb.toString().getBytes(Utils.UTF8);
	}

	private static void singleLine(StringBuilder sb, String str) {
		if (str==null) return;
		for (int i=0;i<str.length();++i) {
			char c=str.charAt(i);
			if (c=='\n' || c=='\r' || c=='\t') c=' ';
			sb.append(c);
		}			
	}

	private void firstConnected() {
		switchToRealHistory();
	}

	private void lastDisconnected() {
		switchToDummyHistory();
		synchronized (getStreamingLock()) {
			streamersBuf.clear();
			streamers.clear();
		}
	}

	public static void main(String[] args) throws IOException {
		server.init(65535, 1024);
		Thread t=new Thread() {
			public void run() {
				try {
					Object ticket=null;
					for (int i=1;;++i) {
						System.in.read();
						if (ticket==null)
							ticket=server.onStart("conn", "sql #"+i, new Object[]{"Some args"});
						else {
							server.onEnd(ticket, true,null);
							ticket=null;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		t.start();
		server.loop();
	}

	@Override
	public final synchronized void setConnectionInfo(Object ticket, Object conId) {
		QData qData=inFlight.get(ticket);
		if (qData!=null)
			qData.setConnectionInfo(conId);
	}
	


}
