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
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;

public class EncodingType {
	public static final int NULL=0; // null value
	public static final int NUMBER=1;
	public static final int TIMESTAMP=2;
	public static final int BUSINESS_DAY=3;
	public static final int TEXT=4;
	public static final int BLOB=5;
	public static final int BOOL=6;
	
	static final int SYB_NUMBER_POSITIVE_INT=0;
	static final int SYB_NUMBER_NEGATIVE_INT=1;
	static final int SYB_NUMBER_POSITIVE_LONG=2;
	static final int SYB_NUMBER_NEGATIVE_LONG=3;
	static final int SYB_NUMBER_NATIVE_DOUBLE=4;
	static final int SYB_NUMBER_NATIVE_FLOAT=5;
	static final int SYB_NUMBER_BINSTR_DOUBLE=6;
	static final int SYB_NUMBER_BINSTR_FLOAT=7;
	static final int SYB_NUMBER_BIG_DECIMAL=8;
	static final int SYB_NUMBER_POSITIVE_INT_DOUBLE=9;
	static final int SYB_NUMBER_NEGATIVE_INT_DOUBLE=10;
	
	static final int NUMBER_POSITIVE_INT= (SYB_NUMBER_POSITIVE_INT<<3) | NUMBER;
	static final int NUMBER_NEGATIVE_INT= (SYB_NUMBER_NEGATIVE_INT<<3) | NUMBER;
	static final int NUMBER_POSITIVE_LONG=(SYB_NUMBER_POSITIVE_LONG<<3) | NUMBER;
	static final int NUMBER_NEGATIVE_LONG=(SYB_NUMBER_NEGATIVE_LONG<<3) | NUMBER;
	static final int NUMBER_NATIVE_DOUBLE=(SYB_NUMBER_NATIVE_DOUBLE<<3) | NUMBER;
	static final int NUMBER_NATIVE_FLOAT=(SYB_NUMBER_NATIVE_FLOAT<<3) | NUMBER;
	static final int NUMBER_BINSTR_DOUBLE=(SYB_NUMBER_BINSTR_DOUBLE<<3) | NUMBER;
	static final int NUMBER_POSITIVE_INT_DOUBLE=(SYB_NUMBER_POSITIVE_INT_DOUBLE<<3) | NUMBER;
	static final int NUMBER_NEGATIVE_INT_DOUBLE=(SYB_NUMBER_NEGATIVE_INT_DOUBLE<<3) | NUMBER;
	static final int NUMBER_BIG_DECIMAL=(SYB_NUMBER_BIG_DECIMAL<<3) | NUMBER;

/*	
	static final int SYB_POSITIVE_BD=0;
	static final int SYB_NEGATIVE_BD=1;
	static final int POSITIVE_BUSINESS_DAY=(SYB_POSITIVE_BD<<3) | BUSINESS_DAY;
	static final int NEGATIVE_BUSINESS_DAY=(SYB_NEGATIVE_BD<<3) | BUSINESS_DAY;
*/
	
	
	public static void encodeObject(ByteBufferHolder bh, Object obj, boolean fb) throws IOException {
		if (obj==null) encodeNull(bh,fb);
		else if (obj.getClass()==String.class) encodeText(bh,(String)obj,fb);
		else if (obj.getClass()==Integer.class) encodeInt(bh,((Integer)obj).intValue(),fb);
		else if (obj.getClass()==(Boolean.class)) encodeBoolean(bh,(Boolean)obj,fb);
		else if (obj.getClass()==Double.class) encodeDouble(bh,((Double)obj),fb);
		else if (obj.getClass()==java.util.Date.class) encodeTimestamp(bh,((Date)obj).getTime(),fb);
		else if (obj.getClass()==BigDecimal.class) encodeBigDecimal(bh,(BigDecimal)obj,fb);
		else if (obj.getClass()==Long.class) encodeLong(bh,((Long)obj).longValue(),fb);
		else if (obj.getClass()==Float.class) encodeFloat(bh,((Float)obj).floatValue(),fb);
		else if (obj.getClass()==byte[].class) encodeBlob(bh,((byte[])obj),fb);
		else if (obj instanceof Date) encodeTimestamp(bh,((Date)obj).getTime(),fb);
		else throw new IOException("Unsupported class "+obj.getClass());
	}
	


