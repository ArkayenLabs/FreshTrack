package com.example.freshtrack.di

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

    // The GoodBefore store. Source of truth for everything on screen.
    single { com.example.freshtrack.data.local.GoodBeforeDatabase.getInstance(androidContext()) }

    single { get<com.example.freshtrack.data.local.GoodBeforeDatabase>().itemDao() }
    single { get<com.example.freshtrack.data.local.GoodBeforeDatabase>().categoryDao() }
    single { get<com.example.freshtrack.data.local.GoodBeforeDatabase>().locationDao() }
    single { get<com.example.freshtrack.data.local.GoodBeforeDatabase>().itemEventDao() }
    single { get<com.example.freshtrack.data.local.GoodBeforeDatabase>().outboxDao() }

    single<com.example.freshtrack.data.local.TransactionRunner> {
        com.example.freshtrack.data.local.RoomTransactionRunner(get())
    }
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
    // Bound under the interface too. Anything that only needs "which kitchen"
    // asks for KitchenSession, and Koin resolves by declared type, so without
    // this the lookup fails at runtime rather than at compile time.
    single<com.example.freshtrack.data.session.KitchenSession> {
        get<com.example.freshtrack.data.session.UserSession>()
    }

    // Firestore sync
    single { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    single<com.example.freshtrack.data.sync.RemoteProductStore> {
        com.example.freshtrack.data.remote.firestore.RemoteProductDataSource(get())
    }
    single {
        com.example.freshtrack.data.account.AccountDeleter(
            authRepository = get(),
            remote = get(),
            itemDao = get(),
            eventDao = get(),
            outboxDao = get(),
            session = get(),
            syncPrefs = get(),
            onboardingPrefs = get()
        )
    }

    single<ItemRepository> {
        ItemRepositoryImpl(
            itemDao = get(),
            eventDao = get(),
            outboxDao = get(),
            session = get(),
            transactions = get(),
            clock = com.example.freshtrack.util.AppClock.System,
            ids = com.example.freshtrack.util.IdGenerator.Uuid,
            clientId = get<com.example.freshtrack.data.preferences.ClientIdProvider>().clientId,
            serialise = com.example.freshtrack.data.sync.OutboxPayload::serialise
        )
    }

    single<LocationRepository> { LocationRepositoryImpl(locationDao = get(), session = get()) }

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

    // Analytics consent
    single { com.example.freshtrack.data.preferences.ConsentPreferences(androidContext()) }

    // Sync watermarks
    single { com.example.freshtrack.data.preferences.SyncPreferences(androidContext()) }

    // Stable per-installation id, used to attribute queued changes
    single { com.example.freshtrack.data.preferences.ClientIdProvider(androidContext()) }
}

/**
 * Koin module for ViewModels
 * Provides ViewModels with injected dependencies
 */
val viewModelModule = module {

    viewModel<TodayViewModel> {
        TodayViewModel(itemRepository = get())
    }

    viewModel<ItemListViewModel> {
        ItemListViewModel(itemRepository = get(), categoryRepository = get())
    }

    viewModel<AddEditItemViewModel> {
        AddEditItemViewModel(
            itemRepository = get(),
            categoryRepository = get(),
            locationRepository = get(),
            productLookupRepository = get()
        )
    }

    viewModel<ItemDetailsViewModel> {
        ItemDetailsViewModel(itemRepository = get())
    }

    viewModel<ReceiptReviewViewModel> {
        ReceiptReviewViewModel(
            itemRepository = get(),
            categoryRepository = get(),
            locationRepository = get()
        )
    }

    viewModel<ImpactViewModel> {
        ImpactViewModel(itemRepository = get())
    }

    // Settings ViewModel
    viewModel <SettingsViewModel>{
        SettingsViewModel()
    }

    viewModel<HistoryViewModel> {
        HistoryViewModel(itemRepository = get())
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