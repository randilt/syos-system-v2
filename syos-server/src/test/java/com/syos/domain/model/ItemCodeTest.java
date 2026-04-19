package com.syos.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ItemCodeTest {
  @Test
  void shouldCreateItemCode() {
    ItemCode code = ItemCode.of("APPLE001");
    assertEquals("APPLE001", code.getValue());
  }

  @Test
  void shouldNormalizeItemCode() {
    ItemCode code = ItemCode.of("  apple001  ");
    assertEquals("APPLE001", code.getValue());
  }

  @Test
  void shouldRejectNullItemCode() {
    assertThrows(IllegalArgumentException.class, () -> ItemCode.of(null));
  }

  @Test
  void shouldRejectEmptyItemCode() {
    assertThrows(IllegalArgumentException.class, () -> ItemCode.of(""));
    assertThrows(IllegalArgumentException.class, () -> ItemCode.of("   "));
  }

  @Test
  void shouldBeEqualForSameValue() {
    ItemCode code1 = ItemCode.of("APPLE001");
    ItemCode code2 = ItemCode.of("APPLE001");
    assertEquals(code1, code2);
    assertEquals(code1.hashCode(), code2.hashCode());
  }
}
