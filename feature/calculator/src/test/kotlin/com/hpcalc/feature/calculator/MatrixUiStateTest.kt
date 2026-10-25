package com.hpcalc.feature.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 矩阵面板的形状推导。
 *
 * 这里的规则一旦写错，界面就会允许用户拼出无法计算的组合（例如乘法两边尺寸不匹配），
 * 所以每条推导都单独钉住。
 */
class MatrixUiStateTest {

    private fun MatrixUiState.widthOf(side: MatrixSide): Int =
        if (side == MatrixSide.LEFT) columns else rightColumnCount

    @Test
    fun `defaults are two by two`() {
        val state = MatrixUiState()
        assertEquals(2, state.rows)
        assertEquals(2, state.columns)
        assertEquals(2, state.left.size)
        assertEquals(2, state.left.first().size)
        assertEquals(2, state.right.size)
        assertEquals(2, state.right.first().size)
    }

    @Test
    fun `changing rows reshapes the left grid`() {
        val state = MatrixUiState().withRows(3)
        assertEquals(3, state.rows)
        assertEquals(3, state.left.size)
        assertEquals(2, state.left.first().size)
    }

    @Test
    fun `changing columns reshapes the left grid`() {
        val state = MatrixUiState().withColumns(4)
        assertEquals(4, state.columns)
        assertEquals(2, state.left.size)
        assertEquals(4, state.left.first().size)
    }

    @Test
    fun `addition keeps both operands the same shape`() {
        val state = MatrixUiState().withRows(2).withColumns(3)
        assertEquals(2, state.rightRows)
        assertEquals(3, state.rightColumnCount)
        assertEquals(2, state.right.size)
        assertEquals(3, state.right.first().size)
    }

    @Test
    fun `multiplication derives the right rows from the left columns`() {
        val state = MatrixUiState()
            .withOperation(MatrixOperationKind.MULTIPLY)
            .withRows(2)
            .withColumns(3)
        assertEquals(3, state.rightRows)
        assertEquals(3, state.right.size)
    }

    @Test
    fun `multiplication lets the right columns float`() {
        val state = MatrixUiState()
            .withOperation(MatrixOperationKind.MULTIPLY)
            .withColumns(3)
            .withRightColumns(4)
        assertEquals(3, state.rightRows)
        assertEquals(4, state.rightColumnCount)
        assertEquals(3, state.right.size)
        assertEquals(4, state.right.first().size)
    }

    @Test
    fun `unary operations do not need a right operand`() {
        MatrixOperationKind.entries.filterNot { it.needsRightOperand }.forEach { kind ->
            assertTrue(kind.name, !kind.needsRightOperand)
        }
        assertEquals(3, MatrixOperationKind.entries.count { it.needsRightOperand })
    }

    @Test
    fun `switching operation reshapes the right grid`() {
        val multiply = MatrixUiState()
            .withOperation(MatrixOperationKind.MULTIPLY)
            .withColumns(3)
            .withRightColumns(4)
        val addition = multiply.withOperation(MatrixOperationKind.ADD)
        assertEquals(2, addition.rightRows)
        assertEquals(3, addition.rightColumnCount)
        assertEquals(2, addition.right.size)
        assertEquals(3, addition.right.first().size)
    }

    @Test
    fun `cell values survive a resize`() {
        val state = MatrixUiState()
            .withCell(MatrixSide.LEFT, 0, 0, "5")
            .withCell(MatrixSide.LEFT, 1, 1, "7")
            .withRows(3)
            .withColumns(3)

        assertEquals("5", state.left[0][0])
        assertEquals("7", state.left[1][1])
        assertEquals("", state.left[2][2])
    }

    @Test
    fun `size is clamped to the supported range`() {
        assertEquals(
            CalculatorViewModel.MAX_MATRIX_SIZE,
            MatrixUiState().withRows(99).rows
        )
        assertEquals(
            CalculatorViewModel.MIN_MATRIX_SIZE,
            MatrixUiState().withColumns(0).columns
        )
        assertEquals(
            CalculatorViewModel.MAX_MATRIX_SIZE,
            MatrixUiState().withRightColumns(99).rightColumns
        )
    }

    @Test
    fun `clicking outside the grid is a no-op`() {
        val state = MatrixUiState().withCell(MatrixSide.LEFT, 5, 5, "9")
        assertEquals(2, state.left.size)
        assertEquals("", state.left[0][0])
    }
}