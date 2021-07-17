/*
 * utils.hh
 *
 *  Created on: Sep 7, 2012
 *      Author: alex
 */

#ifndef UTILS_HH_
#define UTILS_HH_

#include <stdint.h>
#include <stdlib.h>
#ifndef uint8
#define uint8 uint8_t
#endif
#ifndef uint16
#define uint16 uint16_t
#endif
#ifndef uint32
#define uint32 uint32_t
#endif
#ifndef uint
#define uint uint32_t
#endif
#ifndef uint64
#define uint64 uint64_t
#endif
#ifndef int8
#define int8 int8_t
#endif
#ifndef int16
#define int16 int16_t
#endif
#ifndef int32
#define int32 int32_t
#endif
#ifndef int64
#define int64 int64_t
#endif
#ifndef restrict
#define restrict __restrict__
#endif


/*
class GoodSpinLock {
	uint32 lck;
public:
	void lock();
	void unlock();
};
inline void GoodSpinLock::lock() restrict {
	while (__sync_val_compare_and_swap(&lck, 0, 1)) pthread_yield();
}

inline void GoodSpinLock::unlock() restrict {
	#if defined(__x86_64__)
		asm volatile("sfence" ::: "memory");
	#endif
	lck=0;
}

*/

#include <jni.h>
#include <string>
#include <vector>

void errno_exception(const std::string & msg);
void errno_string(std::string & str);
void errno_exception(const std::string & msg);
std::string jstringToString(JNIEnv* env, jstring jstr);
std::vector<std::string> jstringsToStrings(JNIEnv* env, jobjectArray jstrArray);
jint throwNoClassDefError(JNIEnv* env, const char* msg);
jint throwNoNoSuchMethodError(JNIEnv* env, const char* className, const char* methodName, const char* sig);
jint throwOutOfMemoryError(JNIEnv* env, const char* msg);
jint throwException(JNIEnv* env, const char* className, const char* msg);

#endif /* UTILS_HH_ */
