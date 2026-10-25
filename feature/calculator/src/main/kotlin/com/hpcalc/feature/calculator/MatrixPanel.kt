package com.hpcalc.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * 阶段 2 只支持 n×n（2..6）。矩形矩阵的领域层能力阶段 1 就有了，
 * 界面解锁留到阶段 3，避免一次改动同时动排版、状态与矩阵输入三块。
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
        SizeRow(size = matrix.size, onEvent = onEvent)
        OperationSelector(selected = matrix.operation, onSelect = {
            onEvent(CalculatorUiEvent.SetMatrixOperation(it))
        })

        Text(
            text = stringResource(R.string.matrix_a),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MatrixGrid(cells = matrix.left, side = MatrixSide.LEFT, onEvent = onEvent)

        if (matrix.operation.needsRightOperand) {
            Text(
                text = stringResource(R.string.matrix_b),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun SizeRow(size: Int, onEvent: (CalculatorUiEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.matrix_size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(CalcTokens.KeyGap))
        FilledTonalButton(
            onClick = { onEvent(CalculatorUiEvent.SetMatrixSize(size - 1)) },
            enabled = size > CalculatorViewModel.MIN_MATRIX_SIZE
        ) {
            Text("−")
        }
        Text(
            text = "$size × $size",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = CalcTokens.KeyGap)
        )
        FilledTonalButton(
            onClick = { onEvent(CalculatorUiEvent.SetMatrixSize(size + 1)) },
            enabled = size < CalculatorViewModel.MAX_MATRIX_SIZE
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