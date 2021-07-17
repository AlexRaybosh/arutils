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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;


public class NamedObject extends NamedNode {
	private final String name;
	private Object value;
	protected Internal internal=null;
	
	public NamedObject(String n, Object value) {
		name=n;
		this.value=value;
	}
	public NamedObject() {
		name=null;value=null;
	}
	public static NamedObject create() {
		return new NamedObject(null,null);
	}
	public static NamedObject create(String name) {
		return new NamedObject(name,null);
	}
	
	public static NamedObject create(String n, Object value) {
		return new NamedObject(n,value);
	}

	@Override
	public NamedNode add(String dn, Object val) {
		return add(create(dn,val));
	}

	@Override
	public NamedNode add(String name) {
		return add(name,null);
	}

	
	@Override
	public NamedNode add(NamedNode child) {
		initInternal();
		internal.addChild(child);
		return child;
	}


	@Override
	public List<NamedNode> getChildren() {
		initInternal();
		return internal;
	}


	@Override
	public boolean hasChildren() {
		return internal==null?false:internal.size()>0;
	}

	@Override
	public List<NamedNode> getChildren(String n) {
		if (internal==null) return Collections.<NamedNode>emptyList();
		return internal.getChildren(n);
	}
	@Override
	public boolean hasChildWithName(String n) {
		if (internal==null) return false;
		return internal.hasChildWithName(n);
	}
	
	public NamedNode getFirst(String n) {
		if (internal==null || internal.getChildren(n).size()==0) return null;
		return internal.getChildren(n).get(0);
	}
	@Override
	public boolean removeNode(NamedNode node) {
		if (internal==null) return false;
		return internal.removeNode(node);
	}

	public boolean containsName(String n) {
		if (internal==null) return false;
		return internal.containsName(n);
	}
	
	
	private void initInternal() {
		if (internal==null) internal=new Internal();
	}

	public final String getName() {
		return name;
	}
	
	public final Object getValue() {
		return value;
	}
	@Override
	public final String getValueAsString() {
		return value==null?null:value.toString();
	}
	@Override
	public final Integer getValueAsInteger() {
		if (value instanceof Number) return ((Number)value).intValue();
		if (value==null) return null;
		return Integer.parseInt(value.toString());
	}

	@Override
	public final Long getValueAsLong() {
		if (value instanceof Number) return ((Number)value).longValue();
		if (value==null) return null;
		return Long.parseLong(value.toString());		
	}
	public void setValue(Object value) {
		this.value = value;
	}
	
	@Override
	public Object getAttributeValue(String dn) {
		if (internal==null) return null;
		return internal.getScalarValue(dn);	
	}
	@Override
	public String getAttributeValueAsString(String dn) {
		if (internal==null) return null;
		Object v=internal.getScalarValue(dn);
		return v==null?null:v.toString();
	}
	@Override
	public Integer getAttributeValueAsInteger(String dn) {
		if (internal==null) return null;
		Object v=internal.getScalarValue(dn);
		if (v instanceof Number) return ((Number) v).intValue();
		return v==null?null:Integer.parseInt(v.toString());
	}
	@Override
	public Boolean getAttributeValueAsBoolean(String dn) {
		if (internal==null) return null;
		Object v=internal.getScalarValue(dn);
		if (v instanceof Boolean) return (Boolean)v;
		if (v==null) return null;
		if (v instanceof Number) return ((Number) v).intValue()!=0;
		if (v.toString().equals("true")) return true;
		if (v.toString().equals("false")) return false;
		if (v.toString().equals("Y")) return true;
		if (v.toString().equals("N")) return false;
		if (v.toString().startsWith("Y")) return true;
		if (v.toString().startsWith("N")) return false;
		if (v.toString().startsWith("y")) return true;
		if (v.toString().startsWith("n")) return false;
		if (v.toString().equals("0")) return false;
		if (v.toString().matches("\\d+")) return true;
		throw new RuntimeException("Can't interpret ("+v+" as a boolean");
	}

	
	@Override
	public List<NamedNode> remove(String dn) {
		if (internal==null) return null;
		return internal.removeName(dn);	
	}
	@Override
	public void removeAll() {
		internal=null; 
	}
	@Override
	public NamedNode setAttributeValue(String dn, Object val) {
		initInternal();
		return internal.setScalarValue(this, dn, val);	
	}

	
	
	
	private static class Internal extends ArrayList<NamedNode> {
		private static final long serialVersionUID = 1L;

