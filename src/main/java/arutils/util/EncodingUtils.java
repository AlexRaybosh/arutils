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
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.commons.codec.binary.Base32;

public class EncodingUtils {
	public static int encodePositiveLong(ByteBufferHolder bh, long val) {
		if (val<128) { // 2^(8-1)
			bh.put((byte)val);
			return 1;
		} else if (val<16384) { // 2^(16-2)
			// good for 2 bytes
			// need to get first 8-14 bits, then 1-7 bits
			bh.put((byte)(val>>>7 | 0x80));
			bh.put((byte)(val & 0x7f));
			return 2;
		} else if (val<2097152) { // 2^(24-3) - 21 bits
			// good for 3 bytes
			bh.put((byte)(val>>>14 | 0x80));
			bh.put((byte)(val>>>7  | 0x80));
			bh.put((byte)(val & 0x7f));
			return 3;
		} else if (val<268435456) { // 2^(8*4-4)  - 28 bits
			// good for 4 bytes
			bh.put((byte)(val>>>21 | 0x80));
			bh.put((byte)(val>>>14 | 0x80));
			bh.put((byte)(val>>>7  | 0x80));
			bh.put((byte)(val & 0x7f));
			return 4;
		} else if (val<34359738368l) { //2^(8*5-5)  - 35 bits
			// good for 5 bytes
			bh.put((byte)(val>>>28 | 0x80));
			bh.put((byte)(val>>>21 | 0x80));
			bh.put((byte)(val>>>14 | 0x80));
			bh.put((byte)(val>>>7  | 0x80));
			bh.put((byte)(val & 0x7f));
			return 5;
		} else if (val<4398046511104l) { //2^(8*6-6)  - 42 bits
			// good for 6 bytes
			bh.put((byte)(val>>>35 | 0x80));
			bh.put((byte)(val>>>28 | 0x80));
			bh.put((byte)(val>>>21 | 0x80));
			bh.put((byte)(val>>>14 | 0x80));
			bh.put((byte)(val>>>7  | 0x80));
			bh.put((byte)(val & 0x7f));
			return 6;
		} else if (val<562949953421312l) { //2^(8*7-7)  - 49 bits
			// good for 7 bytes
			bh.put((byte)(val>>>42 | 0x80));
			bh.put((byte)(val>>>35 | 0x80));
			bh.put((byte)(val>>>28 | 0x80));
			bh.put((byte)(val>>>21 | 0x80));
			bh.put((byte)(val>>>14 | 0x80));
			bh.put((byte)(val>>>7  | 0x80));
			bh.put((byte)(val & 0x7f));
			return 7;
		} else if (val<72057594037927936l) { //2^(8*8-8)  - 56 bits
			// good for 8 bytes
			bh.put((byte)(val>>>49 | 0x80));
			bh.put((byte)(val>>>42 | 0x80));
			bh.put((byte)(val>>>35 | 0x80));
			bh.put((byte)(val>>>28 | 0x80));
			bh.put((byte)(val>>>21 | 0x80));
			bh.put((byte)(val>>>14 | 0x80));
			bh.put((byte)(val>>>7  | 0x80));
			bh.put((byte)(val & 0x7f));
			return 8;
		} else {
			// good for 9 bytes
			bh.put((byte)(val>>>56 | 0x80));
			bh.put((byte)(val>>>49 | 0x80));
			bh.put((byte)(val>>>42 | 0x80));
			bh.put((byte)(val>>>35 | 0x80));
			bh.put((byte)(val>>>28 | 0x80));
			bh.put((byte)(val>>>21 | 0x80));
			bh.put((byte)(val>>>14 | 0x80));
			bh.put((byte)(val>>>7  | 0x80));
			bh.put((byte)(val & 0x7f));
			return 9;
		}	
	}
	
