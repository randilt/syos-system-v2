# SYOS Billing System v2

## Architecture
- Three-tier: Client (Swing GUI) ↔ Server (Socket + Thread Pool) ↔ Database (MySQL)
- Clean Architecture preserved across all tiers

## Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8.0+ running on localhost:3306
- Database: syos_billing (created automatically by Flyway)

## Running the System
1. Start MySQL
2. ./scripts/start-server.sh (wait for "Server started" message)
3. ./scripts/start-client.sh (GUI opens, connect to localhost:9090)

## Running Test Clients
./scripts/run-test-clients.sh

## Running Tests
mvn test

## Module Structure
- syos-protocol: Shared DTOs and protocol classes
- syos-server: Business logic, domain, infrastructure, socket server
- syos-client: Swing GUI, connects to server via sockets
- syos-test-clients: Automated concurrent test clients
