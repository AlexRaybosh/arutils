
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
	public abstract <T> Result<T> call(String serviceName, Object... args) throws InterruptedException;
	public abstract <T> void callWithCallback(String serviceName, CompletionCallback<T> callback, Object... args) throws InterruptedException;
	public abstract <T> Result<T> callNoLimit(String serviceName, Object... args) throws InterruptedException;
	public abstract <T> void callWithCallbackNoLimit(String serviceName, CompletionCallback<T> callback, Object... args) throws InterruptedException;

	


	public abstract Workload createWorkload();

	public abstract <T> Service<T> getService(String serviceName);

	public abstract <T> Service<T> register(String serviceName, ServiceBackend<T> backend);
	public abstract <T> Service<T> registerIfAbsent(String serviceName, ServiceBackend<T> backend);
	public abstract <T> Service<T> register(String serviceName, ServiceBackend<T> backend, Workload w);
	
	/*public abstract List<Runnable> shutdownNow();
	public abstract void shutdown();
	*/
	public static AsyncEngine create() {return new AsyncEngineImpl();}
	public static ExecutorService getEngineExecutorService() {return AsyncEngineImpl.getExecutorService();}
	
}