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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import arutils.db.ArrayRowHandler;
import arutils.db.BatchInputIterator;
import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.DynamicTableSet;
import arutils.db.LabelRowHandler;
import arutils.db.ResultSetHandler;
import arutils.db.StatementBlock;
import arutils.db.DB.Dialect;
import arutils.db.impl.DBImpl.CommitContext;
import arutils.db.impl.DBImpl.StickyBlock;
import arutils.util.Utils;

public class ConnectionWrapImpl extends ConnectionWrap {

	final private CommitContext cx;
	final private DBImpl db;
	final private boolean autoCommit;
	private Connection con;
	private Object conId;
	private static final BigDecimal BIG_LONG_MAX = new BigDecimal(Long.MAX_VALUE);
	private static final BigDecimal BIG_LONG_MIN = new BigDecimal(Long.MIN_VALUE);
	LRUCache<String, PreparedStatement> psCache, psGenCache;
	LRUCache<String, BulkRewrite> bulkRewriteCache;
	private long stickyVersion=0;
	private TreeMap<Long, StickyBlock> knownInitBlocks=new TreeMap<>();
	private Set<StatementBlock<Void>> beforeCommitSet=new LinkedHashSet<StatementBlock<Void>>();


	ConnectionWrapImpl(CommitContext commitContext, boolean autoCommit) {
		this.cx=commitContext;
		this.db=cx.getDB();
		this.autoCommit=autoCommit;
		psCache=new LRUCache<>(db.getMaxCachedPreparedStatements());
		psGenCache=new LRUCache<>(db.getMaxCachedPreparedStatements());
		bulkRewriteCache=new LRUCache<>(db.getMaxCachedPreparedStatements());
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#commit()
	 */
	@Override
	public void commit() throws SQLException, InterruptedException {
		if (!beforeCommitSet.isEmpty()) 
			try {
				for (StatementBlock<Void> s : beforeCommitSet) {
					s.execute(this);
				}
			} finally {
			beforeCommitSet.clear();
		}
		Object ticket=db.profilerStart(this, "commit");
		boolean success=false;
		SQLException sex=null;
		try {
			if (con!=null) 
				con.commit();
			success=true;
		} catch (SQLException ex) {
			if (Thread.interrupted()) throw new InterruptedException();
			sex=ex;
			throw sex;
		} finally {
			db.profilerEnd(ticket,success,sex);
		}
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#rollback()
	 */
	@Override
	public void rollback() throws InterruptedException {
		if (con!=null)
			try {con.rollback();} catch (SQLException e) {}		
	}

	
	@Override
	public void beforeCommit(StatementBlock<Void> statementBlock) {
		beforeCommitSet.add(statementBlock);
	}

	static void toSqlException(Throwable t) throws SQLException {
		if (t instanceof SQLException)
			throw (SQLException)t;
		if (t instanceof RuntimeException)
			throw (RuntimeException)t;
		throw new RuntimeException(t);
	}


	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#close()
	 */
	@Override
	public void close() throws InterruptedException  {
		closeStatements();
		closeSticky();
		close(con);
		con=null;
	}

	private void closeStatements() {
		for (PreparedStatement ps : psCache.values()) close(ps);
		psCache.clear();
		for (PreparedStatement ps : psGenCache.values()) close(ps);
		psGenCache.clear();
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#update(java.lang.String, boolean, java.lang.Object)
	 */
	@Override
	public int update(String sql, boolean cache, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		int result;
		try {
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				result=st.executeUpdate(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, args);
				result=ps.executeUpdate();
			}
			success=true;
			return result;
		} catch (SQLException ex) {
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
			return 0;
		} finally {
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
		}
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#update(java.util.Collection, java.lang.String, boolean, java.lang.Object)
	 */
	@Override
	public int update(Collection<Number> genKeys, String sql, boolean cache, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		int result;
		try {
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				result=st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
				ResultSet rs=st.getGeneratedKeys();
				try {
					while (rs.next()) {
						Number k=(Number)rs.getObject(1);
						genKeys.add(k);
					}
				} finally {
					rs.close();
				}
			} else {
				ps = getPrepraredStatementGenKeys(ticket,sql,cache);
				applyArgs(ps, args);
				result=ps.executeUpdate();
				ResultSet rs=ps.getGeneratedKeys();
				try {
					while (rs.next()) {
						Number k=(Number)rs.getObject(1);
						genKeys.add(k);
					}
				} finally {
					rs.close();
				}
			}
			success=true;
			return result;
		} catch (SQLException ex) {
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
			return 0;
		} finally {
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
		}
	}
	
	private void throwExtendedSQLException(String sql, boolean cache, SQLException ex, Object... args) throws InterruptedException, SQLException {
		if (Thread.interrupted()) throw new InterruptedException();
		/*if (ex.getCause()!=null)
			ex.getCause().printStackTrace();*/
		//ex.printStackTrace();
		if (ex instanceof ExtendedSQLException) throw ex;
		throw new ExtendedSQLException(conId, ex, sql, cache, args);
	}

	
	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#select(java.lang.String, boolean, java.lang.Object)
	 */
	@SuppressWarnings("resource")
	@Override
	public List<Object[]> select(String sql, boolean cache, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		try {
			
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				rs = st.executeQuery(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, args);
				rs = ps.executeQuery();
			}
			ResultSetMetaData rm = rs.getMetaData();
			int numberOfColumns = rm.getColumnCount();
			List<Object[]> result = new ArrayList<Object[]>();
			while (rs.next()) {
				if (Thread.interrupted()) throw new InterruptedException();
				Object[] row = new Object[numberOfColumns];
				for (int i = 0; i < numberOfColumns; i++)
					row[i] = normalizeGetObject(rm, rs, i + 1);
				result.add(row);
			}
			success=true;
			return result;
		} catch (SQLException ex) {		
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
			return null;
		} finally {
			close(rs);
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
		}
	}





	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#selectLabelMap(java.lang.String, boolean, java.lang.Object)
	 */
	@SuppressWarnings("resource")
	@Override
	public List<Map<String,Object>> selectLabelMap(String sql, boolean cache, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		try {
			
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				rs = st.executeQuery(sql);
			} else {
				ps = getPrepraredStatement(ticket, sql,cache);
				applyArgs(ps, args);
				rs = ps.executeQuery();
			}
			ResultSetMetaData rm = rs.getMetaData();
			int numberOfColumns = rm.getColumnCount();
			List<Map<String,Object>> result = new ArrayList<>();
			while (rs.next()) {
				if (Thread.interrupted()) throw new InterruptedException();
				Map<String,Object> row = new HashMap<>(numberOfColumns);
				for (int i = 0; i < numberOfColumns; i++)
					row.put(rm.getColumnLabel(i+1), normalizeGetObject(rm, rs, i + 1));
				result.add(row);
			}
			success=true;
			return result;
		} catch (SQLException ex) {		
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
			return null;
		} finally {
			close(rs);
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
		}
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#select(java.lang.String, boolean, arutils.util.db.ArrayRowHandler, java.lang.Object)
	 */
	@SuppressWarnings("resource")
	@Override
	public void select(String sql, boolean cache, ArrayRowHandler rh, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		try {
			
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				rs = st.executeQuery(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, args);
				rs = ps.executeQuery();
			}
			ResultSetMetaData rm = rs.getMetaData();
			int numberOfColumns = rm.getColumnCount();
			while (rs.next()) {
				if (Thread.interrupted()) throw new InterruptedException();
				Object[] row = new Object[numberOfColumns];
				for (int i = 0; i < numberOfColumns; i++)
					row[i] = normalizeGetObject(rm, rs, i + 1);
				rh.onRow(rm, row);
			}
			success=true;
		} catch (SQLException ex) {		
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
		} finally {
			close(rs);
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
			if (!success) rh.reset();
		}
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#select(java.lang.String, boolean, arutils.util.db.LabelRowHandler, java.lang.Object)
	 */
	@SuppressWarnings("resource")
	@Override
	public void select(String sql, boolean cache, LabelRowHandler rh,Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		try {
			
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				rs = st.executeQuery(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, args);
				rs = ps.executeQuery();
			}
			ResultSetMetaData rm = rs.getMetaData();
			int numberOfColumns = rm.getColumnCount();
			Map<String,Integer> labelToPos=new HashMap<String, Integer>();
			for (int i = 1; i <= numberOfColumns; i++)
				labelToPos.put(rm.getColumnLabel(i), i);
			final ResultSet myrs=rs;
			while (rs.next()) {
				if (Thread.interrupted()) throw new InterruptedException();
				Map<String,Object> row = new HashMap<>(numberOfColumns);
				for (int i = 1; i <= numberOfColumns; i++)
					row.put(rm.getColumnLabel(i), normalizeGetObject(rm, rs, i));
				rh.onRow(rm, new Map<String,Object>(){
					public int size() {return labelToPos.size();}
					public boolean isEmpty() {return labelToPos.size()==0;}
					public boolean containsKey(Object key) {return labelToPos.containsKey(key);}
					public boolean containsValue(Object value) {throw new RuntimeException("Unimplemented");}
					public Object get(Object key) {
						try {
							return normalizeGetObject(rm, myrs,labelToPos.get(key));
						} catch (SQLException e) {throw new RuntimeException(e);}					
					}
					public Object put(String key, Object value) {throw new RuntimeException("Unimplemented");}
					public Object remove(Object key) {throw new RuntimeException("Unimplemented");}
					public void putAll(Map<? extends String, ? extends Object> m) {throw new RuntimeException("Unimplemented");}
					public void clear() {throw new RuntimeException("Unimplemented");}
					public Set<String> keySet() {return Collections.unmodifiableSet(labelToPos.keySet());}
					public Collection<Object> values() {
						List<Object> row = new ArrayList<>(numberOfColumns);
						for (int i = 1; i <= numberOfColumns; i++)
							try {row.add(normalizeGetObject(rm, myrs, i));} catch (SQLException e) {throw new RuntimeException(e);}
						return row;
					}

					@Override
					public Set<java.util.Map.Entry<String, Object>> entrySet() {
						Set<java.util.Map.Entry<String, Object>> ret=new LinkedHashSet<Map.Entry<String,Object>>();
						for (int i = 1; i <= numberOfColumns; ++i) {
							String k;
							Object v;
							try {
								k = rm.getColumnLabel(i);
								v= normalizeGetObject(rm, myrs, i);
							} catch (SQLException e) {
								throw new RuntimeException(e);
							}
							Map.Entry<String, Object> e=new Entry<String, Object>() {
								private Object value=v;
								private String key=k;
								public Object setValue(Object value) {
									this.value=value;
									return value;
								}
								public Object getValue() {return value;}
								public String getKey() {return key;}
							};
							ret.add(e);
						}	
						return Collections.unmodifiableSet(ret);
					}
					public String toString() {
						return new LinkedHashMap<>(this).toString();
					}
				});
			}
			success=true;
		} catch (SQLException ex) {		
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
		} finally {
			close(rs);
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
			if (!success) rh.reset();
		}
	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#select(java.lang.String, boolean, arutils.util.db.ResultSetHandler, java.lang.Object)
	 */
	@SuppressWarnings("resource")
	@Override
	public void select(String sql, boolean cache, ResultSetHandler rh, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		try {
			
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				rs = st.executeQuery(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, args);
				rs = ps.executeQuery();
			}
			while (rs.next()) {
				if (Thread.interrupted()) throw new InterruptedException();
				rh.onRow(rs);
			}
			success=true;
		} catch (SQLException ex) {		
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
		} finally {
			close(rs);
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
			if (!success) rh.reset();
		}

	}

