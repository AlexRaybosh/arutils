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

import java.util.Map;
import java.util.TreeMap;


public class PackageNode {
	String name, fullName;
	Map<String,PackageNode> children=new TreeMap<String, PackageNode>();

	public PackageNode() {
		this.name = "";
		this.fullName="";
	}

	public PackageNode(String fullName, String name) {
		this.name = name;
		this.fullName=fullName;
	}
	public final Map<String, PackageNode> getChildren() {
		return children;
	}
	
	public final String getName() {
		return name;
	}

	public final String getFullName() {
		return fullName;
	}

	public String toString() {
		StringBuilder sb=new StringBuilder();
		toString(0,sb);
		return sb.toString();
	}

	private void toString(int offset, StringBuilder sb) {
		for (int i=0; i<offset; i++) sb.append("\t");
		sb.append("\"").append(fullName).append("\" ").append("(").append(name).append(")\n");
		for (PackageNode n : children.values()) {
			n.toString(offset+1, sb);
		}
	}

    
    public static String getPackage(String fullName) {
    	int last=fullName.lastIndexOf('.');
    	if (last>=0) return fullName.substring(0, last);
    	return "";
    }
    
	public static String getName(String name) {
    	int last=name.lastIndexOf('.');
    	if (last>=0) {
    		return last>=name.length()?"":name.substring(last+1);
    	}
    	return name;
	}
	
    
	public static PackageNode add(PackageNode root, String pkg) {
		if (pkg==null || "".equals(pkg)) return root;
		while (pkg.startsWith(".")) pkg=pkg.substring(1);
		String parentPackageName="";
		String fullName="";
		String childPackageName=pkg;
		PackageNode currentRootNode=root;
		
		int pos=0;
		for (;;) {
			int nextDot=pkg.indexOf('.', pos);
			if (nextDot<0) {
				parentPackageName=pkg.substring(pos);
				fullName=pkg;
				childPackageName=null;
			} else {
				fullName=pkg.substring(0,nextDot);
				parentPackageName=pkg.substring(pos, nextDot);
				childPackageName=pkg.substring(nextDot+1);
			}
			Map<String, PackageNode>  map=currentRootNode.getChildren();
			PackageNode next=map.get(parentPackageName);
			if (next==null) {
				next=new PackageNode(fullName, parentPackageName);
				map.put(parentPackageName, next);
			}
			
			if (childPackageName==null || childPackageName.length()==0) {
				return next;
			} 
			
			currentRootNode=next;
			pos=nextDot+1;
			
		}
	}

	@Override
	public int hashCode() {
		return fullName.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PackageNode other = (PackageNode) obj;
		if (fullName == null) {
			if (other.fullName != null)
				return false;
		} else if (!fullName.equals(other.fullName))
			return false;
		return true;
	}

	public static void main(String[] strs) {
		String pkg="a.";
		PackageNode root=new PackageNode();
		System.out.println(PackageNode.add(root, "a.b.c"));
		System.out.println("----------------");
		PackageNode.add(root, "a.x.c");
		PackageNode d = PackageNode.add(root, "a.b.d");
		//breakPackage(root, "");
		//System.out.println(breakPackage(root, "e"));
		System.out.println(root);
		System.out.println("d.getFullName(): "+d.getFullName());
	}
}