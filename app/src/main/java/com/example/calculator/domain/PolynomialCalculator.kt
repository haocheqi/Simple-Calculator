package com.example.calculator.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private typealias Mono = Map<String, Int>
private data class Poly(val terms: Map<Mono, BigDecimal>) {
    fun plus(o:Poly, sign:Int=1):Poly { val m=terms.toMutableMap(); o.terms.forEach{(k,v)->m[k]=(m[k]?:BigDecimal.ZERO).add(v.multiply(BigDecimal(sign))); if(m[k]!!.compareTo(BigDecimal.ZERO)==0)m.remove(k)}; return Poly(m) }
    fun times(o:Poly):Poly { val m=mutableMapOf<Mono,BigDecimal>(); for((a,av)in terms)for((b,bv)in o.terms){val k=(a.keys+b.keys).associateWith{(a[it]?:0)+(b[it]?:0)}.filterValues{it!=0};m[k]=(m[k]?:BigDecimal.ZERO).add(av.multiply(bv))};return Poly(m) }
    fun pow(n:Int):Poly { require(n>=0){"Exponent must be a non-negative integer"}; var r=constant(BigDecimal.ONE); repeat(n){r=r.times(this)};return r }
    fun substitute(values:Map<String,BigDecimal>):BigDecimal { val mc=MathContext(50,RoundingMode.HALF_UP);return terms.entries.fold(BigDecimal.ZERO){s,(mono,c)->s.add(mono.entries.fold(c){v,(name,p)->v.multiply((values[name]?:error("Missing value for $name")).pow(p,mc),mc)},mc)} }
    override fun toString():String { if(terms.isEmpty())return "0"; val sorted=terms.entries.sortedWith(compareByDescending<Map.Entry<Mono,BigDecimal>>{it.key.values.sum()}.thenBy{it.key.keys.joinToString()}); return sorted.mapIndexed{idx,(mono,coef)->val negative=coef< BigDecimal.ZERO;val abs=coef.abs();val body=mono.entries.sortedBy{it.key}.joinToString(""){(n,p)->if(p==1)n else "$n^$p"};val coeff=if(body.isEmpty())abs.stripTrailingZeros().toPlainString() else if(abs==BigDecimal.ONE)"" else abs.stripTrailingZeros().toPlainString();val sign=if(idx==0){if(negative)"-" else ""}else if(negative)" - " else " + "; sign+coeff+body}.joinToString("") }
    companion object { fun constant(v:BigDecimal)=Poly(mapOf(emptyMap<String,Int>() to v));fun variable(n:String)=Poly(mapOf(mapOf(n to 1) to BigDecimal.ONE)) }
}

class PolynomialCalculator : SymbolicEvaluator {
    private var ans: BigDecimal? = null
    fun setAns(value: BigDecimal?) { ans = value }
    private sealed interface T { data class Num(val v:BigDecimal):T; data class Name(val v:String):T; data class Op(val v:Char):T; data object L:T;data object R:T;data object End:T }
    private var ts=emptyList<T>();private var p=0
    override fun simplify(expression:String):SymbolicResult=try{ts=lex(expression);p=0;val r=parseAdd();require(take() is T.End);SymbolicResult.Success(r.toString())}catch(e:Exception){SymbolicResult.Error(e.message?:"Invalid polynomial")}
    override fun substitute(expression:String, values:Map<String,BigDecimal>):SymbolicResult=try{ts=lex(expression);p=0;val r=parseAdd();require(take() is T.End);SymbolicResult.Success(r.substitute(values).stripTrailingZeros().toPlainString())}catch(e:Exception){SymbolicResult.Error(e.message?:"Invalid substitution")}
    private fun lex(s:String):List<T>{val o=mutableListOf<T>();var i=0;while(i<s.length)when(val c=s[i]){' ','\t','\n'->i++;in '0'..'9','.'->{val st=i;i++;while(i<s.length&&(s[i].isDigit()||s[i]=='.'))i++;o+=T.Num(s.substring(st,i).toBigDecimal())};else->if(c.isLetter()){val st=i;i++;while(i<s.length&&s[i].isLetterOrDigit())i++;o+=T.Name(s.substring(st,i))}else when(c){'+','-','*','/','^'->{o+=T.Op(c);i++};'('->{o+=T.L;i++};')'->{o+=T.R;i++};else->error("Unsupported character: $c")}};return o+T.End}
    private fun peek()=ts[p];private fun take()=ts[p++]
    private fun parseAdd():Poly{var v=parseMul();while(peek() is T.Op&&((peek()as T.Op).v=='+'||(peek()as T.Op).v=='-')){val op=(take()as T.Op).v;val r=parseMul();v=if(op=='+')v.plus(r)else v.plus(r,-1)};return v}
    private fun startsPrimary(t:T)=t is T.Num||t is T.Name||t is T.L
    private fun parseMul():Poly{var v=parsePow();while(true){if(peek()is T.Op&&((peek()as T.Op).v=='*'||(peek()as T.Op).v=='/')){val op=(take()as T.Op).v;val r=parsePow();require(op=='*'){"Polynomial division is not supported"};v=v.times(r)}else if(startsPrimary(peek()))v=v.times(parsePow())else break};return v}
    private fun parsePow():Poly{var v=parseUnary();if(peek()is T.Op&&(peek()as T.Op).v=='^'){take();val t=take();require(t is T.Num&&t.v.scale()<=0){"Exponent must be an integer"};v=v.pow(t.v.toInt())};return v}
    private fun parseUnary():Poly{if(peek()is T.Op&&((peek()as T.Op).v=='+'||(peek()as T.Op).v=='-')){val op=(take()as T.Op).v;val r=parseUnary();return if(op=='-')r.plus(r,-2)else r};return when(val t=take()){is T.Num->Poly.constant(t.v);is T.Name->if(t.v.equals("ans",true))Poly.constant(ans?:error("Ans is not available yet"))else Poly.variable(t.v);T.L->{val r=parseAdd();require(take()is T.R);r};else->error("Expected term")}}
}