	public static long decodePositiveLong(ByteBuffer bb) {
		int c0=bb.get();
		if ((c0 & 0x80)==0) {
			// 0 upper bit
			return c0;
		} 
		int c1=bb.get();
		if ((c1 & 0x80)==0) {
			// second byte is a terminator
			int b1=(c0 & 0x7f)<<7;
			return b1 + c1;
		}
		int c2=bb.get();
		if ((c2 & 0x80)==0) {
			// third byte is a terminator
			int b2=(c0 & 0x7f)<<14;
			int b1=(c1 & 0x7f)<<7;
			return b2 + b1 + (long)c2;
		} 
		int c3=bb.get();
		if ((c3 & 0x80)==0) {
			// forth byte is a terminator
			long b3=(c0 & 0x7f)<<21;
			long b2=(c1 & 0x7f)<<14;
			long b1=(c2 & 0x7f)<<7;
			return b3 + b2 + b1 + c3;
		} 
		int c4=bb.get();
		if ((c4 & 0x80)==0) {
			long b4=((long)c0 & 0x7f)<<28;
			long b3=(c1 & 0x7f)<<21;
			long b2=(c2 & 0x7f)<<14;
			long b1=(c3 & 0x7f)<<7;
			return b4 + b3 + b2 + b1 + c4;
		} 
		int c5=bb.get();
		if ((c5 & 0x80)==0) {
			long b5=((long)c0 & 0x7f)<<35;
			long b4=((long)c1 & 0x7f)<<28;
			long b3=(c2 & 0x7f)<<21;
			long b2=(c3 & 0x7f)<<14;
			long b1=(c4 & 0x7f)<<7;
			return b5 + b4 + b3 + b2 + b1 + ((long)c5);
		}
		int c6=bb.get();
		if ((c6 & 0x80)==0) {
			long b6=((long)c0 & 0x7f)<<42;
			long b5=((long)c1 & 0x7f)<<35;
			long b4=((long)c2 & 0x7f)<<28;
			long b3=(c3 & 0x7f)<<21;
			long b2=(c4 & 0x7f)<<14;
			long b1=(c5 & 0x7f)<<7;
			return b6 + b5 + b4 + b3 + b2 + b1 + c6;
		}
		int c7=bb.get();
		if ((c7 & 0x80)==0) {
			long b7=((long)c0 & 0x7f)<<49;
			long b6=((long)c1 & 0x7f)<<42;
			long b5=((long)c2 & 0x7f)<<35;
			long b4=((long)c3 & 0x7f)<<28;
			long b3=(c4 & 0x7f)<<21;
			long b2=(c5 & 0x7f)<<14;
			long b1=(c6 & 0x7f)<<7;
			return b7 + b6 + b5 + b4 + b3 + b2 + b1 + c7;
		}
		int c8=bb.get();
		long b8=((long)c0 & 0x7f)<<56;
		long b7=((long)c1 & 0x7f)<<49;
		long b6=((long)c2 & 0x7f)<<42;
		long b5=((long)c3 & 0x7f)<<35;
		long b4=((long)c4 & 0x7f)<<28;
		long b3=(c5 & 0x7f)<<21;
		long b2=(c6 & 0x7f)<<14;
		long b1=(c7 & 0x7f)<<7;
		return b8 + b7 + b6 + b5 + b4 + b3 + b2 + b1 + c8;
	}

	
	
