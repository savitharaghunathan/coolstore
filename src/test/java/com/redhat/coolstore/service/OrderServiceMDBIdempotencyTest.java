package com.redhat.coolstore.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
/** Task 4.6: duplicate (redelivered) order payloads must not double-persist (at-least-once Kafka). */
@ExtendWith(MockitoExtension.class)
public class OrderServiceMDBIdempotencyTest {
    @Mock OrderService orderService;
    @Mock CatalogService catalogService;
    @InjectMocks OrderServiceMDB mdb;
    @Test
    public void testDuplicatePayloadProcessedOnce() {
        String json = "{\"orderValue\":10.49,\"customerName\":\"Karl Svensson\",\"customerEmail\":\"karl@gmail.com\",\"items\":[{\"productSku\":\"329299\",\"quantity\":1}]}";
        mdb.onMessage(json);
        mdb.onMessage(json);
        verify(orderService, times(1)).save(any());
        verify(catalogService, times(1)).updateInventoryItems("329299", 1);
    }
}
