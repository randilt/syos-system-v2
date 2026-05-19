#!/bin/bash
cd "$(dirname "$0")/.."

HOST="${1:-localhost}"
PORT="${2:-9090}"
THREADS="${3:-20}"

mvn -DskipTests=true install -pl syos-test-clients -am
mvn -pl syos-test-clients exec:java \
	-Dexec.mainClass="com.syos.testclient.TestClientRunner" \
	-Dexec.args="$HOST $PORT $THREADS"
