package com.syos.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.syos.application.transaction.TransactionManager;
import com.syos.domain.event.EventPublisher;
import com.syos.domain.event.SaleEvent;
import com.syos.domain.event.SaleEventListener;
import com.syos.domain.model.*;
import com.syos.domain.repository.BillRepository;
import com.syos.domain.repository.ItemRepository;
import com.syos.domain.repository.StockBatchRepository;
import com.syos.domain.repository.TransactionRepository;
import com.syos.domain.service.DiscountCalculator;
import com.syos.domain.service.PercentageDiscountCalculator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInStoreSaleUnitTest {
  @Mock private ItemRepository itemRepository;
  @Mock private BillRepository billRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private StockBatchRepository storeStockRepository;
  @Mock private TransactionManager transactionManager;

  private final DiscountCalculator discountCalculator = new PercentageDiscountCalculator(5.0);

  @Test
  void shouldCreateBillAndPublishEventWithoutPersistence() {
    EventPublisher eventPublisher = new EventPublisher();
    CapturingListener listener = new CapturingListener();
    eventPublisher.register(listener);

    ProcessInStoreSale useCase =
        new ProcessInStoreSale(
            itemRepository,
            billRepository,
            transactionRepository,
            storeStockRepository,
            discountCalculator,
            eventPublisher,
            transactionManager);

    Item item = new Item(ItemCode.of("APPLE001"), "Red Apples", Money.of(2.50));
    when(itemRepository.findByCode(ItemCode.of("APPLE001"))).thenReturn(Optional.of(item));
    when(storeStockRepository.getTotalStock(ItemCode.of("APPLE001"))).thenReturn(10);
    when(billRepository.getNextSerialNumber()).thenReturn(1);
    when(transactionRepository.nextTransactionId()).thenReturn("TXN-000001");
    when(transactionManager.executeInTransaction(any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

    Map<ItemCode, Integer> items = new HashMap<>();
    items.put(ItemCode.of("APPLE001"), 2);

    Bill bill = useCase.execute(items, Money.of(10.0), LocalDate.of(2026, 2, 1));

    assertNotNull(bill);
    assertEquals(TransactionType.IN_STORE, bill.getType());
    assertEquals(1, bill.getItems().size());
    assertEquals(2, bill.getItems().get(0).getQuantity());
    assertTrue(bill.getFullPrice().isGreaterThan(Money.zero()));
    assertNotNull(listener.lastEvent);
    assertEquals(bill, listener.lastEvent.getBill());

    verify(billRepository).save(any(Bill.class));
    verify(transactionRepository).save(any(Transaction.class));
  }

  private static class CapturingListener implements SaleEventListener {
    private SaleEvent lastEvent;

    @Override
    public void onSale(SaleEvent event) {
      this.lastEvent = event;
    }
  }
}
