package staff.android.builds

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundaryJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetAcceptedQuantity() {
        runBlocking { compose.activity.cart.setQuantity("tea", 3) }
        compose.waitForIdle()
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun quantity(value: Int) = compose.onNodeWithText("Tea in Cart: $value").assertExists()
    private fun set(value: String) {
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(value)
        tap("Apply quantity")
    }

    @Test fun savedAddTwoRejectsWithoutPublishingFive() {
        tap("Add 2 Tea to Cart")
        quantity(3)
        compose.onNodeWithText("Not added: Maximum quantity reached. Cart remains 3.").assertExists()
    }

    @Test fun acceptedSavedAdditionIsVisibleInCart() {
        tap("Add 1 Tea to Cart")
        quantity(4)
        tap("Open Cart")
        quantity(4)
    }

    @Test fun cartRejectionPreservesSavedQuantity() {
        tap("Open Cart")
        set("5")
        quantity(3)
        compose.onNodeWithText("Not changed: Maximum quantity reached. Cart remains 3.").assertExists()
        tap("Open Saved items")
        quantity(3)
    }

    @Test fun cartChangeBecomesTheBasisOfSavedAddition() {
        tap("Open Cart")
        set("2")
        quantity(2)
        tap("Open Saved items")
        tap("Add 2 Tea to Cart")
        quantity(4)
    }

    @Test fun activityRecreationKeepsTheApplicationOwnedCart() {
        tap("Add 1 Tea to Cart")
        tap("Open Cart")
        compose.activityRule.scenario.recreate()
        quantity(4)
        compose.onNodeWithText("Tea quantity").assertExists()
    }

    @Test fun repeatedAdditionCannotExceedTheLimit() {
        tap("Add 1 Tea to Cart")
        tap("Add 1 Tea to Cart")
        quantity(4)
        compose.onNodeWithText("Not added: Maximum quantity reached. Cart remains 4.").assertExists()
    }

    @Test fun removingTheLineAllowsANewAddition() {
        tap("Open Cart")
        set("0")
        quantity(0)
        tap("Open Saved items")
        tap("Add 2 Tea to Cart")
        quantity(2)
    }

    @Test fun invalidDraftDoesNotBecomeAcceptedState() {
        tap("Open Cart")
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Apply quantity").assertIsNotEnabled()
        quantity(3)
        compose.onNode(hasSetTextAction()).performTextReplacement("not a number")
        compose.onNodeWithText("Apply quantity").assertIsNotEnabled()
        quantity(3)
    }
}
