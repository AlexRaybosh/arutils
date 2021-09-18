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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.digest.MessageDigestAlgorithms;

final public class Local {
	SimpleDateFormat localDateFormat=new SimpleDateFormat("yyyy-MM-dd");
	SimpleDateFormat localDateIntFormat=new SimpleDateFormat("yyyyMMdd");
	SimpleDateFormat localDateTimeFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	SimpleDateFormat utcDateTimeFormatWithMS=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	SimpleDateFormat utcDateTimeFormatLog=new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS");
	SimpleDateFormat utcDateTimeFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	SimpleDateFormat utcDateFormat=new SimpleDateFormat("yyyy-MM-dd");
	SimpleDateFormat utcDateIntFormat=new SimpleDateFormat("yyyyMMdd");
	MessageDigest md5Digest;
	MessageDigest sha256Digest;
	Mac hmacSHA256;
	
	{
		utcDateTimeFormat.setTimeZone(Utils.UTC);
		utcDateFormat.setTimeZone(Utils.UTC);
		utcDateTimeFormatWithMS.setTimeZone(Utils.UTC);
		utcDateIntFormat.setTimeZone(Utils.UTC);
		utcDateTimeFormatLog.setTimeZone(Utils.UTC);
	}
	
	final byte[] md5(byte[] payload) {
		if (md5Digest==null) try {
			md5Digest=MessageDigest.getInstance(MessageDigestAlgorithms.MD5);
		} catch (NoSuchAlgorithmException e) {
			if (Utils.initBouncyCastle()) try {
				md5Digest=MessageDigest.getInstance(MessageDigestAlgorithms.MD5,"BC");
			} catch (Exception ee) {
				throw new RuntimeException(e);	
			}
		}
		md5Digest.update(payload);
		return md5Digest.digest();
	}

