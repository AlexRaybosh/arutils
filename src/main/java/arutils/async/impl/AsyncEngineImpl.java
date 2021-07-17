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

package arutils.async.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import arutils.async.AsyncEngine;
import arutils.async.CompletionCallback;
import arutils.async.Result;
import arutils.async.Service;
import arutils.async.ServiceBackend;
import arutils.async.Workload;

public class AsyncEngineImpl extends AsyncEngine implements Workload {
	volatile Map<String,ServiceImpl> services=new HashMap<>();
	Lock bigLock=new ReentrantLock();
	static AtomicInteger tn=new AtomicInteger();
	static ExecutorService executorService=Executors.newCachedThreadPool(new ThreadFactory() {
		   @Override
		   public Thread newThread(Runnable r) {
		      Thread thread =  new Thread(r, "async-engine-thread-"+tn.incrementAndGet());
		      thread.setDaemon(true);
		      return thread;
		   }
		});
	
	WorkloadImpl mainWorkload=new WorkloadImpl(this);
	

	@Override
	public Result call(String serviceName, Object... args) throws InterruptedException {
		Service service=getService(serviceName);
		return service.call(this, args);
	}

	@Override
	public void callWithCallback(String serviceName,CompletionCallback callback, Object... args) throws InterruptedException {
		Service service=getService(serviceName);
		service.callWithCallback(this, callback, args);
	}

	@Override
	public Result callNoLimit(String serviceName, Object... args) throws InterruptedException {
		Service service=getService(serviceName);
		return service.callNoLimit(this, args);
	}

	@Override
	public void callWithCallbackNoLimit(String serviceName,CompletionCallback callback, Object... args) throws InterruptedException {
		Service service=getService(serviceName);
		service.callWithCallbackNoLimit(this, callback, args);
	}

	
	/* (non-Javadoc)
	 * @see engine.AsyncEngine#createWorkload()
	 */
	@Override
	public Workload createWorkload() {
		return new WorkloadImpl(this);
	}
	
	/* (non-Javadoc)
	 * @see engine.AsyncEngine#getService(java.lang.String)
	 */
	@Override
	public Service getService(String serviceName) {
		return services.get(serviceName);
	}

	/* (non-Javadoc)
	 * @see engine.AsyncEngine#register(java.lang.String, engine.ServiceBackend)
	 */
	@Override
	public Service register(String serviceName, ServiceBackend backend) {
		return register(serviceName, backend, this);
	}
	/* (non-Javadoc)
	 * @see engine.AsyncEngine#register(java.lang.String, engine.ServiceBackend)
	 */
	@Override
	public Service register(String serviceName, ServiceBackend backend, Workload trackingWorkload) {
		ServiceImpl simpl=new ServiceImpl(this, backend,trackingWorkload);
		bigLock.lock();
		try {
			Map<String,ServiceImpl> newservices=new HashMap<>(services);
			newservices.put(serviceName, simpl);
			services=newservices;
			return simpl;
		} finally {
			bigLock.unlock();	
		}
	}
	@Override
	public Service registerIfAbsent(String serviceName, ServiceBackend backend) {
		ServiceImpl simpl=new ServiceImpl(this, backend, this);
		bigLock.lock();
		try {
			ServiceImpl si = services.get(serviceName);
			if (si!=null)
				return si;
			Map<String,ServiceImpl> newservices=new HashMap<>(services);
			newservices.put(serviceName, simpl);
			services=newservices;
			return simpl;
		} finally {
			bigLock.unlock();	
		}
	}

	public static ExecutorService getExecutorService() {
		return executorService;
	}

	@Override
	public void callCompleted() {
		mainWorkload.callCompleted();
	}
	
	@Override
	public Future<Long> completeLast() {
		return mainWorkload.completeLast(); 
	}

	@Override
	public void callSubmitted() {
		mainWorkload.callSubmitted(); 
	}

	@Override
	public long last() {
		return mainWorkload.last();
	}
	@Override
	public void reset() {
		mainWorkload.reset();
	}
	@Override
	public long getNumberOfCompletedCalls() {
		return mainWorkload.getNumberOfCompletedCalls();
	}
	@Override
	public long getNumberOfSubmittedCalls() {
		return mainWorkload.getNumberOfSubmittedCalls();
	}
	/*
	@Override
	public List<Runnable> shutdownNow() {
		return executorService.shutdownNow();
	}

	@Override
	public void shutdown() {
		executorService.shutdown();
	}

*/





}
