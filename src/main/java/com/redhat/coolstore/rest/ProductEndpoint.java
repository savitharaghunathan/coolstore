package com.redhat.coolstore.rest;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.service.ProductService;

@ApplicationScoped
@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(ProductEndpoint.class);

    @Inject
    private ProductService productService;


    @GET
    @Path("")
    public List<Product> listAll() {
        logger.debug("Fetching all products");
        return productService.getProducts();
    }

    @GET
    @Path("/{itemId}")
    public Response getProduct(@PathParam("itemId") String itemId) {
        logger.debug("Fetching product with itemId: {}", itemId);
        Product product = productService.getProductByItemId(itemId);
        if (product == null) {
            logger.warn("Product not found for itemId: {}", itemId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(product).build();
    }

}
