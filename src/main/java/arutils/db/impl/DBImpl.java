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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import arutils.async.AsyncEngine;
import arutils.db.ArrayRowHandler;
import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.DynamicTableSet;
import arutils.db.LabelRowHandler;
import arutils.db.ResultSetHandler;
import arutils.db.StatementBlock;
import arutils.db.TableName;
import arutils.util.Utils;

public class DBImpl extends DB {
	private DBIntristics intrinsics;
	private Dialect dialect=Dialect.UNKNOWN;
	private String url;
	private String user;
	private String password;
	private Properties props;
	private volatile int maxConnections=30;
	private volatile int transactionIsolation=Connection.TRANSACTION_READ_COMMITTED;
	private volatile int maxCachedPreparedStatements=300;
	private volatile int batchSize=1024;
	private volatile String defaultDatabase;
	private volatile long retryTimeout=5000;
	private volatile long overborrowPenaltyTimeout=1000;
	volatile Future<Set<TableName>> initialTableNames;
	private volatile String version;
	private volatile String versionComment;
	private volatile boolean allowOverborrow=true;

//	private LinkedHashMap<String, List<Object[]>> initSqls=new LinkedHashMap<String, List<Object[]>>(); 
	Set<ConnectionWrapImpl> overborrowSet=new HashSet<>();
	private Map<Thread,String> threadsMap=new ConcurrentHashMap<>();
	
	class CommitContext {
		int conNum=0;
		ArrayList<ConnectionWrapImpl> pool=new ArrayList<ConnectionWrapImpl>();
		ReentrantLock lock=new ReentrantLock();
		Condition cond=lock.newCondition();
		final boolean autoCommit;
		NavigableMap<Long,StickyBlock> initBlocks=new TreeMap<Long,StickyBlock>();
		private long stickyVersion=0;
		
		public CommitContext(boolean autocommit) {
			this.autoCommit=autocommit;
		}
		final DBImpl getDB() {
			return DBImpl.this;
		}
		
		volatile boolean debugEnabled=getDebugEnabled();
		ConnectionWrapImpl take() throws InterruptedException, SQLException {
			ConnectionWrapImpl cw=null;
			boolean stickyOutOfSync=false;
			lock.lockInterruptibly();
			String stackTrace = null;
			try {
				while (!allowOverborrow && pool.isEmpty()) {
					if (conNum < maxConnections) {
						cw= new ConnectionWrapImpl(this, autoCommit);
						++conNum;
						break;
					} else {
						cond.await(overborrowPenaltyTimeout, TimeUnit.MILLISECONDS);
					}
				}
				if (cw==null) {
					if (pool.isEmpty()) {
						/*
						 * something went wrong, even as each thread with an existing connection
						 * is waiting on async computation, which requires another connection, we still need more
						 * 
						 */
						Thread myThread=Thread.currentThread();
						if (!threadsMap.containsKey(myThread) && overborrowSet.size() + conNum >= 3*maxConnections) {
							// add penalty
							cond.await(overborrowPenaltyTimeout, TimeUnit.MILLISECONDS);
							
							stackTrace=genStackTrace();
							if (pool.isEmpty()) {
								debugEnabled=true;
								StringBuilder msg=new StringBuilder("Stalled DB pool connection request\nautoCommit="
										+ autoCommit + "\npool size=" + pool.size()+ "\nconNum=" + conNum
										+ "\nmaxConnections=" + maxConnections + "\n#Lockers="+threadsMap.size()
										+ "\noverborrow size=" + overborrowSet.size() + "\n");
								
								msg.append("Stuck at "+stackTrace +"\n");
								for (Entry<Thread, String> e : threadsMap.entrySet()) {
									Thread t=e.getKey();
									String trace=e.getValue();
									String nowAt=genCurrentStackTrace(t);
									msg.append("\n--------" + t.getName() + "---------");
									if (debugEnabled && trace!=null) 
										msg.append("\n initial locker stack: " + trace);
									msg.append("\n current locker stack: " + nowAt );
									
								}
								msg.append("\n=======================================================\n");
								Utils.logerr(msg.toString());
								cw=new ConnectionWrapImpl(this, autoCommit);
								overborrowSet.add(cw);
							} else {
								cw=pool.remove(pool.size()-1);
							}
							threadsMap.put(Thread.currentThread(), genStackTrace());
						} else {
							cw=new ConnectionWrapImpl(this, autoCommit);
							if (conNum >= maxConnections) {
								overborrowSet.add(cw);
							} else {
								conNum++;
							}
						}
					} else {
						cw=pool.remove(pool.size()-1);
					}
				}
				
				stickyOutOfSync=cw.getStickyVersion()!=stickyVersion;
				
				if (debugEnabled) threadsMap.put(Thread.currentThread(), stackTrace==null?genStackTrace():stackTrace);

			} finally {
				lock.unlock();
			}
			if (stickyOutOfSync)
				cw.syncUpInitStatements();
			return cw;
		}
		void release(ConnectionWrapImpl w) throws InterruptedException {
			lock.lock();
			if (debugEnabled) threadsMap.remove(Thread.currentThread());
			try {
				if (overborrowSet.remove(w)) {
					w.close();
					
				} else {
					pool.add(w);
					cond.signalAll();
				}
			} finally {
				lock.unlock();
			}
		}
		void close() {
			lock.lock();
			try {
				for (ConnectionWrap cw  : pool) 
					while (true) {
						try {
							cw.close();
							--conNum;
							break;
						} catch (InterruptedException e) {}
					}
					
			} finally {
				lock.unlock();
			}		
		}
		
