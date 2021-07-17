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

package arutils.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @author Alex Raybosh
 * The class represents a tuple of elements, as a thin wrapper on top of an array (e.g. a record), suitable for identity comparison based on identity of the individual elements
 * @param <T>
 */
public class UnorderedTuple<T>  {
	final T[] tuple;
	/**
	 * Construct a tuple based on an array of elements
	 * @param t
	 */
	public UnorderedTuple(T... t) {
		if (t==null) throw new IllegalArgumentException("null row passed");
		tuple=t;
	}
	/**
	 * 
	 * @return array of corresponding elements
	 */
	final public T[] get() {
		return tuple;
	}
	
	final public int size() {
		return tuple.length;
	}
	final public void set(int i, T val) {
		tuple[i]=val;
	}

	/* 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(tuple);
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnorderedTuple<T> other = (UnorderedTuple<T>) obj;
		if (!Arrays.equals(tuple, other.tuple))
			return false;
		return true;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "UnorderedTuple" + Arrays.toString(tuple);
	}

	/**
	 * Simple demo of the class
	 * @param args
	 */
	public static void main(String[] args) {
		Set<UnorderedTuple<String>> ordered=new HashSet<UnorderedTuple<String>>();
		ordered.add(new UnorderedTuple<String>(new String[] {"B","A"}));
		ordered.add(new UnorderedTuple<String>(new String[] {"A","B"}));
		ordered.add(new UnorderedTuple<String>(new String[] {"C","A"}));
		ordered.add(new UnorderedTuple<String>(new String[] {"C",null,"X"}));
		ordered.add(new UnorderedTuple<String>(new String[] {"B","B"}));
		ordered.add(new UnorderedTuple<String>(new String[] {"B",null}));
		ordered.add(new UnorderedTuple<String>(new String[] {"B",""}));
		ordered.add(new UnorderedTuple<String>(new String[] {"B"}));
		ordered.add(new UnorderedTuple<String>(new String[0]));  
		
		for (UnorderedTuple<String> t : ordered) System.out.println(t);
	}

}