	public static Object decodeObject(ByteBufferHolder bh,boolean[] fbOut) throws IOException {
		return decodeObject(bh.getByteBuffer(),fbOut);
	}
	public static Object decodeObject(ByteBuffer bb,boolean[] fbOut) throws IOException {
		int full=0xff & bb.get();
		fbOut[0]= (full & 0x80)!=0;
		
		switch (full & 0x07) {
		case NULL:
			return null;
		case TEXT:
			return decodeText(bb, full);
		case BOOL:
			return decodeBool(full);			
		case NUMBER:
			return decodeNumber(bb,full);
		case TIMESTAMP:
			return decodeTimestamp(bb, full);
/*
		case BUSINESS_DAY:
			return decodeBusinessDate(bb, full);
*/
		case BLOB:
			return decodeBlob(bb, full);

		default:
			throw new IOException("Invalid byte "+full+" in "+bb);
		}
	}

	private static Object decodeNumber(ByteBuffer bb, int full) throws IOException {
		switch (full & 0x7f) {
		case NUMBER_POSITIVE_INT: return (int)EncodingUtils.decodePositiveLong(bb);
		case NUMBER_NEGATIVE_INT: return -(int)EncodingUtils.decodePositiveLong(bb);
		case NUMBER_POSITIVE_LONG: return EncodingUtils.decodePositiveLong(bb);
		case NUMBER_NEGATIVE_LONG: return -EncodingUtils.decodePositiveLong(bb);
		case NUMBER_NATIVE_DOUBLE: return decodeNativeDouble(bb);
		case NUMBER_NATIVE_FLOAT:  return decodeNativeFloat(bb);
		case NUMBER_BINSTR_DOUBLE: return decodeBinStrDouble(bb);
		case NUMBER_POSITIVE_INT_DOUBLE: return decodePositiveIntDouble(bb);
		case NUMBER_NEGATIVE_INT_DOUBLE: return decodeNegativeIntDouble(bb);
		case NUMBER_BIG_DECIMAL: return decodeBigDecimal(bb);
		default:
			throw new IOException("Invalid NUMBER qualifier "+full+" in "+bb);
		}
	}
	

	private static Float decodeNativeFloat(ByteBuffer bb) {
		return bb.getFloat();
	}
	
	private static Double decodeNativeDouble(ByteBuffer bb) {
		return bb.getDouble();
	}

	private static void encodeInt(ByteBufferHolder bh, int i, boolean fb) {
		int fm= fb?0x80:0;
		if (i<0) {
			bh.put((byte) (fm | NUMBER_NEGATIVE_INT));
			EncodingUtils.encodePositiveLong(bh, -i);
		} else {
			bh.put((byte) (fm | NUMBER_POSITIVE_INT));
			EncodingUtils.encodePositiveLong(bh, i);
		}
	}
	

	private static void encodeLong(ByteBufferHolder bh, long l, boolean fb) {
		int fm= fb?0x80:0;
		if (l<0) {
			bh.put((byte)(fm | NUMBER_NEGATIVE_LONG));
			EncodingUtils.encodePositiveLong(bh, -l);
		} else {
			bh.put((byte)(fm | NUMBER_POSITIVE_LONG));
			EncodingUtils.encodePositiveLong(bh, l);
		}
	}

	private static void encodeBigDecimal(ByteBufferHolder bh, BigDecimal bd, boolean fb) {
		String strNum=bd.toString();
		int fm= fb?0x80:0;
		bh.put((byte)(fm | NUMBER_BIG_DECIMAL));
		EncodingUtils.encodeNumberString(bh, strNum);
	}
	private static BigDecimal decodeBigDecimal(ByteBuffer bb) throws IOException {
		String strNum=EncodingUtils.decodeNumberString(bb);
		return new BigDecimal(strNum);
	}	
	
