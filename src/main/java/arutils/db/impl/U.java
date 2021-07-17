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

package arutils.db.impl;

import java.util.Date;

import arutils.util.Utils;

public final class U {
	public static void appendWithLimit(StringBuilder sb, int limit, String str) {
		if (str==null)
			return;
		for (int i = 0; i<str.length();++i) {
			if (limit>0 && i>=limit) {
				sb.append("...");
				return;
			}
			sb.append(str.charAt(i));
		}
	}
	public static void normPrintObj(StringBuilder sb, Object obj) {
		if (obj==null) sb.append("NULL");
		else if (obj instanceof Number) sb.append(obj);
		else if (obj instanceof CharSequence) sb.append('\'').append(obj).append('\'');
		else if (obj instanceof Date) sb.append('\'').append(Utils.formatLocalDateTime((Date)obj)).append('\'');
		else sb.append('[').append(obj).append(']');
	}
	public static void printRow(StringBuilder sb, Object[] args) {
		for (int i=0;i<args.length;++i) {
			normPrintObj(sb,args[i]);
			if (i<args.length-1) sb.append(", ");
		}
	}
	public static String printRow(Object[] args) {
		StringBuilder sb=new StringBuilder();
		printRow(sb,args);
		return sb.toString();
	}
	public static String limit(String a, int l) {
		if (a==null) return "";
		if (l<=0) return a;
		return a.length()>l?(a.substring(0, l)+" ..."):a;
	}
}
