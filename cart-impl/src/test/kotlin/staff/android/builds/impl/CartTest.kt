package staff.android.builds.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import staff.android.builds.api.*

class CartTest {
    private fun seeded(quantity: Int = 3): CartRepository = createCart(setOf("tea"), 4, listOf(CartItem("tea", quantity)))
    @Test fun savedAddTwoPreservesAcceptedThree() = runBlocking {
        val cart = seeded(); val before = cart.state.value
        val result = cart.add("tea", 2) as CartEdit.Rejected
        assertEquals(CartRejection.MAXIMUM_EXCEEDED, result.reason)
        assertSame(before, result.snapshot); assertSame(before, cart.state.value)
    }
    @Test fun addOneCompletesAcceptedFour() = runBlocking {
        val cart = seeded(); val result = cart.add("tea", 1) as CartEdit.Accepted
        assertEquals(4, result.snapshot.quantityOf("tea")); assertEquals(1L, result.snapshot.revision)
        assertSame(result.snapshot, cart.state.value)
    }
    @Test fun bothEntryOperationsUseTheSameLimit() = runBlocking {
        val cart = seeded()
        assertEquals(CartRejection.MAXIMUM_EXCEEDED, (cart.setQuantity("tea", 5) as CartEdit.Rejected).reason)
        assertEquals(3, cart.state.value.quantityOf("tea"))
    }
    @Test fun zeroSetRemovesButZeroAddIsInvalid() = runBlocking {
        val cart = seeded()
        assertEquals(CartRejection.INVALID_QUANTITY, (cart.add("tea", 0) as CartEdit.Rejected).reason)
        val removed = cart.setQuantity("tea", 0) as CartEdit.Accepted
        assertTrue(removed.snapshot.lines.isEmpty()); assertEquals(1L, removed.snapshot.revision)
    }
    @Test fun negativeCommandsDoNotChangeAcceptedState() = runBlocking {
        val cart = seeded(); val before = cart.state.value
        for (result in listOf(cart.add("tea", -1), cart.setQuantity("tea", -1))) {
            assertEquals(CartRejection.INVALID_QUANTITY, (result as CartEdit.Rejected).reason)
            assertSame(before, result.snapshot)
        }
    }
    @Test fun additionCannotOverflowIntoAnAllowedQuantity() = runBlocking {
        val cart = seeded()
        assertEquals(CartRejection.MAXIMUM_EXCEEDED, (cart.add("tea", Int.MAX_VALUE) as CartEdit.Rejected).reason)
        assertEquals(3, cart.state.value.quantityOf("tea"))
    }
    @Test fun unknownProductsCannotEnterCart() = runBlocking {
        val cart = seeded()
        assertEquals(CartRejection.UNKNOWN_PRODUCT, (cart.add("camera", 1) as CartEdit.Rejected).reason)
        assertEquals(CartRejection.UNKNOWN_PRODUCT, (cart.setQuantity("camera", 0) as CartEdit.Rejected).reason)
        assertEquals(0, cart.state.value.quantityOf("camera"))
    }
    @Test fun acceptedNoOpDoesNotAdvanceRevision() = runBlocking {
        val cart = seeded(); val before = cart.state.value
        assertSame(before, (cart.setQuantity("tea", 3) as CartEdit.Accepted).snapshot)
        assertEquals(0L, cart.state.value.revision)
    }
    @Test fun initialListIsNotRetainedByReference() {
        val source = mutableListOf(CartItem("tea", 3))
        val cart = createCart(setOf("tea"), 4, source)
        source.clear(); assertEquals(3, cart.state.value.quantityOf("tea"))
    }
    @Test fun snapshotDoesNotExposeMutableBackingListOrFlow() {
        val cart = seeded()
        assertThrows(UnsupportedOperationException::class.java) { (cart.state.value.lines as MutableList).clear() }
        assertFalse(cart.state is MutableStateFlow<*>)
        assertEquals(3, cart.state.value.quantityOf("tea"))
    }
    @Test fun oldSnapshotKeepsItsValuesAfterLaterEdit() = runBlocking {
        val cart = seeded(); val before = cart.state.value
        cart.add("tea", 1)
        assertEquals(3, before.quantityOf("tea")); assertEquals(0L, before.revision)
        assertEquals(4, cart.state.value.quantityOf("tea"))
    }
    @Test fun competingAddsCannotBothSpendTheLastSlot() = runBlocking {
        val cart = seeded()
        val results = (1..2).map { async(Dispatchers.Default) { cart.add("tea", 1) } }.awaitAll()
        assertEquals(1, results.count { it is CartEdit.Accepted })
        assertEquals(1, results.count { it is CartEdit.Rejected })
        assertEquals(4, cart.state.value.quantityOf("tea")); assertEquals(1L, cart.state.value.revision)
    }
    @Test fun twoAvailableSlotsAreBothPreserved() = runBlocking {
        val cart = seeded(2)
        val results = (1..2).map { async(Dispatchers.Default) { cart.add("tea", 1) } }.awaitAll()
        assertTrue(results.all { it is CartEdit.Accepted })
        assertEquals(4, cart.state.value.quantityOf("tea")); assertEquals(2L, cart.state.value.revision)
    }
    @Test fun separateInstancesAreSeparateTruthEvenWithIdenticalTypes() = runBlocking {
        val savedInstance = seeded(); val cartInstance = seeded()
        savedInstance.add("tea", 1)
        assertEquals(4, savedInstance.state.value.quantityOf("tea"))
        assertEquals(3, cartInstance.state.value.quantityOf("tea"))
    }
    @Test fun fixtureRecreationReseedsRatherThanClaimingDurability() = runBlocking {
        val before = seeded(); before.add("tea", 1)
        val reopened = seeded()
        assertEquals(3, reopened.state.value.quantityOf("tea")); assertEquals(0L, reopened.state.value.revision)
    }
    @Test fun invalidStartingStatesFailBeforeARepositoryEscapes() {
        assertThrows(IllegalArgumentException::class.java) { createCart(setOf("tea"), 4, listOf(CartItem("tea", 5))) }
        assertThrows(IllegalArgumentException::class.java) { createCart(setOf("tea"), 4, listOf(CartItem("camera", 1))) }
        assertThrows(IllegalArgumentException::class.java) { createCart(setOf("tea"), 0, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CartSnapshot(listOf(CartItem("tea", 1), CartItem("tea", 2)), 0) }
    }
}
