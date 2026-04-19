package com.syos.application.usecase;

import com.syos.application.transaction.TransactionManager;
import com.syos.domain.event.EventPublisher;
import com.syos.domain.event.SaleEvent;
import com.syos.domain.model.*;
import com.syos.domain.repository.*;
import com.syos.domain.service.DiscountCalculator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Use case: processes an in-store sale, persists bill/transaction, and publishes domain events.
 */
public class ProcessInStoreSale {
  private final ItemRepository itemRepository;
  private final BillRepository billRepository;
  private final TransactionRepository transactionRepository;
  private final StockBatchRepository storeStockRepository;
  private final DiscountCalculator discountCalculator;
  private final EventPublisher eventPublisher;
  private final TransactionManager transactionManager;

  public ProcessInStoreSale(
      ItemRepository itemRepository,
      BillRepository billRepository,
      TransactionRepository transactionRepository,
      StockBatchRepository storeStockRepository,
      DiscountCalculator discountCalculator,
      EventPublisher eventPublisher,
      TransactionManager transactionManager) {
    if (itemRepository == null)
      throw new IllegalArgumentException("Item repository cannot be null");
    if (billRepository == null)
      throw new IllegalArgumentException("Bill repository cannot be null");
    if (transactionRepository == null)
      throw new IllegalArgumentException("Transaction repository cannot be null");
    if (storeStockRepository == null)
      throw new IllegalArgumentException("Store stock repository cannot be null");
    if (discountCalculator == null)
      throw new IllegalArgumentException("Discount calculator cannot be null");
    if (eventPublisher == null)
      throw new IllegalArgumentException("Event publisher cannot be null");
    if (transactionManager == null)
      throw new IllegalArgumentException("Transaction manager cannot be null");

    this.itemRepository = itemRepository;
    this.billRepository = billRepository;
    this.transactionRepository = transactionRepository;
    this.storeStockRepository = storeStockRepository;
    this.discountCalculator = discountCalculator;
    this.eventPublisher = eventPublisher;
    this.transactionManager = transactionManager;
  }

  /**
   * Executes an in-store sale within a single transaction boundary. The bill and transaction
   * records are persisted atomically. Before creating the bill, the store stock is validated to
   * ensure sufficient inventory exists (Scenario Brief stock integrity requirement). A {@link
   * SaleEvent} is published after the bill is saved to trigger downstream side effects such as
   * shelf stock reduction.
   *
   * @param itemsWithQuantities items and quantities purchased
   * @param cashTendered cash provided by the customer
   * @param date transaction date
   * @return the generated bill
   */
  public Bill execute(
      Map<ItemCode, Integer> itemsWithQuantities, Money cashTendered, LocalDate date) {
    if (itemsWithQuantities == null || itemsWithQuantities.isEmpty()) {
      throw new IllegalArgumentException("Items cannot be null or empty");
    }
    if (cashTendered == null) {
      throw new IllegalArgumentException("Cash tendered cannot be null");
    }
    if (date == null) {
      throw new IllegalArgumentException("Date cannot be null");
    }

    return transactionManager.executeInTransaction(
        () -> {
          List<BillItem> billItems = new ArrayList<>();
          Money fullPrice = Money.zero();

          for (Map.Entry<ItemCode, Integer> entry : itemsWithQuantities.entrySet()) {
            ItemCode itemCode = entry.getKey();
            int quantity = entry.getValue();

            Item item =
                itemRepository
                    .findByCode(itemCode)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemCode));

            int availableStoreStock = storeStockRepository.getTotalStock(itemCode);
            if (availableStoreStock < quantity) {
              throw new IllegalStateException(
                  "Insufficient store stock for item: "
                      + itemCode
                      + ". Available: "
                      + availableStoreStock
                      + ", Required: "
                      + quantity);
            }

            Money itemTotal = item.calculateTotal(quantity);
            fullPrice = fullPrice.add(itemTotal);

            billItems.add(
                new BillItem(itemCode, item.getName(), quantity, item.getUnitPrice(), itemTotal));
          }

          Money discount = discountCalculator.calculateDiscount(fullPrice);
          Money finalAmount = fullPrice.subtract(discount);
          Money change = cashTendered.subtract(finalAmount);

          if (change.isLessThan(Money.zero())) {
            throw new IllegalArgumentException(
                "Insufficient cash. Required: " + finalAmount + ", Provided: " + cashTendered);
          }

          int serialNumber = billRepository.getNextSerialNumber();
          Bill.Builder billBuilder =
              Bill.builder()
                  .serialNumber(serialNumber)
                  .date(date)
                  .type(TransactionType.IN_STORE)
                  .fullPrice(fullPrice)
                  .discount(discount)
                  .cashTendered(cashTendered)
                  .change(change)
                  .userId("CASHIER");

          for (BillItem item : billItems) {
            billBuilder.addItem(item);
          }

          Bill bill = billBuilder.build();

          billRepository.save(bill);

          eventPublisher.publish(new SaleEvent(bill));

          String transactionId = transactionRepository.nextTransactionId();
          Transaction transaction =
              new Transaction(
                  transactionId, serialNumber, date, TransactionType.IN_STORE, "CASHIER");
          transactionRepository.save(transaction);

          return bill;
        });
  }
}
