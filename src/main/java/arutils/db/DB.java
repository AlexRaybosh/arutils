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

package arutils.db;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import arutils.db.impl.DBImpl;

/**
 * an object to work with a relational database. Provides independent pooling of connection in both autommit=true, autommit=false contexts.
 * @author Alex Raybosh
 *
 */
public abstract class DB {
	
	public static enum Dialect {
		MYSQL,	DRIZZLE_MYSQL, DRIZZLE, TDS, ORACLE, POSTGRESS, UNKNOWN
	}
	
	
	public abstract DB clone();

	/**
	 * Executes sql statement in autocommit context. Internally takes a {@link ConnectionWrap} from a pool of connections with autocommit=true.
	 * @param sql Select SQL, with binded variables represented by ?. Number of binded variables must match number of args. 
	 * @param cache To cache PreparedStatement for future use
	 * @param args 
	 * @return a list of rows
	 * @throws SQLException
	 * @throws InterruptedException
	 */
	public abstract List<Object[]> select(String sql, boolean cache, Object... args) throws SQLException, InterruptedException;
	/**
	 * A wrapper on top of {@link #select(String sql, boolean cache, Object... args)} method, with no caching, and no arguments
	 * @param sql
	 * @return a list of rows
	 * @throws SQLException
	 * @throws InterruptedException
	 */
	public List<Object[]> select(String sql) throws SQLException, InterruptedException {return select(sql,false);}
	public abstract List<Object> selectFirstColumn(String sql, boolean cache, Object... args) throws SQLException, InterruptedException;
	public abstract List<Map<String, Object>> selectLabelMap(String sql, boolean cache, Object... args) throws SQLException, InterruptedException;
	public abstract void select(String sql, boolean cache, ArrayRowHandler rh, Object... args) throws SQLException, InterruptedException;
	public abstract void select(String sql, boolean cache, LabelRowHandler rh, Object... args) throws SQLException, InterruptedException;
	public abstract void select(String sql, boolean cache, ResultSetHandler rh, Object... args) throws SQLException, InterruptedException;
	public abstract <T> T selectSingle(String sql, boolean cache, Object... args)  throws SQLException, InterruptedException;
	public <T> T selectSingle(String sql)  throws SQLException, InterruptedException {
		return selectSingle(sql, false);
	}
	
	public abstract int update(String sql, boolean cache, Object... args) throws SQLException, InterruptedException;
	public int update(String sql) throws SQLException, InterruptedException {return update(sql, false);}
	public abstract int update(Collection<Number> genKeys, String sql, boolean cache, Object... args) throws SQLException, InterruptedException;

	/**
	 * Provides default retry logic for a {@link StatementBlock#execute(ConnectionWrap)} invocation
	 * Upon each failure underlying SQL Connection in {@link ConnectionWrap} object will be closed, and reopened on a first SQL invocation after retry. 
	 * @param tb
	 * @return {@link StatementBlock#execute(ConnectionWrap)} return value
	 * @throws SQLException
	 * @throws InterruptedException
	 */
	public abstract <Ret> Ret autocommit(StatementBlock<Ret> tb) throws SQLException, InterruptedException;
	/**
	 * Wraps execution of a StatementBlock in a transaction. Provides default retry mechanism. The retry can be altered by modifying timeout, or {@link StatementBlock#onError(ConnectionWrap, boolean, SQLException, long, long)} return value.
	 * Upon each failure underlying SQL Connection in {@link ConnectionWrap} object will be closed, and reopened on a first SQL invocation after the retry.
	 * @param StatementBlock
	 * @return the result produced by {@link StatementBlock#execute(ConnectionWrap)} method
	 * @throws SQLException Failed to retry. Signals, that there is a failure occurred either during {@link StatementBlock#execute(ConnectionWrap)} call, or during {@link ConnectionWrap#beforeCommit(StatementBlock)}, or during actual commit
	 * @throws InterruptedException The thread has been interrupted
	 */
	public abstract <Ret> Ret commit(StatementBlock<Ret> tb) throws SQLException, InterruptedException;

	
	public static DB create(String url, String user, String password, Properties props) {
		DBImpl db=new DBImpl(url,user,password,props == null ? new Properties() : new Properties(props));
		DBImpl.register(db);
		return db;
	}
	public static DB create(String url, String user, String password) {
		return create( url,  user,  password, new Properties());
	}

	public abstract Dialect getDialect();

	

	public abstract String getUrl();
	public abstract int getTransactionIsolation();
	public abstract void setTransactionIsolation(int ti);
	public abstract String getDefaultDatabase() throws SQLException, InterruptedException;
	public abstract void setDefaultDatabase(String db);
	public abstract int getMaxCachedPreparedStatements();

	public static interface Intrinsics { 
		public char getEscapeChar();
		public String getUTCTimestampFunction();
		public String getUTCTimestampFunctionSelect();
		public String getLongSequenceTypeName();
	}
	public abstract Intrinsics getIntrinsics();

	public abstract void close();

	public abstract void setMaxCachedPreparedStatements(int num);

	public abstract int getMaxConnections();
	public abstract void setMaxConnections(int max);

	
	public abstract long getRetryTimeoutMS();
	public abstract void setRetryTimeout(TimeUnit tu, long timeout);

	public abstract int getBatchSize();
	public abstract void setBatchSize(int bs);
	
	public abstract Object addInitStatement(boolean autoCommit, StatementBlock<Void> onConnectionInitBlock, StatementBlock<Void> onConnectionCloseBlock);

	
	public abstract Set<? extends Object> getInitStatementKeys(boolean autoCommit);
	public abstract boolean removeInitStatementKey(boolean autoCommit,Object key);
	public Object addInitSqlWithArgs(boolean autoCommit, final String sql, final Object... args) {
		return addInitStatement(autoCommit,sql==null?null:new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				cw.update(sql, false, args);
				return null;
			}
		}, null);
	};
	public Object addInitSqlWithCleanup(boolean autoCommit, final String sql, final String cleanupSql) {
		return addInitStatement(false,sql==null?null:new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				cw.update(sql, false);
				return null;
			}
		}, cleanupSql==null?null:new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				cw.update(cleanupSql, false);
				return null;
			}
		});
	};
	public Object addInitSqlWithArgs(final String sql, final Object... args) {
		return addInitSqlWithArgs(false,sql,args);
	}


	
	public abstract Future<Set<TableName>> getInitialUserTableNames();
	public abstract Future<Set<TableName>> getUserTableNames();
	public static <T> T resolve(Future<? extends T> f) throws SQLException, InterruptedException {
		try {
			return f.get();
		} catch (InterruptedException e) {
			throw e;
		} catch (ExecutionException  e) {
			if (e.getCause() instanceof SQLException) throw (SQLException)e.getCause();
			if (e.getCause() instanceof InterruptedException) throw (SQLException)e.getCause();
			if (e.getCause()!=null) throw new RuntimeException(e.getCause());
			throw new RuntimeException(e);
		}
		
	}
	public abstract TableName getTableName(String name) throws SQLException, InterruptedException;

	public abstract DynamicTableSet createDynamicTableSet(String name) throws SQLException, InterruptedException;

	public abstract String findDefaultDatabase() throws SQLException, InterruptedException ;
	
	public abstract void allowOverborrow(boolean allowOverborrow);
	
	
}