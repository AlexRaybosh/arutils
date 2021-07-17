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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import arutils.async.CompletionCallback;
import arutils.async.Result;
import arutils.async.Workload;

public class WorkloadImpl implements Workload {
	Lock completionLock=new ReentrantLock();
	Condition completionCond=completionLock.newCondition();
	AtomicLong numOfCallsSubmitted=new AtomicLong();
	long numOfCallsCompleted=0;
	boolean isDone=false;
	private AsyncEngineImpl engine;
	
	WorkloadImpl(AsyncEngineImpl engine) {
		this.engine=engine;
	}
	
	@Override
	public void callCompleted() {
		completionLock.lock();
		try {
			++numOfCallsCompleted;
//			System.out.println("Completed  : "+numOfCallsCompleted);
			completionCond.signalAll();
		} finally {
			completionLock.unlock();
		}
	}
	
	@Override
	public Future<Long> completeLast() {
		return new Future<Long>() {
			public boolean cancel(boolean mayInterruptIfRunning) {return false;}
			public boolean isCancelled() {return false;}
			public boolean isDone() {
				completionLock.lock();
				try {
					return numOfCallsCompleted==numOfCallsSubmitted.get();
				} finally {
					completionLock.unlock();
				}
			}
			public Long get() throws InterruptedException, ExecutionException {
				completionLock.lock();
				try {
					while ( (!isDone) || numOfCallsCompleted<numOfCallsSubmitted.get()) {
//						System.out.println("Awaiting numOfCallsCompleted="+numOfCallsCompleted+" numOfCallsSubmitted="+numOfCallsSubmitted);
						completionCond.await();
					}
					return numOfCallsCompleted;
				} finally {
					completionLock.unlock();
				}
			}
			public Long get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				completionLock.lock();
				try {
					while ( (!isDone) || numOfCallsCompleted<numOfCallsSubmitted.get()) {
						boolean signalled=completionCond.await(timeout,unit);
						if (!signalled) throw new TimeoutException(timeout+" "+unit+" elapsed");
					}
					return numOfCallsCompleted;
				} finally {
					completionLock.unlock();
				}
			}			
		};
	}

	@Override
	public void callSubmitted() {
		numOfCallsSubmitted.incrementAndGet();
	}

	@Override
	public long last() {
		completionLock.lock();
		try {
			isDone=true;
//			System.out.println("Done Signal");
			completionCond.signalAll();
			return numOfCallsSubmitted.get();
		} finally {
			completionLock.unlock();
		}
	}


	@Override
	public void reset() {
		completionLock.lock();
		try {
			isDone=false;
			numOfCallsSubmitted.set(0L);
			numOfCallsCompleted=0L;
		} finally {
			completionLock.unlock();
		}
		
	}

	@Override
	public Result call(String serviceName, Object... args) throws InterruptedException {
		return engine.getService(serviceName).call(this, args);
	}

	@Override
	public void callWithCallback(String serviceName, CompletionCallback callback, Object... args) throws InterruptedException {
		engine.getService(serviceName).callWithCallback(this,callback,args);
	}

	@Override
	public Result callNoLimit(String serviceName, Object... args) throws InterruptedException {
		return engine.getService(serviceName).callNoLimit(this, args);
	}

	@Override
	public void callWithCallbackNoLimit(String serviceName,CompletionCallback callback, Object... args) throws InterruptedException {
		engine.getService(serviceName).callWithCallbackNoLimit(this,callback,args);
	}

	@Override
	public long getNumberOfCompletedCalls() {
		completionLock.lock();
		try {
			return numOfCallsCompleted;
		} finally {
			completionLock.unlock();
		}
	}
	@Override
	public long getNumberOfSubmittedCalls() {
		return numOfCallsSubmitted.get();
	}



}