	public static long decodePositiveLong(byte[] arr, int offset, int[] shift) throws IOException {
		int c0=arr[offset++];
		if ((c0 & 0x80)==0) {
			// 0 upper bit
			shift[0]+=1;
			return c0;
		} 
		int c1=arr[offset++];
		if ((c1 & 0x80)==0) {
			// second byte is a terminator
			shift[0]+=2;
			int b1=(c0 & 0x7f)<<7;
			return b1 + c1;
		}
		int c2=arr[offset++];
		if ((c2 & 0x80)==0) {
			// third byte is a terminator
			shift[0]+=3;
			int b2=(c0 & 0x7f)<<14;
			int b1=(c1 & 0x7f)<<7;
			return b2 + b1 + (long)c2;
		} 
		int c3=arr[offset++];
		if ((c3 & 0x80)==0) {
			// forth byte is a terminator
			shift[0]+=4;
			long b3=(c0 & 0x7f)<<21;
			long b2=(c1 & 0x7f)<<14;
			long b1=(c2 & 0x7f)<<7;
			return b3 + b2 + b1 + c3;
		} 
		int c4=arr[offset++];
		if ((c4 & 0x80)==0) {
			shift[0]+=5;
			long b4=((long)c0 & 0x7f)<<28;
			long b3=(c1 & 0x7f)<<21;
			long b2=(c2 & 0x7f)<<14;
			long b1=(c3 & 0x7f)<<7;
			return b4 + b3 + b2 + b1 + c4;
		} 
		int c5=arr[offset++];
		if ((c5 & 0x80)==0) {
			shift[0]+=6;
			long b5=((long)c0 & 0x7f)<<35;
			long b4=((long)c1 & 0x7f)<<28;
			long b3=(c2 & 0x7f)<<21;
			long b2=(c3 & 0x7f)<<14;
			long b1=(c4 & 0x7f)<<7;
			return b5 + b4 + b3 + b2 + b1 + ((long)c5);
		}
		int c6=arr[offset++];
		if ((c6 & 0x80)==0) {
			shift[0]+=7;
			long b6=((long)c0 & 0x7f)<<42;
			long b5=((long)c1 & 0x7f)<<35;
			long b4=((long)c2 & 0x7f)<<28;
			long b3=(c3 & 0x7f)<<21;
			long b2=(c4 & 0x7f)<<14;
			long b1=(c5 & 0x7f)<<7;
			return b6 + b5 + b4 + b3 + b2 + b1 + c6;
		}
		int c7=arr[offset++];
		if ((c7 & 0x80)==0) {
			shift[0]+=8;
			long b7=((long)c0 & 0x7f)<<49;
			long b6=((long)c1 & 0x7f)<<42;
			long b5=((long)c2 & 0x7f)<<35;
			long b4=((long)c3 & 0x7f)<<28;
			long b3=(c4 & 0x7f)<<21;
			long b2=(c5 & 0x7f)<<14;
			long b1=(c6 & 0x7f)<<7;
			return b7 + b6 + b5 + b4 + b3 + b2 + b1 + c7;
		}
		int c8=arr[offset++];
		long b8=((long)c0 & 0x7f)<<56;
		long b7=((long)c1 & 0x7f)<<49;
		long b6=((long)c2 & 0x7f)<<42;
		long b5=((long)c3 & 0x7f)<<35;
		long b4=((long)c4 & 0x7f)<<28;
		long b3=(c5 & 0x7f)<<21;
		long b2=(c6 & 0x7f)<<14;
		long b1=(c7 & 0x7f)<<7;
		shift[0]+=9;
		return b8 + b7 + b6 + b5 + b4 + b3 + b2 + b1 + c8;
	}
	
	/*
	 * 0x0 0x1 0x2 0x3 0x4 0x5 0x6 0x7 0x8 0x9 
	 * 0xA "+"  
	 * 0xB "-" 
	 * 0xC "∞" 
	 * 0xD "." 
	 * 0xE "E" 
	 * 0xF end
	 * 
	 * 
0 - 48
9 - 57
+ - 43
- - 45
. - 46
/ - 47
e - 101
E - 69
	 * 
	 */
	static final byte[] nullNumberString=new byte[] {(byte) 0xF0};
	public static byte[] encodeNumberString(String numstr) throws NumberFormatException {
		if (numstr==null) return nullNumberString;
		int l=numstr.length();
		//byte[] ret=new byte[l];
		if (l==0) return nullNumberString;
		//boolean b=
		int current=0;
		int bn=0;
		int sl=(l+1)>>>1;
		if (l%2==0) sl+=1;
		//else if (sl==0) sl=1;
		byte[] val=new byte[sl];
		boolean flushed = false;
		for (int i=0;i<l;i++) {
			int b=numstr.charAt(i);
			if (b>=48 && b<=57) b-=48; // This is a decimal digit
			else if (b==46) b=0xD;   // .
			else if (b==45) b=0xB;   // -
			else if (b==43) b=0xA; // +
			else if (b==47) b=0xC; // /
			else if (b==69 || b==101) b=0xE; // e or E
			else throw new NumberFormatException("Not a number string "+numstr);
			//System.out.println(b);
			if (i%2==0) {
				// second quadrant
				current=b<<4;
				flushed=false;
			} else {
				// first quadrant
				current|=b;
				val[bn]=(byte)current;
				bn++;
				current=0;
				flushed=true;
				
			}
		}
		//System.out.println("current "+getHex(intToByteArray(current)));
		//System.out.println("result "+getHex(val));
		if (flushed) {
			// Need to flush 0xF end of sequence
			val[bn]=(byte)0xF0;
		} else {
			// Need to flush last quadrant + 0xF end of sequence, the whole byte
			current|=0xF;
			val[bn]=(byte)current;
		}
		//System.out.println("final current "+getHex(intToByteArray(current)));
		//System.out.println("final result "+getHex(val));
		return val;
	}
	

