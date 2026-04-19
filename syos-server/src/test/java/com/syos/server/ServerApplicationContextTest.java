package com.syos.server;

import static org.junit.jupiter.api.Assertions.*;

import com.syos.testutil.TestDatabaseSupport;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerApplicationContextTest {

  @BeforeAll
  static void migrateDatabase() {
    TestDatabaseSupport.migrate();
  }

  @BeforeEach
  void resetSingleton() throws Exception {
    TestDatabaseSupport.resetDatabase();
    Field instanceField = ServerApplicationContext.class.getDeclaredField("instance");
    instanceField.setAccessible(true);
    instanceField.set(null, null);
  }

  @Test
  void getRequestRouter_beforeInitialize_throws() {
    ServerApplicationContext ctx = ServerApplicationContext.getInstance();
    assertFalse(ctx.isInitialized());
    assertThrows(IllegalStateException.class, ctx::getRequestRouter);
  }

  @Test
  void initialize_wiresAllComponents() {
    ServerApplicationContext ctx = ServerApplicationContext.getInstance();
    ctx.initialize();

    assertTrue(ctx.isInitialized());
    assertNotNull(ctx.getRequestRouter());
    assertNotNull(ctx.getItemRepository());
    assertNotNull(ctx.getBillRepository());
    assertNotNull(ctx.getUserRepository());
    assertNotNull(ctx.getStoreStockRepository());
    assertNotNull(ctx.getShelfStockRepository());
    assertNotNull(ctx.getOnlineStockRepository());
    assertNotNull(ctx.getStockManager());
    assertNotNull(ctx.getProcessInStoreSale());
    assertNotNull(ctx.getProcessOnlineSale());
    assertNotNull(ctx.getStoreAddStock());
    assertNotNull(ctx.getOnlineAddStock());
    assertNotNull(ctx.getRegisterUser());
    assertNotNull(ctx.getReportFactory());
  }

  @Test
  void initialize_calledTwice_isIdempotent() {
    ServerApplicationContext ctx = ServerApplicationContext.getInstance();
    ctx.initialize();
    RequestRouter first = ctx.getRequestRouter();
    ctx.initialize();
    assertSame(first, ctx.getRequestRouter());
  }
}
