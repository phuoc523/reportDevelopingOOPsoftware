package com.scentstation.catalog.config;

import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CatalogSampleDataLoaderTest {

    private CatalogSampleDataProperties properties;
    private ProductRepository productRepository;
    private CampaignRepository campaignRepository;
    private CatalogSampleDataLoader loader;

    @BeforeEach
    void setUp() {
        properties = new CatalogSampleDataProperties();
        productRepository = mock(ProductRepository.class);
        campaignRepository = mock(CampaignRepository.class);

        loader = new CatalogSampleDataLoader(
                properties,
                productRepository,
                campaignRepository
        );
    }

    @Test
    void run_shouldDoNothingWhenSampleDataDisabled() {
        properties.setEnabled(false);

        loader.run();

        verifyNoInteractions(
                productRepository,
                campaignRepository
        );
    }

    @Test
    void run_shouldDoNothingWhenProductsAlreadyExist() {
        properties.setEnabled(true);

        when(productRepository.count())
                .thenReturn(3L);

        loader.run();

        verify(productRepository).count();
        verify(productRepository, never())
                .save(any());

        verifyNoInteractions(campaignRepository);
    }

    @Test
    void run_shouldCreateProductsAndCampaignsWhenEnabledAndDatabaseEmpty() {
        properties.setEnabled(true);

        when(productRepository.count())
                .thenReturn(0L);

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product product =
                            invocation.getArgument(0);

                    if ("Rose Garden".equals(product.getName())) {
                        product.setId(1L);
                    } else if ("Citrus Fresh".equals(product.getName())) {
                        product.setId(2L);
                    } else {
                        product.setId(3L);
                    }

                    return product;
                });

        loader.run();

        verify(productRepository, times(3))
                .save(any(Product.class));

        verify(campaignRepository, times(2))
                .save(any(Campaign.class));

        ArgumentCaptor<Campaign> campaignCaptor =
                ArgumentCaptor.forClass(Campaign.class);

        verify(campaignRepository, times(2))
                .save(campaignCaptor.capture());

        List<Campaign> campaigns =
                campaignCaptor.getAllValues();

        assertEquals(
                1L,
                campaigns.get(0).getProductId()
        );

        assertEquals(
                2L,
                campaigns.get(1).getProductId()
        );
    }
}