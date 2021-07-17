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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import arutils.async.CompletionCallback;
import arutils.async.Request;
import arutils.async.Result;
import arutils.async.Service;
import arutils.async.ServiceBackend;
import arutils.async.Workload;


public class ServiceImpl implements Service {

	final private AsyncEngineImpl engine;
	final private ServiceBackend backend;
	final private ArrayDeque<Request> requestsQueue= new ArrayDeque<>();
	final private Lock lock=new ReentrantLock();
	final private Condition requestAddedCond=lock.newCondition();
	final private Condition requestDrainedCond=lock.newCondition();
	int MAX_REQUESTS;
	int numOfWorkers=0;
	int MAX_WORKERS;
	private Workload trackingWorkload;
	
	//private static final boolean DEBUG = System.getenv("DEBUG")!=null;


	public ServiceImpl(AsyncEngineImpl engine, ServiceBackend backend,Workload trackingWorkload) {
		this.engine=engine;
		this.backend=backend;
		this.trackingWorkload=trackingWorkload;
		MAX_WORKERS=backend.getMaxWorkers();
		if (MAX_WORKERS<=0)
			MAX_WORKERS=20;
		MAX_REQUESTS=backend.getMaxQueuedRequests();
		if (MAX_REQUESTS<=0)
			MAX_REQUESTS=10000;
		
	}
	
	private int getBulkSize() {
		try {
			int bs=backend.getMaxBulkSize();
			return (bs<=0)?1:bs;
		} catch (Throwable tt) {tt.printStackTrace();return 1;}
	}
	
	private long getWorkerReleaseTimeout() {
		try {
			long t=backend.getWorkerReleaseTimeout();
			return (t<=0)?1:t;
		} catch (Throwable tt) {tt.printStackTrace();return 1;}
		
	}

	@Override
	public <T> Result call(Workload w, Object... args) throws InterruptedException {
		ResultImpl<T> callback=new ResultImpl<T>(args);
		callWithCallback(w, callback,args);
		return callback;
	}
	
	@Override
	public <T> Result callNoLimit(Workload w, Object... args) throws InterruptedException {
		ResultImpl<T> callback=new ResultImpl<T>(args);
		callWithCallbackNoLimit(w, callback, args);
		return callback;
	}

	@Override
	public <T> void callWithCallback(Workload w, CompletionCallback<T> callback, Object... args) throws InterruptedException {
		Request req=new Request(w,callback,args);
		w.callSubmitted();
		lock.lockInterruptibly();
		try {
			while (requestsQueue.size()>=MAX_REQUESTS)
				requestDrainedCond.await();
			requestsQueue.addFirst(req);
			checkWorkers();
			requestAddedCond.signalAll();
		} finally {
			lock.unlock();
		}
	}

	
	@Override
	public <T> void callWithCallbackNoLimit(Workload w, CompletionCallback<T> callback, Object... args) throws InterruptedException {
		Request req=new Request(w,callback,args);
		w.callSubmitted();
		lock.lockInterruptibly();
		try {
			requestsQueue.addFirst(req);
			checkWorkers();
			requestAddedCond.signalAll();
		} finally {
			lock.unlock();
		}
	}
	
	@Override
	public <T> void callWithCallback(CompletionCallback<T> callback, Object... args) throws InterruptedException {
		callWithCallback(trackingWorkload, callback, args);
	}

	@Override
	public <T> void callWithCallbackNoLimit(CompletionCallback<T> callback, Object... args) throws InterruptedException {
		callWithCallbackNoLimit(trackingWorkload, callback, args);
	}
	
	@Override
	public Workload getTrackingWorkload() {
		return trackingWorkload;
	}
	
	@Override
	public <T> boolean tryCallWithCallback(CompletionCallback<T> callback, Object... args) throws InterruptedException {
		return tryCallWithCallback(trackingWorkload, callback, args);
	}
	
	@Override
	public <T> boolean tryCallWithCallback(Workload w, CompletionCallback<T> callback, Object... args) throws InterruptedException {
		Request req=new Request(w,callback,args);
		lock.lockInterruptibly();
		try {
			while (requestsQueue.size()>=MAX_REQUESTS)
				return false;
			w.callSubmitted();
			requestsQueue.addFirst(req);
			checkWorkers();
			requestAddedCond.signalAll();
		} finally {
			lock.unlock();
		}
		return true;
	}
	
	
	class Worker implements Runnable {
		public void run() {
			//if (DEBUG) System.err.println("Worker Started - "+Thread.currentThread());
			int bulkSize=getBulkSize();
			long releaseTimeout=getWorkerReleaseTimeout();
			try {
				lock.lockInterruptibly();
				for (;;) {
					
					while (requestsQueue.size()==0) {
						boolean signaled=requestAddedCond.await(releaseTimeout,TimeUnit.MILLISECONDS);
						if (!signaled && requestsQueue.size()==0) {
//							if (DEBUG) System.err.println("Worker Released - "+Thread.currentThread());
							return;
						}
					}
					
					ArrayList<Request> bulk=new ArrayList<>(Math.min(bulkSize, requestsQueue.size()));
					while (requestsQueue.size()>0 && bulk.size()<bulkSize) {			
						Request r=requestsQueue.removeLast();
						bulk.add(r);
					}
					requestDrainedCond.signalAll();
					// Time to call the backend
					try {
						lock.unlock();
						try {
							backend.process(Collections.unmodifiableList(bulk));
							for (Request r : bulk) {
								try {
									r.completed();
								} catch (Throwable tt) {}
							}
						} catch (Throwable e) {
							for (Request r : bulk) {try {r.errored(e);} catch (Throwable tt) {}}
						}
					} finally {
						lock.lockInterruptibly();
					}
				}
			} catch (InterruptedException e) {
				//e.printStackTrace();
			} finally {
				--numOfWorkers;
				lock.unlock();
			}	
			
		}
		
	}
	
	private void checkWorkers() {
		if (numOfWorkers==MAX_WORKERS)
			return;
		ExecutorService es=AsyncEngineImpl.getExecutorService();
		Worker w=new Worker();
		++numOfWorkers;
		es.submit(w);
		
	}


}
