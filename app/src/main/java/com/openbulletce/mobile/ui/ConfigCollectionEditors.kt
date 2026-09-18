package com.openbulletce.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openbulletce.mobile.config.CustomInputSpec
import com.openbulletce.mobile.config.DataRuleSpec
import com.openbulletce.mobile.config.DesktopConfigCollections

@Composable
fun CustomInputsEditor(
    values: List<CustomInputSpec>,
    onChange: (List<CustomInputSpec>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Custom inputs", fontWeight = FontWeight.Bold)
            Button(onClick = {
                onChange(values + DesktopConfigCollections.newCustomInput(values))
            }) {
                Text("Add input")
            }
        }

        if (values.isEmpty()) {
            Text("No custom inputs")
        }

        values.forEachIndexed { index, item ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Input #${item.id}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = item.description,
                        onValueChange = { next ->
                            onChange(values.updated(index, item.copy(description = next)))
                        },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = item.variableName,
                        onValueChange = { next ->
                            onChange(values.updated(index, item.copy(variableName = next)))
                        },
                        label = { Text("Variable name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        onChange(values.filterIndexed { i, _ -> i != index })
                    }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
fun DataRulesEditor(
    values: List<DataRuleSpec>,
    onChange: (List<DataRuleSpec>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Data rules", fontWeight = FontWeight.Bold)
            Button(onClick = {
                onChange(values + DesktopConfigCollections.newDataRule(values))
            }) {
                Text("Add rule")
            }
        }

        if (values.isEmpty()) {
            Text("No data rules")
        }

        values.forEachIndexed { index, item ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Rule #${item.id}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = item.sliceName,
                        onValueChange = { next ->
                            onChange(values.updated(index, item.copy(sliceName = next)))
                        },
                        label = { Text("Slice name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        onChange(
                            values.updated(
                                index,
                                item.copy(ruleType = (item.ruleType + 1).mod(5))
                            )
                        )
                    }) {
                        Text("Rule type: ${item.ruleTypeLabel}")
                    }
                    OutlinedTextField(
                        value = item.ruleString,
                        onValueChange = { next ->
                            onChange(values.updated(index, item.copy(ruleString = next)))
                        },
                        label = { Text("Rule value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        onChange(values.filterIndexed { i, _ -> i != index })
                    }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }
