package com.scentstation.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.exception.GlobalExceptionHandler;
import com.scentstation.catalog.exception.InvalidProductSearchCriteriaException;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.scentstation.catalog.dto.request.UpdateProductAvailabilityRequest;
import com.scentstation.catalog.dto.request.UpdateProductPriceRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.scentstation.catalog.dto.response.ProductResponse;
import com.scentstation.catalog.exception.InvalidProductSearchCriteriaException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    void shouldCreateProduct() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .tenantId(1L)
                .name("Scent Sample")
                .description("Sample perfume")
                .fragranceNotes("Citrus, Woody")
                .imageUrl("https://example.com/sample.jpg")
                .defaultPrice(new BigDecimal("20000"))
                .available(true)
                .build();

        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .tenantId(1L)
                .name("Scent Sample")
                .description("Sample perfume")
                .fragranceNotes("Citrus, Woody")
                .imageUrl("https://example.com/sample.jpg")
                .defaultPrice(new BigDecimal("20000"))
                .available(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Scent Sample"))
                .andExpect(jsonPath("$.defaultPrice").value(20000))
                .andExpect(jsonPath("$.available").value(true));
    }

        @Test
        void shouldUpdateProductPrice() throws Exception {
        UpdateProductPriceRequest request =
                UpdateProductPriceRequest.builder()
                        .defaultPrice(new BigDecimal("25000"))
                        .build();

        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .tenantId(1L)
                .name("Scent Sample")
                .defaultPrice(new BigDecimal("25000"))
                .available(true)
                .build();

        when(productService.updatePrice(
                any(Long.class),
                any(UpdateProductPriceRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        patch("/api/products/1/price")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.defaultPrice").value(25000));
        }

        @Test
        void shouldReturn400WhenPriceIsNegative() throws Exception {
        UpdateProductPriceRequest request =
                UpdateProductPriceRequest.builder()
                        .defaultPrice(new BigDecimal("-1000"))
                        .build();

        mockMvc.perform(
                        patch("/api/products/1/price")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void shouldReturn400WhenPriceIsMissing() throws Exception {
        String requestBody = """
                {}
                """;

        mockMvc.perform(
                        patch("/api/products/1/price")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void searchProducts_shouldRejectMissingTenantId()
                throws Exception {

        mockMvc.perform(
                        get("/api/products/search")
                                .param("keyword", "rose")
                )
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldChangeProductAvailability() throws Exception {
        UpdateProductAvailabilityRequest request =
                UpdateProductAvailabilityRequest.builder()
                        .available(false)
                        .build();

        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .tenantId(1L)
                .name("Scent Sample")
                .defaultPrice(new BigDecimal("20000"))
                .available(false)
                .build();

        when(productService.changeAvailability(
                any(Long.class),
                any(UpdateProductAvailabilityRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        patch("/api/products/1/availability")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.available").value(false));
        }

        @Test
        void shouldReturn400WhenAvailabilityIsMissing() throws Exception {
        String requestBody = """
                {}
                """;

        mockMvc.perform(
                        patch("/api/products/1/availability")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

    @Test
    void shouldReturnAllProducts() throws Exception {
        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .tenantId(1L)
                .name("Scent Sample")
                .defaultPrice(new BigDecimal("20000"))
                .available(true)
                .build();

        when(productService.getProducts())
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Scent Sample"));
    }

    @Test
    void shouldReturnProductById() throws Exception {
        ProductResponse response = ProductResponse.builder()
                .id(1L)
                .tenantId(1L)
                .name("Scent Sample")
                .defaultPrice(new BigDecimal("20000"))
                .available(true)
                .build();

        when(productService.getProductById(1L))
                .thenReturn(response);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Scent Sample"));
    }

    @Test
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(productService.getProductById(999L))
                .thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Product not found with id: 999"));
    }

    @Test
    void shouldReturn400WhenCreateProductIsInvalid() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .tenantId(1L)
                .name("")
                .defaultPrice(new BigDecimal("-100"))
                .available(true)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

        @Test
        void searchProducts_shouldReturnFilteredProducts()
                throws Exception {

        ProductResponse response =
                ProductResponse.builder()
                        .id(10L)
                        .tenantId(1L)
                        .name("Rose Garden")
                        .defaultPrice(
                                new BigDecimal("200000.00")
                        )
                        .available(true)
                        .build();

        when(productService.searchProducts(any()))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/products/search")
                                .param("tenantId", "1")
                                .param("keyword", "rose")
                                .param("available", "true")
                                .param(
                                        "minPrice",
                                        "100000"
                                )
                                .param(
                                        "maxPrice",
                                        "300000"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[0].id")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$[0].name")
                                .value("Rose Garden")
                )
                .andExpect(
                        jsonPath("$[0].available")
                                .value(true)
                );
        }

        @Test
        void searchProducts_shouldReturnBadRequestForInvalidPriceRange()
                throws Exception {

        when(productService.searchProducts(any()))
                .thenThrow(
                        new InvalidProductSearchCriteriaException(
                                "Minimum price must not exceed maximum price"
                        )
                );

        mockMvc.perform(
                        get("/api/products/search")
                                .param("tenantId", "1")
                                .param(
                                        "minPrice",
                                        "500000"
                                )
                                .param(
                                        "maxPrice",
                                        "100000"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Minimum price must not exceed maximum price"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/api/products/search"
                                )
                );
        }
}