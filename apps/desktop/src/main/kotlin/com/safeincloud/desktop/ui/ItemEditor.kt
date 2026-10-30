package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.safeincloud.core.model.Field
import com.safeincloud.core.model.FieldType
import com.safeincloud.core.security.GeneratorOptions
import com.safeincloud.core.security.PasswordGenerator
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopUiState

private val fieldTypes = listOf(
    FieldType.TEXT to "文本",
    FieldType.PASSWORD to "密码",
    FieldType.SECRET to "秘密",
    FieldType.MULTILINE to "多行文本",
    FieldType.URL to "网址",
    FieldType.EMAIL to "邮箱",
    FieldType.PHONE to "电话"
)

private fun typeLabel(type: FieldType): String = fieldTypes.firstOrNull { it.first == type }?.second ?: "文本"

@Composable
fun ItemEditor(state: DesktopUiState, viewModel: AppViewModel) {
    val item = state.editingItem ?: return
    var generatorFor by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            if (state.creatingItem) "新建条目" else "编辑条目",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f)) {
            item {
                OutlinedTextField(
                    value = item.title,
                    onValueChange = { value -> viewModel.updateEditing { it.title = value } },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }
            itemsIndexed(item.fields) { index, field ->
                FieldEditorRow(
                    field = field,
                    onLabel = { value -> viewModel.updateEditing { it.fields[index].label = value } },
                    onValue = { value -> viewModel.updateEditing { it.fields[index].value = value } },
                    onType = { type -> viewModel.updateEditing { it.fields[index].type = type; it.fields[index].sensitive = type == FieldType.PASSWORD || type == FieldType.SECRET } },
                    onRemove = { viewModel.updateEditing { if (it.fields.size > 1) it.fields.removeAt(index) } },
                    onGenerate = { generatorFor = index }
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    TextButton(onClick = { viewModel.updateEditing { it.fields.add(Field(label = "新字段")) } }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加字段")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = item.notes,
                    onValueChange = { value -> viewModel.updateEditing { it.notes = value } },
                    label = { Text("备注") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveEditing() }) { Text("保存") }
            OutlinedButton(onClick = { viewModel.cancelEdit() }) { Text("取消") }
        }
    }

    generatorFor?.let { index ->
        GeneratorDialog(
            onDismiss = { generatorFor = null },
            onUse = { password ->
                viewModel.updateEditing { if (index in it.fields.indices) it.fields[index].value = password }
                generatorFor = null
            }
        )
    }
}

@Composable
private fun FieldEditorRow(
    field: Field,
    onLabel: (String) -> Unit,
    onValue: (String) -> Unit,
    onType: (FieldType) -> Unit,
    onRemove: () -> Unit,
    onGenerate: () -> Unit
) {
    var typeMenu by remember { mutableStateOf(false) }
    val sensitive = field.type == FieldType.PASSWORD || field.type == FieldType.SECRET

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = field.label,
                onValueChange = onLabel,
                label = { Text("字段名") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )
            OutlinedButton(onClick = { typeMenu = true }) {
                Text(typeLabel(field.type))
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                fieldTypes.forEach { (type, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onType(type); typeMenu = false })
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除字段", tint = MaterialTheme.colorScheme.error)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = field.value,
                onValueChange = onValue,
                label = { Text("值") },
                singleLine = field.type != FieldType.MULTILINE,
                minLines = if (field.type == FieldType.MULTILINE) 3 else 1,
                visualTransformation = if (sensitive) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.weight(1f)
            )
            if (field.type == FieldType.PASSWORD) {
                IconButton(onClick = onGenerate) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "生成密码", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var length by remember { mutableStateOf(20) }
    var lowercase by remember { mutableStateOf(true) }
    var uppercase by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var passphraseMode by remember { mutableStateOf(false) }
    var result by remember {
        mutableStateOf(
            PasswordGenerator.generate(GeneratorOptions(length = length, lowercase = lowercase, uppercase = uppercase, digits = digits, symbols = symbols))
        )
    }

    fun regenerate() {
        result = if (passphraseMode) {
            PasswordGenerator.passphrase()
        } else {
            PasswordGenerator.generate(GeneratorOptions(length = length, lowercase = lowercase, uppercase = uppercase, digits = digits, symbols = symbols))
        }
    }

    PanelDialog(title = "密码生成器", onDismiss = onDismiss) {
        OutlinedTextField(
            value = result,
            onValueChange = { result = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true
        )
        if (!passphraseMode) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("长度：$length", modifier = Modifier.width(80.dp))
                androidx.compose.material3.Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt(); regenerate() },
                    valueRange = 8f..64f,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilterChipLike("小写", lowercase) { lowercase = it; regenerate() }
                FilterChipLike("大写", uppercase) { uppercase = it; regenerate() }
                FilterChipLike("数字", digits) { digits = it; regenerate() }
                FilterChipLike("符号", symbols) { symbols = it; regenerate() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            OutlinedButton(onClick = { passphraseMode = !passphraseMode; regenerate() }) {
                Text(if (passphraseMode) "改用随机密码" else "改用口令模式")
            }
            OutlinedButton(onClick = { regenerate() }) { Text("换一个") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onUse(result) }) { Text("使用") }
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        }
    }
}

@Composable
private fun FilterChipLike(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (checked) {
        Button(onClick = { onChange(false) }) { Text(label) }
    } else {
        OutlinedButton(onClick = { onChange(true) }) { Text(label) }
    }
}
