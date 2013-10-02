# Makefile
# $Header$
include ../../../../../Makefile.common
include	../../../../Makefile.common

PACKAGEDIR	= ngat/ioi/command
BINDIR 		= $(IOI_BIN_HOME)/java/$(PACKAGEDIR)
SRCS 		= Command.java CommandReplyBroker.java StandardReplyCommand.java KeywordValueReplyCommand.java \
		AcquireRampCommand.java GetConfigCommand.java InitializeCommand.java \
		PingCommand.java PowerUpASICCommand.java PowerDownASICCommand.java SetFSModeCommand.java \
		SetFSParamCommand.java SetIdleModeOptionCommand.java SetRampParamCommand.java \
		SetWindowModeCommand.java SetWinParamsCommand.java StopAcquisitionCommand.java
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)

DIRS 		= 

top: $(OBJS)
	@for i in $(DIRS); \
	do \
		(echo making in $$i...; cd $$i; $(MAKE) ); \
	done;

$(BINDIR)/%.class:%.java
	$(JAVAC) -Xlint -sourcepath .:$(IOI_SRC_HOME)/java \
		-classpath $(IOI_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) -d $(IOI_BIN_HOME)/java $<

checkout:
	$(CO) $(CO_OPTIONS) $(SRCS)
	@for i in $(DIRS); \
	do \
		(echo checkout in $$i...; cd $$i; $(CO) $(CO_OPTIONS) Makefile; $(MAKE) checkout); \
	done;

checkin:
	-$(CI) $(CI_OPTIONS) $(SRCS)
	-@for i in $(DIRS); \
	do \
		(echo checkin in $$i...; cd $$i; $(MAKE) checkin; $(CI) $(CI_OPTIONS) Makefile); \
	done;

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
