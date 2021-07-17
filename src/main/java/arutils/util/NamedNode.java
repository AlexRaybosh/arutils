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

import java.util.ArrayList;
import java.util.List;


public abstract class NamedNode {
	public abstract NamedNode add(String dn, Object val);
	public abstract NamedNode add(NamedNode child);
	public abstract NamedNode add(String name);
	public final NamedNode add() {return add((String)null);}
	public abstract boolean hasChildren();
	public abstract List<? extends NamedNode> getChildren();
	public abstract List<? extends NamedNode> getChildren(String n);
	public abstract NamedNode getFirst(String n);
	public abstract boolean containsName(String n);
	public abstract String getName();
	public abstract Object getValue();
	public abstract String getValueAsString();
	public abstract Long getValueAsLong();
	public abstract Integer getValueAsInteger();
	public abstract void setValue(Object value);
	
	public abstract List<? extends NamedNode> remove(String dn);
	public abstract boolean removeNode(NamedNode node);
	public abstract void removeAll();
	
	
	public abstract NamedNode setAttributeValue(String n, Object val);
	public abstract Object getAttributeValue(String n);
	public abstract String getAttributeValueAsString(String dn);
	public abstract Integer getAttributeValueAsInteger(String dn);
	public abstract Boolean getAttributeValueAsBoolean(String dn);
	public abstract NamedNode createClone();

	public final boolean hasAttributeValue(String n, Object val) {
		for (NamedNode c: getChildren(n)) {
			if (c.hasValue(val)) return true;
		}
		return false;
	}
	public final boolean hasValue(Object val) {
		Object v=getValue();
		if (v==null)
			return val==null;
		return v.equals(val);
	}
	public final boolean hasName(String n) {
		String name=getName();
		if (name==null)
			return n==null;
		return name.equals(n);
	}
	public List<? extends NamedNode> getChildren(String attr, String... attrs) {
		List<NamedNode> ret=new ArrayList<>(getChildren(attr));
		for (String a : attrs) {
			ret.addAll(getChildren(a));
		}
		return ret;
	}
	public abstract boolean hasChildWithName(String n);

}
