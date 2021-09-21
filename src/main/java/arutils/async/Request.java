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


public class Request<T> {

	final private Workload workload;
	final private CompletionCallback<T> callback;
	final private Object[] args;
	private boolean reported;

	public Request(Workload workload, CompletionCallback<T> callback, Object[] args) {
		this.workload=workload;
		this.callback=callback;
		this.args=args;
	}

	public final void errored(Throwable e) {
		if (!reported) {
			reported=true;
			try {
				callback.errored(workload, e, args );
			} finally {
				workload.callCompleted();
			}
		}		
	}

	public final void completed() {
		if (!reported) {
			reported=true;
			try {
				callback.completed(workload, null, args );
			} finally {
				workload.callCompleted();
			}
		}
	}

	public final Object[] getArgs() {
		return args;
	}
	@SuppressWarnings("unchecked")
	public final <A>  A arg(int i) {
		return (A)args[i];
	}


	public final void setResult(T result) {
		if (!reported) {
			reported=true;
			try {
				callback.completed(workload, result, args );
			} finally {
				workload.callCompleted();
			}
		}
	}
}
