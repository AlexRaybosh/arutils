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
import java.util.TreeSet;

public class Tuple<T extends Comparable<T>> implements Comparable<Tuple<T>> {
	final T[] tuple;
	
	public Tuple(T... t) {
		if (t==null) throw new IllegalArgumentException("null row passed");
		tuple=t;
	}

	public T[] get() {
		return tuple;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(tuple);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Tuple<T> other = (Tuple<T>) obj;
		if (!Arrays.equals(tuple, other.tuple))
			return false;
		return true;
	}

	@Override
	public int compareTo(Tuple<T> o) {
		T[] otherRow=o.get();
		
		for (int i=0;i<tuple.length;i++) {
			T my=tuple[i];
			if (i>=otherRow.length) {
				//shorter is lower 
				return 1;
			}
			T other=otherRow[i];
			if (my==null && other!=null) return -1;
			if (other==null && my!=null) return 1;
			if (my!=null && other!=null) {
				int c=my.compareTo(other);
				if (c!=0) return c;
			}
		}
		return otherRow.length>tuple.length? -1 : 0;
	}


	@Override
	public String toString() {
		return "Tuple" + Arrays.toString(tuple);
	}

	public static void main(String[] args) {
		TreeSet<Tuple<String>> ordered=new TreeSet<Tuple<String>>();
		ordered.add(new Tuple<String>(new String[] {"B","A"}));
		ordered.add(new Tuple<String>(new String[] {"A","B"}));
		ordered.add(new Tuple<String>(new String[] {"C","A"}));
		ordered.add(new Tuple<String>(new String[] {"C",null,"X"}));
		ordered.add(new Tuple<String>(new String[] {"B","B"}));
		ordered.add(new Tuple<String>(new String[] {"B",null}));
		ordered.add(new Tuple<String>(new String[] {"B",""}));
		ordered.add(new Tuple<String>(new String[] {"B"}));
		ordered.add(new Tuple<String>(new String[0]));
		
		
		for (Tuple<String> t : ordered) System.out.println(t);
	}

}
