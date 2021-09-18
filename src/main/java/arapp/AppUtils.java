package arapp;

import arutils.util.Utils;


public class AppUtils {
	public final static boolean DEBUG=!Utils.isEmpty(System.getenv("DEBUG")) && 
			!"0".equals(System.getenv("DEBUG")) && 
			!"no".equalsIgnoreCase(System.getenv("DEBUG")) && 
			!"false".equalsIgnoreCase(System.getenv("DEBUG")) && 
			!"n".equalsIgnoreCase(System.getenv("DEBUG"));

	
}
