package staff.android.builds.savedui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import staff.android.builds.api.CartEdit
import staff.android.builds.api.CartRepository

class SavedOwner(private val cart: CartRepository) : ViewModel() {
    val cartState = cart.state
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()

    fun addTea(amount: Int) {
        viewModelScope.launch {
            try {
                val result = cart.add("tea", amount)
                mutableMessage.value =
                    when (result) {
                        is CartEdit.Accepted ->
                            "Added $amount. " +
                                "Cart now ${result.snapshot.quantityOf("tea")}."
                        is CartEdit.Rejected ->
                            "Not added: ${result.message} " +
                                "Cart remains ${result.snapshot.quantityOf("tea")}."
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableMessage.value =
                    "Cart unavailable. " +
                        "Check its current contents before retrying."
            }
        }
    }
}

@Composable
fun SavedScreen(owner: SavedOwner) {
    val state by owner.cartState.collectAsStateWithLifecycle()
    val message by owner.message.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Saved items",
            style = MaterialTheme.typography.headlineMedium,
        )
        TeaPhoto()
        Text("Tea is saved for later. Saving does not add it to Cart.")
        Text("Tea in Cart: ${state.quantityOf("tea")}")
        Button(
            onClick = { owner.addTea(2) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add 2 Tea to Cart")
        }
        OutlinedButton(
            onClick = { owner.addTea(1) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add 1 Tea to Cart")
        }
        if (message.isNotBlank()) Text(message)
    }
}
