package com.redhat.coolstore.rest;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.ShoppingCartService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CartEndpointTest {

    @Mock
    private ShoppingCartService shoppingCartService;

    @InjectMocks
    private CartEndpoint endpoint;

    private ShoppingCart cart;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        cart = new ShoppingCart();
        when(shoppingCartService.getShoppingCart(anyString())).thenReturn(cart);
    }

    @Test
    public void testGetCart() {
        ShoppingCart result = endpoint.getCart("cart-1");
        assertNotNull(result);
        assertSame(cart, result);
    }

    @Test
    public void testAddItem() throws Exception {
        Product product = new Product();
        product.setItemId("329299");
        product.setPrice(34.99);
        when(shoppingCartService.getProduct("329299")).thenReturn(product);

        ShoppingCart result = endpoint.add("cart-1", "329299", 1);

        assertNotNull(result);
        assertEquals(1, result.getShoppingCartItemList().size());
        assertEquals("329299", result.getShoppingCartItemList().get(0).getProduct().getItemId());
        assertEquals(1, result.getShoppingCartItemList().get(0).getQuantity());
        verify(shoppingCartService).priceShoppingCart(cart);
    }

    @Test
    public void testDeleteItemRemovesFromCart() throws Exception {
        Product product = new Product();
        product.setItemId("329299");
        product.setPrice(34.99);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(product);
        sci.setQuantity(3);
        sci.setPrice(34.99);
        cart.addShoppingCartItem(sci);

        ShoppingCart result = endpoint.delete("cart-1", "329299", 3);

        assertEquals(0, result.getShoppingCartItemList().size());
        verify(shoppingCartService).priceShoppingCart(cart);
    }

    @Test
    public void testDeleteItemReducesQuantity() throws Exception {
        Product product = new Product();
        product.setItemId("329299");
        product.setPrice(34.99);

        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(product);
        sci.setQuantity(5);
        sci.setPrice(34.99);
        cart.addShoppingCartItem(sci);

        ShoppingCart result = endpoint.delete("cart-1", "329299", 2);

        assertEquals(1, result.getShoppingCartItemList().size());
        assertEquals(3, result.getShoppingCartItemList().get(0).getQuantity());
    }

    @Test
    public void testCheckout() {
        ShoppingCart checkedOut = new ShoppingCart();
        when(shoppingCartService.checkOutShoppingCart("cart-1")).thenReturn(checkedOut);

        ShoppingCart result = endpoint.checkout("cart-1");

        assertSame(checkedOut, result);
        verify(shoppingCartService).checkOutShoppingCart("cart-1");
    }
}
