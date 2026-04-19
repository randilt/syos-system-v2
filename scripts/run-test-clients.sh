#!/bin/bash
cd "$(dirname "$0")/.."
mvn -pl syos-test-clients exec:java -Dexec.mainClass="com.syos.testclient.TestClientRunner"