	private static void encodeDouble(ByteBufferHolder bh, Double d, boolean fb) {
		int fm= fb?0x80:0;
		long f=d.longValue();
		double d1=(double)f;
		if (d==d1) {
			if (f<0) {
				bh.put((byte)(fm | NUMBER_NEGATIVE_INT_DOUBLE));
				EncodingUtils.encodePositiveLong(bh, -f);

			} else {
				bh.put((byte)(fm | NUMBER_POSITIVE_INT_DOUBLE)); 
				EncodingUtils.encodePositiveLong(bh, f);
			}
		} else {
			bh.put((byte)(fm | NUMBER_BINSTR_DOUBLE));
			EncodingUtils.encodeNumberString(bh, d.toString());
		}
	}
	
	private static void encodeFloat(ByteBufferHolder bh, float f, boolean fb) {
		int fm= fb?0x80:0;
		bh.put((byte) (fm | NUMBER_NATIVE_FLOAT));
		bh.put(f);		
	}
	
	private static Double decodeBinStrDouble(ByteBuffer bb) throws IOException {
		String strNum=EncodingUtils.decodeNumberString(bb);
		return new Double(strNum);	
	}
	private static Double decodePositiveIntDouble(ByteBuffer bb) throws IOException {
		return (double)EncodingUtils.decodePositiveLong(bb);
	}
	
	private static Double decodeNegativeIntDouble(ByteBuffer bb) throws IOException {
		return -(double)EncodingUtils.decodePositiveLong(bb);
	}

	
	private final static long UTC_2030=1893456000000l;
/*	
	private final static int BUSINESS_DATE_UTC_2030=21915;
*/	

/*	
	private static void encodeBusinessDate(ByteBufferHolder bh, int utcDays, boolean fb) {
		int fm= fb?0x80:0;
		int delta=BUSINESS_DATE_UTC_2030-utcDays; // u=BC-delta
		if (delta<0) {
			bh.pushByte((byte)(fm | NEGATIVE_BUSINESS_DAY));
			EncodingUtils.encodePositiveLong(bh, -delta);
		} else {
			bh.pushByte((byte)(fm | POSITIVE_BUSINESS_DAY));
			EncodingUtils.encodePositiveLong(bh, delta);
		}
	}
		
	private static BusinessDate decodeBusinessDate(ByteBuffer bb, int full) {
		if ((0x7f & full)==POSITIVE_BUSINESS_DAY) {
			int delta=(int)EncodingUtils.decodePositiveLong(bb);
			return new BusinessDate(BUSINESS_DATE_UTC_2030-delta);
		} else {
			int delta=(int)EncodingUtils.decodePositiveLong(bb);
			return new BusinessDate(BUSINESS_DATE_UTC_2030+delta);
		}
	}
*/
	
	private static void encodeTimestamp(ByteBufferHolder bh, long ts, boolean fb) {
		long delta=UTC_2030-ts;
		int signMask;
		if (delta<0) {
			signMask=0x8;
			delta=-delta;
		} else {
			signMask=0x0;
		}
		if (fb) signMask|=0x80;
		long t=delta/10000;
		if ( t*10000 == delta/*(delta & 0x3ffl) == 0*/ ) {
			// shift 12 bit
			bh.put((byte) (signMask | 0x40 | TIMESTAMP));
			EncodingUtils.encodePositiveLong(bh, t/*delta>>>10*/);
		} else {
			t=delta/1000;
			if ( t*1000 == delta/*(delta & 0xffl) == 0*/) {
				//shif 8 bit
				bh.put((byte) (signMask | 0x30 | TIMESTAMP));
				EncodingUtils.encodePositiveLong(bh, t/*delta>>>8*/);
			} else {
				t=delta/100;
				if ( t*100 == delta/* (delta & 0x3fl) == 0 */) {
					//shif 8 bit
					bh.put((byte) (signMask | 0x20 | TIMESTAMP));
					EncodingUtils.encodePositiveLong(bh, t/*delta>>>6*/);
				} else {
					t=delta/10;
					if ( t*10 == delta/*(delta & 0xfl) == 0 */) {
						//shif 4 bit
						bh.put((byte) (signMask | 0x10 | TIMESTAMP));
						EncodingUtils.encodePositiveLong(bh, t/*delta>>>4*/);
					} else {
						// no shift, pay full price
						bh.put((byte) (signMask | TIMESTAMP));
						EncodingUtils.encodePositiveLong(bh, delta);	
					}
				}
			}
		}
	}

	
	private static Date decodeTimestamp(ByteBuffer bb, int full) {
		long raw=EncodingUtils.decodePositiveLong(bb);
		int shift= (0x7f & full)>>>4;
		if ( shift == 0x4) raw*=10000;//<<=10;
		else if ( shift == 0x3) raw*=1000;//<<=8;
		else if ( shift == 0x2) raw*=100;//<<=6;
		else if ( shift == 0x1) raw*=10;//<<=4;

		if ( (full & 0x08) != 0) raw=-raw;
		long ts=UTC_2030-raw;
		
		return new Date(ts);
	}
	
