
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

import java.util.concurrent.ExecutorService;

import arutils.async.impl.AsyncEngineImpl;

public abstract class AsyncEngine implements Workload {

	/**
	 * Submits an asynchronous call to the engine, to be served by <code>serviceName</code> backend.
	 * @param serviceName - backend name registered with {@link Workload#register(java.lang.String,ServiceBackend)} method.
	 * @see engine.Workload#call... methods
	 */
	public abstract Result call(String serviceName, Object... args) throws InterruptedException;
	public abstract void callWithCallback(String serviceName, CompletionCallback callback, Object... args) throws InterruptedException;
	public abstract Result callNoLimit(String serviceName, Object... args) throws InterruptedException;
	public abstract void callWithCallbackNoLimit(String serviceName, CompletionCallback callback, Object... args) throws InterruptedException;

	


	public abstract Workload createWorkload();

	public abstract Service getService(String serviceName);

	public abstract Service register(String serviceName, ServiceBackend backend);
	public abstract Service registerIfAbsent(String serviceName, ServiceBackend backend);
	public abstract Service register(String serviceName, ServiceBackend backend, Workload w);
	
	/*public abstract List<Runnable> shutdownNow();
	public abstract void shutdown();
	*/
	public static AsyncEngine create() {return new AsyncEngineImpl();}
	public static ExecutorService getEngineExecutorService() {return AsyncEngineImpl.getExecutorService();}
	
}