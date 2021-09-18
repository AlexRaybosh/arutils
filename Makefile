where-am-i = $(CURDIR)/$(word $(words $(MAKEFILE_LIST)),$(MAKEFILE_LIST))
THIS_MAKEFILE := $(call where-am-i)
#$(info $(THIS_MAKEFILE))
ROOT := $(dir $(THIS_MAKEFILE))



all:
	@$(MAKE) -f arutils.mk zip ROOT=$(ROOT)

clean:
	@$(MAKE) -f arutils.mk clean ROOT=$(ROOT)

clean_libs:
	@$(MAKE) -f arutils.mk clean-libs ROOT=$(ROOT)

libs:
	@$(MAKE) -f arutils.mk get-libraries ROOT=$(ROOT)


.PHONY: clean