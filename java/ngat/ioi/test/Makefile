# Makefile
# $Header$
include ../../../../../Makefile.common
include	../../../../Makefile.common

PACKAGEDIR	= ngat/ioi/test
BINDIR 		= $(IOI_BIN_HOME)/java/$(PACKAGEDIR)
SRCS 		= AcquireRampAndGuideCallbackInterface.java AcquireRampAndGuide.java TestAcquireRampAndGuide.java \
		SicfTCPServer.java SicfTCPServerConnectionThread.java SicfTCPClientConnectionThread.java \
		SendConfigCommand.java TestIDLSocketServer.java BSSServer.java BSSServerConnectionThread.java \
		SendMultrunCommand.java SendGetStatusCommand.java
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)

DIRS 		= 

top: $(OBJS)
	@for i in $(DIRS); \
	do \
		(echo making in $$i...; cd $$i; $(MAKE) ); \
	done;

$(BINDIR)/%.class:%.java
	$(JAVAC) -Xlint $(JAVAC_FLAGS) -sourcepath .:$(IOI_SRC_HOME)/java \
		-classpath $(IOI_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) -d $(IOI_BIN_HOME)/java $<

clean: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS)
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

backup: tidy
	@for i in $(DIRS); \
	do \
		(echo backup in $$i...; cd $$i; $(MAKE) backup); \
	done;
#
# $Log$
#
