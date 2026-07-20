package com.example.freshtrack.di

import com.example.freshtrack.data.local.FreshTrackDatabase
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.data.repository.*
import com.example.freshtrack.domain.repository.*
import com.example.freshtrack.presentation.viewmodel.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for database dependencies
 * Provides DAOs and database instance
 */
val databaseModule = module {

    // Singleton Database Instance
    single { FreshTrackDatabase.getInstance(androidContext()) }

    // DAOs
    single { get<FreshTrackDatabase>().productDao() }
    single { get<FreshTrackDatabase>().categoryDao() }
}

/**
 * Koin module for repository dependencies
 * Provides repository implementations
 */
val repositoryModule = module {

    // Firebase Auth
    single { com.google.firebase.auth.FirebaseAuth.getInstance() }

    // Repositories
    single { com.example.freshtrack.data.session.UserSession(get()) }

    single<ProductRepository> {
        ProductRepositoryImpl(productDao = get(), session = get())
    }

    // Category Repository
    single<CategoryRepository> {
        CategoryRepositoryImpl(categoryDao = get())
    }

    // Auth Repository
    single<com.example.freshtrack.domain.repository.AuthRepository> {
        com.example.freshtrack.data.repository.FirebaseAuthRepositoryImpl(get())
    }

    // Product Lookup Repository
    single<ProductLookupRepository> {
        ProductLookupRepositoryImpl(api = get())
    }
}

/**
 * Koin module for network dependencies
 */
val networkModule = module {
    single {
        // Bodies are logged in debug only. On release this must stay NONE so that
        // request and response contents never reach logcat on a user's device.
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = if (com.example.freshtrack.BuildConfig.DEBUG) {
                okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            } else {
                okhttp3.logging.HttpLoggingInterceptor.Level.NONE
            }
        }
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        retrofit2.Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
    }

    single {
        get<retrofit2.Retrofit>().create(com.example.freshtrack.data.remote.OpenFoodFactsApi::class.java)
    }
}

/**
 * Koin module for preferences and settings
 * Provides SharedPreferences-based managers
 */
val preferencesModule = module {

    // Onboarding Preferences
    single { OnboardingPreferences(androidContext()) }
}

/**
 * Koin module for ViewModels
 * Provides ViewModels with injected dependencies
 */
val viewModelModule = module {

    // Dashboard ViewModel
    viewModel < DashboardViewModel>{
        DashboardViewModel(
            productRepository = get(),
            categoryRepository = get()
        )
    }

    // Product List ViewModel
    viewModel<ProductListViewModel> {
        ProductListViewModel(
            productRepository = get(),
            categoryRepository = get()
        )
    }

    // Add/Edit Product ViewModel
    viewModel<AddEditProductViewModel> {
        AddEditProductViewModel(
            productRepository = get(),
            categoryRepository = get(),
            productLookupRepository = get()
        )
    }

    // Product Details ViewModel
    viewModel<ProductDetailsViewModel> {
        ProductDetailsViewModel(
            productRepository = get()
        )
    }

    // Impact ViewModel
    viewModel<ImpactViewModel> {
        ImpactViewModel(
            productRepository = get()
        )
    }

    // Settings ViewModel
    viewModel <SettingsViewModel>{
        SettingsViewModel()
    }

    // History ViewModel
    viewModel<HistoryViewModel> {
        HistoryViewModel(
            productRepository = get()
        )
    }

    // Auth ViewModel
    viewModel<com.example.freshtrack.presentation.viewmodel.AuthViewModel> {
        com.example.freshtrack.presentation.viewmodel.AuthViewModel(
            authRepository = get()
        )
    }
}

/**
 * Combine all modules for app initialization
 */
val appModules = listOf(
    databaseModule,
    repositoryModule,
    preferencesModule,
    networkModule,
    viewModelModule
)