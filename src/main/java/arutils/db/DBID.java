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
import java.util.HashMap;
import java.util.Map;

import arutils.db.DB.Dialect;


public class DBID {
	private DB db;
	private String table;

	
	public DBID(DB db) throws SQLException, InterruptedException {
		this(db,null);
	}
	public DBID(DB db, String table) throws SQLException, InterruptedException {
		this.db=db;
		if (table==null) {
			table="seq";
		}
		TableName tbl = db.getTableName(table);
/*		if (tbl.getSchema()==null) {
			tbl=new TableName(null,db.<String>selectSingle("SELECT DATABASE()"),tbl.getName());
		}
		*/
		this.table=tbl.toString(db);
		init();
	}
	public long next(String name) throws SQLException, InterruptedException {
		IdAlloc a=null;
		synchronized (idAllocMap) {
			a = idAllocMap.get(name);
			if (a != null && a.isValid()) return a.next();
		}
		a=alloc(a, name);
		synchronized (idAllocMap) {
			idAllocMap.put(name, a);
			assert a.isValid();
			return a.next();
		}
	}
	
	private static class IdAlloc {
		public IdAlloc() {
			to=-1;
			from=0;
		}
		final boolean isValid() {
			return from < to;
		}
		long from;
		long to;
		long start;
		final long next() {return from++;}
		@Override
		public String toString() {
			return "IdAlloc [from=" + from + ", to=" + to + "]";
		} 
		
	}
	private Map<String, IdAlloc> idAllocMap =new HashMap<String, IdAlloc>();
	private String SELECT_SQL;
	private String DELETE_SQL;
	private String INSERT_SQL;
	
	private void init() throws SQLException, InterruptedException {
		if (db.getDialect()==Dialect.TDS)
			SELECT_SQL="select value from "+table+" WITH (updlock) where name=?";
		else 
			SELECT_SQL="select value from "+table+" where name=? for update";
		
		
		DELETE_SQL="delete from "+table+" where name=?";
		INSERT_SQL="insert into "+table+" (name,value,last_ms) values (?,?,?)";
		
		checkCreateTable();
	}
	

	protected void checkCreateTable() throws SQLException, InterruptedException {
		db.autocommit(new StatementBlock<Void>() {
			public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				try {
					cw.select("SELECT 1 FROM "+table+" WHERE 0=1", false);
				} catch (SQLException ex) {
					if (Thread.interrupted()) throw new InterruptedException();
					String createSql="CREATE TABLE "+table+ "(name varchar(200) not null,"
							+ "value "+db.getIntrinsics().getLongSequenceTypeName()+" NOT NULL, last_ms "+db.getIntrinsics().getLongSequenceTypeName()+", primary key (name) )";
					cw.update(createSql, false);
				}
				return null;
			}
		});
	}

	
	private IdAlloc alloc(final IdAlloc prev, final String key) throws SQLException, InterruptedException {
		return db.commit(new StatementBlock<IdAlloc>() {
			public IdAlloc execute(ConnectionWrap cw) throws SQLException, InterruptedException {
				Number current=((Number)cw.selectSingle(SELECT_SQL, true, key));
				if (current!=null) 
					cw.update(DELETE_SQL, true, key);
				long distance=1;
				if (prev!=null) {
					distance=2*(prev.to-prev.start);
					if (distance>500000) distance=500000;
				}
				IdAlloc a=new IdAlloc();
				a.from=current==null?1:current.longValue();
				a.to=(current==null)?2:current.longValue()+distance;
				a.start=a.from;
				cw.update(INSERT_SQL, true, key ,a.to,System.currentTimeMillis());
				return a;	
			}
			public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
				if (errCnt++>1000) throw ex;
				Thread.sleep(errCnt);
				return false;
			}
			int errCnt=0;
		});
	}
	
	public static void main (String[] args) throws Exception {
		//DB db=DB.create("jdbc:mysql://192.168.1.2:6666/?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "business", "business");
		DB db=DB.create("jdbc:mysql://localhost:3306/test1?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true", "test1", "");
		//DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=192.168.1.2)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");
//		DB db=DB.create("jdbc:oracle:thin:@(DESCRIPTION=(sdu=32000)(ADDRESS=(PROTOCOL=TCP)(HOST=dummy.google.com)(PORT=1521))(CONNECT_DATA=(SID=orcl)(SERVER=DEDICATED)))", "business", "business");
		DBID idgen=new DBID(db,"seq");
		System.out.println(idgen.next("alex"));
	}
}
