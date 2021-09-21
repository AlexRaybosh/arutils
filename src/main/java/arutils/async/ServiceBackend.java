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

import java.util.List;

public abstract class ServiceBackend<T> {

	public abstract void process(List<Request<T>> bulk) throws Exception;
	public int getMaxBulkSize() {return 1;}
	public long getWorkerReleaseTimeout() {return 0;}
	public int getMaxWorkers() {return 1;}
	public int getMaxQueuedRequests() {return 1;}

}
