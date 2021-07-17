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

package arutils.db;

public class TableName implements Comparable<TableName> {
	final private String catalog;
	final private String schema;
	final private String name;
	
	
	public final String getCatalog() {
		return catalog;
	}

	public final String getSchema() {
		return schema;
	}

	public final String getName() {
		return name;
	}


	public TableName(String catalog, String schema, String name) {
		this.catalog = catalog;
		this.schema = schema;
		this.name = name;
	}


	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder();
		if (catalog!=null) sb.append(catalog).append('.');
		if (schema!=null) sb.append(schema);
		if (sb.length()>0 && sb.codePointAt(0)!='.') sb.append('.');
		sb.append(name);
		return sb.toString();
	}
	
	public String toString(DB db) {
		char escape=db.getIntrinsics().getEscapeChar();
		StringBuilder sb=new StringBuilder();
		if (catalog!=null) sb.append(escape).append(catalog).append(escape).append('.');
		if (schema!=null) sb.append(escape).append(schema).append(escape);
		if (sb.length()>0 && sb.codePointAt(0)!='.') sb.append('.');
		sb.append(escape).append(name).append(escape);
		return sb.toString();
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((catalog == null) ? 0 : catalog.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((schema == null) ? 0 : schema.hashCode());
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
		TableName other = (TableName) obj;
		if (catalog == null) {
			if (other.catalog != null)
				return false;
		} else if (!catalog.equals(other.catalog))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (schema == null) {
			if (other.schema != null)
				return false;
		} else if (!schema.equals(other.schema))
			return false;
		return true;
	}

	@Override
	public int compareTo(TableName o) {
		int cc=compare(catalog,o.catalog);
		if (cc!=0) return cc;
		int cs=compare(schema, o.schema);
		if (cs!=0) return cs;
		return compare(name,o.name);
	}

	private int compare(String my, String other) {
		if (my==null && other!=null) return -1;
		if (other==null && my!=null) return 1;
		if (my==null && other==null) return 0;
		return my.compareTo(other);
	}

	
	
}

