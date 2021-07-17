package arutils.jni.tests;

import arutils.jni.API;
import arutils.util.Utils;

public class Test1 {

	public static void main(String[] args) throws Exception {
		Utils.getUnsafe();
		API.testNative();
	}

}
