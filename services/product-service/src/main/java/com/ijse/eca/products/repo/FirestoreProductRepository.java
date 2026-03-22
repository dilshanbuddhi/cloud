package com.ijse.eca.products.repo;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.ijse.eca.products.config.FirestoreProperties;
import com.ijse.eca.products.domain.Product;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class FirestoreProductRepository implements ProductRepository {
    private final CollectionReference collection;

    public FirestoreProductRepository(Firestore firestore, FirestoreProperties properties) {
        String collectionName = StringUtils.hasText(properties.productsCollection())
                ? properties.productsCollection()
                : "products";
        this.collection = firestore.collection(collectionName);
    }

    @Override
    public Product save(Product product) {
        String id = product.getId();
        DocumentReference docRef = StringUtils.hasText(id) ? collection.document(id) : collection.document();
        product.setId(docRef.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("name", product.getName());
        data.put("price", product.getPrice());
        data.put("imageUrl", product.getImageUrl());

        await(docRef.set(data));
        return product;
    }

    @Override
    public List<Product> findAll() {
        List<QueryDocumentSnapshot> docs = await(collection.get()).getDocuments();
        List<Product> products = new ArrayList<>(docs.size());
        for (QueryDocumentSnapshot doc : docs) {
            products.add(toProduct(doc));
        }
        return products;
    }

    @Override
    public Optional<Product> findById(String id) {
        DocumentSnapshot doc = await(collection.document(id).get());
        if (!doc.exists()) {
            return Optional.empty();
        }
        return Optional.of(toProduct(doc));
    }

    @Override
    public void deleteById(String id) {
        await(collection.document(id).delete());
    }

    private static Product toProduct(DocumentSnapshot doc) {
        Product product = new Product();
        product.setId(doc.getId());
        product.setName(doc.getString("name"));
        Number price = doc.getDouble("price");
        product.setPrice(price == null ? 0.0 : price.doubleValue());
        product.setImageUrl(doc.getString("imageUrl"));
        return product;
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