		final public void modify() {
			map=null;
		}

		Map<String,List<NamedNode>> map=null;
		
		final boolean containsName(String n) {
			initMap();
			return map.containsKey(n);
			
		}
		
		final void addChild(NamedNode child) {
			super.add(child);
			if (map!=null) {
				List<NamedNode> l=map.get(child.getName());
				if (l==null) {
					l=new ArrayList<NamedNode>(1);
					map.put(child.getName(),l);
				}
				l.add(child);
			}
		}

		final List<NamedNode> removeName(String dn) {
			List<NamedNode> ret=new ArrayList<NamedNode>(1);
			for (InternalIterator it=(InternalIterator)iterator(); it.hasNext();) {
				NamedNode obj=it.next();
				if (equalsWithNull(obj.getName(),dn)) {
					ret.add(obj);
					it.removeCheat();
				}
			}
			return ret;
		}
		public boolean removeNode(NamedNode node) {
			boolean ret=false;
			for (InternalIterator it=(InternalIterator)iterator(); it.hasNext();) {
				NamedNode obj=it.next();
				if (obj.equals(node)) {
					it.removeCheat();
					ret=true;
				}
			}
			return ret;
		}
		
		final NamedNode setScalarValue(NamedObject namedObject, String dn, Object val) {
			initMap();
			List<NamedNode> l=map.get(dn);
			if (l==null || l.size()==0) {
				// Brand new
				l=new ArrayList<NamedNode>(1);
				NamedNode obj=namedObject.create(dn,val);
				l.add(obj);
				map.put(dn, l);
				super.add(obj);
				return obj;
			} else {
				NamedNode theOne=l.get(0);
				theOne.setValue(val);
				if (l.size()>0) {
					// here i really need to cleanup
					l.clear();
					l.add(theOne);
					for (InternalIterator it=(InternalIterator)iterator(); it.hasNext();) {
						NamedNode obj=it.next();
						if (equalsWithNull(obj.getName(),dn) && obj!=theOne)
							it.removeCheat();
					}
				}
				return theOne;
			}
		}
		final List<NamedNode> getChildren(String dn) {
			initMap();
			List<NamedNode> list=map.get(dn);
			return list==null?Collections.<NamedNode>emptyList():list;
		}
		
		final boolean hasChildWithName(String dn) {
			initMap();
			return map.containsKey(dn);
		}
		
		
		public Object getScalarValue(String dn) {
			List<NamedNode> l=getChildren(dn);
			return (l.size()>0)?l.get(0).getValue():null; 
		}
		
		private void initMap() {
			if (map==null) {
				map=new HashMap<String,List<NamedNode>>();
				int l=size();
				for (int i=0;i<l;++i) {
					NamedNode obj=get(i);
					List<NamedNode> list=map.get(obj.getName());
					if (list==null) {
						list=new ArrayList<NamedNode>();
						map.put(obj.getName(), list);
					}
					list.add(obj);
				}
			}
		}