	private static void encodeBlob(ByteBufferHolder bh, byte[] blob, boolean fb) {
		int fm= fb?0x80:0;
		if (blob.length>14) {
			bh.put((byte) (fm  | 0x78 | BLOB));
			EncodingUtils.encodePositiveLong(bh,blob.length);
			bh.putBytes(blob);
		} else {
			bh.put((byte) ( fm | (blob.length<<3) | BLOB));
			bh.putBytes(blob);
		}
	}
	
	private static byte[] decodeBlob(ByteBuffer bb, int full) {
		int length=(0x7f & full)>>3;
		if (length>14) {
			length=(int)EncodingUtils.decodePositiveLong(bb);
			byte[] arr=new byte[length];
			bb.get(arr);
			return arr;
		} else {
			byte[] arr=new byte[length];
			bb.get(arr);
			return arr;
		}
	}
	
	private static String decodeText(ByteBuffer bb, int full) throws CharacterCodingException {
		int length=(0x7f & full)>>3;
		if (length>14) {
			length=(int)EncodingUtils.decodePositiveLong(bb);
			String ret=new String(bb.array(),bb.position(),length,Utils.UTF8);
			bb.position(bb.position()+length);
			return ret;
		} else {
			String ret=new String(bb.array(),bb.position(),length,Utils.UTF8);
			bb.position(bb.position()+length);
			return ret;
		}
	}

	private static void encodeText(ByteBufferHolder bh, String str, boolean fb) {
		byte[] blob=str.getBytes(Utils.UTF8);
		int fm= fb?0x80:0;
		if (blob.length>14) {
			bh.put((byte) (fm | 0x78 | TEXT));
			EncodingUtils.encodePositiveLong(bh,blob.length);
			bh.putBytes(blob);
		} else {
			bh.put((byte) (fm | (blob.length<<3) | TEXT));
			bh.putBytes(blob);
		}
	}

	private static Boolean decodeBool(int full) {
		return (0x8 & full )!=0;
	}	

	private static void encodeBoolean(ByteBufferHolder bh, Boolean bool, boolean fb) {
		int fm= fb?0x80:0;
		if (bool) {
			bh.put((byte) (fm | 0x8 | BOOL));
		} else {
			bh.put((byte) (fm | BOOL)); 
		}
	}
	
	private static void encodeNull(ByteBufferHolder bh, boolean fb) {
		if (fb) bh.put((byte)(0x80 | NULL));
		else bh.put((byte)NULL);
	}
	
