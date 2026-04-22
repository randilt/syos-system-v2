#!/bin/bash
cd "$(dirname "$0")/.."
mvn -DskipTests=true install -pl syos-test-clients -am
mvn -pl syos-test-clients exec:java -Dexec.mainClass="com.syos.testclient.TestClientRunner"
