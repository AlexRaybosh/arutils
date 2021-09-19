ifeq "$(ROOT)" ""
where-am-i = $(CURDIR)/$(word $(words $(MAKEFILE_LIST)),$(MAKEFILE_LIST))
THIS_MAKEFILE := $(call where-am-i)
#$(info $(THIS_MAKEFILE))
ROOT := $(dir $(THIS_MAKEFILE))
endif

ROOT:=$(shell echo $(ROOT) | sed -e 's/\/\//\//g' -e 's/\/$$//g' )
JAVAC?=javac

SRC_JAVA:=$(ROOT)/src/main/java
SRC_ROOTS:= $(SRC_JAVA)
JVM_VERSION:=1.8

export JAVA_BUILD:=$(ROOT)/java_build
export SRC_NATIVE:=$(ROOT)/src_native 
export NATIVE_JAVA_UTILS_SO_VERSION:=$(shell cat $(SRC_JAVA)/arutils/jni/NATIVE_JAVA_UTILS_SO_VERSION)

$(info NATIVE_JAVA_UTILS_SO_VERSION $(NATIVE_JAVA_UTILS_SO_VERSION))


all: prep-java-list

.PHONY: prep-java-list native-bild java-compile clean zip copy-resources



get-libraries-src: $(ROOT)/javalibssrc/DONE

$(ROOT)/javalibssrc/DONE:
	@echo DOWNLOADING SRC JAVA LIBS
	@mkdir -p $(ROOT)/javalibssrc
	curl -sS -o $(ROOT)/javalibssrc/commons-codec-1.15-sources.jar https://repo1.maven.org/maven2/commons-codec/commons-codec/1.15/commons-codec-1.15-sources.jar
	curl -sS -o $(ROOT)/javalibssrc/gson-2.8.7-sources.jar https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.7/gson-2.8.7-sources.jar
	curl -sS -o $(ROOT)/javalibssrc/mysql-connector-java-5.1.49-sources.jar https://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.49/mysql-connector-java-5.1.49-sources.jar
	curl -sS -o $(ROOT)/javalibssrc/postgresql-42.2.23-sources.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.2.23/postgresql-42.2.23-sources.jar
	curl -sS -o $(ROOT)/javalibssrc/bcprov-jdk15on-1.69-sources.jar https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.69/bcprov-jdk15on-1.69-sources.jar
	touch $(ROOT)/javalibssrc/DONE
	#@rm -rf $(ROOT)/javalibssrc

get-libraries: get-libraries-src $(ROOT)/javalibs/DONE

$(ROOT)/javalibs/DONE:
	@echo DOWNLOADING JAVA LIBS
	@mkdir -p $(ROOT)/javalibs
	curl -sS -o $(ROOT)/javalibs/commons-codec-1.15.jar https://repo1.maven.org/maven2/commons-codec/commons-codec/1.15/commons-codec-1.15.jar || exit 1
	curl -sS -o $(ROOT)/javalibs/gson-2.8.7.jar https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.7/gson-2.8.7.jar || exit 1
	curl -sS -o $(ROOT)/javalibs/mysql-connector-java-5.1.49.jar https://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.49/mysql-connector-java-5.1.49.jar || exit 1
	curl -sS -o $(ROOT)/javalibs/postgresql-42.2.23.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.2.23/postgresql-42.2.23.jar || exit 1
	curl -sS -o $(ROOT)/javalibs/bcprov-jdk15on-1.69.jar https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.69/bcprov-jdk15on-1.69.jar || exit 1
	touch $(ROOT)/javalibs/DONE
	#@rm -rf $(ROOT)/javalibs

#JAVA_LIBS:=$(shell find $(ROOT)/javalibs -name '*.jar' -printf '%p:' | sed -e 's/\/\//\//g' -e 's/:$$//g')
#$(info JAVA_LIBS $(JAVA_LIBS))

MOST_RECENT_JAVA:=$(shell find $(SRC_JAVA) -type f -printf "%T@\0%p\0" | awk '{if ($$0>max) {max=$$0;getline mostrecent} else getline}END{print mostrecent}' RS='\0')
$(info MOST_RECENT_JAVA $(MOST_RECENT_JAVA))

