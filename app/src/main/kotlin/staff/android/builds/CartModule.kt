package staff.android.builds

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import staff.android.builds.api.CartItem
import staff.android.builds.api.CartRepository
import staff.android.builds.impl.createCart

@Module
@InstallIn(SingletonComponent::class)
object CartModule {
    @Provides
    @Singleton
    fun cart(): CartRepository = createCart(
        products = setOf("tea"),
        maximumPerProduct = 4,
        initial = listOf(CartItem("tea", 3)),
    )
}
