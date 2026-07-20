package com.example.freshtrack.presentation.viewmodel

import com.example.freshtrack.MainDispatcherRule
import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ProductRepository
import com.example.freshtrack.domain.model.ProductInfo
import com.example.freshtrack.domain.repository.ProductLookupRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AddEditProductViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var productRepository: ProductRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var productLookupRepository: ProductLookupRepository
    private lateinit var viewModel: AddEditProductViewModel

    @Before
    fun setup() {
        productRepository = mockk(relaxed = true)
        categoryRepository = mockk(relaxed = true)
        productLookupRepository = mockk()

        // Mock category flow for init block
        coEvery { categoryRepository.getAllCategories() } returns flowOf(emptyList())

        viewModel = AddEditProductViewModel(
            productRepository,
            categoryRepository,
            productLookupRepository
        )
    }

    @Test
    fun `setBarcodeFromScanner updates barcode and looks up product successfully`() = runTest {
        // Arrange
        val barcode = "12345"
        val expectedProduct = ProductInfo(
            name = "Test Product",
            brand = null,
            imageUrl = "http://image.url",
            category = null
        )
        
        coEvery { productLookupRepository.getProductByBarcode(barcode) } returns Result.success(expectedProduct)

        // Act
        viewModel.setBarcodeFromScanner(barcode)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(barcode, state.barcode)
        assertEquals("Test Product", state.name)
        assertEquals("http://image.url", state.imageUri)
        assertFalse(state.isLookingUp)
    }

    @Test
    fun `setBarcodeFromScanner handles lookup failure gracefully`() = runTest {
        // Arrange
        val barcode = "12345"
        
        coEvery { productLookupRepository.getProductByBarcode(barcode) } returns Result.failure(Exception("Not found"))

        // Act
        viewModel.setBarcodeFromScanner(barcode)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(barcode, state.barcode)
        assertEquals("", state.name) // name shouldn't change
        assertEquals("Product not found or lookup failed", state.error)
        assertFalse(state.isLookingUp)
    }
}