	public static void encodeNamedNode(ByteBufferHolder bh, NamedNode node) throws IOException {
		Set<String> names=new HashSet<>();
		Map<String, Integer> dict=new HashMap<>();
		collectNames(node, names);
		int idx=0;
		for (String n : names) {
			dict.put(n, idx);
			encodeObject(bh, n, idx==names.size()-1);
			idx++;
		}
		_encode(bh, dict, node);
		
	}
	public static void _encode(ByteBufferHolder bh, Map<String, Integer> dict, NamedNode node) throws IOException  {
		encodeInt(bh, dict.get(node.getName()), false);
		boolean hasChildren=node.hasChildren();
		encodeObject(bh, node.getValue(), hasChildren);
		if (hasChildren) {
			List<? extends NamedNode> cs=node.getChildren();
			encodeObject(bh, cs.size(), false);
			for (NamedNode c: cs) {
				_encode(bh, dict, c);
			}
		}
		
	}
	
	private static NamedNode _decodeNamedNode(ByteBufferHolder bh, Map<Integer, String> dict) throws IOException {
		boolean[] boolOut=new boolean[1];
		
		Integer dicidx=(Integer)decodeObject(bh,boolOut);
		String name=dict.get(dicidx);
		Object value=decodeObject(bh, boolOut);
		boolean hasChildren=boolOut[0];
		NamedNode n=NamedObject.create(name, value);
		if (hasChildren) {
			int num=(Integer)decodeObject(bh, boolOut);
			for (int i=0; i<num; ++i) {
				n.add(_decodeNamedNode(bh, dict));
			}
		}
		
		return n;
		
	}
	
	
	
	
	public static NamedNode decodeNamedNode(ByteBufferHolder bh) throws IOException {
		Map<Integer, String> dict=new HashMap<>();
		boolean[] boolOut=new boolean[1];
		
		for (int idx=0;;++idx) {
			Object obj=decodeObject(bh, boolOut);
			dict.put(idx, Utils.toString(obj));
			if (boolOut[0]) break;
		}
		
		return _decodeNamedNode(bh, dict);
	}
	
	
	private static void collectNames(NamedNode node, Set<String> names) {
		names.add(node.getName());
		for (NamedNode c : node.getChildren()) {
			collectNames(c, names);
		}
	}
	
	public static void main(String[] args) throws Exception {

		boolean fb=true;
		boolean[] boolOut=new boolean[1];
		
		ByteBufferHolder bh=new ByteBufferHolder(128);
		
		NamedNode n=NamedObject.create();
		n.add("attr1", 123);
		NamedNode n1=n.add("attr2", "abc");
		NamedNode n2=n1.add("attr3", "abc".getBytes());
		n2.add("attr1", 321);
		
		System.out.println("Before "+n);
		encodeNamedNode(bh, n);
		bh.flip();
		n=decodeNamedNode(bh);
		System.out.println("After "+n);
		System.exit(0);
		encodeObject(bh,null,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		Object obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - " + obj + " boolOut="+boolOut[0]);
		
		bh.clear();
		encodeObject(bh,"123456789ABCDEFG", fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);
		
		bh.clear();
		encodeObject(bh,12345,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);

		bh.clear();
		encodeObject(bh,-12345,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);

		bh.clear();
		encodeObject(bh,12345L,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);

		bh.clear();
		encodeObject(bh,-12345L,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);
		
		Date d=Utils.parseLocalDateTime("2011-07-31 12:11:11");//new Date();
		bh.clear();
		encodeObject(bh,d,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);

		/*
		bh.clear();
		encodeObject(bh,new BusinessDate("2010-02-04"),fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);

		bh.clear();
		encodeObject(bh,new BusinessDate("2099-12-31"),fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);
*/		
		
		bh.clear();
		encodeObject(bh,-1d,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);
		
		bh.clear();
		encodeObject(bh,new BigDecimal("1312432434.34342363546735474"),fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);
		
		
		bh.clear();
		encodeObject(bh,"123456789ABCDEF".getBytes(),fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + new String((byte[])obj) + "> boolOut="+boolOut[0]);
		
		bh.clear();
		encodeObject(bh,false,fb);
		System.out.println(" size= "+bh.position());
		bh.flip();
		obj=decodeObject(bh,boolOut);
		System.out.println("decoded: " + (obj==null?null:obj.getClass())+ " - <" + obj + "> boolOut="+boolOut[0]);
		
	}
	
	
}
