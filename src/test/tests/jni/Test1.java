package tests.jni;

import arutils.jni.API;
import arutils.util.Utils;

public class Test1 {

	public static void main(String[] args) throws Exception {
		API.testNative();
		System.out.println(Utils.getCodeMarker());
	}

}
