#!/bin/bash
cd "$(dirname "$0")/.."
mvn -pl syos-client exec:java -Dexec.mainClass="com.syos.ClientApp"
