package dev.tlong.traveler.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.model.Price
import dev.tlong.traveler.model.isCurrencyCode

/** The price typed into a booking dialog. Starts from [initial] (an estimate, a range's low end), so saving unchanged keeps it. */
class PriceInput(initial: Price?) {
    var amount by mutableStateOf(initial?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    var currency by mutableStateOf(initial?.currency ?: "USD")
    private val parsed get() = amount.replace(",", "").trim().toDoubleOrNull()
    private val code get() = currency.trim().uppercase()
    val currencyOk get() = isCurrencyCode(code)
    val ok get() = (amount.isBlank() || (parsed ?: -1.0) >= 0) && currencyOk
    val value: Price? get() = parsed?.takeIf { ok }?.let { Price(it, currency = code) }
}

@Composable
fun rememberPriceInput(initial: Price?) = remember { PriceInput(initial) }

@Composable
fun PriceField(p: PriceInput, label: String = "Price paid (optional)") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            p.amount, { p.amount = it }, label = { Text(label) }, singleLine = true, isError = !p.ok && p.amount.isNotBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            p.currency, { p.currency = it.take(3).uppercase() }, label = { Text("Currency") }, singleLine = true,
            isError = !p.currencyOk, modifier = Modifier.width(110.dp),
        )
    }
}
