package arutils.jni;


public class APIStub {

	public static native void testNativeCall();
	

	public static native void lfence();
	public static native void sfence();
	
	public static native int sync_lock_test_and_set(long addr, int v);
	public static native int sync_val_compare_and_swap(long addr, int o, int v);
	public static native void sync_lock_release(long addr);
	
	public static native void lock_cas(long addr);
	public static native void lock_tas(long addr);
	public static native void unlock_sfence(long addr);
	public static native void unlock_dummy(long addr);
	public static native void unlock_lock_release(long addr);
	
	
	
}