	final String md5HexLow(byte[] payload) {
		return EncodingUtils.getHexLow(md5(payload));
	}
	final String sha256HexLow(byte[] payload) {
		if (payload==null) return null;
		return EncodingUtils.getHexLow(sha256(payload));
	}
	final byte[] sha256(byte[] value) {
		if (value==null) return null;
		return sha256(value, 0, value.length );
	}
	final byte[] sha256(byte[] value, int off, int len) {
		if (value==null) return null;
		if (sha256Digest==null) try {
			sha256Digest=MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256);
		} catch (NoSuchAlgorithmException e) {
			if (Utils.initBouncyCastle()) try {
				sha256Digest=MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256, "BC");
			} catch (Exception ee) {
				throw new RuntimeException(e);	
			}
		}
		try {
			sha256Digest.update(value,off,len);
			return sha256Digest.digest();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}
	final byte[] hmacSHA256(byte[] secret, byte[] value) {
		if (value==null) return null;		
		return hmacSHA256(secret, value, 0, value.length);
	}
	final byte[] hmacSHA256(byte[] secret, byte[] value, int off, int len) {
		if (value==null) return null;
		if (hmacSHA256==null) try {
			hmacSHA256=Mac.getInstance("HmacSHA256");
		} catch (NoSuchAlgorithmException e) {
			if (Utils.initBouncyCastle()) try {
				hmacSHA256=Mac.getInstance("HmacSHA256","BC");
			} catch (Exception ee) {
				throw new RuntimeException(e);	
			}
		}		
		try {
			SecretKeySpec secretKeySpec=new SecretKeySpec(secret, "HmacSHA256");
			hmacSHA256.init(secretKeySpec);
			hmacSHA256.update(value, off, len);
			return hmacSHA256.doFinal();
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		}
	}	
	
	
	
	
	final String utcDateTimeString(Date date) {
		return utcDateTimeFormat.format(date);
	}
	public String formatLocalDateTime(Date yyyyMMdd) {
		return localDateTimeFormat.format(yyyyMMdd);
	}
	
	public final Date parseUTCDateTime(String val) throws ParseException {
		return utcDateTimeFormat.parse(val);
	}
	final String utcDateTimeWithMSString(Date date) {
		return utcDateTimeFormatWithMS.format(date);
	}	
	final String utcDateTimeLogString(Date date) {
		return utcDateTimeFormatLog.format(date);
	}

	final String formatLocalDate(Date date) {
		return localDateTimeFormat.format(date);
	}
	
	public final Date parseUTCDate(String val) throws ParseException {
		return utcDateFormat.parse(val);
	}
	final String formatUTCDate(Date val) {
		return utcDateFormat.format(val);
	}
	final String formatUTCDateTime(Date val) {
		return utcDateTimeFormat.format(val);
	}
	final String formatUTCDateInt(Date val) {
		return utcDateIntFormat.format(val);
	}
	public final Date parseUTCLogDateTime(String val) throws ParseException {
		return utcDateTimeFormatLog.parse(val);
	}		
	public final Date parseLocalDate(String val) throws ParseException {
		return localDateFormat.parse(val);
	}
	public final Date parseLocalDateTime(String val) throws ParseException {
		return localDateTimeFormat.parse(val);
	}
	public final Date parseLocalDateInt(String val) throws ParseException {
		return localDateIntFormat.parse(val);
	}	
	final String utcDateString(Date date) {
		return utcDateFormat.format(date);
	}	
	final String currentUTCDateTimeLogString() {
		return utcDateTimeFormatLog.format(new Date(System.currentTimeMillis()));
	}
	final String currentUTCDateTimeString() {
		return utcDateTimeFormat.format(new Date());
	}
	final String currentLocalDateTimeString() {
		return localDateTimeFormat.format(new Date());
	}
	public final Date parseUTCDateInt(String val) throws ParseException {
		return utcDateIntFormat.parse(val);
	}

	
	private ByteBufferHolder[] byteBufferHolders=new ByteBufferHolder[2];
	{
		byteBufferHolders[0]=new ByteBufferHolder();
		byteBufferHolders[1]=new ByteBufferHolder();
	}
	
	public final ByteBufferHolder getByteBufferHolder() {
		for (int i=0;i<byteBufferHolders.length;++i) {
			ByteBufferHolder bh=byteBufferHolders[i];
			if (bh.isFree()) {
				bh.setTaken();
				return bh;
			}
		}
		ByteBufferHolder[] arr=new ByteBufferHolder[byteBufferHolders.length+1];
		System.arraycopy(byteBufferHolders, 0, arr, 0, byteBufferHolders.length);
		ByteBufferHolder h=new ByteBufferHolder();
		arr[byteBufferHolders.length]=h;
		h.setTaken();
		byteBufferHolders=arr;
		return h;
	}
	
	public final ByteBufferHolder getByteBufferHolder(int from) {
		for (int i=from;i<byteBufferHolders.length;++i) {
			ByteBufferHolder bh=byteBufferHolders[i];
			if (bh.isFree()) {
				bh.setTaken();
				return bh;
			}
		}
		if (from>=byteBufferHolders.length) {
			ByteBufferHolder[] arr=new ByteBufferHolder[from+1];
			System.arraycopy(byteBufferHolders, 0, arr, 0, byteBufferHolders.length);
			for (int i=byteBufferHolders.length;i<=from;i++) arr[i]=new ByteBufferHolder();
			byteBufferHolders=arr;
			ByteBufferHolder h=byteBufferHolders[from];
			h.setTaken();
			return h;
		}
		ByteBufferHolder[] arr=new ByteBufferHolder[byteBufferHolders.length+1];
		System.arraycopy(byteBufferHolders, 0, arr, 0, byteBufferHolders.length);
		ByteBufferHolder h=new ByteBufferHolder();
		arr[byteBufferHolders.length]=h;
		h.setTaken();
		byteBufferHolders=arr;
		return h;
	}
	
	
}
