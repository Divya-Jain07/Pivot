package com.pivot.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivot.agent.repositories.MerchantRepository;
import com.pivot.agent.repositories.ProductRepository;
import com.pivot.agent.seed.DatabaseSeeder;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DatabaseSeederTests {

    @Test
    void shouldNotDeleteExistingCatalogOnStartupWhenReloadFlagIsOff() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(productRepository.count()).thenReturn(2L);
        when(merchantRepository.count()).thenReturn(1L);

        DatabaseSeeder seeder = new DatabaseSeeder(productRepository, merchantRepository, objectMapper);
        seeder.run();

        verify(productRepository, never()).deleteAll();
        verify(merchantRepository, never()).deleteAll();
    }
}
