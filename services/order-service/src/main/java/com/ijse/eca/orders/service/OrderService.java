package com.ijse.eca.orders.service;

import com.ijse.eca.orders.api.dto.CreateOrderRequest;
import com.ijse.eca.orders.domain.Order;
import com.ijse.eca.orders.repo.OrderRepository;
import com.ijse.eca.orders.web.NotFoundException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order place(CreateOrderRequest request) {
        return orderRepository.save(new Order(request.userId(), request.productId(), request.quantity(), Instant.now()));
    }

    public List<Order> listByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public Order getById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    public void delete(String id) {
        if (orderRepository.findById(id).isEmpty()) {
            throw new NotFoundException("Order not found");
        }
        orderRepository.deleteById(id);
    }
}
