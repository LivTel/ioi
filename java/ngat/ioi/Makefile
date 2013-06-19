# Makefile
# $Header$
include ../../../Makefile.common
include ../../../../Makefile.common

MAKE 		= make
PACKAGEDIR	= ngat/ioi/
BINDIR 		= $(IOI_BIN_HOME)/java/$(PACKAGEDIR)
CONFIG_BINDIR 	= $(IOI_BIN_HOME)/java
SCRIPT_BINDIR 	= $(IOI_BIN_HOME)/java
SRCS 		= $(MAIN_SRCS) $(IMPL_SRCS)
MAIN_SRCS 	= IOIConstants.java \
		IOI.java IOIStatus.java IOITCPClientConnectionThread.java \
		IOITCPServerConnectionThread.java IOITCPServer.java IOIREBOOTQuitThread.java
IMPL_SRCS = $(BASE_IMPL_SRCS) $(CALIBRATE_IMPL_SRCS) $(EXPOSE_IMPL_SRCS) $(INTERRUPT_IMPL_SRCS) $(SETUP_IMPL_SRCS)
BASE_IMPL_SRCS		= CommandImplementation.java JMSCommandImplementation.java UnknownCommandImplementation.java \
			HardwareImplementation.java FITSImplementation.java
CALIBRATE_IMPL_SRCS 	= 
#BIASImplementation.java DARKImplementation.java 
EXPOSE_IMPL_SRCS	= EXPOSEImplementation.java MULTRUNImplementation.java
#TIMED_MULTRUNATImplementation.java
INTERRUPT_IMPL_SRCS 	= INTERRUPTImplementation.java GET_STATUSImplementation.java REBOOTImplementation.java
#ABORTImplementation.java
SETUP_IMPL_SRCS		= SETUPImplementation.java CONFIGImplementation.java
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)

CONFIG_SRCS	= ioi1.ioi.net.properties ioi1.ioi.properties ioi1.fits.properties \
		ltobs9.ioi.net.properties ltobs9.ioi.properties ltobs9.fits.properties 
CONFIG_OBJS	= $(CONFIG_SRCS:%.properties=$(CONFIG_BINDIR)/%.properties)
SCRIPT_SRCS	= 
SCRIPT_OBJS	= $(SCRIPT_SRCS:%=$(SCRIPT_BINDIR)/%)
DIRS 		= command test

top: dirs $(OBJS) config scripts dirs

#
# Java classes
#
$(BINDIR)/%.class:%.java
	$(JAVAC) -sourcepath .:$(IOI_SRC_HOME)/java \
		-classpath $(IOI_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) -d $(IOI_BIN_HOME)/java $<

#
# configuration files
#
config: $(CONFIG_OBJS) $(INI_OBJS)


$(CONFIG_BINDIR)/%.properties: %.properties
	$(CP) $< $@

#
# run scripts
#
scripts: $(SCRIPT_OBJS)

#
# sub dirs
#
dirs:
	@for i in $(DIRS); \
	do \
		(echo making in $$i...; cd $$i; $(MAKE) ); \
	done;

#
# other targets
#
checkin:
	-$(CI) $(CI_OPTIONS) $(SRCS)
	-@for i in $(DIRS); \
	do \
		(echo checkin in $$i...; cd $$i; $(MAKE) checkin; $(CI) $(CI_OPTIONS) Makefile); \
	done;

checkout:
	$(CO) $(CO_OPTIONS) $(SRCS)
	@for i in $(DIRS); \
	do \
		(echo checkout in $$i...; cd $$i; $(CO) $(CO_OPTIONS) Makefile; $(MAKE) checkout); \
	done;

depend:
	echo no depend in ngat directory.

clean: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS)
	-$(RM) $(RM_OPTIONS) $(JARLIBDIR)/$(JAR_FILE)
	@for i in $(DIRS); \
	do \
		(echo clean in $$i...; cd $$i; $(MAKE) clean); \
	done;

tidy:
	-$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)
	@for i in $(DIRS); \
	do \
		(echo tidy in $$i...; cd $$i; $(MAKE) tidy); \
	done;

backup: tidy checkin
	@for i in $(DIRS); \
	do \
		(echo backup in $$i...; cd $$i; $(MAKE) backup); \
	done;
#
# $Log$
#
