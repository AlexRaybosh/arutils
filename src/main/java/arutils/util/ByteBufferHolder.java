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

import java.nio.ByteBuffer;
import java.util.UUID;

public final class ByteBufferHolder {
	public final static int DEFAULT_SIZE=65536; 
	private ByteBuffer byteBuffer;
	private boolean isFree=true;
	
	public ByteBufferHolder() {
		byteBuffer=ByteBuffer.allocate(DEFAULT_SIZE);
	}
	public ByteBufferHolder(ByteBuffer b) {
		byteBuffer=b;
	}
		
	public ByteBufferHolder(byte[] arr, int offset, int length) {
		byteBuffer=ByteBuffer.wrap(arr, offset, length);
	}
	public ByteBufferHolder(byte[] arr) {
		byteBuffer=ByteBuffer.wrap(arr);
	}
	public ByteBufferHolder(int size) {
		byteBuffer=ByteBuffer.allocate(size);
	}

	final public ByteBuffer getByteBuffer() {return byteBuffer;}
	
	final public void put(byte b) {
		makeAvailable(1);
		byteBuffer.put(b);
	}
	
	final public void makeAvailable(int size) {
		if (byteBuffer.capacity()-byteBuffer.position()<size) {
			ByteBuffer nb=ByteBuffer.allocate(2*byteBuffer.capacity()+size+1);
			System.arraycopy(byteBuffer.array(), 0, nb.array(), 0, byteBuffer.position());
			nb.position(byteBuffer.position());
			byteBuffer=nb;
			//System.out.println("expanded to "+nb.capacity());
		}
	}
	
	final public byte get() {
		return byteBuffer.get();
	}
	final public int getInt() {
		return byteBuffer.getInt();
	}
	final public long getLong() {
		return byteBuffer.getLong();
	}
	final public float getFloat() {
		return byteBuffer.getLong();
	}	
	final public double getDouble() {
		return byteBuffer.getDouble();
	}	
	
	final public void flip() {
		byteBuffer.flip();
	}
	
	final public void putBytes(byte[] bytes) {
		makeAvailable(bytes.length);
		byteBuffer.put(bytes);
	}
	
	final public boolean isFree() {
		return isFree;
	}
	final public void setTaken() {
		isFree=false;
	}
	final public void setFree() {
		byteBuffer.clear();
		isFree=true;
	}
	final public int position() {
		return byteBuffer.position();
	}
	final public void clear() {
		byteBuffer.clear();
	}
	final public void put(float f) {
		makeAvailable(4);
		byteBuffer.putFloat(f);
	}
	final public void putInt(int i) {
		makeAvailable(4);
		byteBuffer.putInt(i);
	}
	final public void putDouble(int d) {
		makeAvailable(8);
		byteBuffer.putDouble(d);
	}
	final public void putLong(int l) {
		makeAvailable(8);
		byteBuffer.putLong(l);
	}
	final public void putUUID(UUID id) {
		makeAvailable(16);
		byteBuffer.putLong(id.getMostSignificantBits());
		byteBuffer.putLong(id.getLeastSignificantBits());
	}

	
	@Override
	public String toString() {
		return "ByteBufferHolder [byteBuffer=" + byteBuffer + ", isFree="
				+ isFree + "]";
	}
	final public int limit() {
		return byteBuffer.limit();
	}
	final public void get(byte[] arr) {
		byteBuffer.get(arr);
	}
	final public void get(byte[] arr,int offset, int length) {
		byteBuffer.get(arr, offset, length);
	}
	final public void moveTo(byte[] data) {
		System.arraycopy(byteBuffer.array(), 0,data, 0, byteBuffer.position());
	}
	final public byte[] array() {
		return byteBuffer.array();
	}
	
}
