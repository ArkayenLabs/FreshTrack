package com.example.freshtrack.data.repository

import com.example.freshtrack.data.remote.OpenFoodFactsApi
import com.example.freshtrack.data.remote.dto.OpenFoodFactsResponse
import com.example.freshtrack.data.remote.dto.ProductDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductLookupRepositoryImplTest {

    private lateinit var api: OpenFoodFactsApi
    private lateinit var repository: ProductLookupRepositoryImpl

    @Before
    fun setup() {
        api = mockk()
        repository = ProductLookupRepositoryImpl(api)
    }

    @Test
    fun `getProductByBarcode returns ProductInfo when product is found`() = runTest {
        // Arrange
        val barcode = "123456789"
        val expectedName = "Test Product"
        val expectedImageUrl = "http://example.com/image.png"
        
        val mockResponse = OpenFoodFactsResponse(
            code = barcode,
            status = 1,
            product = ProductDto(
                productName = expectedName,
                brands = "Test Brand",
                imageUrl = expectedImageUrl,
                categories = null
            )
        )
        
        coEvery { api.getProductByBarcode(barcode) } returns mockResponse

        // Act
        val result = repository.getProductByBarcode(barcode)

        // Assert
        assertTrue(result.isSuccess)
        result.onSuccess { productInfo ->
            assertEquals(expectedName, productInfo.name)
            assertEquals(expectedImageUrl, productInfo.imageUrl)
        }

    }

    @Test
    fun `getProductByBarcode returns failure when product is not found`() = runTest {
        // Arrange
        val barcode = "123456789"
        
        val mockResponse = OpenFoodFactsResponse(
            code = barcode,
            status = 0,
            product = null
        )
        
        coEvery { api.getProductByBarcode(barcode) } returns mockResponse

        // Act
        val result = repository.getProductByBarcode(barcode)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertEquals("Product not found for barcode: $barcode", exception.message)
        }
    }

    @Test
    fun `getProductByBarcode returns failure on network error`() = runTest {
        // Arrange
        val barcode = "123456789"
        
        coEvery { api.getProductByBarcode(barcode) } throws RuntimeException("Network error")

        // Act
        val result = repository.getProductByBarcode(barcode)

        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assert(exception.message?.contains("Network error") == true)
        }
    }
}
