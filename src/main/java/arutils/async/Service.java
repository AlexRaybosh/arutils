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

public interface Service<T> {
	Result<T> call(Workload w, Object... args) throws InterruptedException;
	Result<T> callNoLimit(Workload w, Object... args) throws InterruptedException;
	
	void callWithCallback(Workload w, CompletionCallback<T> callback,Object... args) throws InterruptedException;
	void callWithCallbackNoLimit(Workload w, CompletionCallback<T> callback,Object... args) throws InterruptedException;
	
	void callWithCallback(CompletionCallback<T> callback,Object... args) throws InterruptedException;
	void callWithCallbackNoLimit(CompletionCallback<T> callback,Object... args) throws InterruptedException;
	Workload getTrackingWorkload();

	boolean tryCallWithCallback(CompletionCallback<T> callback, Object... args) throws InterruptedException;
	boolean tryCallWithCallback(Workload w, CompletionCallback<T> callback, Object... args) throws InterruptedException;

}
