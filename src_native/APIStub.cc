
#pragma GCC visibility push(default)
#include "APIStub.h"
#pragma GCC visibility pop

#include <stdlib.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <stdio.h>
#include <pthread.h>
#include <unistd.h>
#include "utils.h"


JNIEXPORT void JNICALL Java_arutils_jni_APIStub_testNativeCall(JNIEnv * env, jclass jc) {
	printf("native side\n");
}


/*
 * Class:     arutils_jni_APIStub
 * Method:    lfence
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_arutils_jni_APIStub_lfence(JNIEnv * e, jclass c) {
#if defined(__x86_64__)
	asm volatile("lfence" ::: "memory");
#endif
}

/*
 * Class:     arutils_jni_APIStub
 * Method:    sfence
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_arutils_jni_APIStub_sfence(JNIEnv * e, jclass c) {
	#if defined(__x86_64__)
		asm volatile("sfence" ::: "memory");
	#endif
}

/*
 * Class:     arutils_jni_APIStub
 * Method:    sync_lock_test_and_set
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_arutils_jni_APIStub_sync_1lock_1test_1and_1set(JNIEnv *e, jclass c, jlong a, jint v) {
	return __sync_lock_test_and_set(reinterpret_cast<int*>(a), v);
}

/*
 * Class:     arutils_jni_APIStub
 * Method:    sync_val_compare_and_swap
 * Signature: (JII)I
 */
JNIEXPORT jint JNICALL Java_arutils_jni_APIStub_sync_1val_1compare_1and_1swap(JNIEnv *e, jclass c, jlong a, jint o, jint v) {
	return __sync_val_compare_and_swap(reinterpret_cast<int*>(a), o, v);
}

/*
 * Class:     arutils_jni_APIStub
 * Method:    sync_lock_release
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_arutils_jni_APIStub_sync_1lock_1release(JNIEnv * e, jclass c, jlong a) {
	__sync_lock_release(reinterpret_cast<int*>(a));
}

/*
 * Class:     arutils_jni_APIStub
 * Method:    lock_cas
 * Signature: (J)V
 */

JNIEXPORT void JNICALL Java_arutils_jni_APIStub_lock_1cas(JNIEnv * e, jclass c, jlong a) {
	while (__sync_val_compare_and_swap(reinterpret_cast<int*>(a), 0, 1)) pthread_yield();
}
/*JNIEXPORT void JNICALL Java_arutils_jni_APIStub_lock_1cas(JNIEnv * e, jclass c, jlong a) {
	CAS: while (__sync_val_compare_and_swap(reinterpret_cast<int*>(a), 0, 1)) {
		unsigned int c=0;
		while (*(reinterpret_cast<int*>(a))) {
			__asm__ __volatile__("rep;nop": : :"memory");
			if (++c>10) {
				pthread_yield();
				goto CAS;

			}
		}
		//
	}
}
*/
/*
 * Class:     arutils_jni_APIStub
 * Method:    lock_tas
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_arutils_jni_APIStub_lock_1tas(JNIEnv * e, jclass c, jlong a) {
	while (__sync_lock_test_and_set(reinterpret_cast<int*>(a), 1)) pthread_yield();
}
/*
 * Class:     arutils_jni_APIStub
 * Method:    unlock_sfence
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_arutils_jni_APIStub_unlock_1sfence(JNIEnv * e, jclass c, jlong a) {
	#if defined(__x86_64__)
		asm volatile("sfence" ::: "memory");
	#endif
	*(reinterpret_cast<int*>(a))=0;
}

/*
 * Class:     arutils_jni_APIStub
 * Method:    unlock_dummy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_arutils_jni_APIStub_unlock_1dummy(JNIEnv * e, jclass c, jlong a) {
	*(reinterpret_cast<int*>(a))=0;
}
/*
 * Class:     arutils_jni_APIStub
 * Method:    unlock_lock_release
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_arutils_jni_APIStub_unlock_1lock_1release(JNIEnv * e, jclass c, jlong a) {
	__sync_lock_release(reinterpret_cast<int*>(a));
}
