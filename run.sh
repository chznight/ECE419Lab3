#!/bin/bash
JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
# $1 = NS ip
# $2 = NS port
# $3 = port
# $4 = number of players (Do we need this?)

${JAVA_HOME}/bin/java Mazewar $1 $2 $3 $4
