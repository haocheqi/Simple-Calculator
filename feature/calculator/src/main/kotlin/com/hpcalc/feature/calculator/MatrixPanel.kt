package com.hpcalc.feature.calculator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hpcalc.core.designsystem.CalcTokens

/**
 * 矩阵面板。
 *
 * 左矩阵的行列各自可调，右矩阵的形状由运算种类推导（加减同形、乘法由 A 的列数决定 B 的行数），
 * 所以只暴露「右矩阵列数」一个额外旋钮。这样界面不会允许用户拼出无法计算的组合。
 */
@Composable
fun MatrixPanel(
    matrix: MatrixUiState,
    onEvent: (CalculatorUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CalcTokens.SectionSpacing)
    ) {
        DimensionsRow(matrix = matrix, onEvent = onEvent)
        OperationSelector(selected = matrix.operation, onSelect = {
            onEvent(CalculatorUiEvent.SetMatrixOperation(it))
        })

        SectionLabel(
            stringResource(R.string.matrix_a) +
                "  " + stringResource(R.string.matrix_dimension, matrix.rows, matrix.columns)
        )
        MatrixGrid(cells = matrix.left, side = MatrixSide.LEFT, onEvent = onEvent)

        if (matrix.operation.needsRightOperand) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(
                    stringResource(R.string.matrix_b) +
                        "  " + stringResource(
                            R.string.matrix_dimension,
                            matrix.rightRows,
                            matrix.rightColumnCount
                        )
                )
                Spacer(Modifier.weight(1f))
                // 乘法才需要用户选 B 的列数；加减由 A 的形状决定。
                if (matrix.operation == MatrixOperationKind.MULTIPLY) {
                    Stepper(
                        label = stringResource(R.string.matrix_columns),
                        value = matrix.rightColumns,
                        onChange = { onEvent(CalculatorUiEvent.SetMatrixRightColumns(it)) }
                    )
                }
            }
            MatrixGrid(cells = matrix.right, side = MatrixSide.RIGHT, onEvent = onEvent)
        }

        Button(
            onClick = { onEvent(CalculatorUiEvent.EvaluateMatrix) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.matrix_compute))
        }
        Text(
            text = stringResource(R.string.matrix_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(CalcTokens.ScreenPadding))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DimensionsRow(matrix: MatrixUiState, onEvent: (CalculatorUiEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CalcTokens.SectionSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Stepper(
            label = stringResource(R.string.matrix_rows),
            value = matrix.rows,
            onChange = { onEvent(CalculatorUiEvent.SetMatrixRows(it)) }
        )
        Stepper(
            label = stringResource(R.string.matrix_columns),
            value = matrix.columns,
            onChange = { onEvent(CalculatorUiEvent.SetMatrixColumns(it)) }
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(CalcTokens.KeyGap))
        FilledTonalButton(
            onClick = { onChange(value - 1) },
            enabled = value > CalculatorViewModel.MIN_MATRIX_SIZE,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("−")
        }
        Text(
            text = "$value",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = CalcTokens.KeyGap)
        )
        FilledTonalButton(
            onClick = { onChange(value + 1) },
            enabled = value < CalculatorViewModel.MAX_MATRIX_SIZE,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("+")
        }
    }
}

@Composable
private fun OperationSelector(
    selected: MatrixOperationKind,
    onSelect: (MatrixOperationKind) -> Unit
) {
    val operations = MatrixOperationKind.entries
    Column(verticalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)) {
        operations.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CalcTokens.KeyGap)) {
                row.forEach { operation ->
                    FilterChip(
                        selected = operation == selected,
                        onClick = { onSelect(operation) },
                        label = { Text(stringResource(operation.labelRes())) }
                    )
                }
            }
        }
    }
}

private fun MatrixOperationKind.labelRes(): Int = when (this) {
    MatrixOperationKind.ADD -> R.string.matrix_op_add
    MatrixOperationKind.SUBTRACT -> R.string.matrix_op_subtract
    MatrixOperationKind.MULTIPLY -> R.string.matrix_op_multiply
    MatrixOperationKind.DETERMINANT -> R.string.matrix_op_determinant
    MatrixOperationKind.INVERSE -> R.string.matrix_op_inverse
    MatrixOperationKind.TRANSPOSE -> R.string.matrix_op_transpose
    MatrixOperationKind.RANK -> R.string.matrix_op_rank
}

@Composable
private fun MatrixGrid(
    cells: List<List<String>>,
    side: MatrixSide,
    onEvent: (CalculatorUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.forEachIndexed { row, values ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                values.forEachIndexed { column, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            onEvent(CalculatorUiEvent.SetMatrixCell(side, row, column, it))
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}