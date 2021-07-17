/*--------------------------------------------------------------------------
 * The below code is based on org.xerial Dip native api code.
 * 
 * The original copyright:
 *  Copyright 2011 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
//--------------------------------------
package arutils.jni;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import javax.management.RuntimeErrorException;

import arutils.util.Utils;
import sun.misc.Unsafe;

/**
 * <b>Internal only - Do not use this class.</b> This class loads a native
 * library of dip-java (dipjava.dll, libdipjava.so, etc.) according to the
 * user platform (<i>os.name</i> and <i>os.arch</i>). The natively compiled
 * libraries bundled to dip-java contain the codes of the original dip and
 * JNI programs to access Dip.
 * 
 * In default, no configuration is required to use dip-java, but you can load
 * your own native library created by 'make native' command.
 * 
 * This LibLoader searches for native libraries (dipjava.dll,
 * libdip.so, etc.) in the following order:
 * <ol>
 * <li>If system property <i>dip.use.systemlib</i> is set to true,
 * lookup folders specified by <i>java.lib.path</i> system property (This is the
 * default path that JVM searches for native libraries)
 * <li>(System property: <i>dip.lib.path</i>)/(System property:
 * <i>dip.lib.name</i>)
 * <li>One of the libraries embedded in dip-java-(version).jar extracted into
 * (System property: <i>java.io.tempdir</i>). If
 * <i>dip.tempdir</i> is set, use this folder instead of
 * <i>java.io.tempdir</i>.
 * </ol>
 * 
 * <p>
 * If you do not want to use folder <i>java.io.tempdir</i>, set the System
 * property <i>dip.tempdir</i>. For example, to use
 * <i>/tmp/my</i> as a temporary folder to copy native libraries, use -D option
 * of JVM:
 * 
 * <pre>
 * <code>
 * java -Ddip.tempdir="/tmp/my" ...
 * </code>
 * </pre>
 * 
 * </p>
 * 
 * Original author:
 * @author leo
 * 
 */
public class APILoader {

	private final static Unsafe unsafe;
	static {
		unsafe=Utils.getUnsafe();
	}
    private final static File TMP_DIR=initTmpDir();
    private static File initTmpDir() {
    	String t=System.getProperty("java.io.tmpdir");
		return t==null? new File("/tmp"): new File(t);
	}
    
    public final static String SO_LOADER_CLASS_NAME="arutils.jni.SoLoader";
    private static volatile boolean isLoaded=false;

    private static ClassLoader findRootClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while (cl.getParent() != null) {
            cl = cl.getParent();
        }
        return cl;
    }



	private static byte[] getResourceBytes(String resourcePath) throws IOException {
        InputStream in = APILoader.class.getResourceAsStream(resourcePath);
        if (in == null) throw new IOException(resourcePath + " is not found");
        try {
	        byte[] buf = new byte[4096];
	        ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        for (int readLength; (readLength = in.read(buf)) != -1;) {
	            bos.write(buf, 0, readLength);
	        }
	        return bos.toByteArray();
        } finally {
        	in.close();
        }
    }

    public static boolean isNativeLibraryLoaded() {
        return isLoaded;
    }
    private static boolean hasSoLoaderClassLoaded() {
    	try {
    	  	Class<?> c=Class.forName(SO_LOADER_CLASS_NAME);
    	  	return true;
    	} catch (ClassNotFoundException e) {
			return false;
		}
    }
    
    static synchronized APIStub load() {
    	try {
    		if (!hasSoLoaderClassLoaded()) {
    			Class<?> soLoaderClass=injectSoLoader();
    			loadSharedLibrary(soLoaderClass);
    			isLoaded=true;
    		}
    		return (APIStub) Class.forName("arutils.jni.APIStub").newInstance();
    	} catch (Throwable t) {
			return Utils.<APIStub>runtimeException("Failed to init APILoader", t);
		}
    }
    


    /**
     * Loads  native implementation using the root class
     * loader. This hack is for avoiding the JNI multi-loading issue when the
     * same JNI library is loaded by different class loaders.
     * 
     * In order to load native code in the root class loader, this method first
     * inject DipNativeLoader class into the root class loader, because
     * {@link System#load(String)} method uses the class loader of the caller
     * class when loading native libraries.
     * 
     * <pre>
     * (root class loader) -> [APILoader (load JNI code), APIStub (has native methods), DipNativeAPI, DipErrorCode]  (injected by this method)
     *    |
     *    |
     * (child class loader) -> Sees the above classes loaded by the root class loader.
     *   Then creates DipNativeAPI implementation by instantiating DipNaitive class.
     * </pre>
     * 
     * 
     * <pre>
     * (root class loader) -> [DipNativeLoader, DipNative ...]  -> native code is loaded by once in this class loader 
     *   |   \
     *   |    (child2 class loader)      
     * (child1 class loader)
     * 
     * child1 and child2 share the same DipNative code loaded by the root class loader.
     * </pre>
     * 
     * Note that Java's class loader first delegates the class lookup to its
     * parent class loader. So once DipNativeLoader is loaded by the root
     * class loader, no child class loader initialize DipNativeLoader again.
     * 
     * @return 
     */

