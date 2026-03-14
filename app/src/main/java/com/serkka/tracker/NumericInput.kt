@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Float = 1f,
    imeAction: ImeAction = ImeAction.Default,
    onNext: (() -> Unit)? = null
) {
    val isInteger = step % 1 == 0f

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (!isInteger || (!newValue.contains(".") && !newValue.contains(","))) {
                onValueChange(newValue)
            }
        },
        label = {
            Text(text = label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onNext?.invoke() }
        ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 14.sp),
        leadingIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(
                    onClick = {
                        val current = value.toLeadFloat() ?: 0f
                        if (current >= step) {
                            val next = if (isInteger) (current.toInt() - step.toInt()).toFloat()
                                       else current - step
                            onValueChange(formatWeight(next))
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingIcon = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(
                    onClick = {
                        val current = value.toLeadFloat() ?: 0f
                        val next = if (isInteger) (current.toInt() + step.toInt()).toFloat()
                                   else current + step
                        onValueChange(formatWeight(next))
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(20.dp))
                }
            }
        }
    )
}