		Object addInitStatement(StickyBlock block) {
			++stickyVersion;
			Long key=stickyNum.incrementAndGet();
			initBlocks.put(key, block);
			return key;
		}
		public Set<Long> getInitStatementKeys() {
			return initBlocks.keySet();
		}
		public boolean removeInitStatementKey(Object key) {
			stickyVersion++;
			return initBlocks.remove(key)!=null;
		}
		
		final ReentrantLock getLock() {
			return lock;
		}
		NavigableMap<Long,StickyBlock>  getInitBlocks() {
			return initBlocks;
		}
		final long getStickyVersion() {
			return stickyVersion;
		}
	}
	
	CommitContext txCommitContext=new CommitContext(false);
	CommitContext nonTxCommitContext=new CommitContext(true);
	ReentrantLock getLock(boolean autocommit) {
		return autocommit?nonTxCommitContext.lock:txCommitContext.lock;
	}



	
	static String genCurrentStackTrace(Thread t) {
		StringBuilder sb=new StringBuilder("Thread "+t.getName()+" current location\n");
		StackTraceElement[] trace=t.getStackTrace();
		if (trace!=null) for (StackTraceElement st : trace) {
			sb.append("\tat ").append(st).append('\n');
		}
		return sb.toString();
	}
	
	
	
	
	String genStackTrace() {
		try {
			throw new Exception();
		} catch (Exception e) {
			return Utils.getStackTrace(e);
		}
	}
	
	
	
	
	boolean getDebugEnabled() {
		String d=System.getenv("DB_DEBUG");
		boolean ret= d!=null && (d.equals("1") || d.equalsIgnoreCase("true") || d.startsWith("y") || d.startsWith("Y"));
		if (ret) {
			Utils.logerr("DB debugging enabled");
		}
		return ret;
	}
	
	
	
	
	public DBImpl(String url, String user, String password, Properties props) {
		this.url = url;
		this.user = user;
		this.password = password;
		this.props = props;
		if (user != null)
			this.props.put("user", user);
		if (password != null)
			this.props.put("password", password);
		String driver = null;
		if (Pattern.matches("^jdbc:mysql:thin:.*", url)) {
			driver = "org.drizzle.jdbc.DrizzleDriver";
			dialect = Dialect.DRIZZLE_MYSQL;
		} else if (Pattern.matches("^jdbc:drizzle:.*", url)) {
			driver = "org.drizzle.jdbc.DrizzleDriver";
			dialect = Dialect.DRIZZLE;
		} else if (Pattern.matches("^jdbc:mysql:/.*", url)) {
			driver = "com.mysql.jdbc.Driver";
			dialect = Dialect.MYSQL;
		} else if (Pattern.matches("^jdbc:jtds:.*", url)) {
			driver = "net.sourceforge.jtds.jdbc.Driver";// "com.sybase.jdbc2.jdbc.SybDriver";
			dialect = Dialect.TDS;
		} else if (Pattern.matches("^jdbc:oracle:.*", url)) {
			driver = "oracle.jdbc.driver.OracleDriver";
			dialect = Dialect.ORACLE;
			this.props.put("SetBigStringTryClob", "true");
			this.props.put("defaultBatchValue", "1");
			this.props.put("defaultExecuteBatch", "1");
			this.props.put("defaultRowPrefetch", "2000");
			this.props.put("useFetchSizeWithLongColumn", "true");
			this.props.put("oracle.jdbc.TcpNoDelay", "true");
		} else if (Pattern.matches("^jdbc:postgresql:.*", url)) {
			driver="org.postgresql.Driver";
			dialect=Dialect.POSTGRESS;
		} else {
			throw new RuntimeException("Don't support url: " + url);
		}
		try {
			Class.forName(driver).newInstance();
		} catch (Exception e) {
			throw new RuntimeException(driver+" not found",e);
		}
		switch (getDialect()) {
			case ORACLE:
				intrinsics=getOracleIntristics();
				break;
			case TDS:
				intrinsics=getTDSIntristics();
				break;
			case POSTGRESS:
				intrinsics=getPostgressIntristics();
				break;
			default:
				intrinsics=getMySQLIntristics();
		}

	}

