# Build RenderGraph

SHELL := /bin/bash
.PHONY: all test clean

classPath := gephi-toolkit-0.9.1-all.jar:docopt.jar

all: RenderGraph.class

%.class: %.java
	javac -cp $(classPath):. $<
