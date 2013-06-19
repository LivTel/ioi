
JYTHONLIB = /usr/share/java/jython.jar

SRCS = InterpreterExample.java
CLASSES = $(SRCS:%.java=%.class)


top: $(CLASSES)

%.class: %.java
	javac -classpath ${JYTHONLIB}:${CLASSPATH} $<
