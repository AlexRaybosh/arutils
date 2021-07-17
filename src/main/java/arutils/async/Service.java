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

public interface Service {
	<T> Result<T> call(Workload w, Object... args) throws InterruptedException;
	<T> Result<T> callNoLimit(Workload w, Object... args) throws InterruptedException;
	
	<T> void callWithCallback(Workload w, CompletionCallback<T> callback,Object... args) throws InterruptedException;
	<T> void callWithCallbackNoLimit(Workload w, CompletionCallback<T> callback,Object... args) throws InterruptedException;
	
	<T> void callWithCallback(CompletionCallback<T> callback,Object... args) throws InterruptedException;
	<T> void callWithCallbackNoLimit(CompletionCallback<T> callback,Object... args) throws InterruptedException;
	Workload getTrackingWorkload();

	<T> boolean tryCallWithCallback(CompletionCallback<T> callback, Object... args) throws InterruptedException;
	<T> boolean tryCallWithCallback(Workload w, CompletionCallback<T> callback, Object... args) throws InterruptedException;

}