/*
    final static String SO_LOADER_B64=
    				"yv66vgAAADQAPwoAEQAiCQAQACMKAA4AJAoADgAlBwAmCgAFACcKACgAKQoABQAqCgAOACsHACwK"+
    				"AAoALQkAEAAuCgAoAC8HADAKAA4AIgcAMQcAMgEADmxvYWRlZExpYkZpbGVzAQATTGphdmEvdXRp"+
    				"bC9IYXNoTWFwOwEACVNpZ25hdHVyZQEAOkxqYXZhL3V0aWwvSGFzaE1hcDxMamF2YS9sYW5nL1N0"+
    				"cmluZztMamF2YS9sYW5nL0Jvb2xlYW47PjsBAAlsb2FkZWRMaWIBAAY8aW5pdD4BAAMoKVYBAARD"+
    				"b2RlAQAPTGluZU51bWJlclRhYmxlAQAEbG9hZAEAFShMamF2YS9sYW5nL1N0cmluZzspVgEADVN0"+
    				"YWNrTWFwVGFibGUBAAtsb2FkTGlicmFyeQEACDxjbGluaXQ+AQAKU291cmNlRmlsZQEADVNvTG9h"+
    				"ZGVyLmphdmEMABcAGAwAEgATDAAzADQMADUANgEAEWphdmEvbGFuZy9Cb29sZWFuDAA3ADgHADkM"+
    				"ABsAHAwAOgA7DAA8AD0BABNqYXZhL2xhbmcvRXhjZXB0aW9uDAA+ABgMABYAEwwAHgAcAQARamF2"+
    				"YS91dGlsL0hhc2hNYXABABRhcnV0aWxzL2puaS9Tb0xvYWRlcgEAEGphdmEvbGFuZy9PYmplY3QB"+
    				"AAtjb250YWluc0tleQEAFShMamF2YS9sYW5nL09iamVjdDspWgEAA2dldAEAJihMamF2YS9sYW5n"+
    				"L09iamVjdDspTGphdmEvbGFuZy9PYmplY3Q7AQAMYm9vbGVhblZhbHVlAQADKClaAQAQamF2YS9s"+
    				"YW5nL1N5c3RlbQEAB3ZhbHVlT2YBABYoWilMamF2YS9sYW5nL0Jvb2xlYW47AQADcHV0AQA4KExq"+
    				"YXZhL2xhbmcvT2JqZWN0O0xqYXZhL2xhbmcvT2JqZWN0OylMamF2YS9sYW5nL09iamVjdDsBAA9w"+
    				"cmludFN0YWNrVHJhY2UAIQAQABEAAAACAAoAEgATAAEAFAAAAAIAFQAKABYAEwABABQAAAACABUA"+
    				"BAABABcAGAABABkAAAAdAAEAAQAAAAUqtwABsQAAAAEAGgAAAAYAAQAAABYAKQAbABwAAQAZAAAA"+
    				"egADAAIAAAA0sgACKrYAA5kAFLIAAiq2AATAAAW2AAaZAASxKrgAB7IAAioEuAAItgAJV6cACEwr"+
    				"tgALsQABABsAKwAuAAoAAgAaAAAAHgAHAAAAGwAbAB0AHwAeACsAIQAuAB8ALwAgADMAIgAdAAAA"+
    				"CAADG1IHAAoEACkAHgAcAAEAGQAAAH8AAwACAAAANbIADCq2AAOZABWyAAwqtgAEwAAFtgAGBKAA"+
    				"BLEquAANsgAMKgS4AAi2AAlXpwAITCu2AAuxAAEAHAAsAC8ACgACABoAAAAiAAgAAAAlABsAJgAc"+
    				"ACkAIAAqACwALgAvACwAMAAtADQALwAdAAAACAADHFIHAAoEAAgAHwAYAAEAGQAAADEAAgAAAAAA"+
    				"FbsADlm3AA+zAAK7AA5ZtwAPswAMsQAAAAEAGgAAAAoAAgAAABcACgAYAAEAIAAAAAIAIQ==";
    
    static byte[] getSoLoaderBytes() {
    	return Base64.getDecoder().decode(SO_LOADER_B64);
    }
    */
     /* Inject Loader class to the root class loader
     * @param loaderInjected 
     * @param clazz 
     * 
     */
    private static Class< ? > injectSoLoader() throws IOException, ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            // Use parent class loader to load DipNative, since Tomcat, which uses different class loaders for each webapps, cannot load JNI interface twice

            ClassLoader rootClassLoader = findRootClassLoader();
            LinkedHashMap<String, byte[]> classByteCodeMap=new LinkedHashMap<>();
            
            
            String stubClassName="arutils.jni.APIStub";//APIStub.class.getName();
            String apiClassName="arutils.jni.API";//API.class.getName();
            
            String soResName=SO_LOADER_CLASS_NAME.replace('.', '/');
            String stubResName=stubClassName.replace('.', '/');
            String apiResName=apiClassName.replace('.', '/');
            //getSoLoaderBytes());//
            byte[] soLoaderBytes=getResourceBytes(String.format("/%s.bytes", soResName));
            byte[] apiBytes=getResourceBytes(String.format("/%s.class", apiResName));
            byte[] stubBytes=getResourceBytes(String.format("/%s.class", stubResName));
            
            classByteCodeMap.put(SO_LOADER_CLASS_NAME, soLoaderBytes);
            classByteCodeMap.put(apiClassName, apiBytes);
            classByteCodeMap.put(stubClassName, stubBytes);
            
            
            
            
            
            Class< ? > classLoader = Class.forName("java.lang.ClassLoader");
            Method defineClass = classLoader.getDeclaredMethod("defineClass", new Class[] { String.class, byte[].class, int.class, int.class, ProtectionDomain.class });
            ProtectionDomain pd = System.class.getProtectionDomain();
            // ClassLoader.defineClass is a protected method, so we have to make it accessible
            defineClass.setAccessible(true);
                       
            try {
            	for (Entry<String, byte[]> e : classByteCodeMap.entrySet()) {
            		String className=e.getKey();
            		byte[] bytes=e.getValue();
            		defineClass.invoke(rootClassLoader, className, bytes, 0, bytes.length, pd);
            	}
            } finally {
                // Reset the accessibility to defineClass method
                defineClass.setAccessible(false);
            }

            // Load the DipNativeLoader class
            return rootClassLoader.loadClass(SO_LOADER_CLASS_NAME);
    }

    /**
     * Load dip-java's native code using load method of the
     * SoLoader class injected to the root class loader.
     * @param loaderInjected 
     * 
     * @param loaderClass
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws IOException 
     * @throws InterruptedException 
     * @throws NoSuchAlgorithmException 
     */
    private static void loadSharedLibrary(Class< ? > loaderClass) throws Exception {
        if (loaderClass == null) throw new IllegalArgumentException("missing SoLoader class");
        Exception last=null;
        for (int a=0;a<2;++a) {
	        try {
	        	File nativeLib = findNativeLibrary();
		        if (nativeLib != null) {
		            // Load extracted or specified dipjava native library. 
		            Method loadMethod = loaderClass.getDeclaredMethod("load", new Class[] { String.class });
		            loadMethod.invoke(null, nativeLib.getAbsolutePath());
		        } else {
		            // Load preinstalled dipjava (in the path -Djava.library.path) 
		            Method loadMethod = loaderClass.getDeclaredMethod("loadLibrary", new Class[] { String.class });
		            loadMethod.invoke(null, "arutilsjni-"+nativeSoVersion+"-"+Utils.getArchName());
		        }
		        return;
	        } catch (Exception e) {
	        	last = Utils.extraceCause(e);
			}
        }
        Utils.rethrowCause(last);
    }

    /*
    static String md5sum(InputStream input) throws IOException {
        BufferedInputStream in = new BufferedInputStream(input);
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            DigestInputStream digestInputStream = new DigestInputStream(in, digest);
            for (; digestInputStream.read() >= 0;) {}
            ByteArrayOutputStream md5out = new ByteArrayOutputStream();
            md5out.write(digest.digest());
            return md5out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm is not available: " + e);
        }
        finally {
            in.close();
        }
    }
    */
    static File findNativeLibrary() throws InterruptedException, IOException, NoSuchAlgorithmException {
    	//String uid=Utils.echo("`id -u`").replaceAll("(\n|\r)+", "");
    	String resNameUnmapped="arutilsjni-"+nativeSoVersion+"-"+Utils.getArchName();
    	String resNameMapped=System.mapLibraryName(resNameUnmapped);
    	if (resNameMapped.endsWith(".a")) resNameMapped=resNameMapped.replaceAll("\\.a$", ".so");
    	String fullResPath="/so/"+resNameMapped;
    	String md5=new String(getResourceBytes(fullResPath+".md5"), Utils.UTF8);
    	md5=md5.replace("\n", "").replace("\r", "");
    	
    	
    	
    	String unmapped="arutilsjni-"+md5;////nativeSoVersion+"-"+Utils.getArchName()+"-"+uid;
    	String mapped=System.mapLibraryName(unmapped);
    	if (mapped.endsWith(".a")) mapped=mapped.replaceAll("\\.a$", ".so");
    	
    	File extractedLibFile=new File(TMP_DIR, mapped);
    	if (!extractedLibFile.exists()) { 
    		byte[] soContent=getResourceBytes(fullResPath);
        	Files.write(extractedLibFile.toPath(), soContent, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        	String osName=System.getProperty("os.name");
        	if (!( osName!=null  && osName.contains("Windows"))) {
        		try {
        			Runtime.getRuntime().exec(new String[] {"chmod", "755", extractedLibFile.getAbsolutePath()}).waitFor();
        		} catch (Exception e) {
        			e=Utils.proceedUnlessInterrupted(e);
        			System.err.println("Failed to chmod "+extractedLibFile.getAbsolutePath());
    			}
        	}
    	}
    	return extractedLibFile;

    }

    
    static String nativeSoVersion=readNativeSoVersion();
    	
    
    
    /**
     * 
     * @return the version string
     */
    static String readNativeSoVersion() {
    	String v;
		try {
			v = new String(getResourceBytes("/arutils/jni/NATIVE_JAVA_UTILS_SO_VERSION"));
			return v.replace("\r", "").replace("\n", "");
		} catch (IOException e) {
			return Utils.rethrowRuntimeException(e);
		}
    }
}
