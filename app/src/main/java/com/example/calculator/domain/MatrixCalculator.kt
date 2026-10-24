package com.example.calculator.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class MatrixCalculator(private val mc: MathContext = MathContext(50, RoundingMode.HALF_UP)) : MatrixEvaluator {
    override fun evaluate(operation: MatrixOperation): MatrixResult = try { when(operation) {
        is MatrixOperation.Add -> elementwise(operation.left, operation.right) { a,b->a.add(b,mc) }
        is MatrixOperation.Subtract -> elementwise(operation.left, operation.right) { a,b->a.subtract(b,mc) }
        is MatrixOperation.Multiply -> multiply(operation.left,operation.right)
        is MatrixOperation.ScalarMultiply -> MatrixResult.Success(BigDecimalMatrix(operation.matrix.rows.map { row->row.map { it.multiply(operation.scalar,mc) } }))
        is MatrixOperation.Transpose -> MatrixResult.Success(operation.matrix.transpose())
        is MatrixOperation.Determinant -> MatrixResult.Scalar(determinant(operation.matrix))
        is MatrixOperation.Inverse -> inverse(operation.matrix)
        is MatrixOperation.Rank -> MatrixResult.Text(rank(operation.matrix).toString())
    }} catch(e: IllegalArgumentException) { MatrixResult.Error(e.message ?: "Invalid matrix") }
    private fun elementwise(a:BigDecimalMatrix,b:BigDecimalMatrix,f:(BigDecimal,BigDecimal)->BigDecimal):MatrixResult { require(a.rowCount==b.rowCount&&a.columnCount==b.columnCount){"Matrix dimensions must match"}; return MatrixResult.Success(BigDecimalMatrix(a.rows.zip(b.rows){x,y->x.zip(y,f)})) }
    private fun multiply(a:BigDecimalMatrix,b:BigDecimalMatrix):MatrixResult { require(a.columnCount==b.rowCount){"Matrix dimensions are incompatible"}; return MatrixResult.Success(BigDecimalMatrix((0 until a.rowCount).map{r->(0 until b.columnCount).map{c->(0 until a.columnCount).fold(BigDecimal.ZERO){s,k->s.add(a.rows[r][k].multiply(b.rows[k][c],mc),mc)}}})) }
    private fun determinant(m:BigDecimalMatrix):BigDecimal { require(m.rowCount==m.columnCount){"Determinant requires a square matrix"}; if(m.rowCount==1)return m.rows[0][0]; if(m.rowCount==2)return m.rows[0][0].multiply(m.rows[1][1],mc).subtract(m.rows[0][1].multiply(m.rows[1][0],mc),mc); return (0 until m.columnCount).fold(BigDecimal.ZERO){s,c->s.add((if(c%2==0)BigDecimal.ONE else BigDecimal.ONE.negate()).multiply(m.rows[0][c],mc).multiply(determinant(minor(m,0,c)),mc),mc)} }
    private fun minor(m:BigDecimalMatrix,row:Int,col:Int)=BigDecimalMatrix(m.rows.filterIndexed{r,_->r!=row}.map{it.filterIndexed{c,_->c!=col}})
    private fun inverse(m:BigDecimalMatrix):MatrixResult { require(m.rowCount==m.columnCount){"Inverse requires a square matrix"}; val n=m.rowCount; val a=Array(n){r->Array(2*n){c->if(c<n)m.rows[r][c] else if(c-n==r)BigDecimal.ONE else BigDecimal.ZERO}}; for(i in 0 until n){ var pivot=i; while(pivot<n&&a[pivot][i].compareTo(BigDecimal.ZERO)==0)pivot++; require(pivot<n){"Matrix is singular"}; val tmp=a[i]; a[i]=a[pivot]; a[pivot]=tmp; val d=a[i][i]; for(c in 0 until 2*n)a[i][c]=a[i][c].divide(d,mc); for(r in 0 until n)if(r!=i){val f=a[r][i]; for(c in 0 until 2*n)a[r][c]=a[r][c].subtract(f.multiply(a[i][c],mc),mc)}}; return MatrixResult.Success(BigDecimalMatrix((0 until n).map{r->(0 until n).map{c->a[r][c+n]}})) }
    private fun rank(m:BigDecimalMatrix):Int { val a=m.rows.map{it.toMutableList()}.toMutableList(); var rank=0; var col=0; while(col<m.columnCount&&rank<m.rowCount){var p=rank; while(p<m.rowCount&&a[p][col].abs()<BigDecimal("1e-40"))p++; if(p<m.rowCount){val t=a[rank];a[rank]=a[p];a[p]=t; for(r in rank+1 until m.rowCount){val f=a[r][col].divide(a[rank][col],mc);for(c in col until m.columnCount)a[r][c]=a[r][c].subtract(f.multiply(a[rank][c],mc),mc)};rank++};col++};return rank}
}
