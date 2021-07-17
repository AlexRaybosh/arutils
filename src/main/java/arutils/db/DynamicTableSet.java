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

public abstract class DynamicTableSet {

	public abstract Table addTable(String name,String... fields);
	public abstract Table getTable(String name);
	
	public interface Table {
		public void add(Object... vals);
		void getSelectSQL(StringBuilder sb);
		public String getSelectSQL();
		public void getJoinableSelectSQL(StringBuilder sb);
		public String getJoinableSelectSQL();
		
	}

	public abstract void insert(ConnectionWrap cw) throws SQLException, InterruptedException;
	public abstract void delete(ConnectionWrap cw) throws SQLException, InterruptedException;
	public abstract String getName();
	

}
