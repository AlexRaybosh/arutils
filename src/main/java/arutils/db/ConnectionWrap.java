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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import arutils.async.Request;

/**
 * Encapsulates database connection. The underlying connection can be closed/reopened. Each connection is dedicated to either autocommit=true, or autocommit=false. 
 * Provides convenience methods for common SQL operation
 * @author Alex Raybosh
 * 
 * 
 */
public abstract class ConnectionWrap {

	public abstract void commit() throws SQLException, InterruptedException;
	public abstract void rollback() throws InterruptedException;
	public abstract void close() throws InterruptedException;

	public abstract List<Object[]> select(String sql, boolean cache, Object... args) throws SQLException, InterruptedException;
	public abstract List<Map<String, Object>> selectLabelMap(String sql,boolean cache, Object... args) throws SQLException, InterruptedException;
	public abstract void select(String sql, boolean cache, ArrayRowHandler rh, Object... args) throws SQLException, InterruptedException;
	public abstract void select(String sql, boolean cache, LabelRowHandler rh, Object... args) throws SQLException, InterruptedException;
	public abstract void select(String sql, boolean cache, ResultSetHandler rh, Object... args) throws SQLException, InterruptedException;
	public abstract List<Object> selectFirstColumn(String sql, boolean cache, Object... args) throws SQLException, InterruptedException;
	public abstract <T> T selectSingle(String sql, boolean cache, Object... args)  throws SQLException, InterruptedException;
	public <T> T selectSingle(String sql)  throws SQLException, InterruptedException {
		return selectSingle(sql, false);
	}
	
	public abstract int update(String sql, boolean cache, Object... args) throws SQLException, InterruptedException;
	public int update(String sql) throws SQLException, InterruptedException {
		return update(sql, false);
	}
	public abstract int update(Collection<Number> genKeys, String sql, boolean cache, Object... args) throws SQLException, InterruptedException;

	public abstract void batchUpdate(String sql, boolean useCache, BatchInputIterator it) throws SQLException, InterruptedException;
	public abstract void batchInsert(String sql, BatchInputIterator it) throws SQLException, InterruptedException;

	public abstract int binaryStreamUpdate(String sql, boolean useCache, int[] blobPositions, Object... args) throws SQLException, InterruptedException;
	
	public void batchUpdate(String sql, boolean useCache, final Collection<Object[]> argsRows) throws SQLException, InterruptedException {
		batchUpdate(sql, useCache, new BatchInputIterator() {
			Iterator<Object[]> it=argsRows.iterator();
			Object[] row=null;
			public boolean hasNext() {return it.hasNext();}
			public void next() {row=it.next();}
			public Object get(int idx) {return row[idx];}
			public void reset() {it=argsRows.iterator();}
			public int getColumnCount() {return row.length;}
		}); 
	}	
	public void batchInsertSingleColumn(String sql, final Collection<? extends Object> argsRows) throws SQLException, InterruptedException {
		batchInsert(sql, new BatchInputIterator() {
			Iterator<?> it=argsRows.iterator();
			Object row=null;
			public boolean hasNext() {return it.hasNext();}
			public void next() {row=it.next();}
			public Object get(int idx) {return row;}
			public void reset() {it=argsRows.iterator();}
			public int getColumnCount() {return 1;}
		}); 
	}	
	public void batchInsert(String sql, final Collection<Object[]> argsRows) throws SQLException, InterruptedException {
		batchInsert(sql,new BatchInputIterator() {
			Iterator<Object[]> it=argsRows.iterator();
			Object[] row=null;
			public boolean hasNext() {return it.hasNext();}
			public void next() {row=it.next();}
			public Object get(int idx) {return row[idx];}
			public void reset() {it=argsRows.iterator();}
			public int getColumnCount() {return row.length;}
		}); 
	}
	public <T> void batchInsertRequests(String sql, final List<Request<T>> bulk) throws SQLException, InterruptedException {
		batchInsert(sql, new BatchInputIterator() {
			Iterator<Request<T>> it=bulk.iterator();
			Object[] row;
			public boolean hasNext() {return it.hasNext();}
			public void next() {row=it.next().getArgs();}
			public Object get(int idx) {return row[idx];}
			public void reset() {it=bulk.iterator();}
			public int getColumnCount() {return row.length;}
			
		});
	}
	
	public abstract DB getDB();
	public abstract void syncUpInitStatements() throws SQLException, InterruptedException;
	public abstract boolean getAutoCommit();
	public abstract void beforeCommit(StatementBlock<Void> statementBlock);
	

}