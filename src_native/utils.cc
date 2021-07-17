#include "utils.h"
#include <jni.h>
#include <string.h>
#include <stdexcept>
#include <vector>
#include <unistd.h>


extern "C" {
	extern char ** environ;
}


void errno_string(std::string & str) {
	char b[1024];
	*b=0;
	auto len=sizeof(b);
	strerror_r(errno, b, len);
	str.append(b, b+strlen(b));
}

void errno_exception(const std::string & msg) {
	char b[1024];
	*b=0;
	auto len=sizeof(b);
	strerror_r(errno, b, len);
	std::string str(msg);
	str.append(": ");
	str.append(b, b+strlen(b));
	throw std::runtime_error(str);
}

std::string jstringToString(JNIEnv* env, jstring jstr) {
	if (!jstr) return "";
	jsize s=env->GetStringUTFLength(jstr);
	std::string ret((size_t)s, (char)0x0);
	env->GetStringUTFRegion(jstr, 0, s, (char*)ret.data());
	return std::move(ret);
}

std::vector<std::string> jstringsToStrings(JNIEnv* env, jobjectArray jstrArray) {
	if (jstrArray) {
		size_t size=env->GetArrayLength(jstrArray);
		std::vector<std::string> ret(size);
		for (size_t i=0; i<size; ++i) {
			std::string el=jstringToString(env, (jstring)env->GetObjectArrayElement(jstrArray, i));
			ret.emplace_back(std::move(el));
		}
		return std::move(ret);
	}
	return std::vector<std::string>();
}


jint throwNoClassDefError(JNIEnv* env, const char* msg) {
	const char* exClassName="java/lang/NoClassDefFoundError";
	jclass exClass=env->FindClass(exClassName);
	if (!exClass) throw std::runtime_error(std::string("Failed to find ")+exClassName);
	return env->ThrowNew(exClass, msg);
}

jint throwNoNoSuchMethodError(JNIEnv* env, const char* className, const char* methodName, const char* sig) {
	const char* exClassName="java/lang/NoSuchMethodError";
	jclass exClass=env->FindClass(exClassName);
	if (!exClass) return throwNoClassDefError(env,exClassName);
	std::string msg;
	msg.append(className).append(".").append(methodName).append(".").append(sig);
	return env->ThrowNew(exClass, msg.c_str());
}
jint throwOutOfMemoryError(JNIEnv* env, const char* msg) {
	const char* exClassName="java/lang/OutOfMemoryError";
	jclass exClass=env->FindClass(exClassName);
	if (!exClass) throwNoClassDefError(env, exClassName);
	return env->ThrowNew(exClass, msg);
}


jint throwException(JNIEnv* env, const char* className, const char* msg) {
	jclass exClass=env->FindClass(className);
	if (!exClass) return throwNoClassDefError(env, className);
	return env->ThrowNew(exClass, msg);
}
