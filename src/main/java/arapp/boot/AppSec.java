package arapp.boot;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

import arapp.AppScope;
import arapp.Env;
import arutils.util.Utils;

public class AppSec {
	
	SecretKeySpec aesSecretKeySpec;
	IvParameterSpec aesIvParameterSpec;
	PublicKey rsaPublicKey;
	PrivateKey rsaPrivateKey;

	
	final ThreadLocal<Ciphers> ciphers=new ThreadLocal<Ciphers>() {
		protected Ciphers initialValue() {
			return new Ciphers();
		}
	};
	final  AppScope appScope;
	


	
	class Ciphers {
		Cipher aesEncoder, aesDecoder;
		Cipher rsaPublicDecoder, rsaPrivateEncoder;
		Signature sha256WithRSASignature;
		
		final byte[] encryptAES(byte[] value, int off, int len) {
			if (aesEncoder==null) throw new RuntimeException("AES encryption is not initialized");
			if (value==null) return null;
			try {
				return aesEncoder.doFinal(value, off, len);
			} catch (Exception e) {
				return Utils.rethrowRuntimeException(e);
			}
		}
		final byte[] decryptAES(byte[] value, int off, int len) {
			if (aesDecoder==null) throw new RuntimeException("AES encryption is not initialized");
			if (value==null) return null;
			try {
				return aesDecoder.doFinal(value, off, len);
			} catch (Exception e) {
				return Utils.rethrowRuntimeException(e);
			}			
		}

		final byte[] encryptPrivateRSA(byte[] value, int off, int len) {
			if (rsaPrivateEncoder==null) throw new RuntimeException("RSA encryption is not initialized");
			if (value==null) return null;
			try {
				return rsaPrivateEncoder.doFinal(value, off, len);
			} catch (Exception e) {
				return Utils.rethrowRuntimeException(e);
			}			
		}
		