		@Override
		public Iterator<NamedNode> iterator() {
			return new InternalIterator(this, super.iterator());
		}
		@Override
		public boolean add(NamedNode e) {modify();return super.add(e);}
		@Override
		public boolean remove(Object o) {modify();return super.remove(o);}
		@Override
		public boolean addAll(Collection<? extends NamedNode> c) {modify();return super.addAll(c);}
		@Override
		public boolean addAll(int index, Collection<? extends NamedNode> c) {modify();return super.addAll(index, c);}
		@Override
		public boolean removeAll(Collection<?> c) {modify();return super.removeAll(c);}
		@Override
		public boolean retainAll(Collection<?> c) {modify();return super.retainAll(c);}
		@Override
		public void clear() {modify();super.clear();}
		@Override
		public NamedNode set(int index, NamedNode element) {modify();return super.set(index, element);}
		@Override
		public void add(int index, NamedNode element) {modify();super.add(index, element);}
		@Override
		public NamedNode remove(int index) {modify();return super.remove(index);}
		@Override
		public ListIterator<NamedNode> listIterator() {
			return new InternalListIterator(this,super.listIterator());
		}
		@Override
		public ListIterator<NamedNode> listIterator(int index) {
			return new InternalListIterator(this,super.listIterator(index));
		}
	}
	
	static class InternalListIterator implements ListIterator<NamedNode> {

		private Internal internal;
		private ListIterator<NamedNode> it;

		public InternalListIterator(Internal internal, ListIterator<NamedNode> it) {
			this.internal=internal;
			this.it=it;
		}
		@Override
		public boolean hasNext() {return it.hasNext();}
		@Override
		public NamedNode next() {return it.next();}
		@Override
		public boolean hasPrevious() {return it.hasPrevious();}
		@Override
		public NamedNode previous() {return it.previous();}
		@Override
		public int nextIndex() {return it.nextIndex();}
		@Override
		public int previousIndex() {return it.previousIndex();}
		@Override
		public void remove() {internal.modify();it.remove();}
		@Override
		public void set(NamedNode e) {internal.modify();it.set(e);}
		@Override
		public void add(NamedNode e) {internal.modify();it.add(e);}		
	}
	static class InternalIterator implements Iterator<NamedNode> {
		private Iterator<NamedNode> it;
		private Internal internal;
		InternalIterator(Internal internal, Iterator<NamedNode> it) {
			this.internal=internal;
			this.it=it;
		}
		@Override
		public final boolean hasNext() {
			return it.hasNext();
		}
		@Override
		public final NamedNode next() {
			return it.next();
		}
		@Override
		public final void remove() {internal.modify();it.remove();}
		public final void removeCheat() {it.remove();}
	}


	public static boolean equalsWithNull(Object o1, Object o2) {
		if (o1!=null)
			return o1.equals(o2);
		if (o2!=null)
			return o2.equals(o1);
		return true;
	}
	
	public static void print(NamedNode node, StringBuilder sb, int offset) {
//		if (obj==null) return;
		Utils.printMargin(sb,offset);
		if (node.getName()!=null) {
			sb.append(node.getName());
			Object val=node.getValue();
			if (val!=null) {
				if (val instanceof String) sb.append("=\"").append((String)val).append("\"");
				else if (val instanceof Number) sb.append("=").append(val).append("");
				else if (val instanceof Date) sb.append("='").append(Utils.formatLocalDateTime((Date)val)).append("'");
				else if (val instanceof byte[]) sb.append("=").append("0x").append(EncodingUtils.getHex((byte[])val)).append("");
				else sb.append("=").append(val.toString()).append("");
			}
		} else {
			Object val=node.getValue();
			if (val!=null) {
				if (val instanceof String) sb.append("\"").append(val).append("\" ");
				else if (val instanceof Number) sb.append("").append(val).append(" ");
				else if (val instanceof Date) sb.append("'").append(Utils.formatLocalDateTime((Date)val)).append("' ");
				else if (val instanceof byte[]) sb.append("").append("0x").append(EncodingUtils.getHex((byte[])val)).append(" ");
//				else if (val instanceof BusinessDate) sb.append("'").append(val.toString()).append("' ");
				else sb.append("").append(val.toString()).append(" ");
			}
		}
		if (node.getName()!=null || node.getValue()!=null) sb.append(" ");
		if (node.hasChildren() && node.getChildren().size()>0) {
			sb.append("{\n");
			
			for (NamedNode  child : node.getChildren()) {
					print(child, sb,offset+1);
			}

			Utils.printMargin(sb, offset);
			sb.append("}\n");
		} else sb.append("\n");

		
	}
	public String toString() {
		StringBuilder sb=new StringBuilder();
		print(this,sb,0);
		return sb.toString();
	}
	

