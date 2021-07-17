

ifeq "$(ARCH)" "Linux-x86_64-64"
	CXX:=g++
	LDFLAGS:=-shared -pthread -static-libgcc -static-libstdc++ -lrt
	CXXFLAGS:=-m64
	STRIP:=strip
	ARCH_INCLUDE:=$(CURDIR)/jni_include/linux
else ifeq "$(ARCH)" "Linux-x86_64-32"
	CXX:=g++
	LDFLAGS:=-shared -pthread -static-libgcc -static-libstdc++ -lrt
	CXXFLAGS:=-m32
	STRIP:=strip
	ARCH_INCLUDE:=$(CURDIR)/jni_include/linux
endif

#COMMON_CXXFLAGS:=-fvisibility=hidden -fvisibility-inlines-hidden -D_REENTRANT

COMMON_CXXFLAGS:=-fvisibility=hidden -fvisibility-inlines-hidden -D_REENTRANT \
	-D_POSIX_C_SOURCE=202001L -D_XOPEN_SOURCE=600 -fpic -pthread \
	-I$(ARCH_INCLUDE) \
	-std=gnu++11 \
	-O2 -g

CXXFLAGS:=$(CXXFLAGS) $(COMMON_CXXFLAGS)

JNI_BUILD:=$(NATIVE_BUILD)/$(ARCH)

$(shell mkdir -p $(JNI_BUILD))

#LIBNAME:=libarutilsjni-$(NATIVE_JAVA_UTILS_SO_VERSION)-$(ARCH).so
LIBNAME:=libarutilsjni-$(NATIVE_JAVA_UTILS_SO_VERSION)-$(ARCH).so
$(info LIBNAME $(LIBNAME))

all: $(JNI_BUILD)/$(LIBNAME)

$(JNI_BUILD)/%.o : %.cc %.h
	echo "Processing " $<
	mkdir -p $(@D)
	$(CXX) $(CXXFLAGS) -c $< -o $@

JNI_OBJS:=$(patsubst %.cc, $(JNI_BUILD)/%.o, $(wildcard *.cc))
JNI_H:=$(wildcard *.h)

$(JNI_OBJS): $(JNI_H) Makefile arch.mk

$(info ARCH $(ARCH))
$(info ARCH_INCLUDE $(ARCH_INCLUDE))
$(info JNI_BUILD $(JNI_BUILD))
$(info CXX $(CXX))
$(info LDFLAGS $(LDFLAGS))
$(info CXXFLAGS $(CXXFLAGS))
$(info JNI_H $(JNI_H))
$(info JNI_OBJS $(JNI_OBJS))
$(info LIBNAME $(JNI_BUILD)/$(LIBNAME))

$(JNI_BUILD)/$(LIBNAME): $(JNI_OBJS) arch.mk
	mkdir -p $(ROOT)/prebuild
	@echo Linking $@
	$(CXX) $(CXXFLAGS) -o $@ $(JNI_OBJS) $(LDFLAGS)
	mkdir -p $(ROOT)/prebuild
	mkdir -p $(ROOT)/java_build/so
	cp $@ $(ROOT)/prebuild/$(LIBNAME)
	$(STRIP) $(ROOT)/prebuild/$(LIBNAME)
	md5sum -z $(ROOT)/prebuild/$(LIBNAME) | awk '{print $$1}' >  $(ROOT)/prebuild/$(LIBNAME).md5
	cp $(ROOT)/prebuild/$(LIBNAME) $(ROOT)/java_build/so
	cp $(ROOT)/prebuild/$(LIBNAME).md5 $(ROOT)/java_build/so
	
#$(STRIP) $(ROOT)/prebuild/$(LIBNAME)

clean:
	@rm -f $(JNI_OBJS) $(ROOT)/prebuild/$(LIBNAME)
	
.PHONY: clean