		final byte[] decryptPublicRSA(byte[] value, int off, int len) {
			if (rsaPublicDecoder==null) throw new RuntimeException("RSA encryption is not initialized");
			if (value==null) return null;
			try {
				return rsaPublicDecoder.doFinal(value, off, len);
			} catch (Exception e) {
				return Utils.rethrowRuntimeException(e);
			}				
			
		}
		final byte[] signSHA256PrivateRSA(byte[] value, int off, int len) {
			if (value==null) return null;
			try {
				byte[] digest=Utils.sha256(value, off, len);
				return encryptPrivateRSA(digest,0, digest.length);
			} catch (Exception e) {
				return Utils.rethrowRuntimeException(e);
			}
		}


		
		Ciphers() {
			if (aesSecretKeySpec!=null && aesIvParameterSpec!=null) {
				try {
					aesEncoder=Cipher.getInstance("AES/CBC/PKCS5PADDING");
					aesDecoder=Cipher.getInstance("AES/CBC/PKCS5PADDING");
					aesEncoder.init(Cipher.ENCRYPT_MODE, aesSecretKeySpec,aesIvParameterSpec);
					aesDecoder.init(Cipher.DECRYPT_MODE, aesSecretKeySpec,aesIvParameterSpec);
				} catch (Exception e) {
					if (Utils.initBouncyCastle()) {						
						try {
							appScope.logerr("AES BC encryption failed to initialize, fallback to BC: "+e.getMessage());
							aesEncoder=Cipher.getInstance("AES/CBC/PKCS5PADDING","BC");
							aesDecoder=Cipher.getInstance("AES/CBC/PKCS5PADDING", "BC");
							aesEncoder.init(Cipher.ENCRYPT_MODE, aesSecretKeySpec,aesIvParameterSpec);
							aesDecoder.init(Cipher.DECRYPT_MODE, aesSecretKeySpec,aesIvParameterSpec);
						} catch (Exception ee) {
							appScope.logerr("AES encryption failed to initialize", ee);
						}
					} else {
						appScope.logerr("AES encryption failed to initialize", e);
					}
				}
			}
			if (rsaPublicKey!=null) {
				try {
					rsaPublicDecoder=Cipher.getInstance("RSA");
					rsaPublicDecoder.init(Cipher.DECRYPT_MODE, rsaPublicKey);
				} catch (Exception e) {
					if (Utils.initBouncyCastle()) {
						try {
							appScope.logerr("RSA BC decoder failed to initialize, fallback to BC: "+e.getMessage());
							rsaPublicDecoder=Cipher.getInstance("RSA","BC");
							rsaPublicDecoder.init(Cipher.DECRYPT_MODE, rsaPublicKey);
						} catch (Exception ee) {
							appScope.logerr("RSA decoder failed to initialize", ee);
						}
					} else {
						BootstrapEnv.logerr("RSA decoder failed to initialize", e);
					}
				}
			}
			if (rsaPrivateKey!=null) {
				try {
					rsaPrivateEncoder=Cipher.getInstance("RSA");
					rsaPrivateEncoder.init(Cipher.ENCRYPT_MODE, rsaPrivateKey);
					sha256WithRSASignature=Signature.getInstance("SHA256WithRSA");
					sha256WithRSASignature.initSign(rsaPrivateKey);
				} catch (Exception e) {
					if (Utils.initBouncyCastle()) {
						try {
							BootstrapEnv.logerr("RSA BC encoder failed to initialize, fallback to BC: "+e.getMessage());
							rsaPrivateEncoder=Cipher.getInstance("RSA","BC");
							rsaPrivateEncoder.init(Cipher.ENCRYPT_MODE, rsaPrivateKey);
							sha256WithRSASignature=Signature.getInstance("SHA256WithRSA","BC");
							sha256WithRSASignature.initSign(rsaPrivateKey);							
						} catch (Exception ee) {
							BootstrapEnv.logerr("RSA encoder failed to initialize:"+ e.getMessage());
						}
					} else {
						BootstrapEnv.logerr("RSA encoder failed to initialize:"+ e.getMessage());
					}
				}
			}
		}
	}
	
	
    public static byte[] getRandom(int numBytes) {
        byte[] r = new byte[numBytes];
        new SecureRandom().nextBytes(r);
        return r;
    }
    
    
	public AppSec(AppScope appScope, Properties properties, Env env) {
		this.appScope=appScope;
		String aesIvBase64 = properties.getProperty("aesIvBase64");
		String aesKeyBase64 = properties.getProperty("aesKeyBase64");
		String rsaPrivatePKCS8Base64 = properties.getProperty("rsaPrivatePKCS8Base64");
		String rsaPublicPKCS8Base64 = properties.getProperty("rsaPublicPKCS8Base64");

		if (!Utils.isEmpty(aesIvBase64) && !Utils.isEmpty(aesKeyBase64)) {
			aesSecretKeySpec=new SecretKeySpec(Base64.decodeBase64(aesKeyBase64.trim()), "AES") ;
			aesIvParameterSpec=new IvParameterSpec(Base64.decodeBase64(aesIvBase64.trim()));		
		}
		if (!Utils.isEmpty(rsaPrivatePKCS8Base64) || !Utils.isEmpty(rsaPublicPKCS8Base64)) {
			KeyFactory kf=null;
			try {
				//Provider bc=(Provider)Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider.BouncyCastleProvider").newInstance();
				if (Utils.initBouncyCastle()) {
					kf=KeyFactory.getInstance("RSA", "BC");	
				} else {
					BootstrapEnv.logerr("Bouncy Castle is not available, key parsing will probably fail");
					kf=KeyFactory.getInstance("RSA");
				}
			} catch (Exception e) {				
				BootstrapEnv.logerr("Failed to initialize RSA: "+ e.getMessage());
				Utils.rethrowRuntimeException(e);
			}

			
			if (!Utils.isEmpty(rsaPrivatePKCS8Base64)) {
				String clean = rsaPrivatePKCS8Base64.trim().replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----", "").replace("\n", "").replace("\r", "");
				byte[] rsaPrivatePKCS8 = Base64.decodeBase64(clean);
				PKCS8EncodedKeySpec spec=new PKCS8EncodedKeySpec(rsaPrivatePKCS8);				
				try {
					if (kf!=null) 
						rsaPrivateKey = kf.generatePrivate(spec);
				} catch (InvalidKeySpecException e) {
					BootstrapEnv.logerr("Failed to initialize RSA private key: "+ e.getMessage());
					Utils.rethrowRuntimeException(e);
				}
			}
			if (!Utils.isEmpty(rsaPublicPKCS8Base64)) {
				byte[] rsaPublicPKCS8 = Base64.decodeBase64(rsaPublicPKCS8Base64.trim().replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----", "").replace("\n", "").replace("\r", ""));
				X509EncodedKeySpec spec=new X509EncodedKeySpec(rsaPublicPKCS8);
				try {
					if (kf!=null) rsaPublicKey = kf.generatePublic(spec);
				} catch (InvalidKeySpecException e) {
					BootstrapEnv.logerr("Failed to initialize RSA public key: "+ e.getMessage());
				}
			}
			
			if (rsaPublicKey==null && rsaPrivateKey!=null) {
				try {
					RSAPrivateCrtKeySpec crtKeySpec = kf.getKeySpec(rsaPrivateKey, RSAPrivateCrtKeySpec.class);
					RSAPublicKeySpec pubSpec=new RSAPublicKeySpec(crtKeySpec.getModulus(), crtKeySpec.getPublicExponent());
					rsaPublicKey=kf.generatePublic(pubSpec);
				} catch (InvalidKeySpecException e) {
					BootstrapEnv.logerr("Failed to convert RSA private to public key: "+ e.getMessage());
				} 
			}
		}
	}
	
	public final byte[] encryptAES(byte[] value, int off, int len) {
		return ciphers.get().encryptAES(value, off, len);
	}
	public final byte[] decryptAES(byte[] value, int off, int len) {
		return ciphers.get().decryptAES(value, off, len);
	}
	public final byte[] encryptAES(byte[] value) {
		if (value==null) return null;
		return ciphers.get().encryptAES(value, 0, value.length);
	}
	public final byte[] decryptAES(byte[] value) {
		if (value==null) return null;
		return ciphers.get().decryptAES(value, 0, value.length);
	}		


	public final byte[] encryptPrivateRSA(byte[] value, int off, int len) {
		return ciphers.get().encryptPrivateRSA(value, off, len);
	}
	public final byte[] decryptPublicRSA(byte[] value, int off, int len) {
		return ciphers.get().decryptPublicRSA(value, off, len);
	}
	public final byte[] encryptPrivateRSA(byte[] value) {
		if (value==null) return null;
		return ciphers.get().encryptPrivateRSA(value, 0, value.length);
	}
	public final byte[] decryptPublicRSA(byte[] value) {
		if (value==null) return null;
		return ciphers.get().decryptPublicRSA(value, 0, value.length);
	}			

	public final byte[] signSHA256PrivateRSA(byte[] value, int off, int len) {
		return ciphers.get().signSHA256PrivateRSA(value, off, len);
	}
	public final byte[] signSHA256PrivateRSA(byte[] value) {
		if (value==null) return null;
		return ciphers.get().signSHA256PrivateRSA(value, 0, value.length);
	}

}
