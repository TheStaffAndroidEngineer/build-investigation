package staff.android.builds.cartui

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

class CartOwner(private val cart: CartRepository) : ViewModel() {
    val cartState = cart.state
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()

    fun setTea(quantity: Int) {
        viewModelScope.launch {
            try {
                val result = cart.setQuantity("tea", quantity)
                mutableMessage.value =
                    when (result) {
                        is CartEdit.Accepted ->
                            "Cart accepted ${result.snapshot.quantityOf("tea")}."
                        is CartEdit.Rejected ->
                            "Not changed: ${result.message} " +
                                "Cart remains ${result.snapshot.quantityOf("tea")}."
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableMessage.value =
                    "Cart unavailable. " +
                        "Read its current contents before retrying."
            }
        }
    }
}

@Composable
fun CartScreen(owner: CartOwner) {
    val state by owner.cartState.collectAsStateWithLifecycle()
    val message by owner.message.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("3") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cart", style = MaterialTheme.typography.headlineMedium)
        TeaPhoto()
        Text("Tea in Cart: ${state.quantityOf("tea")}")

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Tea quantity") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { owner.setTea(draft.toInt()) },
            enabled = draft.toIntOrNull() != null,
        ) {
            Text("Apply quantity")
        }
        if (message.isNotBlank()) Text(message)
        var diagnostics by remember { mutableStateOf(false) }
        TextButton(onClick = { diagnostics = !diagnostics }) { Text(if (diagnostics) "Close cart details" else "Cart details") }
        if (diagnostics) Text("Accepted revision: ${state.revision}")
    }
}