	@Override
	public NamedNode createClone() {
		Object value=getValue();
		NamedObject obj=new NamedObject(getName(),value);
		for (NamedNode c: getChildren()) {
			obj.add(c.createClone());
		}
		return obj;
	}

	
	public static void main(String[] args) throws Exception {
		/*DomainObject obj=DomainObject.create("Attr", "O'm");
		obj.add("a1", "hi").add("a2","there");
		obj.add("a1", "hi").add("a3","there");
		obj.add("a2", "hi").add("a3","there");
		*/
		//System.out.println(obj);
		
		//System.out.println(obj);
		//for (int i=0;i<1000000;++i) {
			NamedObject obj= new NamedObject("Attr", "O'm");
			obj.add("a1", "hi").add("a2","there");
			obj.add("a1", "hi").add("a3","there");
			obj.add("a2", "hi").add("a3","there");
			obj.setAttributeValue("a3", "no");
			String v=obj.getAttributeValueAsString("a3");
			System.out.println(obj);
		//}
		{
		NamedNode treeGrid=new NamedObject("treeGreed",null);
		NamedNode header = treeGrid.add("header",null);
		header.add("column", "C1").add("type","STRING");
		header.add("column", "C2").add("type","SOFTREF");
		header.add("column", "C3").add("type","REF");
		
		NamedNode row=treeGrid.add("row",1);
		row.add("comments", "Very Special");
		NamedNode data=row.add("data");
		data.add("C1", "abc");
		data.add("C2", "/vendors/hello/me");
		data.add("C3", UUID.randomUUID());

		row=row.add("row",2);
		data=row.add("data");
		data.add("C1", "abc");
		data.add("C2", "/vendors/hello/me");
		data.add("C3", UUID.randomUUID());
		
		row=row.add("row",3);
		row.add("enter_condition", "2+2=4");
		data=row.add("data");
		data.add("C1", "abc");
		data.add("C2", "/vendors/hello/me");
		data.add("C3", UUID.randomUUID());
		data.add(null, UUID.randomUUID());

		row=treeGrid.add("row", 4);
		row.add("active",false);
		data=row.add("data");
		data.add("C1", "abc");
		data.add("C2", "/vendors/hello/me");
		data.add("C3", UUID.randomUUID());
		
		System.out.println(treeGrid);
		List<NamedObject> columns=(List<NamedObject>)header.getChildren("column");
		for (Iterator<NamedObject> it=columns.iterator(); it.hasNext();)  {
			NamedObject o=it.next();
			System.out.println(o);
			it.remove();
		}
		System.out.println(header);
		
		/*
		{
		NamedNode treeGrid=new NamedObject("treeGreed",null);
		treeGrid.add("column", "C1").add("type","STRING");
		treeGrid.add("column", "C2").add("type","SOFTREF");
		treeGrid.add("column", "C3").add("type","REF");
		NamedNode record=treeGrid.add("record",1);
		record.add("C1", "abc");
		record.add("C2", "/vendors/hello/me");
		record.add("C3", UUID.randomUUID());
		
		record=treeGrid.add("record",2);
		record.add("C1", "abc");
		record.add("C2", "/vendors/hello/me");
		record.add("C3", UUID.randomUUID());
		
		record=treeGrid.add("record",3);
		record.add("C1", "abc");
		record.add("C2", "/vendors/hello/me");
		record.add("C3", UUID.randomUUID());
		
		System.out.println(treeGrid);
		}
		
		*/
		}
	}




}
