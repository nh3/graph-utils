# Build RenderGraph

SHELL := /bin/bash
.PHONY: all test clean

classPath := gephi-toolkit-0.9.1-all.jar:docopt.jar

targets := RenderGraph

sources = $(addsuffix .java,$(targets))
classes = $(addsuffix .class,$(targets))
manifests = $(addsuffix .manifest,$(targets))
jars = $(addsuffix .jar,$(targets))

maxMem := 16g

all: $(targets)

clean:
	-rm -f $(manifests) $(classes)

$(classes): %.class: %.java
	javac -cp $(classPath):. $<

$(manifests): %.manifest: %.class
	echo "Main-Class: $(subst .class,,$<)" > $@
	echo "Class-Path: $(subst :, ,$(classPath))" >> $@

$(jars): %.jar: %.manifest %.class
	jar cvfm $@ $^

$(targets): %: %.jar
	echo 'java -Xmx$(maxMem) -jar `dirname $$0`/'$<' $$*' > $@
	chmod +x $@
