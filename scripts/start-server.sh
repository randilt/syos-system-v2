#!/bin/bash
cd "$(dirname "$0")/.."
mvn -DskipTests=true install -pl syos-server -am
mvn -pl syos-server exec:java -Dexec.mainClass="com.syos.ServerApp"
