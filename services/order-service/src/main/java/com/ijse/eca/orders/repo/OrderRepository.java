package com.ijse.eca.orders.repo;

import com.ijse.eca.orders.domain.Order;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    List<Order> findByUserId(Long userId);

    Optional<Order> findById(String id);

    void deleteById(String id);
}