	/* (non-Javadoc)
	 * @see arutils.util.db.impl.CW#selectFirstColumn(java.lang.String, boolean, java.lang.Object)
	 */
	@SuppressWarnings("resource")
	@Override
	public List<Object> selectFirstColumn(String sql, boolean cache, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		try {
			
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				rs = st.executeQuery(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, args);
				rs = ps.executeQuery();
			}
			ResultSetMetaData rm = rs.getMetaData();
			List<Object> result = new ArrayList<Object>();
			while (rs.next()) {
				if (Thread.interrupted()) throw new InterruptedException();
				result.add(normalizeGetObject(rm, rs, 1));
			}
			success=true;
			return result;
		} catch (SQLException ex) {		
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
			return null;
		} finally {
			close(rs);
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
		}

	}
	
	


	private static void applyArgs(PreparedStatement ps, Object[] args) throws SQLException {
		if (args == null) return;
		for (int pos = 1; pos<=args.length; ++pos) {
			ps.setObject(pos, args[pos-1]);
		}
	}
	private static void close(AutoCloseable ac) {
		if (ac != null) try {ac.close();} catch (Throwable ex) {}
	}
	
	static Object normalizeGetObject(ResultSetMetaData md, ResultSet rs, int pos) throws SQLException {
		int type = md.getColumnType(pos);
		Object obj = null;
		switch (type) {
		case Types.CHAR:
		case Types.VARCHAR:
		case Types.LONGVARCHAR:
		case Types.LONGNVARCHAR:
			String s = rs.getString(pos);
			if (rs.wasNull())
				return null;
			else
				return s;
		case Types.DATE:
			java.sql.Date date = rs.getDate(pos);
			if (rs.wasNull())
				return null;
			else
				return date;
		case Types.TIME:
			java.sql.Time time = rs.getTime(pos);
			if (rs.wasNull())
				return null;
			else
				return time;
		case Types.TIMESTAMP:
		case -101:
			java.sql.Timestamp timestamp = rs.getTimestamp(pos);
			if (rs.wasNull())
				return null;
			else
				return timestamp;
		case Types.CLOB:
			java.sql.Clob clob = rs.getClob(pos);
			if (rs.wasNull())
				return null;
			else {
				return clob.getSubString(1, (int) clob.length());
			}
		case Types.DOUBLE:
			double dbl = rs.getDouble(pos);
			if (rs.wasNull())
				return null;
			return dbl;
		case Types.REAL:
		case Types.FLOAT:
			float flt = rs.getFloat(pos);
			if (rs.wasNull())
				return null;
			return flt;
		case Types.DECIMAL:
			BigDecimal bd = rs.getBigDecimal(pos);
			if (rs.wasNull())
				return null;
			return bd;
		case Types.SMALLINT:
		case Types.TINYINT:
			short asShort = rs.getShort(pos);
			if (rs.wasNull())
				return null;
			return asShort;
		case Types.INTEGER:
/*			int asInt = rs.getInt(pos);
			if (rs.wasNull())
				return null;
			return asInt;
*/
		case Types.BIGINT:
			long asLong = rs.getLong(pos);
			if (rs.wasNull())
				return null;
			return asLong;
		case Types.NUMERIC:
			obj = rs.getObject(pos);
			if (obj == null)
				return null;
			if (obj instanceof Integer)
				return obj;
			if (obj instanceof Long)
				return obj;
			if (obj instanceof Short)
				return obj;
			BigDecimal big = rs.getBigDecimal(pos);
			if (rs.wasNull()) {
				return null;
			} else {
				if (big.scale() > 0 || big.compareTo(BIG_LONG_MAX) > 0 || big.compareTo(BIG_LONG_MIN) < 0) {
					return big;
				} else {
					long it = big.longValue();
					return it;
				}
			}
		case Types.OTHER:
			obj = rs.getObject(pos);
			if (rs.wasNull())
				return null;
			else {
				return obj;
			}

		case Types.BIT:
			boolean b = rs.getBoolean(pos);
			if (rs.wasNull())
				return null;
			return b;
		case Types.BLOB:
			/*
			 * Object o=rs.getObject(pos); if (o==null) return null; if (o
			 * instanceof byte[]) return o; if (o instanceof Blob) { Blob
			 * bl=(Blob)o; int length=(int)bl.length(); byte[] bs=
			 * bl.getBytes(1, length); return bs; }
			 */
			Blob bl = rs.getBlob(pos);
			if (rs.wasNull())
				return null;
			int length = (int) bl.length();
			byte[] bs = bl.getBytes(1, length);
			return bs;
		case Types.BINARY:
		case Types.VARBINARY:
		case Types.LONGVARBINARY:
			Object o = rs.getObject(pos);
			if (o == null || rs.wasNull())
				return null;
			if (o instanceof byte[])
				return o;
			if (o instanceof Blob) {
				bl = (Blob) o;
				length = (int) bl.length();
				bs = bl.getBytes(1, length);
				return bs;
			}
			return o;
		case Types.NULL:
			return rs.getObject(pos);
		default:
			throw new RuntimeException("Not supported SQL Type " + type
					+ " for field " + md.getColumnName(pos));
		}

	}

	private static AtomicLong cnt=new AtomicLong();
	private Connection getConnection(Object ticket) throws SQLException, InterruptedException {
		if (con == null) {
			con = java.sql.DriverManager.getConnection(db.getUrl(), db.getProperties());
			setUpDrizzle();
			con.setAutoCommit(true);
			//con.setTransactionIsolation(db.getTransactionIsolation());
			Statement st = con.createStatement();
			try {
				switch (db.getDialect()) {
					case MYSQL:
					case DRIZZLE:
					case DRIZZLE_MYSQL:
						{
							ResultSet rs =null;
							String version=null, versionComment=null;
							try {
								rs=st.executeQuery("SELECT CONNECTION_ID(), @@version, @@version_comment");
								if (rs.next()) {
									conId=rs.getObject(1);
									version=Utils.toString(rs.getObject(2));
									versionComment=Utils.toString(rs.getObject(3));
								}
								if (version==null) version="unknown";
								if (versionComment==null) versionComment="unknown";
								
							} catch(SQLException ex) {
								conId="unknown-"+cnt.incrementAndGet();
								version="unknown";
								versionComment="unknown";
							} finally {
								close(rs);
							}
							if (db.getVersion()==null) db.setVersion(version, versionComment); 
							
						}
						break;
					case TDS:
						conId=trySingleSelectValue(st,"select @@SPID","unknown-"+cnt.incrementAndGet());
						break;
					case POSTGRESS:
						conId=trySingleSelectValue(st,"select pg_backend_pid()","unknown-"+cnt.incrementAndGet());
						break;
					case ORACLE:
						conId=trySingleSelectValue(st,"SELECT SID || ',' ||  SERIAL# || ',@' || INST_ID FROM GV$SESSION "
								+ "WHERE AUDSID = Sys_Context('USERENV', 'SESSIONID') AND SID = Sys_Context('USERENV', 'SID')",
								"unknown-"+cnt.incrementAndGet());
						tryExecuteUpdate(st, "alter session set statistics_level = basic");
						tryExecuteUpdate(st, "alter session set OPTIMIZER_INDEX_COST_ADJ = 1");
						tryExecuteUpdate(st,"alter session set OPTIMIZER_INDEX_CACHING = 100");
						tryExecuteUpdate(st,"alter session set OPTIMIZER_MODE = 'FIRST_ROWS'");
						if (new Boolean(System.getProperty("ORACLE_SQL_TRACE")))
							tryExecuteUpdate(st,"ALTER SESSION SET SQL_TRACE = TRUE");
						break;
					default:
						invalidDialect(db.getDialect());
				}
				if (db.getDefaultDatabase() != null)
					st.executeUpdate(db.getDBIntrinsics().getUseDatabaseSQL(db.getDefaultDatabase()));

			} finally {
				close(st);
			}
			db.profilerSetConnectionInfo(ticket, conId);
			syncUpInitStatements();
			con.setAutoCommit(autoCommit);
			con.setTransactionIsolation(db.getTransactionIsolation());
		}
		return con;
	}

	/*
	 * returns true - need to resync autocommit, false - no need
	 */
	private boolean stickyRunAdd(TreeMap<Long, DBImpl.StickyBlock> lst) throws SQLException, InterruptedException {
		if (con==null) return false;
		boolean firstFlipAutoCommit=true;
		for(DBImpl.StickyBlock sb : lst.values()) {
			if (sb.onConnectionInitBlock!=null) {
				if (!autoCommit && firstFlipAutoCommit) {
					firstFlipAutoCommit=false;
					try {con.setAutoCommit(true);} catch (SQLException e) {};
				}
				boolean success=false;
				try {
					sb.onConnectionInitBlock.execute(this);
					success=true;
				} finally {
					if (!success && sb.onConnectionCloseBlock!=null) {
						try {sb.onConnectionCloseBlock.execute(this);} catch (Throwable t) {}
					}
				}
			}
		}
		return !firstFlipAutoCommit;
	}
	/*
	 * returns true - need to set autocommit=false
	 */
	private boolean stickyRunClose(TreeMap<Long, DBImpl.StickyBlock> closes) {
		if (con==null) return false;
		boolean firstFlipAutoCommit=true;
		for(DBImpl.StickyBlock sb : closes.descendingMap().values()) {
			if (sb.onConnectionCloseBlock!=null) {
				if (!autoCommit && firstFlipAutoCommit) {
					firstFlipAutoCommit=false;
					try {con.setAutoCommit(true);} catch (SQLException e) {};
				}
				try {sb.onConnectionCloseBlock.execute(this);} catch (Throwable t) {}
			}
		}
		return !firstFlipAutoCommit;
	}
	
	@Override
	public void syncUpInitStatements() throws SQLException, InterruptedException {
		if (con!=null) {
			TreeMap<Long, DBImpl.StickyBlock> adds=new TreeMap<>(), closes=new TreeMap<>();
			cx.getLock().lock();
			try {
				for (Long sk : cx.getInitBlocks().keySet()) 
					if (!knownInitBlocks.containsKey(sk)) adds.put(sk,cx.getInitBlocks().get(sk));
				for (Long sk : knownInitBlocks.keySet())
					if (!cx.getInitBlocks().containsKey(sk)) closes.put(sk, knownInitBlocks.get(sk));
				knownInitBlocks.clear();
				knownInitBlocks.putAll(cx.getInitBlocks()); // <- even if subsequent sql fails, close() will reset all anyways
				stickyVersion=cx.getStickyVersion();
			} finally {
				cx.getLock().unlock();
			}
			boolean s1=stickyRunAdd(adds);
			boolean s2=stickyRunClose(closes);
			if (s1 || s2) con.setAutoCommit(false);
		}
	}


	private void closeSticky() throws InterruptedException {
		stickyRunClose(knownInitBlocks);
		cx.getLock().lock();
		try {
			knownInitBlocks.clear();
			stickyVersion=0;
		} finally {
			cx.getLock().unlock();
		}
	}



	private void invalidDialect(Dialect dialect) {
		throw new RuntimeException("Unknown dialect "+dialect);
		
	}

	private Object trySingleSelectValue(Statement st, String sql, Object valOnException) {
		ResultSet rs =null;
		try {
			rs=st.executeQuery(sql);
			if (rs.next()) {
				Object ret=rs.getObject(1);
				if (ret!=null) return ret;
			}
		} catch(SQLException ex) {
			return valOnException;
		} finally {
			close(rs);
		}
		return valOnException;
	}

	private void tryExecuteUpdate(Statement st, String sql) {
		try {
			st.executeUpdate(sql);
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
	}
	private void setUpDrizzle() throws SQLException {
		if (db.getDialect() == Dialect.DRIZZLE || db.getDialect() == Dialect.DRIZZLE_MYSQL) {
			try {
				Class<?> drizzleConnectionClass = Class.forName("org.drizzle.jdbc.DrizzleConnection");
				Class<?> bhClass = Class.forName("org.drizzle.jdbc.internal.common.RewriteParameterizedBatchHandlerFactory");
				Class<?> bhfClass = Class.forName("org.drizzle.jdbc.internal.common.ParameterizedBatchHandlerFactory");
				if (con.isWrapperFor(drizzleConnectionClass)) {
					Object dc = con.unwrap(drizzleConnectionClass);
					Object bh = bhClass.newInstance();
					Method shfm = drizzleConnectionClass.getMethod("setBatchQueryHandlerFactory", bhfClass);
					shfm.invoke(dc, bh);
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("serial")
	private static class LRUCache<A, B extends AutoCloseable> extends LinkedHashMap<A, B> {
	    private final int maxEntries;
	    public LRUCache(final int maxEntries) {
	        super(maxEntries + 1, 1.0f, true);
	        this.maxEntries = maxEntries;
	    }
	    @Override
	    protected boolean removeEldestEntry(final Map.Entry<A, B> eldest) {
	    	boolean remove=super.size() > maxEntries;
	    	if (remove) {
	    		try {eldest.getValue().close();} catch (Exception e) {}
	    	}
	        return remove;
	    }
	}

	
	private PreparedStatement getPrepraredStatement(Object ticket, String sql, boolean cache) throws SQLException, InterruptedException {
		if (cache) {
			PreparedStatement ps=psCache.get(sql);
			if (ps!=null) return ps;
			ps = getConnection(ticket).prepareStatement(sql);
			psCache.put(sql,ps);
			return ps;
		} else {
			return getConnection(ticket).prepareStatement(sql);
		}
	}
	private PreparedStatement getPrepraredStatementGenKeys(Object ticket, String sql, boolean cache) throws SQLException, InterruptedException {
		if (cache) {
			PreparedStatement ps=psGenCache.get(sql);
			if (ps!=null) return ps;
			ps = getConnection(ticket).prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			psGenCache.put(sql,ps);
			return ps;
		} else {
			return getConnection(ticket).prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		}
	}
	
	@Override
	public <T> T selectSingle(String sql, boolean cache, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		try {
			if (Thread.interrupted()) throw new InterruptedException();
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				rs = st.executeQuery(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, args);
				rs = ps.executeQuery();
			}
			ResultSetMetaData rm = rs.getMetaData();
			T result = null;
			if (rm.getColumnCount()>0 && rs.next()) {
				result=(T)normalizeGetObject(rm, rs, 1);
			}
			success=true;
			return result;
		} catch (SQLException ex) {
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
			return null;
		} finally {
			close(rs);
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
		}	
	}

	@Override
	protected void finalize() {
		try {close();} catch (InterruptedException e) {}
	}


	@SuppressWarnings("resource")
	@Override
	public void batchUpdate(String sql, boolean cache, BatchInputIterator it) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		Object ticket=db.profilerStart(this, sql);
		boolean success=false;
		SQLException sex=null;
		int rowNum=0,prevRowNum=0;
		try {
			ps = getPrepraredStatement(ticket,sql,cache);
			int maxBulkSize=db.getBatchSize();
			boolean flushed=true;
			for (;it.hasNext();) {
				if (Thread.interrupted()) throw new InterruptedException();
				it.next();
				for (int pos = 1, colNum=it.getColumnCount(); pos<=colNum; ++pos) ps.setObject(pos, it.get(pos-1));
				ps.addBatch();
				flushed=false;
				if (++rowNum % maxBulkSize == 0) {
					ps.executeBatch();
					prevRowNum=rowNum;
					flushed=true;
				}
			}
			if (!flushed)
				ps.executeBatch();
			success=true;
		} catch (SQLException ex) {
			if (Thread.interrupted()) throw new InterruptedException();
			sex=new ExtendedSQLException(conId, ex, sql, cache, it, prevRowNum,  rowNum);;
			throw  sex;
		} finally {
			close(rs);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
			if (!success) it.reset();
		}		
	}

	class BulkRewrite implements AutoCloseable {
		String sql;
		//private String stm;
		//private String bind;
		ArrayList<String> variants=new ArrayList<>();
		public BulkRewrite(String sql, String stm, String bind) {
			this.sql=sql;
			//this.stm=stm;
			//this.bind=bind;
			int max_ext=(int)(Math.log(db.getBatchSize()-0.0001)/Math.log(2)+1);
			for (int ext=0;ext<=max_ext;++ext) {
				int rep=1<<ext;
				StringBuilder sb=new StringBuilder(stm);
				for (int i=0;i<rep;++i) {
					sb.append(bind);
					if (i<rep-1) sb.append(",");
				}
				variants.add(sb.toString());
				//System.out.println((1<<ext)+" : "+sb.toString());
			}
		}
		@Override
		public void close() throws Exception {}
		public int getMaxBatchSize() {
			return 1<<(variants.size()-1);
		}		
		public int getVariantsSize() {return variants.size();}
		public String getMaxVariantSql() {
			return variants.get(variants.size()-1);
		}
		public int getBatchSize(int i) {
			return 1<<i;
		}
		public String getVariantSql(int i) {
			return variants.get(i);
		}	
		
	}
	
	@Override
	public void batchInsert(String sql, BatchInputIterator it) throws SQLException, InterruptedException {
		BulkRewrite br=null;
		if (db.getDialect()==Dialect.MYSQL && (br=getBulkRewrite(sql))!=null) {
			batchInsert(br, it);
		} else batchUpdate(sql, true, it);		
	}
	private void batchInsert(BulkRewrite br, BatchInputIterator it) throws SQLException, InterruptedException  {
		boolean success=false;
		try {
			int maxBulkSize=br.getMaxBatchSize();			
			List<Object[]> buffer=new ArrayList<>(maxBulkSize);
			int colNum=0;
			for (;it.hasNext();) {
				if (Thread.interrupted()) throw new InterruptedException();
				it.next();
				colNum=it.getColumnCount();
				Object[] row=new Object[colNum];
				for (int i = 0; i<colNum; ++i) row[i]=it.get(i);
				buffer.add(row);
				if (buffer.size()==maxBulkSize) {
					Object[] args=new Object[maxBulkSize*colNum];
					int idx=0;
					for (int rep=0;rep<maxBulkSize;++rep) 
						for (int c=0;c<colNum;++c) {
							args[idx++]=buffer.get(rep)[c];
						}
					update(br.getMaxVariantSql(),true ,args);
					buffer.clear();
				}
			}
			if (buffer.size()>0) {
				int bufferPos=0;
				LEFTOVER: for (int i=br.getVariantsSize()-2;i>=0;--i) {
					maxBulkSize=br.getBatchSize(i);
					while (buffer.size()-bufferPos>=maxBulkSize) {
						String sql=br.getVariantSql(i);
						Object[] args=new Object[maxBulkSize*colNum];
						int idx=0;
						for (int rep=0;rep<maxBulkSize;++rep) {
							if (Thread.interrupted()) throw new InterruptedException();
							for (int c=0;c<colNum;++c) {
								args[idx++]=buffer.get(bufferPos+rep)[c];
							}
						}
						update(sql,true ,args);					
						bufferPos+=maxBulkSize;
						if (bufferPos==buffer.size())
							break LEFTOVER;
					}
				}
			}
			success=true;
		} finally {
			if (!success) it.reset();
		}		
	}
	
	private BulkRewrite getBulkRewrite(String sql) {
		BulkRewrite br=bulkRewriteCache.get(sql);
		if (br!=null) return br;
		
		Pattern pp=Pattern.compile("^\\s*(replace\\s.*values)\\s*(\\(.*\\))\\s*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		Matcher m=pp.matcher(sql);
		String stm=null;
		String bind=null;

		if (m.find()) {
			stm=m.group(1);
			bind=m.group(2);
		} else {
			pp=Pattern.compile("^\\s*(insert\\s.*values)\\s*(\\(.*\\))\\s*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			m=pp.matcher(sql);
			if (m.find()) {
				stm=m.group(1);
				bind=m.group(2);
			}
		}
		if (stm==null)
			return null;
		
		br=new BulkRewrite(sql, stm, bind);
		bulkRewriteCache.put(sql, br);
		return br;
	}

	public Object getConnectionId() {
		return conId;
	}

	@Override
	public DBImpl getDB() {
		return db;
	}

	final long getStickyVersion() {
		return stickyVersion;
	}

	@Override
	public boolean getAutoCommit() {
		return autoCommit;
	}
	
	@Override
	public int binaryStreamUpdate(String sql, boolean cache, int[] streamPositions, Object... args) throws SQLException, InterruptedException {
		PreparedStatement ps = null;
		Statement st = null;
		Object ticket=db.profilerStart(this, sql, args);
		boolean success=false;
		SQLException sex=null;
		int result;
		try {
			if (!cache && args.length == 0) {
				st = getConnection(ticket).createStatement();
				result=st.executeUpdate(sql);
			} else {
				ps = getPrepraredStatement(ticket,sql,cache);
				applyArgs(ps, streamPositions, args);
				result=ps.executeUpdate();
			}
			success=true;
			return result;
		} catch (SQLException ex) {
			sex=ex;
			throwExtendedSQLException(sql, cache, ex, args);
			return 0;
		} finally {
			close(st);
			if (!cache) 
				close(ps);
			db.profilerEnd(ticket,success,sex);
		}
	}
	private static void applyArgs(PreparedStatement ps, int[] streamPositions,  Object[] args) throws SQLException {
		if (args == null) return;
		for (int pos = 1; pos<=args.length; ++pos) {
			boolean useBlob=false;
			for (int bp : streamPositions) {
				if (pos==bp) {
					useBlob=true;
					break;
				}
			}
			if (useBlob) {
				InputStream in=(InputStream)args[pos-1];
				int nRead;
				byte[] data=new byte[4096];
				ByteArrayOutputStream buffer=new ByteArrayOutputStream();
				try {
					while ( (nRead=in.read(data,0,data.length))!=-1) {
						buffer.write(data, 0, nRead);
					}
				} catch (IOException e) {
					throw new SQLException(e);
				}
				ByteArrayInputStream is=new ByteArrayInputStream(buffer.toByteArray());
				ps.setBinaryStream(pos, is);
			} else {
				ps.setObject(pos, args[pos-1]);
			}
		}
	}



}
