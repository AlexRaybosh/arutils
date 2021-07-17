
JNI_H:=$(SRC_NATIVE)/APIStub.h
JAVAH:=javah
UNAME=$(shell uname)

export NATIVE_BUILD:=$(ROOT)/native_build


all : $(JNI_H)
ifeq ($(UNAME),Linux)
	$(MAKE) -f arch.mk ARCH="Linux-x86_64-64" ROOT=$(ROOT)
	$(MAKE) -f arch.mk ARCH="Linux-x86_64-32" ROOT=$(ROOT)
endif

$(JNI_H): $(JAVA_BUILD)/arutils/jni/APIStub.class
	@echo GENERATE JNI HEADER
	@ls -l $(JAVA_BUILD)/arutils/jni/APIStub.class
	@ls -l $(JNI_H)
	$(JAVAH) -classpath $(JAVA_BUILD) -o $(JNI_H) arutils.jni.APIStub
	touch $(JNI_H)


clean:
	@rm -rf $(ROOT)/native_build
	$(MAKE) -f arch.mk ARCH="Linux-x86_64-64" ROOT=$(ROOT) clean
	$(MAKE) -f arch.mk ARCH="Linux-x86_64-32" ROOT=$(ROOT) clean

.PHONY: clean
