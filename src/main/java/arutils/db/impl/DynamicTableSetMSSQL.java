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

import arutils.db.ConnectionWrap;

public class DynamicTableSetMSSQL extends DynamicTableSetImpl {


	public DynamicTableSetMSSQL(String name) {
		super(name);
	}


	@Override
	public void init(DBImpl db) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public Table addTable(String name, String... fields) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Table getTable(String name) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void insert(ConnectionWrap cw) throws SQLException,
			InterruptedException {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void delete(ConnectionWrap cw) throws SQLException,
			InterruptedException {
		// TODO Auto-generated method stub
		
	}

}
