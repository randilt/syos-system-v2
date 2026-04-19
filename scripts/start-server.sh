#!/bin/bash
cd "$(dirname "$0")/.."
mvn -pl syos-server exec:java -Dexec.mainClass="com.syos.ServerApp"