	protected DBImpl(DBImpl db) {
		this(db.url, db.user, db.password, new Properties(db.props));
		defaultDatabase=db.defaultDatabase;
		maxConnections=db.maxConnections;
		transactionIsolation=db.transactionIsolation;
		maxCachedPreparedStatements=db.maxCachedPreparedStatements;
		batchSize=db.batchSize;
		retryTimeout=db.retryTimeout;
	}


	


	
	public static void register(DB db) {
		// TODO Auto-generated method stub
		
	}
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#clone()
	 */
	@Override
	public DB clone() {
		DB db=new DBImpl(this);
		register(db);
		return db;
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#select(java.lang.String, boolean, java.lang.Object)
	 */
	@Override
	public List<Object[]> select(final String sql, final boolean cache, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			return retry(cw, new StatementBlock<List<Object[]>>(){
				public List<Object[]> execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					return cw.select(sql, cache, args);
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#selectFirstColumn(java.lang.String, boolean, java.lang.Object)
	 */
	@Override
	public List<Object> selectFirstColumn(final String sql, final boolean cache, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			return retry(cw, new StatementBlock<List<Object>>(){
				public List<Object> execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					return cw.selectFirstColumn(sql, cache, args);
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#selectLabelMap(java.lang.String, boolean, java.lang.Object)
	 */
	@Override
	public List<Map<String,Object>> selectLabelMap(final String sql, final boolean cache, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			return retry(cw, new StatementBlock<List<Map<String,Object>>>(){
				public List<Map<String, Object>> execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					return cw.selectLabelMap(sql, cache, args);
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#select(java.lang.String, boolean, arutils.util.db.ArrayRowHandler, java.lang.Object)
	 */
	@Override
	public void select(final String sql, final boolean cache, final ArrayRowHandler rh, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			retry(cw, new StatementBlock<Void>(){
				public Void execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					cw.select(sql, cache, rh, args);
					return null;
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#select(java.lang.String, boolean, arutils.util.db.LabelRowHandler, java.lang.Object)
	 */
	@Override
	public void select(final String sql, final boolean cache, final LabelRowHandler rh, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			retry(cw, new StatementBlock<Void>(){
				public Void execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					cw.select(sql, cache, rh, args);
					return null;
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#select(java.lang.String, boolean, arutils.util.db.ResultSetHandler, java.lang.Object)
	 */
	@Override
	public void select(final String sql, final boolean cache, final ResultSetHandler rh, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			retry(cw, new StatementBlock<Void>(){
				public Void execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					cw.select(sql, cache, rh, args);
					return null;
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	@Override
	public <T> T selectSingle(final String sql, final boolean cache, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			return retry(cw, new StatementBlock<T>(){
				public T execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					return cw.selectSingle(sql, cache, args);
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}



	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#update(java.lang.String, boolean, java.lang.Object)
	 */
	@Override
	public int update(final String sql, final boolean cache, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			return retry(cw, new StatementBlock<Integer>(){
				public Integer execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					return cw.update(sql, cache, args);
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#update(java.lang.String, boolean, java.lang.Object)
	 */
	@Override
	public int update(final Collection<Number> genKeys, final String sql, final boolean cache, final Object... args) throws SQLException, InterruptedException {
		final ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			return retry(cw, new StatementBlock<Integer>(){
				public Integer execute(ConnectionWrap wrap) throws SQLException, InterruptedException {
					return cw.update(genKeys, sql, cache, args);
				}});
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	
	
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.DB#commit(arutils.util.db.StatementBlock)
	 */
	@Override
	public <Ret> Ret commit(StatementBlock<Ret> tb) throws SQLException, InterruptedException {
		ConnectionWrapImpl cw=txCommitContext.take();
		try {
			return retryCommit(cw,tb);
		} finally {
			txCommitContext.release(cw);
		}
	}
	@Override
	public <Ret> Ret autocommit(StatementBlock<Ret> tb) throws SQLException, InterruptedException {
		ConnectionWrapImpl cw=nonTxCommitContext.take();
		try {
			return retry(cw,tb);
		} finally {
			nonTxCommitContext.release(cw);
		}
	}
	
	private <Ret> Ret retryCommit(ConnectionWrap cw, StatementBlock<Ret> tb) throws SQLException, InterruptedException {
		long start=System.currentTimeMillis();
		//boolean rolledBack=false;
		for (int cnt=0;;++cnt) {
			if (Thread.interrupted()) throw new InterruptedException();
			boolean success=false;
			try {
				Ret ret = tb.execute(cw);
				cw.commit();
				success=true;
				return ret;
			} catch (SQLException ex) {
//				cw.rollback();
				handleFailure(cw, tb, cnt, start,ex);
			} finally {
				if (!success) {
					cw.rollback();	
					cw.close();				
				}
			} 
		}
	}
	

	
	private <Ret> Ret retry(ConnectionWrap cw, StatementBlock<Ret> tb) throws SQLException, InterruptedException {
		long start=System.currentTimeMillis();
		for (int cnt=0;;++cnt) {
			if (Thread.interrupted()) throw new InterruptedException();
			boolean success=false;
			try {
				Ret ret = tb.execute(cw);
				success=true;
				return ret;
			} catch (SQLException ex) {
				handleFailure(cw, tb, cnt, start,ex);
			} finally {
				if (!success) cw.close();				
			}
		}
	}
	
	private <Ret> void handleFailure(ConnectionWrap cw, StatementBlock<Ret> tb, int cnt, long start, SQLException ex) throws SQLException, InterruptedException {
		if (intrinsics.isNeverRetryable(ex)) throw ex;
		long now=System.currentTimeMillis();
		if (now-start>retryTimeout) {
			if (tb.onError(cw, false, ex, start, now))
				throw ex;
		} else {
			if (!tb.onError(cw, true, ex, start, now))
				return;
		}
		if (cnt>0)
			Thread.sleep(cnt>100?100:cnt);
	}




	@Override
	public Dialect getDialect() {
		return dialect;
	}






	@Override
	public String getUrl() {return url;}

	Properties getProperties() {
		return props;
	}

	@Override
	public int getTransactionIsolation() {
		return transactionIsolation;
	}
	@Override
	public void setTransactionIsolation(int ti) {
		transactionIsolation=ti;
	}
	@Override
	public String findDefaultDatabase() throws SQLException, InterruptedException {
		if (defaultDatabase==null) initDefaultDatabase();
		return defaultDatabase;
	}
	@Override
	public String getDefaultDatabase() {
		return defaultDatabase;
	}
	
	private void initDefaultDatabase() throws SQLException, InterruptedException {
		String sql=intrinsics.getDefaultDatabaseSQL();
		defaultDatabase=Utils.toString(selectSingle(sql));
	}




	@Override
	public void setDefaultDatabase(String db) {
		defaultDatabase=db;
	}	
	@Override
	public int getMaxCachedPreparedStatements() {
		return maxCachedPreparedStatements;
	}
	@Override
	public void setMaxCachedPreparedStatements(int num) {
		maxCachedPreparedStatements=num;
	}
	
	@Override
	public long getRetryTimeoutMS() {
		return retryTimeout;
	}
	@Override
	public void setRetryTimeout(TimeUnit tu, long timeout) {
		retryTimeout=TimeUnit.MILLISECONDS.convert(timeout, tu);
	}
	
	


	abstract class DBIntristics implements Intrinsics {
		List<Pattern> neverRetryPatterns=new ArrayList<Pattern>();
		void addNeverRetryPattern(String rg) {
			neverRetryPatterns.add(Pattern.compile(rg, Pattern.DOTALL| Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
			
		}		

		public abstract String getDefaultDatabaseSQL();

		boolean isNeverRetryable(SQLException ex) {
			if (ex instanceof ExtendedSQLException)
				ex=(SQLException)ex.getCause();
			String msg=ex.getMessage();
			if (msg==null) return false;
			for (Pattern p : neverRetryPatterns) {
				if (p.matcher(msg).matches())
					return true;
			}
			return false;
		}
		public String getUseDatabaseSQL(String db) {
			return "use "+db;
		}
	}

	private DBIntristics getOracleIntristics() {
		return  new DBIntristics() {
			{
				//addNeverRetryPattern("(?i:.*no\\s+privileges\\s+on\\s+tablespace.*)");
				addNeverRetryPattern("(?i:.*invalid\\s+datatype.*)");
				addNeverRetryPattern("(?i:.*user\\s+.+\\s+does not exist.*)");
				addNeverRetryPattern("(?i:.*no\\s+privileges\\s+on\\s+.*)");
				addNeverRetryPattern("(?i:.*need\\s+to\\s+specify\\s+.*)");
				addNeverRetryPattern("(?i:.*unimplemented\\s+feature\\s+.*)");
			}
			public String getUTCTimestampFunction() {return "SYSTIMESTAMP AT TIME ZONE 'UTC'";}
			public String getUTCTimestampFunctionSelect() {return "select SYSTIMESTAMP AT TIME ZONE 'UTC' from dual";}
			public String getLongSequenceTypeName() {return "NUMBER";}
			public String getUseDatabaseSQL(String db) {
				return "ALTER SESSION SET CURRENT_SCHEMA = "+db;
			}
			public char getEscapeChar() {return '"';}
			public String getDefaultDatabaseSQL() {
				throw new RuntimeException("UNIMPLEMENTED");
			}
			
		};
	};
	private DBIntristics getTDSIntristics() {
		return  new DBIntristics() {
			public String getUTCTimestampFunctionSelect() {return "select getutcdate()";}
			public String getUTCTimestampFunction() {return "getutcdate()";}
			public String getLongSequenceTypeName() {return "BIGINT";}
			public char getEscapeChar() {return '\'';}
			public String getDefaultDatabaseSQL() {
				throw new RuntimeException("UNIMPLEMENTED");
			}
		};
	}
	private DBIntristics getPostgressIntristics() {
		return  new DBIntristics() {
			public String getUTCTimestampFunctionSelect() {return "select getutcdate()";}
			public String getUTCTimestampFunction() {return "getutcdate()";}
			public String getLongSequenceTypeName() {return "BIGINT";}
			public char getEscapeChar() {return '"';}
			public String getDefaultDatabaseSQL() {
				return "select current_database()";
			}
		};
	}
	private DBIntristics getMySQLIntristics() {
		return  new DBIntristics() {
			{
				addNeverRetryPattern("(?i:.*invalid\\s+argument\\s+value.*)");
				addNeverRetryPattern("(?i:.*incorrect\\s+.*\\s+value.*)");
				addNeverRetryPattern("(?i:.*access\\s+denied\\s+.*)");
				addNeverRetryPattern("(?i:.*out\\s+of\\s+range\\s+value.*)");
				addNeverRetryPattern("(?i:.*error\\s+in\\s+your\\s+sql\\s+syntax.*)");
				addNeverRetryPattern("(?i:.*column\\s+count\\s+doesn't\\s+match\\s+.*)");
			}
			public String getUTCTimestampFunctionSelect() {return "select utc_timestamp()";}
			public String getUTCTimestampFunction() {return "utc_timestamp()";}
			public String getLongSequenceTypeName() {return "BIGINT";}
			public char getEscapeChar() {return '`';}
			public String getDefaultDatabaseSQL() {
				return "select database()";
			}
		};
	}
	
	@Override
	public Intrinsics getIntrinsics() {
		return intrinsics;
	}
	
	public DBIntristics getDBIntrinsics() {
		return intrinsics;
	}

	@Override
	protected void finalize() {
		close();
	}

	@Override
	public void close() {
		txCommitContext.close();
		nonTxCommitContext.close();
	}

	@Override
	public int getMaxConnections() {
		return maxConnections;
	}
	@Override
	public void allowOverborrow(boolean allowOverborrow) {
		this.allowOverborrow=allowOverborrow;
	}

	@Override
	public void setMaxConnections(int max) {
		maxConnections=max>0?max:1;
	}
	@Override
	public int getBatchSize() {
		return batchSize;
	}
	@Override
	public void setBatchSize(int bs) {
		batchSize=bs;
	}

	final static class StickyBlock {
		final public StatementBlock<Void> onConnectionInitBlock;
		final public StatementBlock<Void> onConnectionCloseBlock;
		public StickyBlock(StatementBlock<Void> onConnectionInitBlock,	StatementBlock<Void> onConnectionCloseBlock) {
			this.onConnectionInitBlock = onConnectionInitBlock;
			this.onConnectionCloseBlock = onConnectionCloseBlock;
		}
		
	}


	AtomicLong stickyNum=new AtomicLong();
	CommitContext getCommitContext(boolean autoCommit) {
		return autoCommit?nonTxCommitContext:txCommitContext;
	}
	
	@Override
	public Object addInitStatement(boolean autoCommit, StatementBlock<Void> onConnectionInitBlock, StatementBlock<Void> onConnectionCloseBlock) {
		StickyBlock block=new StickyBlock(onConnectionInitBlock,onConnectionCloseBlock);
		CommitContext cx = getCommitContext(autoCommit);
		cx.getLock().lock();
		try {
			return cx.addInitStatement(block);
		} finally {
			cx.getLock().unlock();
		}
	}


	@Override
	public Set<? extends Object> getInitStatementKeys(boolean autoCommit) {
		CommitContext cx = getCommitContext(autoCommit);
		Set<Long> ret=null;
		cx.getLock().lock();
		try {
			ret = new TreeSet<>(cx.getInitStatementKeys());
		} finally {
			cx.getLock().unlock();
		}
		return Collections.unmodifiableSet(ret);
	}

	@Override
	public  boolean removeInitStatementKey(boolean autoCommit, Object key) {
		CommitContext cx = getCommitContext(autoCommit);
		cx.getLock().lock();
		try {
			return cx.removeInitStatementKey(key);
		} finally {
			cx.getLock().unlock();
		}
	}


	private static final Profiler profiler=createProfiler();
	
	private static Profiler createProfiler() {
		if ("none".equalsIgnoreCase(System.getenv("DB_PROFILER"))) {
			return new Profiler() {
				public final Object onStart(Object conId, String sql, Object[] args) {return null;}
				public final void onEnd(Object ticket, boolean success, SQLException sex) {}
				public final  void setConnectionInfo(Object ticket, Object conId) {}
			};
		} else {
			DBProfilerWebServer w=new DBProfilerWebServer();
			int portStart=65535;
			int portEnd=1024;
			String sportStart=System.getenv("DB_PROFILER_WEB_PORT_START");
			String sportEnd=System.getenv("DB_PROFILER_WEB_PORT_END");

			if (sportStart!=null) try {portStart=Integer.parseInt(sportStart);} catch (Exception ee) {}
			if (sportEnd!=null) try {portEnd=Integer.parseInt(sportEnd);} catch (Exception ee) {}
			w.start(portStart, portEnd);
			return w;
		}
	}


	
	final Object profilerStart(ConnectionWrapImpl cw, String sql, Object... args) {
		return profiler.onStart(cw.getConnectionId(), sql,args);
	}

	final void profilerEnd(Object ticket, boolean success, SQLException sex) {
		profiler.onEnd(ticket,success,sex);		
	}

	final void profilerSetConnectionInfo(Object ticket, Object conId) {
		profiler.setConnectionInfo(ticket,conId);
		
	}


	
	/*
	@Override
	public synchronized void detachDynamicTable(String tableName) {
		Object sk=dynamicTables.get(tableName);
		if (sk!=null) removeInitStatementKey(false, sk);
	}

		
	@Override
	public  boolean appendDynamicTable(String tableName) {
		txCommitContext.getLock().lock();
		try {
			if (dynamicTables.containsKey(tableName)) return false;
	
			String createSQL="create temporary table if not exists "+tableName+" (action_id int, record_id int not null, index (action_id)) engine=MEMORY";
			String createVarcharSQL="create temporary table if not exists "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.VARCHAR)+" (record_id int not null, col_id int not null, val VARCHAR(255) not null, primary key (record_id,col_id)) engine=MEMORY";
			String createTextSQL="create temporary table if not exists "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.TEXT)+" (record_id int not null, col_id int not null, val TEXT not null, primary key (record_id,col_id)) engine=MEMORY";
			String createLongSQL="create temporary table if not exists "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.LONG)+" (record_id int not null, col_id int not null, val BIGINT not null, primary key (record_id,col_id)) engine=MEMORY";
			String createUUIDSQL="create temporary table if not exists "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.UUID)+" (record_id int not null, col_id int not null, val BINARY(16) not null, primary key (record_id,col_id)) engine=MEMORY";
			String createBlobSQL="create temporary table if not exists "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.BLOB)+" (record_id int not null, col_id int not null, val BLOB not null, primary key (record_id,col_id)) engine=MEMORY";
			
			String dropSQL="drop temporary table if exists  "+tableName;
			String dropVarcharSQL="drop temporary table if exists  "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.VARCHAR);
			String dropTextSQL="drop temporary table if exists  "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.TEXT);
			String dropLongSQL="drop temporary table if exists "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.LONG);
			String dropUUIDSQL="drop temporary table if exists  "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.UUID);
			String dropBlobSQL="drop temporary table if exists "+DynamicTable.getTypeTable(tableName,DynamicTable.FieldType.BLOB);
	
			final String create=createSQL+";\n"
					+createVarcharSQL+";\n"
					+createTextSQL+";\n"
					+createLongSQL+";\n"
					+createUUIDSQL+";\n"
					+createBlobSQL+";\n";
			final String drop=dropSQL+";\n"
					+dropVarcharSQL+";\n"
					+dropTextSQL+";\n"
					+dropLongSQL+";\n"
					+dropUUIDSQL+";\n"
					+dropBlobSQL+";\n";
	
			
			StatementBlock<Void> onConnectionInitBlock=new StatementBlock<Void>() {
				public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					cw.update(create);
					return null;
				}			
			};
			
			StatementBlock<Void> onConnectionCloseBlock=new StatementBlock<Void>() {
				public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					cw.update(drop);
					return null;
				}			
			};
			
			Object key=addInitStatement(false, onConnectionInitBlock, onConnectionCloseBlock);
			
			dynamicTables.put(tableName, key);
			return true;
		} finally {
			txCommitContext.getLock().unlock();
		}
		
	}
*/



	@Override
	public DynamicTableSet createDynamicTableSet(String name) throws SQLException, InterruptedException {
		DynamicTableSetImpl dt=null;
		switch (getDialect()) {
		case MYSQL:
		case DRIZZLE:
		case DRIZZLE_MYSQL:
			dt=new DynamicTableSetMySQL(name);
			break;
		case ORACLE:
			dt=new DynamicTableSetOracle(name);
			break;
		case TDS:
			dt=new DynamicTableSetMSSQL(name);
			break;
		default:
			throw new RuntimeException("Unsupported feature for "+getDialect());
		}
		dt.init(this);
		return dt;
	}




	
	@Override
	public Future<Set<TableName>> getInitialUserTableNames() {
		Future<Set<TableName>> tn=initialTableNames;
		if (tn==null) {
			tn=getUserTableNames();
			initialTableNames=tn;
		}
		return tn;
	}
	void resetInitialUserTableNames() {
		initialTableNames=getUserTableNames();
	}




	@Override
	public Future<Set<TableName>> getUserTableNames() {
		return AsyncEngine.getEngineExecutorService().submit(new Callable<Set<TableName>>() {
			public Set<TableName> call() throws Exception {
				String sql=null;
				switch (getDialect()) {
				case ORACLE:
					sql="select NULL, OWNER, TABLE_NAME from ALL_TABLES where OWNER NOT IN ('MDSYS', 'OUTLN', 'CTXSYS', 'OLAPSYS', 'AUDSYS', 'FLOWS_FILES','SYSTEM','DBSNMP','GSMADMIN_INTERNAL','OJVMSYS','ORDSYS','APPQOSSYS','XDB', 'ORDDATA', 'SYS', 'WMSYS','APEX_040200')\n"+
						"union all\n"+
						"select NULL, OWNER, VIEW_NAME from ALL_VIEWS where OWNER NOT IN ('MDSYS', 'OUTLN', 'CTXSYS', 'OLAPSYS', 'AUDSYS', 'FLOWS_FILES','SYSTEM','DBSNMP','GSMADMIN_INTERNAL','OJVMSYS','ORDSYS','APPQOSSYS','XDB', 'ORDDATA', 'SYS', 'WMSYS','APEX_040200')";
					break;
				case MYSQL:
				case DRIZZLE:
				case DRIZZLE_MYSQL:					
					sql="select NULL, TABLE_SCHEMA, TABLE_NAME  from information_schema.tables where TABLE_SCHEMA not in ('information_schema', 'mysql', 'performance_schema', 'sys')";
					break;
				case TDS:
					sql="select TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME  from information_schema.tables";
					break;
				default:
					throw new RuntimeException("Unimplemented feature");
				}
				final TreeSet<TableName> ret=new TreeSet<TableName>();
				select(sql, false, new ArrayRowHandler() {
					@Override
					public void reset() throws InterruptedException {}
					@Override
					public void onRow(ResultSetMetaData md, Object[] row) throws SQLException, InterruptedException {
						ret.add(new TableName(Utils.toString(row[0]), Utils.toString(row[1]), Utils.toString(row[2])));
					}
					
				});
				return Collections.<TableName>unmodifiableSortedSet(ret);
			}
		});
	}



	static String[] split(char escape,String name) {

		boolean useEscape= name.charAt(0)==escape;
		int current= useEscape?1:0;
		
		StringBuilder sb=new StringBuilder();
		ArrayList<String> ret=new ArrayList<String>();
		for (;current<name.length();++current) {
			char c=name.charAt(current);
			if (useEscape) {
				if (c==escape) {
					ret.add(sb.toString());
					sb.setLength(0);
					if (current==name.length()-1) break;
					++current;
					if (name.charAt(current)!='.') {
						throw new RuntimeException("Expect '.' character at position "+current+" in "+name);
					}
					if (name.charAt(current+1)!=escape) {
						useEscape=false; //i'm at . of "`a`.b"
					} else {
						//i'm at . of "`a`.`b`", move current to '`', then it will be moved to b
						++current;
					}
					continue;
				}
				if (current==name.length()-1) throw new RuntimeException("Invalid name "+name);
				sb.append(c);
			} else {
				if (c=='.') {
					ret.add(sb.toString());
					sb.setLength(0);
					if (current==name.length()-1) throw new RuntimeException("Invalid name "+name);
					if (name.charAt(current+1)==escape) {
						useEscape=true;
						++current;
					}
					continue;
				}
				sb.append(c);
				if (current==name.length()-1) {
					ret.add(sb.toString());
					break;
				}
			}
		}
		return ret.toArray(new String[ret.size()]);
	}

	@Override
	public TableName getTableName(String tableName) throws SQLException, InterruptedException {
		String[] split=null;
		char escape=getIntrinsics().getEscapeChar();
		switch (getDialect()) {
		case MYSQL:
		case DRIZZLE:
		case DRIZZLE_MYSQL:
			split=split(escape,tableName);
			if (split.length==2)
				return new TableName(null, split[0], split[1]);
			else
				return new TableName(null, findDefaultDatabase(), split[0]);
		case ORACLE:
			split=split(escape,tableName);
			if (split.length==2)
				return new TableName(null, split[0], split[1]);
			else
				return new TableName(null, findDefaultDatabase(), split[1]);
		case TDS:
			split=split(escape,tableName);
			if (split.length==3)
				return new TableName(split[0], split[1], split[2]);
			if (split.length==2)
				return new TableName(findDefaultDatabase(), split[0], split[1]);
			else
				return new TableName(findDefaultDatabase(), null, split[0]);
		case POSTGRESS:
			split=split(escape,tableName);
			if (split.length==2)
				return new TableName(null, split[0], split[1]);
			else
				return new TableName(null, null, split[1]);
		default:
			throw new RuntimeException("Unimplemented feature");
		}
	}




	public String getVersion() {
		return version;
	}




	public void setVersion(String version, String versionComment) {
		this.version=version;
		this.versionComment=versionComment;
		
	}




	public String getVersionComment() {
		return versionComment;
	}






	
}
