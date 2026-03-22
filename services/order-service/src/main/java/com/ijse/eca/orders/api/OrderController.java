package com.ijse.eca.orders.api;

import com.ijse.eca.orders.api.dto.CreateOrderRequest;
import com.ijse.eca.orders.api.dto.OrderResponse;
import com.ijse.eca.orders.domain.Order;
import com.ijse.eca.orders.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody CreateOrderRequest request) {
        return toResponse(orderService.place(request));
    }

    @GetMapping("/orders")
    public List<OrderResponse> list(@RequestParam("userId") Long userId) {
        return orderService.listByUserId(userId).stream().map(OrderController::toResponse).toList();
    }

    @GetMapping("/orders/{id}")
    public OrderResponse getById(@PathVariable("id") String id) {
        return toResponse(orderService.getById(id));
    }

    @DeleteMapping("/orders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        orderService.delete(id);
    }

    private static OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getUserId(), o.getProductId(), o.getQuantity(), o.getCreatedAt());
    }
}
