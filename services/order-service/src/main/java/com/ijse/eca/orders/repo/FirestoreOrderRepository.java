package com.ijse.eca.orders.repo;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.Timestamp;
import com.ijse.eca.orders.config.FirestoreProperties;
import com.ijse.eca.orders.domain.Order;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class FirestoreOrderRepository implements OrderRepository {
    private final CollectionReference collection;

    public FirestoreOrderRepository(Firestore firestore, FirestoreProperties properties) {
        String collectionName = StringUtils.hasText(properties.ordersCollection())
                ? properties.ordersCollection()
                : "orders";
        this.collection = firestore.collection(collectionName);
    }

    @Override
    public Order save(Order order) {
        String id = order.getId();
        DocumentReference docRef = StringUtils.hasText(id) ? collection.document(id) : collection.document();
        order.setId(docRef.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", order.getUserId());
        data.put("productId", order.getProductId());
        data.put("quantity", order.getQuantity());
        Instant createdAt = order.getCreatedAt() == null ? Instant.now() : order.getCreatedAt();
        order.setCreatedAt(createdAt);
        data.put("createdAtEpochMs", createdAt.toEpochMilli());

        await(docRef.set(data));
        return order;
    }

    @Override
    public Optional<Order> findById(String id) {
        DocumentSnapshot doc = await(collection.document(id).get());
        if (!doc.exists()) {
            return Optional.empty();
        }
        return Optional.of(toOrder(doc));
    }

    @Override
    public void deleteById(String id) {
        await(collection.document(id).delete());
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        QuerySnapshot result = await(collection.whereEqualTo("userId", userId).get());
        List<Order> orders = new ArrayList<>(result.size());
        for (QueryDocumentSnapshot doc : result.getDocuments()) {
            orders.add(toOrder(doc));
        }
        return orders;
    }

    private static Order toOrder(DocumentSnapshot doc) {
        Order order = new Order();
        order.setId(doc.getId());
        order.setUserId(doc.getLong("userId"));
        order.setProductId(doc.getString("productId"));

        Long qty = doc.getLong("quantity");
        order.setQuantity(qty == null ? 0 : qty.intValue());

        Long epochMs = doc.getLong("createdAtEpochMs");
        if (epochMs != null) {
            order.setCreatedAt(Instant.ofEpochMilli(epochMs));
        } else {
            Timestamp ts = doc.getTimestamp("createdAt");
            if (ts != null) {
                order.setCreatedAt(Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()));
            }
        }
        return order;
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firestore operation interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Firestore operation failed", e);
        }
    }
}
