package com.ijse.eca.products.storage;

import java.io.InputStream;

public interface StorageClient {
    String put(String objectName, String contentType, InputStream inputStream);
}
