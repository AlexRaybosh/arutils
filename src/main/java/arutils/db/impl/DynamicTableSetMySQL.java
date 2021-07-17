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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import arutils.db.ConnectionWrap;
import arutils.db.DB;
import arutils.db.DynamicTableSet;
import arutils.db.StatementBlock;
import arutils.db.TableName;
import arutils.util.EncodingUtils;
import arutils.util.Utils;

import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class DynamicTableSetMySQL extends DynamicTableSetImpl {
	public enum FieldType {
		NULL,
		TEXT,
		LONG,
		BLOB,
		UUID
	}
	int table_num;
	int row_num;
	Map<String,TableImpl> tables=new LinkedHashMap<>();
	private String insertSql;
	private String fullName;
	private String deleteSql;
	
	protected DynamicTableSetMySQL(String name) {
		super(name);
	}
	
	
	protected class TableImpl implements DynamicTableSet.Table {
		final int table_id;
		final String name;
		private String[] fields;
		private TreeMap<Integer, Object[]> allRows=new TreeMap<Integer, Object[]>();
		private FieldType[] hints;
		
		protected TableImpl(String name, String[] fields) {
			this.name=name;
			this.fields=fields;
			table_id=table_num++;
		}
		
		public final void add(Object... vals) {
			allRows.put(row_num++, vals);
		}
		@Override
		public String getSelectSQL() {
			StringBuilder sb=new StringBuilder();
			getSelectSQL(sb);
			return sb.toString();
		}
		@Override
		public void getSelectSQL(StringBuilder sb) {
			deriveTypes();
			sb.append("select ");
			for (int c=0;c<fields.length;++c) {
				FieldType type=hints[c];
				String field=fields[c];
				String valField;
				switch (hints[c]) {
				case BLOB:
					valField="val_blob";
					break;
				case LONG:
					valField="val_num";
					break;
				case TEXT:
					valField="val_text";
					break;
				case UUID:
					valField="val_blob";
					break;
				default:
					valField="val_text";
					break;
				}
			
				
				sb.append("(select ").append(valField).append(" from ")
				.append(fullName).append(" v where v.con_id=").append(name).append(".con_id and v.table_id=").append(name).append(".table_id and ")
				.append("v.col_id=").append(c+1)
				.append(" and v.record_id=").append(name).append(".record_id").append(") as ").append(field);
				if (c<fields.length-1) sb.append(", ");
			}
			sb.append(" from ").append(fullName).append(" as ").append(name).append(" where ").append(name).append(".con_id=CONNECTION_ID() and ")
				.append(name).append(".table_id=").append(table_id).append(" and ").append(name).append(".col_id=0");
		}
		@Override
		public void getJoinableSelectSQL(StringBuilder sb) {
			sb.append(" (");
			getSelectSQL(sb);
			sb.append(") as ").append(name).append(" ");
			
		}
		@Override
		public String getJoinableSelectSQL() {
			StringBuilder sb=new StringBuilder();
			return sb.toString();
		}
		
		public void deriveTypes() {
			if (hints==null) {
				hints=new FieldType[fields.length];
				for (int c=0;c<fields.length;++c) {
					FieldType type=FieldType.NULL;
					for (Object[] row :allRows.values()) {
						Object val=row[c];
						if (val==null) continue;
						Class<?> vc = val.getClass();
						if (vc==Byte.class || vc==Short.class || vc==Integer.class ||  vc==Long.class) {
							if (type==FieldType.NULL) type=FieldType.LONG;
							break;
						} else if (vc==UUID.class) {
							if (type==FieldType.NULL) type=FieldType.UUID;
							break;
						} else if (vc==byte[].class) {
							if (type==FieldType.NULL) type=FieldType.BLOB;
							break;
						} else if (vc==String.class) {
							if (type==FieldType.NULL) type=FieldType.TEXT;
							break;
						} else throw new RuntimeException("Failed to interpret type in table "+name+";  field "+fields[c]+" java class "+vc.getName()+" value "+val);
					}
					hints[c]=type;
				}
			}
		}




		
	}






	@Override
	public void init(DBImpl db) throws InterruptedException, SQLException {
		Set<TableName> tables=DB.resolve(db.getInitialUserTableNames());
		TableName tn = db.getTableName(name);
		if (!tables.contains(tn)) {
			String engine="INNODB";
			String versionComment=db.getVersionComment();
			if (versionComment==null)
				versionComment=Utils.toString(db.selectSingle("select @@version_comment"));
			if (versionComment!=null && versionComment.matches(".*Percona.*")) {
				engine="MEMORY";
			}
			String sql="create table if not exists "+tn.toString(db)+" ("
			+ "con_id bigint not null,"
			+ "table_id int not null,"
			+ "col_id int not null,"
			+ "record_id int not null,"
			+ "val_num bigint null,"
			+ "val_text text null,"
			+ "val_blob blob null,"
			+ "primary key(con_id, table_id, record_id, col_id)"
			+ ") engine="+engine;
			db.update(sql);
			db.resetInitialUserTableNames();
		}
		fullName=tn.toString(db);
		insertSql="insert into "+fullName+" values (CONNECTION_ID(),?,?,?,?,?,?)";
		deleteSql="delete from "+fullName+" where con_id=CONNECTION_ID()";
	}

	@Override
	public Table addTable(String name, String... fields) {
		TableImpl t = new TableImpl(name, fields);
		tables.put(name,t);
		return t;
	}
	
	@Override
	public Table getTable(String name) {
		return tables.get(name);
	}

	@Override
	public void insert(ConnectionWrap cw) throws SQLException, InterruptedException {
		List<Object[]> rows=new ArrayList<>();
		for (TableImpl t : tables.values()) {
			t.deriveTypes();
			for (Entry<Integer,Object[]> e : t.allRows.entrySet()) {
				Integer recId=e.getKey();
				Object[] row=e.getValue();
				rows.add(new Object[] {t.table_id,0,recId,null,null,null});
				for (int i=0;i<row.length;++i) {
					Object val=row[i];
					switch (t.hints[i]) {
					case BLOB:
						rows.add(new Object[] {t.table_id,i+1,recId,null,null,val});
						break;
					case LONG:
						rows.add(new Object[] {t.table_id,i+1,recId,val==null?null:((Number)val).longValue(),null,null});
						break;
					case TEXT:
						rows.add(new Object[] {t.table_id,i+1,recId,null,val,null});
						break;
					case UUID:
						rows.add(new Object[] {t.table_id,i+1,recId,null,null,val==null?null:EncodingUtils.uuidToByteArray((UUID)row[i])});
						break;
					default:
						break;
					}
				}
			}
		}
		if (rows.size()>0) {
			cw.batchInsert(insertSql, rows);
			cw.beforeCommit(new StatementBlock<Void>() {
				public Void execute(ConnectionWrap cw) throws SQLException, InterruptedException {
					DynamicTableSetMySQL.this.delete(cw);
					return null;
				}
			});
		}
		
	}

	@Override
	public void delete(ConnectionWrap cw) throws SQLException, InterruptedException {
		cw.update(deleteSql, true);
	}
	

}