	public static void encodeNumberString(ByteBufferHolder bh, String numstr) throws NumberFormatException {
		if (numstr==null) bh.put((byte)0xF0);
		int l=numstr.length();
		if (l==0) bh.put((byte)0xF0);
		int current=0;
		int bn=0;
		boolean flushed = false;
		for (int i=0;i<l;i++) {
			int b=numstr.charAt(i);
			if (b>=48 && b<=57) b-=48; // This is a decimal digit
			else if (b==46) b=0xD;   // .
			else if (b==45) b=0xB;   // -
			else if (b==43) b=0xA; // +
			else if (b==47) b=0xC; // /
			else if (b==69 || b==101) b=0xE; // e or E
			else throw new NumberFormatException("Not a number string "+numstr);
			//System.out.println(b);
			if (i%2==0) {
				// upper quadrant
				current=b<<4;
				flushed=false;
			} else {
				// lower quadrant
				current|=b;
				bh.put((byte)current);
				bn++;
				current=0;
				flushed=true;
				
			}
		}
		if (flushed) {
			// Need to flush 0xF end of sequence
			bh.put((byte)0xF0);
		} else {
			// Need to flush last quadrant + 0xF end of sequence, the whole byte
			bh.put((byte)(current|0xF));
		}
	}

	private final static char[] decimalDigits= new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
	private final static void printQuadrant(int q, StringBuilder sb) {
		if (q<10) sb.append(decimalDigits[q]);
		else {
			switch (q) {
			case 0xA: sb.append('+'); return;
			case 0xB: sb.append('-'); return;
			case 0xC: sb.append('/'); return;
			case 0xD: sb.append('.'); return;
			case 0xE: sb.append('E'); return;
			}
			throw new RuntimeException("Something is badly wrong, got Quadrant="+HEXES.charAt((q & 0x0F)));
		}
	}
	public static String decodeNumberString(ByteBuffer bb) throws IOException {
		StringBuilder sb=new StringBuilder(8);
		for (;;) {
			int current=0xff & bb.get();
			int firstQuadrant=current>>4;
			//System.out.println("firstQuadrant "+getHex(intToByteArray(firstQuadrant)));
			if (firstQuadrant==0xF) break;
			printQuadrant(firstQuadrant, sb);
			int secondQuadrant=0x0F & current;
			//System.out.println("secondQuadrant "+getHex(intToByteArray(secondQuadrant)));
			if (secondQuadrant==0xF) break;
			printQuadrant(secondQuadrant, sb);
		}
		return (sb.length()==0)?null:sb.toString();
	}
	static final String HEXES = "0123456789ABCDEF";
	static final String hexes = "0123456789abcdef";
	
