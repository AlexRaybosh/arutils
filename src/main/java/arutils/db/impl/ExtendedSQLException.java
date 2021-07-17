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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import arutils.db.BatchInputIterator;

public class ExtendedSQLException  extends SQLException {

	//private final SQLException src;
	private final String sql;
	private final boolean cache;
	private Object[] args;
	private List<Object[]> bulk;
	private final Object conId;
	private int startRowIdx;
	private int numOfRows;
	private void printContext(int limit, StringBuilder sb) {
		try {
			norm(sb);
			if (conId!=null) sb.append("Connection=(").append(conId).append(")");
			norm(sb);
			
			sb.append("SQL=");//.append(sql);
			U.appendWithLimit(sb,limit,sql);
			if (args!=null && args.length>0) {
				norm(sb);
				sb.append("Args=");
				StringBuilder sba=new StringBuilder();
				printRow(sba, args);
				U.appendWithLimit(sb,limit,sba.toString());
				norm(sb);
			}
			if (bulk!=null && bulk.size()>0) {
				norm(sb);
				StringBuilder sba=new StringBuilder();
				sb.append("Bulk[").append(startRowIdx).append(",").append(numOfRows).append("): \n");
				for (int r=0;r<bulk.size();++r) {
					//if (r>0) sb.append("\n");
					printRow(sba, bulk.get(r));
					//if (r<bulk.size()-1) sb.append(",");
					sba.append("\n");
				}
				U.appendWithLimit(sb,limit,sba.toString());
				norm(sb);
			}
		} catch (Throwable tt) {
			tt.printStackTrace();
		}

	}


	private void printRow(StringBuilder sb, Object[] args) {
		if (args==null)
			return;
		sb.append("{");
		U.printRow(sb, args);
		sb.append("}");	
	}





	private void norm(StringBuilder sb) {
		if (sb.length()>0 && sb.charAt(sb.length()-1)!='\n') sb.append('\n');
	}
	

	public ExtendedSQLException(Object conId, SQLException src, String sql, boolean cache, Object[] args) {
		super(cast(src));
		this.conId=conId;
		//this.src=cast(src);
		this.sql=sql;
		this.cache=cache;
		this.args=args;
	}
	
    public static SQLException cast(SQLException ex) {
    	SQLException last=ex;
    	Set<Throwable> visited=new HashSet<Throwable>();
    	for (int i=0;i<100;++i) {
    		Throwable next=last.getCause();
    		if (next==null || visited.contains(next))
    			break;
    		visited.add(next);
    		 if (next instanceof SQLException)
				last=(SQLException)next;
    		 else return last;
    	}
    	return last;
	}

    

	public ExtendedSQLException(Object conId, SQLException src, String sql,boolean cache, BatchInputIterator it, int startRowIdx, int numOfRows) {
		super(cast(src));
		this.conId=conId;
		//this.src=cast(src);
		this.sql=sql;
		this.cache=cache;
		this.args=null;
		this.startRowIdx=startRowIdx;
		this.numOfRows=numOfRows;
		bulk=new ArrayList<>();
		try {
			it.reset();
			for (int i=0;it.hasNext() && i<numOfRows;++i) {
				it.next();
				if (i>=startRowIdx) {
					Object[] row=new Object[it.getColumnCount()];
					for (int c=0;c<row.length;++c) row[c]=it.get(c);
					bulk.add(row);
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public String getSQLState() {return ((SQLException)getCause()).getSQLState();}
    public int getErrorCode() {return ((SQLException)getCause()).getErrorCode();}
    public String getMessage() {
    	return getMessage(-1);
    }
    public String getMessage(int limit) {
    	StringBuilder sb=new StringBuilder(getCause().getMessage());
    	printContext(limit,sb);
    	return sb.toString();
    }
    public String getLocalizedMessage() {
    	StringBuilder sb=new StringBuilder(getCause().getLocalizedMessage());
    	printContext(-1, sb);
    	return sb.toString();
    }


	@Override
	public String toString() {
		return getMessage();
	}


	public List<Object[]> getBulkArgs() {
		return bulk;
	}


	public static boolean print(StringBuilder sb,int limit, SQLException sex) {
		if (sex==null)
			return false;
		if (sex instanceof ExtendedSQLException) {
			sb.append(((ExtendedSQLException)sex).getMessage(limit));
			return true;
		} else 
			U.appendWithLimit(sb,limit,sex.getMessage());
		return false;
	}
    
}
