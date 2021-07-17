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

package arutils.async;

import java.util.concurrent.Future;

public interface Workload {
	public Result call(String serviceName, Object... args) throws InterruptedException;
	public void callWithCallback(String serviceName, CompletionCallback callback, Object... args) throws InterruptedException;
	public Result callNoLimit(String serviceName, Object... args) throws InterruptedException;
	public void callWithCallbackNoLimit(String serviceName, CompletionCallback callback, Object... args) throws InterruptedException;

	
	/**
	 * Signals the last call to the workload. See also: complete() can be called to collect completion future 
	 * @return Number of submitted calls 
	 */
	long last();
	/**
	 * Resets counter of calls, so done()/complete() can be called again
	 */
	void reset();
	
	/**
	 * @return Number of submitted calls 
	 */
	public long getNumberOfSubmittedCalls();
	/**
	 * @return Number of submitted calls 
	 */
	long getNumberOfCompletedCalls();
	
	/**
	 * @return Future<Long> of completed number of calls, once <code>last()</code> is called on the Workload
	 */
	Future<Long> completeLast();
	
	void callSubmitted();
	void callCompleted();
	

	
}
