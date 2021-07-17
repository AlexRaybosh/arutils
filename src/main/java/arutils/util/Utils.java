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

package arutils.util;

import java.io.*;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import arutils.async.AsyncEngine;
import sun.misc.Unsafe;

public final class Utils {

	private Utils() {
	}

	public static void close(AutoCloseable c) {
		if (c != null)
			try {
				c.close();
			} catch (Throwable ex) {
			}
	}

	public static boolean isEmpty(String st) {
		return st == null || st.trim().length() == 0;
	}

	public static String getProperty(Properties props, String propertyName, String defaultValue) {
		String result = props.getProperty(propertyName);
		if (result == null || "".equals(result)) {
			result = defaultValue;
		}
		return result;
	}

	public static boolean isNullOrEmpty(Object[] array) {
		return array == null || array.length == 0;
	}

	public static boolean isNullOrEmpty(Collection<Object> coll) {
		return coll == null || coll.isEmpty();
	}

	public static <T> List<List<T>> splitList(List<T> list, int maxNumOfSublists, int maxCountInList) {
		if (list.isEmpty()) {
			return Collections.emptyList();
		}
		if (list.size() > maxNumOfSublists * maxCountInList) {
			throw new IllegalArgumentException("Too big list: " + list.size() + ", maxNumOfSublists = "
					+ maxNumOfSublists + ", maxCountInList = " + maxCountInList);
		}
		int listCount = list.size() / maxCountInList;
		int listReminder = list.size() % maxCountInList;
		if (listReminder != 0) {
			listCount++;
		}
		List<List<T>> result = new ArrayList<List<T>>(listCount);

		for (int i = 0; i < listCount - 1; i++) {
			result.add(list.subList(i * maxCountInList, ((i + 1) * maxCountInList)));
		}

		int offset = maxCountInList * (listCount - 1);
		result.add(list.subList(offset, list.size()));

		return result;
	}

	public static Calendar addHours(Calendar c, int hours) {
		long millis = c.getTimeInMillis();
		millis += (1000 * 60 * 60 * hours);
		c.setTimeInMillis(millis);
		return c;
	}

	public static int system(String command, Object... args) throws InterruptedException, IOException {
		if (args == null)
			args = new Object[0];

		String[] cmd = new String[args.length + 1];
		cmd[0] = command;
		for (int i = 1; i <= args.length; i++)
			cmd[i] = toString(args[i - 1]);

		Process p = Runtime.getRuntime().exec(cmd);
		return p.waitFor();
	}

	public static int rmrf(File file) throws InterruptedException, IOException {
		String[] cmd = new String[] { "/bin/rm", "-rf", file.getAbsolutePath() };
		Process p = Runtime.getRuntime().exec(cmd);
		return p.waitFor();
	}

