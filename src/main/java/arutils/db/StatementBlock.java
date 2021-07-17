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

public abstract class  StatementBlock<Ret> {
	/**
	 * Block of SQL operations combined together. Can be executed as part of a transaction {@link DB#commit(StatementBlock)} method,
	 * Or as series of SQL operations in autocommit context {@link DB#autocommit(StatementBlock)} method
	 * @param cw
	 * @return
	 * @throws SQLException
	 * @throws InterruptedException
	 */
	public abstract Ret execute(ConnectionWrap cw) throws SQLException, InterruptedException;
	
	/**
	 * Signals that SQL Exception occurred during execution
	 * @return true - follow the default retry logic (if execution time exceeds timeout, throw the exception, if not, sleep, then retry ), 
	 * false - ignore the default retry logic (keeps spinning, unless re-throws exception)
	 * @throws SQLException to immediately fail.   
	 */
	public boolean onError(ConnectionWrap cw, boolean willAttemptToRetry, SQLException ex, long start, long now) throws SQLException, InterruptedException {
		return true;
	}
	
}
