package com.example.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.firstOrNull
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.calculator.data.CalculatorDatabase
import com.example.calculator.data.SettingsRepository
import com.example.calculator.ui.CalculatorApp
import com.example.calculator.ui.theme.CalculatorTheme

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val db=CalculatorDatabase.create(applicationContext);val settings=SettingsRepository(applicationContext);setContent{val vm:CalculatorViewModel=viewModel(factory=CalculatorViewModel.factory(db.historyDao(),settings));val s by vm.settings.collectAsState(initial=com.example.calculator.data.Settings());CalculatorTheme(darkTheme=s.dark){Surface(Modifier.fillMaxSize()){CalculatorApp(vm,s)}}}}}

class CalculatorViewModel(private val history:com.example.calculator.data.HistoryDao,private val settingsRepo:SettingsRepository):ViewModel(){
    val settings=settingsRepo.settings
    val entries=history.observe()
    private val numeric=com.example.calculator.domain.HighPrecisionEvaluator();private val symbolic=com.example.calculator.domain.PolynomialCalculator();private val matrix=com.example.calculator.domain.MatrixCalculator();private var ans:java.math.BigDecimal?=null
    suspend fun calculate(mode:com.example.calculator.domain.CalculatorMode,input:String):String{val current=settingsRepo.settings.firstOrNull()?:com.example.calculator.data.Settings();val result=when(mode){com.example.calculator.domain.CalculatorMode.BASIC,com.example.calculator.domain.CalculatorMode.SCIENTIFIC->numeric.evaluate(input,com.example.calculator.domain.EvaluationContext(angleUnit=current.angle,ans=ans));com.example.calculator.domain.CalculatorMode.SYMBOLIC->{symbolic.setAns(ans);symbolic.simplify(input)};else->com.example.calculator.domain.SymbolicResult.Error("Use matrix controls")};val text=when(result){is com.example.calculator.domain.EvaluationResult.Success->{ans=result.value;com.example.calculator.domain.formatResult(result.value)};is com.example.calculator.domain.SymbolicResult.Success->{result.expression.toBigDecimalOrNull()?.let{ans=it};result.expression};is com.example.calculator.domain.EvaluationResult.Error->"Error: ${result.message}";is com.example.calculator.domain.SymbolicResult.Error->"Error: ${result.message}";else->"Error: unsupported result"};if(!text.startsWith("Error"))history.insert(com.example.calculator.data.HistoryEntity(mode=mode.name,expression=input,result=text));return text}
    suspend fun clearHistory()=history.clear(); suspend fun setDark(v:Boolean)=settingsRepo.setDark(v); suspend fun setLanguage(v:String)=settingsRepo.setLanguage(v); suspend fun setAngle(v:com.example.calculator.domain.AngleUnit)=settingsRepo.setAngle(v)
    companion object{fun factory(h:com.example.calculator.data.HistoryDao,s:SettingsRepository)=object:ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>,extras:CreationExtras):T=CalculatorViewModel(h,s) as T}}
}