MOST_RECENT_JAVA_TS=$(shell stat -c %Y $(MOST_RECENT_JAVA))
$(info MOST_RECENT_JAVA_TS $(MOST_RECENT_JAVA_TS))

JAVA_BUILD_TS=$(shell test -d $(JAVA_BUILD) && stat -c %Y $(JAVA_BUILD) || echo 0 )
$(info JAVA_BUILD_TS $(JAVA_BUILD_TS))

REBUILD_JAVA := $(shell [ $(MOST_RECENT_JAVA_TS) -gt $(JAVA_BUILD_TS) ] && echo true || test -f $(JAVA_BUILD)/arutils/jni/SoLoader.bytes || echo true )
$(info REBUILD_JAVA $(REBUILD_JAVA))

java-compile : get-libraries
ifeq ($(REBUILD_JAVA),true)
	@echo Compile java
	@rm -rf $(JAVA_BUILD)
	@mkdir -p $(JAVA_BUILD)
	cp $(SRC_JAVA)/arutils/jni/SoLoader.fake $(SRC_JAVA)/arutils/jni/SoLoader.java
	@for R in $(SRC_ROOTS); do find $$R -name '*.java' >> $(JAVA_BUILD)/java_list ; done
	$(JAVAC) -encoding utf8 -source $(JVM_VERSION) -target $(JVM_VERSION) \
	-cp `find $(ROOT)/javalibs -name '*.jar' -printf '%p:' | sed -e 's/\/\//\//g' -e 's/:$$//g'` \
	-d $(JAVA_BUILD) @$(JAVA_BUILD)/java_list
	mv $(JAVA_BUILD)/arutils/jni/SoLoader.class $(JAVA_BUILD)/arutils/jni/SoLoader.bytes
	rm -f $(SRC_JAVA)/arutils/jni/SoLoader.java
	touch $(JAVA_BUILD)
endif

copy-resources : java-compile native-build
	@echo Preparing binaries
	@rm -rf $(JAVA_BUILD)/so
	@mkdir -p $(JAVA_BUILD)/so
	@cp -a $(ROOT)/prebuild/* $(JAVA_BUILD)/so
	@cp $(SRC_JAVA)/arutils/jni/NATIVE_JAVA_UTILS_SO_VERSION $(JAVA_BUILD)/arutils/jni
	@mkdir -p $(ROOT)/classes
	sh -c "cd $(JAVA_BUILD) && tar -cf - . " | sh -c "cd $(ROOT)/classes && tar -xf -"
 
#
native-build: java-compile 
	@$(MAKE) -C $(ROOT)/src_native ROOT=$(ROOT) JAVA_BUILD=$(JAVA_BUILD) SRC_NATIVE=$(SRC_NATIVE) NATIVE_JAVA_UTILS_SO_VERSION=$(NATIVE_JAVA_UTILS_SO_VERSION)
 
 
JAR_TS:=$(shell test -f $(ROOT)/target/arutils.jar && stat -c %Y $(ROOT)/target/arutils.jar || echo 0 )
$(info JAR_TS $(JAR_TS))


VERSION:=1.0.0

zip:  copy-resources
	rm -rf $(ROOT)/target
	mkdir -p $(ROOT)/target
	sh -c "cd $(SRC_JAVA) && zip -qr $(ROOT)/target/arutils-sources-$(VERSION)-jar . " ; \
	sh -c "cd $(JAVA_BUILD) && zip -qr $(ROOT)/target/arutils-$(VERSION).jar . "
 
clean-libs:
	rm -rf $(ROOT)/javalibssrc $(ROOT)/javalibs

clean:
	rm -rf $(ROOT)/java_build $(ROOT)/java_list $(ROOT)/native_build $(ROOT)/target
	@$(MAKE) -C $(ROOT)/src_native clean ROOT=$(ROOT) JAVA_BUILD=$(JAVA_BUILD) SRC_NATIVE=$(SRC_NATIVE) NATIVE_JAVA_UTILS_SO_VERSION=$(NATIVE_JAVA_UTILS_SO_VERSION)
	
