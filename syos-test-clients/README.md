# SYOS Test Clients

Standalone concurrent load-test clients for the SYOS server. They are plain Java applications — no test frameworks, no Spring Boot, no servlet containers. The only requirement is a **running SYOS server** on the target host/port.

---

## Module structure

```
syos-test-clients/
└── src/main/java/com/syos/testclient/
    ├── BaseTestClient.java             # Shared state, barrier, result printing
    ├── ConcurrentInStoreSaleClient.java
    ├── ConcurrentOnlineSaleClient.java
    └── TestClientRunner.java           # Runs both clients sequentially
```

---

## Threading model

> **No `java.util.concurrent` classes are used anywhere in this module.**

All concurrency is implemented with:

| Construct                        | Purpose                                                |
| -------------------------------- | ------------------------------------------------------ |
| `new Thread(Runnable, name)`     | Create worker threads                                  |
| `synchronized`                   | Mutual exclusion on counters and barrier state         |
| `wait()` / `notifyAll()`         | Manual barrier (start-gate), replaces `CountDownLatch` |
| `volatile boolean go`            | Barrier release flag visible across threads            |
| `t.join()` in a plain `for` loop | Await completion of all workers                        |

The barrier works as follows: every thread calls `arrive()` which increments a shared counter under `synchronized`. The **last** thread to arrive sets `go = true` and calls `notifyAll()`, which wakes all waiting threads simultaneously — achieving a thundering-herd effect against the server.

---

## Prerequisites

1. Java 17+
2. Maven 3.8+
3. The full `syos-billing-system-v2` project built at least once:
   ```bash
   cd syos-billing-system-v2
   mvn install -DskipTests
   ```
4. A running SYOS server (default `localhost:9090`):
   ```bash
   java -jar syos-server/target/syos-server-2.0-SNAPSHOT.jar
   ```
5. The database seeded with test items (`APPLE001`, `MILK001`, etc.)

---

## Building

```bash
cd syos-billing-system-v2
mvn package -pl syos-test-clients -am -DskipTests
```

---

## Running

### Option A — `TestClientRunner` (runs both phases sequentially)

```bash
java -cp "syos-test-clients/target/syos-test-clients-2.0-SNAPSHOT.jar:\
syos-protocol/target/syos-protocol-2.0-SNAPSHOT.jar:\
syos-client/target/syos-client-2.0-SNAPSHOT.jar" \
  com.syos.testclient.TestClientRunner [host] [port] [threads]
```

**Example** (20 threads each against localhost:9090):

```bash
java -cp "syos-test-clients/target/syos-test-clients-2.0-SNAPSHOT.jar:..." \
  com.syos.testclient.TestClientRunner localhost 9090 20
```

### Option B — `exec-maven-plugin`

```bash
mvn -pl syos-test-clients exec:java \
  -Dexec.mainClass=com.syos.testclient.TestClientRunner \
  -Dexec.args="localhost 9090 20"
```

### Option C — individual clients

```bash
# In-store only
java -cp ... com.syos.testclient.ConcurrentInStoreSaleClient \
    [host] [port] [threads] [itemCode] [quantity]

# Online only  (auto-registers a test user)
java -cp ... com.syos.testclient.ConcurrentOnlineSaleClient \
    [host] [port] [threads] [itemCode] [quantity]
```

---

## Arguments (all optional)

| Position | Argument   | Default                | Description                  |
| -------- | ---------- | ---------------------- | ---------------------------- |
| 1        | `host`     | `localhost`            | Server hostname or IP        |
| 2        | `port`     | `9090`                 | Server TCP port              |
| 3        | `threads`  | `20`                   | Number of concurrent threads |
| 4        | `itemCode` | `APPLE001` / `MILK001` | Stock item to purchase       |
| 5        | `quantity` | `2` / `1`              | Units per transaction        |

---

## Expected output

```
╔══════════════════════════════════════╗
║    Concurrent In-Store Sale Test     ║
╠══════════════════════════════════════╣
║  Server   : localhost:9090           ║
║  Threads  : 20                       ║
║  Item     : APPLE001                 ║
║  Qty/sale : 2                        ║
╚══════════════════════════════════════╝

[InStoreSaleThread-1]  Ready — waiting at barrier
[InStoreSaleThread-2]  Ready — waiting at barrier
...
[InStoreSaleThread-20] GO — sending PROCESS_IN_STORE_SALE ...
[InStoreSaleThread-1]  GO — sending PROCESS_IN_STORE_SALE ...
[InStoreSaleThread-7]  SUCCESS — Bill #42 | Items: 1 | Total: 5.98 | Change: 194.02
...

╔══════════════════════════════════════╗
║             TEST RESULTS             ║
╠══════════════════════════════════════╣
║  Total requests  :  20               ║
║  Successes       :  18               ║
║  Failures        :   2               ║
║  Success rate    : 90.0%             ║
║  Duration        : 1234 ms           ║
║  Throughput      : 16.2 req/s        ║
╚══════════════════════════════════════╝
```

Failures are expected once the seeded stock runs out — the server correctly rejects over-stock requests rather than corrupting data.

---

## What is tested

| Client                        | Command                 | Concurrency scenario                                                         |
| ----------------------------- | ----------------------- | ---------------------------------------------------------------------------- |
| `ConcurrentInStoreSaleClient` | `PROCESS_IN_STORE_SALE` | N threads race to buy the same item; server must serialize inventory updates |
| `ConcurrentOnlineSaleClient`  | `PROCESS_ONLINE_SALE`   | Same, plus `REGISTER_USER` before the storm to prove user creation works     |
| `TestClientRunner`            | Both above              | Sequential phases; combined pass/fail summary at end                         |