	public static String getHex(byte[] raw) {
		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(2 * raw.length);
		for (final byte b : raw) {
			hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
		}
		return hex.toString();
	}
	public static String getHexLow(byte[] raw) {
		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(2 * raw.length);
		for (final byte b : raw) {
			hex.append(hexes.charAt((b & 0xF0) >> 4)).append(hexes.charAt((b & 0x0F)));
		}
		return hex.toString();
	}
	public static final int byteArrayToInt(byte[] b) {
		return (b[0] << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8)
				+ (b[3] & 0xFF);
	}
	public static final long byteArrayToLong(byte[] b) {
		return ((long)b[0]<<56) + 
				((long) (b[1] & 0xFF)<<48) + 
				((long) (b[2] & 0xFF)<<40) + 
				((long) (b[3] & 0xFF)<<32) + 
				((long) (b[4] & 0xFF)<<24) + 
				((long) (b[5] & 0xFF)<<16) + 
				((long) (b[6] & 0xFF)<<8) + 
				  (long)(b[7] & 0xFF);
	}

	public static final byte[] intToByteArray(int value) {
		return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}
	public static final byte[] longToByteArray(long value) {
		return new byte[] { (byte) (value >>> 56), (byte) (value >>> 48), (byte) (value >>> 40), (byte) (value >>> 32), (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}
	public static final byte[] uuidToByteArray(UUID uuid) {
		long m=uuid.getMostSignificantBits();
		long l=uuid.getLeastSignificantBits();
		return new byte[] { 
				(byte) (m >>> 56), (byte) (m >>> 48), (byte) (m >>> 40), (byte) (m >>> 32), (byte) (m >>> 24), (byte) (m >>> 16), (byte) (m >>> 8), (byte) m,
				(byte) (l >>> 56), (byte) (l >>> 48), (byte) (l >>> 40), (byte) (l >>> 32), (byte) (l >>> 24), (byte) (l >>> 16), (byte) (l >>> 8), (byte) l		
		};
	}
	
	public static final UUID byteArrayToUUID(byte[] data) {
        long msb = 0;
        long lsb = 0;
        for (int i=0; i<8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i=8; i<16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);
        return new UUID(msb, lsb);
    }
	
    public static long flipLong(long val) { 
        long byte0 = val & 0xff; 
        long byte1 = (val>>8) & 0xff; 
        long byte2 = (val>>16) & 0xff; 
        long byte3 = (val>>24) & 0xff; 
        long byte4 = (val>>32) & 0xff; 
        long byte5 = (val>>40) & 0xff; 
        long byte6 = (val>>48) & 0xff; 
        long byte7 = (val>>56) & 0xff; 
        return (long) ((byte0<<56) | (byte1<<48) | (byte2<<40) | (byte3<<32) | (byte4<<24) | (byte5<<16)| (byte6<<8) | byte7); 
 } 

	
	
	public static void main(String[] args) throws Exception {
		ByteBufferHolder bh=new ByteBufferHolder();
		SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date d=df.parse("2030-01-01");
		System.out.println(d);
		System.out.println(d.getTime());
		System.out.println(d.getTime()/86400000f);
		Date tonight=Utils.parseLocalDateTime("2011-07-29 20:00:00");
		System.out.println(tonight);
		long l=-123456789123456789L;
		byte[]b=longToByteArray(l);
		System.out.println("l="+l+" " + getHex(b));
		System.out.println(byteArrayToLong(b));
		System.out.println(getHex(longToByteArray(byteArrayToLong(b))));
		byte p=(byte)0x00;
		Base32 b32=new Base32(true,p);
		System.out.println(b32.encodeToString("hi there".getBytes()));
		UUID u=base32ToUUID("NC9OFA6SG529J2U5EGB11B8AA8");
		System.out.println(u);
		System.out.println(uuidToBase32(u));
		System.out.println(base32ToUUID(uuidToBase32(u)));
		
	}

	public static String uuidToBase32(UUID uuid) {
		Base32 b32=new Base32(true,(byte)'-');
		byte[] bb=b32.encode(uuidToByteArray(uuid));
		for (int last=bb.length-1;last>=0;--last) {
			if (bb[last]!='-') {
				return new String(bb,0,last+1, Utils.ASCII);
			}
		}
		return ""; 
	}

	public static byte[] base32ToByteArray(String bs) {
		byte p=(byte)'-';
		Base32 b32=new Base32(true,p);
		return b32.decode(bs);
	}
	public static UUID base32ToUUID(String bs) {
		return byteArrayToUUID(base32ToByteArray(bs));
	}


}
