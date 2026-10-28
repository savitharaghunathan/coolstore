package com.redhat.coolstore.service;
import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.StreamableAuditLogger;
import com.redhat.coolstore.model.Order;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
/** Task 2.6: OrderService emits AuditEvent records via logEventAsync (rules 0002/0004/0005). */
@ExtendWith(MockitoExtension.class)
public class OrderServiceAuditTest {
    @Mock EntityManager em;
    @Mock StreamableAuditLogger auditLogger;
    @InjectMocks OrderService orderService;
    @Test
    public void testSaveEmitsAuditEventAsync() {
        Order order = new Order();
        order.setCustomerEmail("karl@gmail.com");
        order.setOrderValue(34.99);
        orderService.save(order);
        verify(em).persist(order);
        verify(auditLogger).logEventAsync(any(AuditEvent.class));
    }
}
