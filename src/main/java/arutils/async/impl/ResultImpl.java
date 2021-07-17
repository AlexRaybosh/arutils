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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import arutils.async.CompletionCallback;
import arutils.async.Result;
import arutils.async.Workload;

public class ResultImpl<T> implements Result<T>, CompletionCallback<T> {

	private T ret;
	private Throwable err;
	final private Object[] args;
	static enum Status {WAITING, SUCCESS, FAILURE, CANCELED}
	private Status status=Status.WAITING;
	private Lock lock=new ReentrantLock();
	private Condition cond=lock.newCondition();
	
	public ResultImpl(Object[] args) {
		this.args=args;
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCancelled() {
		lock.lock();
		try {
			return status==Status.CANCELED;
		} finally {
			lock.unlock();	
		}
	}

	@Override
	public boolean isDone() {
		lock.lock();
		try {
			return status!=Status.WAITING;
		} finally {
			lock.unlock();	
		}
	}

	@Override
	public T get() throws InterruptedException, ExecutionException {
		lock.lockInterruptibly();
		try {
			while (status==Status.WAITING) cond.await();
			return proceed();
		} finally {
			lock.unlock();	
		}	
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		lock.lockInterruptibly();
		try {
			while (status==Status.WAITING) {
				boolean signalled=cond.await(timeout, unit);
				if (!signalled) throw new TimeoutException(timeout+" "+unit+" elapsed");
			}
			return proceed();
		} finally {
			lock.unlock();	
		}	
	}

	private T proceed() throws InterruptedException, ExecutionException {
		switch (status) {
		case SUCCESS:
			return ret;
		case CANCELED:
			throw new InterruptedException("Canceled");
		default:
			throw new ExecutionException(err);
		}
	}

	
	@Override
	public void completed(Workload workload, T ret, Object[] args) {
		lock.lock();
		try {
			status=Status.SUCCESS;
			this.ret=ret;
			cond.signalAll();
		} finally {
			lock.unlock();	
		}
		//workload.callCompleted();
	}

	@Override
	public void errored(Workload workload, Throwable e, Object[] args) {
		lock.lock();
		try {
			status=Status.FAILURE;
			this.err=e;
			cond.signalAll();
		} finally {
			lock.unlock();	
		}
		//workload.callCompleted();
	}

	@Override
	public Object[] getArgs() {
		return args;
	}

}