	public static String echo(String str) throws InterruptedException {
		String[] cmd = new String[] { "/bin/sh", "-c", "echo -n " + str };
		BufferedReader br = null;
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			int c = 0;
			StringBuilder sb = new StringBuilder();
			while ((c = br.read()) != -1)
				sb.append((char) c);
			int s = p.waitFor();
			if (s != 0)
				throw new RuntimeException("Sub-process died with exit status " + s);
			return sb.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(br);
		}

	}

	public static byte[] collectStdout(String... args) throws InterruptedException {
		InputStream is = null;
		try {
			Process p = Runtime.getRuntime().exec(args);
			is = p.getInputStream();
			final InputStream es = p.getErrorStream();
			Future<String> ef = AsyncEngine.getEngineExecutorService().submit(new Callable<String>() {

				public String call() throws Exception {
					StringBuilder sb=new StringBuilder();
					for (;;) {
						int c=es.read();
						if (c==-1) break;
						sb.append((char)c);
					}
					return sb.toString();
				}
			});
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int sizeRead = -1;
			while ((sizeRead = is.read(buffer)) != -1) {
				bos.write(buffer, 0, sizeRead);
			}
			int s = p.waitFor();
			if (s != 0) {
				String err=null;
				try {
					err=ef.get();
				} catch (InterruptedException e) {
					throw e;
				} catch (ExecutionException e) {
				}
				throw new RuntimeException("Subprocess died with exit status: " + s+ ( err ==null ? "":  "\n"+err));
			}
			return bos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(is);
		}
	}

	public static String toString(Object obj) {
		return obj == null ? null : obj.toString();
	}

	public static Double toDouble(Object obj) {
		return obj == null ? null : new Double(obj.toString());
	}

	public static Long toLong(Object obj) {
		if (obj == null)
			return null;
		if (obj instanceof Number)
			return ((Number) obj).longValue();
		return new Long(obj.toString());
	}

	public static Boolean toBoolean(Object obj) {
		return obj == null ? null : new Boolean(obj.toString());
	}

	public static Integer toInteger(Object obj) {
		if (obj == null)
			return null;
		if (obj instanceof Number)
			return ((Number) obj).intValue();
		return new Integer(obj.toString());
	}

	public static Integer toInt(Object obj) {
		if (obj == null)
			return 0;
		if (obj instanceof Number)
			return ((Number) obj).intValue();
		return Integer.parseInt(obj.toString());
	}

	public static Date toDate(Object obj) {
		if (obj == null)
			return null;
		if (obj instanceof Date)
			return new Date(((Date) obj).getTime());
		return null;
	}

	public static byte[] toByteArray(Object obj) {
		if (obj == null)
			return null;
		if (obj instanceof byte[])
			return (byte[]) obj;
		if (obj instanceof Blob) {
			Blob b = (Blob) obj;
			try {
				return b.getBytes(0, (int) b.length());
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	/*
	 * args: Long age, boolean olderThanAge=true
	 */
	public static Map<String, Long> dirListAgeMap(File dir, String regExpr, Object... args) {
		Long age = null;
		Boolean olderThanAge = true;
		if (args != null) {
			if (args.length > 0)
				age = toLong(args[0]);
			if (args.length > 1)
				olderThanAge = toBoolean(args[1]);
		}
		String[] list = dir.list();
		Map<String, Long> ret = new HashMap<String, Long>();
		if (list == null)
			return ret;
		for (String name : dir.list()) {
			if (name == null)
				continue;
			if (regExpr == null || name.matches(regExpr)) {
				long lst = new File(dir, name).lastModified();
				long a = System.currentTimeMillis() - lst;

				if (age == null) {
					ret.put(name, lst);
					continue;
				}
				if (olderThanAge && a >= age)
					ret.put(name, lst);
				if (!olderThanAge && a <= age)
					ret.put(name, lst);
			}
		}
		return ret;
	}

	public static boolean in(String str, String... list) {
		if (list == null)
			return false;
		for (String val : list)
			if (equals(str, val))
				return true;
		return false;
	}

	public static boolean equals(Object obj1, Object obj2) {
		if (obj1 == null)
			return obj2 == null;
		return obj1.equals(obj2);
	}

	public static void close(ResultSet rs) {
		if (rs != null)
			try {
				rs.close();
			} catch (Exception e) {
			}
	}

	public static void close(Statement ps) {
		if (ps != null)
			try {
				ps.close();
			} catch (Exception e) {
			}
	}

	public static void logout(String str, Object... args) {
		System.out.printf("[" + currentLocalDateTimeString() + " ] " + str + "\n", args);
		System.out.flush();
	}

	public static void logout(String str) {
		System.out.println("[" + currentLocalDateTimeString() + " ] " + str);
		System.out.flush();
	}

	public static void logerr(String str, Object... args) {
		System.err.printf("[" + currentLocalDateTimeString() + " ] " + str + "\n", args);
		System.err.flush();
	}

	public static void logerr(String str) {
		System.err.println("[" + currentLocalDateTimeString() + " ] " + str);
		System.err.flush();
	}

	public static void throwableToSQLException(Throwable t) throws SQLException {
		if (t instanceof SQLException)
			throw (SQLException) t;
		if (t instanceof RuntimeException)
			throw (RuntimeException) t;
		throw new RuntimeException(t);
	}

	public static void throwableToException(Throwable t) throws Exception {
		if (t instanceof Exception)
			throw (Exception) t;
		if (t instanceof RuntimeException)
			throw (RuntimeException) t;
		throw new RuntimeException(t);
	}

	public static void printMargin(StringBuilder sb, int m) {
		for (int i = 0; i < m; i++)
			sb.append('\t');

	}

	public static void close(Connection con) {
		if (con != null)
			try {
				con.close();
			} catch (Exception e) {
			}
	}

	public static String printArray(Object[] row) {
		if (row == null)
			return null;
		StringBuilder sb = new StringBuilder("");
		int c = 0;
		for (Object o : row) {
			sb.append(o);
			if (c < row.length - 1)
				sb.append(", ");
			c++;
		}
		return sb.toString();
	}

	public static boolean equalLists(List<?> l1, List<?> l2) {
		if (l1 == null)
			return l2 == null || l2.size() == 0;
		if (l2 == null)
			return l1 == null || l1.size() == 0;
		if (l1.size() == l2.size()) {
			for (int i = 0; i < l1.size(); i++) {
				Object o1 = l1.get(i);
				Object o2 = l2.get(i);
				if (!equals(o1, o2))
					return false;
			}
			return true;
		}
		return false;
	}

	public static boolean equalMaps(Map<?, ?> m1, Map<?, ?> m2) {
		if (m1 == null)
			return m2 == null || m2.size() == 0;
		if (m2 == null)
			return m1 == null || m1.size() == 0;
		return m1.equals(m2);
	}

	public static void printList(StringBuilder sb, List<?> list) {
		if (list == null)
			return;
		int size = list.size();
		int c = 1;
		for (Object p : list) {
			sb.append(p);
			if (c != size)
				sb.append(", ");
			c++;
		}
	}

	public final static TimeZone UTC = TimeZone.getTimeZone("UTC");
	public static final Charset UTF8 = Charset.forName("utf8");
	public static final Charset ASCII = Charset.forName("ascii");

	public static String csvList(Collection<? extends Object> list) {
		if (list == null)
			return "";
		int size = list.size();
		StringBuilder sb = new StringBuilder();
		int c = 1;
		for (Object p : list) {
			sb.append('"').append(p).append('"');
			if (c != size)
				sb.append(", ");
			c++;
		}
		return sb.toString();
	}

	public static <T extends Object> String csvList(T[] list) {
		if (list == null)
			return "";
		int size = list.length;
		StringBuilder sb = new StringBuilder();
		int c = 1;
		for (Object p : list) {
			sb.append('"').append(p).append('"');
			if (c != size)
				sb.append(", ");
			c++;
		}
		return sb.toString();
	}

	public static void close(Closeable c) {
		if (c != null)
			try {
				c.close();
			} catch (Throwable ignore) {
			}
	}

	public static String rightTrimNumber(String v) {
		int dot = v.indexOf('.');
		if (dot < 0) {
			return v;
		}
		if (dot == v.length() - 1) {
			v = v.substring(0, dot);
		}
		String s = v.substring(0, dot);
		String e = v.substring(dot + 1);

		int exclude = e.length();
		for (int i = exclude - 1; i >= 0; i--) {
			if (e.charAt(i) != '0')
				break;
			exclude--;
		}
		if (exclude == 0) {
			v = s;
		} else {
			v = s + "." + e.substring(0, exclude);
		}
		return v;
	}

	public static boolean isBit(int val, int num) {
		return ((val << (31 - num)) >>> 31) == 1;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static int compare(Comparable o1, Comparable o2) {
		if (o1 == null)
			return o2 == null ? 0 : 1;
		if (o2 == null)
			return -1;
		return o1.compareTo(o2);
	}

	/*
	 * public static void main(String[] strs) {
	 * logerr("Starting:\n%s",getStackTrace(new RuntimeException())); for
	 * (Map.Entry<String,Long> name : dirListAgeMap(new
	 * File("/home/alex/test_group"),"^\\d+$",60*1000).entrySet()) {
	 * System.out.println(name.getKey()+" " +utcDateTimeString(name.getValue())); }
	 * }
	 * 
	 */

	public static String boolchar(boolean b) {
		return b ? "Y" : "N";
	}

	public static boolean boolchar(String str) {
		if ("Y".equals(str))
			return true;
		return false;
	}

	private final static ThreadLocal<Local> locals = new ThreadLocal<Local>() {
		@Override
		protected Local initialValue() {
			return new Local();
		}
	};

	public static byte[] md5(byte[] payload) {
		return getLocal().md5(payload);
	}

	public static byte[] sha256(byte[] payload) {
		return getLocal().sha256(payload);
	}

	public static String md5HexLow(byte[] payload) {
		return getLocal().md5HexLow(payload);
	}

	public static String sha256HexLow(byte[] payload) {
		return getLocal().sha256HexLow(payload);
	}

	public static Local getLocal() {
		return locals.get();
	}

	public static Date parseLocalDate(String yyyyMMdd) throws ParseException {
		return getLocal().parseLocalDate(yyyyMMdd);
	}

	public static String currentUTCDateTimeString() {
		return getLocal().currentUTCDateTimeString();
	}

	public static String currentLocalDateTimeString() {
		return getLocal().currentLocalDateTimeString();
	}

	public static String currentUTCDateTimeLogString() {
		return getLocal().currentUTCDateTimeLogString();
	}

	public static Date parseLocalDateInt(String yyyyMMdd) throws ParseException {
		return getLocal().parseLocalDateInt(yyyyMMdd);
	}

	public static Date parseLocalDateTime(String yyyyMMdd) throws ParseException {
		return getLocal().parseLocalDateTime(yyyyMMdd);
	}

	public static Date parseUTCDate(String yyyyMMdd) throws ParseException {
		return getLocal().parseUTCDate(yyyyMMdd);
	}

	public static String formatUTCDate(Date yyyyMMdd) {
		return getLocal().formatUTCDate(yyyyMMdd);
	}

	public static String formatUTCDateInt(Date yyyyMMdd) {
		return getLocal().formatUTCDateInt(yyyyMMdd);
	}

	public static String formatLocalDate(Date yyyyMMdd) {
		return getLocal().formatLocalDate(yyyyMMdd);
	}

	public static Date parseUTCDateInt(String yyyyMMdd) throws ParseException {
		return getLocal().parseUTCDateInt(yyyyMMdd);
	}

	public static String formatLocalDateTime(Date yyyyMMdd) {
		return getLocal().formatLocalDateTime(yyyyMMdd);
	}

	public static UUID getMD5UUID(byte[] bs) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		byte[] bytes = md.digest(bs);
		return EncodingUtils.byteArrayToUUID(bytes);
	}

	final static AtomicInteger tn = new AtomicInteger();
	final static ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r, "async" + tn.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		}
	});

	final ExecutorService getAsyncExecutorService() {
		return executorService;
	}

	public static String getStackTrace(Throwable t) {
		Set<Throwable> visited = new HashSet<>();
		StringBuilder sb = new StringBuilder("Exception (\"");
		sb.append(Thread.currentThread().getName()).append("\") ");
		sb.append(t.getClass().getName());
		sb.append(": ");
		getStackTrace(sb, visited, t, null);
		return sb.toString();
	}

	public static void getStackTrace(StringBuilder sb, Set<Throwable> visited, Throwable t, String prefix) {
		if (visited.contains(t))
			return;
		visited.add(t);
		if (prefix != null)
			sb.append(prefix).append("\n");
		sb.append(t.getMessage()).append("\n");
		if (t.getStackTrace() != null)
			for (StackTraceElement st : t.getStackTrace()) {
				sb.append("\tat ").append(st).append('\n');
			}
		if (t.getCause() != null) {
			getStackTrace(sb, visited, t.getCause(), "Caused By:");
		}
	}

	private final static Future<UnameInfo> unameFuture = initUnameFuture();

	static class UnameInfo {
		final String uname, processor;

		public UnameInfo(String uname, String processor) {
			this.uname = uname;
			this.processor = processor;
		}
	}

	private static Future<UnameInfo> initUnameFuture() {
		return AsyncEngine.getEngineExecutorService().submit(new Callable<UnameInfo>() {
			public UnameInfo call() throws Exception {
				try {
					byte[] bun = collectStdout("/bin/sh", "-c", "PATH=/bin:/usr/bin:$PATH uname");
					byte[] bunp = collectStdout("/bin/sh", "-c", "PATH=/bin:/usr/bin:$PATH uname -p");
					String un = new String(bun, UTF8).replace("\n", "").replace("\r", "");
					String unp = new String(bunp, UTF8).replace("\n", "").replace("\r", "");
					UnameInfo u = new UnameInfo(un, unp);
					return u;
				} catch (Exception e) {
					System.err.println("uname utility not found");
					return new UnameInfo("unknown", "unknown");
				}
			}

		});
	}

	public static String getUname() {
		try {
			return unameFuture.get().uname;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String getProcessorName() {
		try {
			return unameFuture.get().processor;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	
	final private static sun.misc.Unsafe unsafe = loadUnsafe();

	public static sun.misc.Unsafe getUnsafe() {return unsafe;}
	
	@SuppressWarnings("rawtypes")
	private static sun.misc.Unsafe loadUnsafe() {
		try {
			Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			Unsafe u = (sun.misc.Unsafe) field.get(null);
			try {
				Class cls=Class.forName("jdk.internal.module.IllegalAccessLogger");
				Field logger=cls.getDeclaredField("logger");
				u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
			} catch (Throwable e) {
				System.err.println("Failed to disable unsafe logger: " + e.getMessage());
			}
			return u;
		} catch (Throwable e) {
			System.err.println("Failed to load unsafe: " + e.getMessage());
			return null;
		}

	}
	public static int getAddressSize() {
		return unsafe.addressSize();
	}

	public static String getArchName() {
		return getUname()+"-"+getProcessorName()+"-"+(8*getAddressSize());
	}
	
	public static void rethrowCause(Throwable e) throws Exception {
		if (e instanceof InterruptedException) throw (InterruptedException)e;
		Throwable current=e;
		InterruptedException lastInterrupted=null;
		for (;;) {
			if (current instanceof InterruptedException)  lastInterrupted=(InterruptedException)current;
			Throwable c=current.getCause();
			if (c==null) break;
			current=c;
		}
		if (lastInterrupted!=null) throw lastInterrupted;
		if (current instanceof RuntimeException)  throw (RuntimeException)current;
		throw new RuntimeException(current);
	}	
	
	public static void rethrowInterrupted(Throwable e) throws InterruptedException {
		if (e instanceof InterruptedException) throw (InterruptedException)e;
		Throwable current=e;
		InterruptedException lastInterrupted=null;
		for (;;) {
			if (current instanceof InterruptedException)  lastInterrupted=(InterruptedException)current;
			Throwable c=current.getCause();
			if (c==null) break;
			current=c;
		}
		if (lastInterrupted!=null) throw lastInterrupted;
		if (current instanceof RuntimeException)  throw (RuntimeException)current;
		throw new RuntimeException(current);
	}	
	
	
	public static Exception proceedUnlessInterrupted(Throwable e) throws InterruptedException {
		if (e instanceof InterruptedException) throw (InterruptedException)e;
		Throwable current=e;
		InterruptedException lastInterrupted=null;
		for (;;) {
			if (current instanceof InterruptedException)  lastInterrupted=(InterruptedException)current;
			Throwable c=current.getCause();
			if (c==null) 
				break;
			current=c;
		}
		if (lastInterrupted!=null) throw lastInterrupted;
		if (current instanceof Exception) return (Exception)current;
		return new Exception(current);
	}	
	public static Exception proceedUnlessSQLOrInterrupted(Throwable e) throws SQLException, InterruptedException {
		if (e instanceof InterruptedException) throw (InterruptedException)e;
		
		Throwable current=e;
		SQLException lastSQLException=null;
		InterruptedException lastInterrupted=null;
		for (;;) {
			if (current instanceof SQLException)  lastSQLException=(SQLException)current;
			if (current instanceof InterruptedException)  lastInterrupted=(InterruptedException)current;
			Throwable c=current.getCause();
			if (c==null) 
				break;
			current=c;
		}
		if (lastSQLException!=null) throw lastSQLException;
		if (lastInterrupted!=null) throw lastInterrupted;
		if (current instanceof Exception) return (Exception)current;
		return new Exception(current);
	}
	public static void rethrowSQLOrInterrupted(Throwable e) throws SQLException, InterruptedException {
		if (e instanceof InterruptedException) throw (InterruptedException)e;
		
		Throwable current=e;
		SQLException lastSQLException=null;
		InterruptedException lastInterrupted=null;
		for (;;) {
			if (current instanceof SQLException)  lastSQLException=(SQLException)current;
			if (current instanceof InterruptedException)  lastInterrupted=(InterruptedException)current;
			Throwable c=current.getCause();
			if (c==null) 
				break;
			current=c;
		}
		if (lastSQLException!=null) throw lastSQLException;
		if (lastInterrupted!=null) throw lastInterrupted;
		throw new SQLException(current);
	}
	
	public static <Ret> Ret runtimeException(Throwable t) {
		t=proceedThrowable(t);
		throw new RuntimeException(t);
	}
	public static <Ret> Ret runtimeException(String msg, Throwable t) {
		t=proceedThrowable(t);
		throw new RuntimeException(msg, t);
	}
	public static Throwable proceedThrowable(Throwable t) {
		Throwable current=t;
		for (;;) {
			Throwable c=current.getCause();
			if (c==null) return current; 
		}
	}
	public static Exception proceedException(Throwable t) {
		Throwable current=t;
		for (;;) {
			Throwable c=current.getCause();
			if (c==null) {
				if (current instanceof Exception) return (Exception)current;
				return new Exception(current);
			}
		}
	}
	public static Exception extraceCause(Throwable t) {
		if (t instanceof InterruptedException) return (InterruptedException)t;
		Throwable current=t;
		for (;;) {
			Throwable c=current.getCause();
			if (c==null) return extractException(current);
			if (c instanceof InterruptedException) return (InterruptedException)c;
			current=c;
		}
	}
	public static Exception extractException(Throwable t) {
		if (t instanceof Exception) return (Exception)t;
		return new RuntimeException(t);
	}

	public static String getCanonicalHostName() {
		return processHolder.cannonicalHostName;
	}
	public static Long getPid() {
		return processHolder.pid;
	}
	public static String getProcessCmdLine() {
		return processHolder.cmdLine;
	}
	public static String[] getProcessCmd() {
		return processHolder.args.toArray(new String[processHolder.args.size()]);
	}
	public static Long getProcessStartTime() {
		return processHolder.startTime;
	}
	
	
	final static ProcessInfoHolder processHolder=initProcessHolder();
	static class ProcessInfoHolder {
		final String cannonicalHostName;
		final Long pid;
		final Long startTime;
		final String cmdLine;
		final List<String> args;
		
		ProcessInfoHolder(String cannonicalHostName, Long pid, Long statTime, String cmdLine, List<String> args) {
			this.cannonicalHostName = cannonicalHostName;
			this.pid = pid;
			this.startTime = statTime;
			this.cmdLine = cmdLine;
			this.args=args;
		}
		
	}
	
	

	private static ProcessInfoHolder initProcessHolder() {
		Long pid=derivePid();
		Long startTime=null;
		List<String> lst=new ArrayList<>();
		String cmd=null;
		if (pid!=null) try {
			File procFile=new File("/proc/"+pid+"/cmdline");
			if (procFile.exists()) {
				BasicFileAttributes attr=Files.readAttributes(procFile.toPath(),BasicFileAttributes.class);
				startTime=attr.creationTime().to(TimeUnit.MILLISECONDS);
				byte[] data=Files.readAllBytes(procFile.toPath());
				
				StringBuilder sb=new StringBuilder();
				for (int i=0;i<data.length;++i) {
					if (data[i]==0) {
						if (sb.length()>0) {
							lst.add(sb.toString());
							sb.setLength(0);
						}
					} else sb.append((char)data[i]);
				}
				if (sb.length()>0) {
					lst.add(sb.toString());
				}
				sb.setLength(0);
				for (int i=0; i< lst.size();++i) {
					sb.append(lst.get(i));
					if (i != lst.size()-1) sb.append(' ');
				}
				cmd=sb.toString();
			}
					
		} catch (Exception e) {
		}
		return new ProcessInfoHolder(deriveCannonicalHostName(), pid, startTime, cmd, lst );
	}

	private static String deriveCannonicalHostName() {
		try {
			
			String name=new String(collectStdout("/bin/sh", 
					"-c", 
					"PATH=/bin:/usr/bin:$PATH nslookup `hostname`| perl -ne \"m/^\\s*Name:\\s+(.*?)\\s*$/ && print \\$1\""), UTF8);
			if (name!=null) {
				name=name.trim();
				if (name.length()>0) return name;
			}
			
		} catch (Exception e) {
		}
		try {return InetAddress.getLocalHost().getCanonicalHostName();} catch (Exception e) {
			return null;
		}
	}

	private static Long derivePid() {
		try {
			String p=echo("$PPID");
			StringBuilder sb=new StringBuilder();
			for (int i=0;i<p.length();++i) {
				if (Character.isDigit(p.charAt(i))) sb.append(p.charAt(i));
				else break;
			}
			return Long.parseLong(sb.toString());
		} catch (Exception e) {
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		System.out.println(getArchName());
		System.out.println(getCanonicalHostName());
		System.out.println(getProcessCmdLine());
		System.out.println(getPid());
		
	}

	public static <R> R rethrowRuntimeException(Throwable t) {
		Exception c = extraceCause(t);
		if (c instanceof RuntimeException) throw (RuntimeException)c;
		throw new RuntimeException(t);
	}

	
}
