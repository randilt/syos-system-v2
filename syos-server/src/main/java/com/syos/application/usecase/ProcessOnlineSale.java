package com.syos.application.usecase;

import com.syos.application.transaction.TransactionManager;
import com.syos.domain.model.*;
import com.syos.domain.repository.*;
import com.syos.domain.service.DiscountCalculator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Use case: processes an online sale for a registered user with online stock deduction.
 */
public class ProcessOnlineSale {
  private final ItemRepository itemRepository;
  private final BillRepository billRepository;
  private final TransactionRepository transactionRepository;
  private final StockBatchRepository onlineStockRepository;
  private final UserRepository userRepository;
  private final DiscountCalculator discountCalculator;
  private final TransactionManager transactionManager;

  public ProcessOnlineSale(
      ItemRepository itemRepository,
      BillRepository billRepository,
      TransactionRepository transactionRepository,
      StockBatchRepository onlineStockRepository,
      UserRepository userRepository,
      DiscountCalculator discountCalculator,
      TransactionManager transactionManager) {
    if (itemRepository == null)
      throw new IllegalArgumentException("Item repository cannot be null");
    if (billRepository == null)
      throw new IllegalArgumentException("Bill repository cannot be null");
    if (transactionRepository == null)
      throw new IllegalArgumentException("Transaction repository cannot be null");
    if (onlineStockRepository == null)
      throw new IllegalArgumentException("Online stock repository cannot be null");
    if (userRepository == null)
      throw new IllegalArgumentException("User repository cannot be null");
    if (discountCalculator == null)
      throw new IllegalArgumentException("Discount calculator cannot be null");
    if (transactionManager == null)
      throw new IllegalArgumentException("Transaction manager cannot be null");

    this.itemRepository = itemRepository;
    this.billRepository = billRepository;
    this.transactionRepository = transactionRepository;
    this.onlineStockRepository = onlineStockRepository;
    this.userRepository = userRepository;
    this.discountCalculator = discountCalculator;
    this.transactionManager = transactionManager;
  }

  /** Executes the use case with the given inputs. */

  public Bill execute(String userId, Map<ItemCode, Integer> itemsWithQuantities, LocalDate date) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("User ID cannot be null or empty");
    }
    if (!userRepository.exists(userId)) {
      throw new IllegalArgumentException("User not found: " + userId);
    }
    if (itemsWithQuantities == null || itemsWithQuantities.isEmpty()) {
      throw new IllegalArgumentException("Items cannot be null or empty");
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

            List<StockBatch> batches = onlineStockRepository.findByItemCode(itemCode);
            int availableStock =
                batches.stream()
                    .filter(batch -> !batch.isExpired(date))
                    .mapToInt(StockBatch::getQuantity)
                    .sum();
            if (availableStock < quantity) {
              throw new IllegalStateException(
                  "Insufficient online stock for item: "
                      + itemCode
                      + ". Available: "
                      + availableStock
                      + ", Required: "
                      + quantity);
            }

            reduceOnlineStock(batches, itemCode, quantity, date);

            Money itemTotal = item.calculateTotal(quantity);
            fullPrice = fullPrice.add(itemTotal);

            billItems.add(
                new BillItem(itemCode, item.getName(), quantity, item.getUnitPrice(), itemTotal));
          }

          Money discount = discountCalculator.calculateDiscount(fullPrice);

          int serialNumber = billRepository.getNextSerialNumber();
          Bill.Builder billBuilder =
              Bill.builder()
                  .serialNumber(serialNumber)
                  .date(date)
                  .type(TransactionType.ONLINE)
                  .fullPrice(fullPrice)
                  .discount(discount)
                  .userId(userId);

          for (BillItem item : billItems) {
            billBuilder.addItem(item);
          }

          Bill bill = billBuilder.build();
          billRepository.save(bill);

          String transactionId = transactionRepository.nextTransactionId();
          Transaction transaction =
              new Transaction(transactionId, serialNumber, date, TransactionType.ONLINE, userId);
          transactionRepository.save(transaction);

          return bill;
        });
  }

  private void reduceOnlineStock(
      List<StockBatch> batches, ItemCode itemCode, int quantity, LocalDate currentDate) {
    int remaining = quantity;

    while (remaining > 0 && !batches.isEmpty()) {
      StockBatch batch =
          batches.stream()
              .filter(b -> b.getItemCode().equals(itemCode))
              .filter(b -> !b.isExpired(currentDate))
              .filter(StockBatch::hasQuantity)
              .min(
                  (b1, b2) -> {
                    int expiryCompare = b1.getExpiryDate().compareTo(b2.getExpiryDate());
                    if (expiryCompare != 0) return expiryCompare;
                    return b1.getPurchaseDate().compareTo(b2.getPurchaseDate());
                  })
              .orElseThrow(
                  () ->
                      new IllegalStateException("Insufficient online stock for item: " + itemCode));

      int toReduce = Math.min(remaining, batch.getQuantity());
      batch.reduceQuantity(toReduce);
      onlineStockRepository.save(batch);
      remaining -= toReduce;

      if (!batch.hasQuantity()) {
        batches.remove(batch);
      }
    }

    if (remaining > 0) {
      throw new IllegalStateException("Insufficient online stock for item: " + itemCode);
    }
  }
}
