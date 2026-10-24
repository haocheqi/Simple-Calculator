package com.example.calculator.domain

import ch.obermuhlner.math.big.BigDecimalMath
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max

class HighPrecisionEvaluator : NumericEvaluator {
    private sealed interface Token { data class Number(val value: BigDecimal): Token; data class Name(val value: String): Token; data class Op(val value: Char): Token; data object Left: Token; data object Right: Token; data object End: Token }
    private var tokens = emptyList<Token>(); private var pos = 0; private lateinit var context: EvaluationContext

    override fun evaluate(expression: String, context: EvaluationContext): EvaluationResult = try {
        this.context = context; tokens = tokenize(expression); pos = 0
        val value = parseExpression()
        if (peek() !is Token.End) error("Unexpected token")
        EvaluationResult.Success(value.round(context.mathContext))
    } catch (e: IllegalArgumentException) { EvaluationResult.Error(e.message ?: "Invalid expression") }
      catch (e: ArithmeticException) { EvaluationResult.Error(e.message ?: "Arithmetic error") }

    private fun tokenize(s: String): List<Token> {
        val out = mutableListOf<Token>(); var i = 0
        while (i < s.length) when (val c = s[i]) {
            ' ', '\t', '\n' -> i++
            in '0'..'9', '.' -> { val start=i; i++; while (i<s.length && (s[i].isDigit() || s[i]=='.' || s[i]=='e' || s[i]=='E' || ((s[i]=='+'||s[i]=='-') && i>start && (s[i-1]=='e'||s[i-1]=='E')))) i++; out += Token.Number(s.substring(start,i).toBigDecimal()) }
            else -> if (c.isLetter() || c == 'π') { val start=i; i++; while (i<s.length && (s[i].isLetterOrDigit() || s[i]=='_')) i++; out += Token.Name(s.substring(start,i)) } else when (c) {
                '+','-','*','/','^','%','!' -> { out += Token.Op(c); i++ }
                '(' -> { out += Token.Left; i++ }
                ')' -> { out += Token.Right; i++ }
                else -> error("Unsupported character: $c")
            }
        }
        return out + Token.End
    }
    private fun peek() = tokens[pos]
    private fun take() = tokens[pos++]
    private fun parseExpression(): BigDecimal { var v=parseTerm(); while (peek() is Token.Op && ((peek() as Token.Op).value=='+' || (peek() as Token.Op).value=='-')) { val op=(take() as Token.Op).value; val r=parseTerm(); v=if(op=='+') v.add(r,context.mathContext) else v.subtract(r,context.mathContext) }; return v }
    private fun parseTerm(): BigDecimal { var v=parsePower(); while (peek() is Token.Op && ((peek() as Token.Op).value=='*' || (peek() as Token.Op).value=='/' || (peek() as Token.Op).value=='%')) { val op=(take() as Token.Op).value; val r=parsePower(); v=when(op){ '*'->v.multiply(r,context.mathContext); '/'->v.divide(r,context.mathContext); else->v.remainder(r,context.mathContext) } }; return v }
    private fun parsePower(): BigDecimal { var v=parseUnary(); if (peek() is Token.Op && (peek() as Token.Op).value=='^') { take(); val exponent=parsePower(); v=BigDecimalMath.pow(v, exponent, context.mathContext) }; return v }
    private fun parseUnary(): BigDecimal { if (peek() is Token.Op && ((peek() as Token.Op).value=='+' || (peek() as Token.Op).value=='-')) { val op=(take() as Token.Op).value; val v=parseUnary(); return if(op=='-') v.negate(context.mathContext) else v }; return parsePostfix() }
    private fun parsePostfix(): BigDecimal { var v=parsePrimary(); while (peek() is Token.Op && (peek() as Token.Op).value=='!') { take(); v=factorial(v) }; return v }
    private fun parsePrimary(): BigDecimal {
        return when (val t=take()) {
            is Token.Number -> t.value
            is Token.Name -> if (peek() is Token.Left) { take(); val arg=parseExpression(); require(take() is Token.Right); function(t.value,arg) } else when(t.value.lowercase()) { "pi","π"->BigDecimalMath.pi(context.mathContext); "e"->BigDecimalMath.e(context.mathContext); "ans"->context.ans ?: error("Ans is not available yet"); else->error("Unknown name: ${t.value}") }
            Token.Left -> { val v=parseExpression(); require(take() is Token.Right); v }
            else -> error("Expected number or expression")
        }
    }
    private fun function(name: String, x: BigDecimal): BigDecimal { val n=name.lowercase(); val a=when(context.angleUnit){AngleUnit.RADIANS->x; AngleUnit.DEGREES->x.multiply(BigDecimalMath.pi(context.mathContext),context.mathContext).divide(BigDecimal(180),context.mathContext); AngleUnit.GRADIANS->x.multiply(BigDecimalMath.pi(context.mathContext),context.mathContext).divide(BigDecimal(200),context.mathContext)}; val result=when(n){"sin"->BigDecimalMath.sin(a,context.mathContext);"cos"->BigDecimalMath.cos(a,context.mathContext);"tan"->BigDecimalMath.tan(a,context.mathContext);"asin"->BigDecimalMath.asin(x,context.mathContext);"acos"->BigDecimalMath.acos(x,context.mathContext);"atan"->BigDecimalMath.atan(x,context.mathContext);"sinh"->BigDecimalMath.sinh(x,context.mathContext);"cosh"->BigDecimalMath.cosh(x,context.mathContext);"tanh"->BigDecimalMath.tanh(x,context.mathContext);"ln"->BigDecimalMath.log(x,context.mathContext);"log","log10"->BigDecimalMath.log10(x,context.mathContext);"exp"->BigDecimalMath.exp(x,context.mathContext);"abs"->x.abs(context.mathContext);"sqrt"->BigDecimalMath.sqrt(x,context.mathContext);"floor"->x.setScale(0,RoundingMode.FLOOR);"ceil"->x.setScale(0,RoundingMode.CEILING);"round"->x.setScale(0,RoundingMode.HALF_UP);else->error("Unknown function: $name")}; return result }
    private fun factorial(x: BigDecimal): BigDecimal { require(x.scale()<=0 && x >= BigDecimal.ZERO && x <= BigDecimal(1000)) { "Factorial requires an integer from 0 to 1000" }; var r=BigDecimal.ONE; for(i in 2..x.toInt()) r=r.multiply(BigDecimal(i),context.mathContext); return r }
}

fun formatResult(value: BigDecimal, significantDigits: Int = 16): String {
    val rounded=value.round(MathContext(significantDigits, RoundingMode.HALF_UP)).stripTrailingZeros(); val abs=rounded.abs()
    return if (abs != BigDecimal.ZERO && (abs >= BigDecimal("1e16") || abs < BigDecimal("1e-6"))) rounded.toEngineeringString() else rounded.toPlainString()
}

