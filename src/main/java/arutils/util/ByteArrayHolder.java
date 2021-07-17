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

import java.io.IOException;
import java.io.InputStream;

public final class ByteArrayHolder {
	public final static int DEFAULT_SIZE=65536; 
	private byte[] arr;
	private int pos=0;
	private boolean isFree=true;
	
	public ByteArrayHolder() {arr=new byte[DEFAULT_SIZE];}
	
	
	public ByteArrayHolder(byte[] another) {
		arr=another;
	}
	
	public ByteArrayHolder(int size) {
		arr=new byte[size];
	}
	
	final public byte[] array() {return arr;}
	final public byte[] getBytes() {return arr;}
	final public void array(byte[] another) {
		arr=another;
	}	
	final public void setBytes(byte[] another) {
		arr=another;
	}
	final public void pushByte(byte b) {
		makeAvailable(1);
		arr[pos]=b;
		++pos;
	}
	
	final public void makeAvailable(int size) {
		if (arr.length-pos<size) {
			byte[] na=new byte[ 2*(arr.length+size+1) ];
			System.arraycopy(arr, 0, na, 0, arr.length);
			arr=na;
		}
	}


	final public void position(int p) {
		pos=p;
	}

	
	final public boolean isFree() {
		return isFree;
	}
	final public void setTaken() {
		isFree=false;
	}
	final public void setFree() {
		isFree=true;
	}


	final public byte get() {
		return arr[pos++];
	}
	final public void clear() {
		pos=0;
	}
	final public void flip() {
		pos=0;
	}


	final public int position() {
		return pos;
	}

	public final int pushInputStreamShot(InputStream in, int upToLen) throws IOException {
		if (upToLen>0) {
			makeAvailable(upToLen);
			int r=in.read(arr, pos, upToLen);
			if (r>0) pos+=r;
			return r;
		} else {
			int avail=arr.length-pos;
			if (avail<=0) {
				makeAvailable(1);
				avail=arr.length-pos;
			}
			int r=in.read(arr,pos,avail);
			if (r>0) pos+=r;
			return r;
		}
		
	}
	
	public final void pushBytes(byte[] blob) {
		makeAvailable(blob.length);
		System.arraycopy(blob, 0, arr, pos, blob.length);
		pos+=blob.length;
	}
	public void pushBytes(byte[] blob, int s, int size) {
		makeAvailable(blob.length);
		System.arraycopy(blob, s, arr, pos, size);
		pos+=size;
	}

	public final void pushFloat(float f) {
		int asInt=Float.floatToRawIntBits(f);
		pushInt(asInt);
		
	}
	public final void pushInt(int value) {
		makeAvailable(4);
		arr[pos++]=(byte)(value >>> 24);
		arr[pos++]=(byte)(value >>> 16);
		arr[pos++]=(byte)(value >>> 8);
		arr[pos++]=(byte)(0xff & value);
	}


    private static byte int3(int x) { return (byte)(x >> 24); }
    private static byte int2(int x) { return (byte)(x >> 16); }
    private static byte int1(int x) { return (byte)(x >>  8); }
    private static byte int0(int x) { return (byte)(x >>  0); }


	public final double getDouble() {
		return Double.longBitsToDouble(getLong());
	}


	private long getLong() {
		return makeLong(
				arr[pos++],
				arr[pos++],
				arr[pos++],
				arr[pos++],
				arr[pos++],
				arr[pos++],
				arr[pos++],
				arr[pos++]);
	}



    public static long makeLong(byte b7, byte b6, byte b5, byte b4,byte b3, byte b2, byte b1, byte b0) {
    	return ((((long)b7 & 0xff) << 56) |
				(((long)b6 & 0xff) << 48) |
				(((long)b5 & 0xff) << 40) |
				(((long)b4 & 0xff) << 32) |
				(((long)b3 & 0xff) << 24) |
				(((long)b2 & 0xff) << 16) |
				(((long)b1 & 0xff) <<  8) |
				(((long)b0 & 0xff) <<  0));
	}


	final public float getFloat() {
		return Float.intBitsToFloat(getInt());
	}

	public static int makeInt(byte b3, byte b2, byte b1, byte b0) {
		return (int)((((b3 & 0xff) << 24) |
			      ((b2 & 0xff) << 16) |
			      ((b1 & 0xff) <<  8) |
			      ((b0 & 0xff) <<  0)));
    }

	final public int getInt() {
		return makeInt(arr[pos++],
		arr[pos++],
		arr[pos++],
		arr[pos++]);
	}
	final public int getIntAt(int x) {
		return makeInt(arr[x++],
		arr[x++],
		arr[x++],
		arr[x++]);
	}


	final public void get(byte[] dst) {
		System.arraycopy(arr, pos, dst, 0, dst.length);
	}



	
	

}
