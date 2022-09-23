@file:OptIn(ExperimentalMaterial3Api::class)

package de.digitalService.useID.ui.components.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.digitalService.useID.ui.theme.UseIDTheme

@Composable
fun PINEntryField(
    value: String,
    onValueChanged: (String) -> Unit,
    digitCount: Int,
    obfuscation: Boolean = false,
    spacerPosition: Int?,
    contentDescription: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    onDone: () -> Unit = { }
) {
    var textFieldValueState by remember(value) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    Box(
        modifier = modifier.background(backgroundColor)
    ) {
        BasicTextField(
            value = textFieldValueState,
            onValueChange = { newValue ->
                if (newValue.text.length <= digitCount) {
                    if (newValue.selection.length > 0) {
                        textFieldValueState = newValue.copy(selection = textFieldValueState.selection)
                    } else {
                        onValueChanged(newValue.text)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() }
            ),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .focusRequester(focusRequester)
                .clipToBounds()
                .testTag("PINEntryField")
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    stateDescription = value.replace(".".toRegex(), "$0 ")
                }
        )

        PINDigitRow(
            input = value,
            digitCount = digitCount,
            obfuscation = obfuscation,
            placeholder = false,
            spacerPosition = spacerPosition,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Preview
@Composable
fun PreviewPINEntryField() {
    UseIDTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            var text by remember { mutableStateOf("") }
            val focusRequester = remember {
                FocusRequester()
            }

            PINEntryField(
                text,
                onValueChanged = { text = it },
                digitCount = 6,
                obfuscation = false,
                spacerPosition = 3,
                contentDescription = "",
                focusRequester = focusRequester,
                modifier = Modifier.padding(64.dp)
            )
        }
    }
}
