package com.nowarro.truckinspection

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.nowarro.truckinspection.data.*
import kotlinx.coroutines.launch
import java.io.File

private data class ShotTask(val id:String,val title:String,val hint:String,val samples:List<String>,val required:Boolean=true)
private val tasks = listOf(
 ShotTask("front_three_quarter","Передняя часть, 3/4","Автомобиль целиком, видны кабина и передняя часть", listOf("1000241614.jpg","1000241620.jpg","1000241621.jpg","1000241626.jpg")),
 ShotTask("front_detail","Передний бампер и низ","Снимите переднюю часть снизу", listOf("1000241613.jpg")),
 ShotTask("left_side","Левая сторона","Весь кузов и колёса полностью", listOf("1000241611.jpg","1000241612.jpg","1000241619.jpg","1000241627.jpg")),
 ShotTask("rear","Задняя часть","Двери и задняя часть автомобиля целиком", listOf("1000241618.jpg","1000241625.jpg")),
 ShotTask("lift_platform","Гидроборт снаружи","Платформа и механизм сзади", listOf("1000241624.jpg")),
 ShotTask("rear_opening","Задний проём кузова","Откройте задние двери и покажите проём", listOf("1000241629.jpg")),
 ShotTask("engine","Моторный отсек","Откройте капот, снимите двигатель целиком", listOf("1000241615.jpg")),
 ShotTask("driver_door","Кабина и водительская дверь","Покажите открытую дверь и вход в кабину", listOf("1000241616.jpg","1000241617.jpg","1000241628.jpg")),
 ShotTask("lift_controls","Управление гидробортом","Крупно рычаги и блок управления", listOf("1000241622.jpg","1000241623.jpg")),
 ShotTask("dashboard","Центральная панель","Снимите органы управления и магнитолу", listOf("1000241630.jpg")),
 ShotTask("passenger_panel","Панель со стороны пассажира","Покажите торпедо и салон", listOf("1000241631.jpg")),
 ShotTask("odometer","Приборная панель и пробег","Зажигание включено, показания читаемы", listOf("1000241632.jpg")),
 ShotTask("cabin_storage","Дополнительное оборудование кабины","Снимите отсек/оборудование крупным планом", listOf("1000241633.jpg"))
)

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { InspectionApp() } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun InspectionApp() {
 val context= LocalContext.current; val dao= remember { InspectionDb.get(context).dao() }; val scope= rememberCoroutineScope()
 val tfliteModel = remember { TfliteAngleModel.loadIfAvailable(context) }
 var inspections by remember { mutableStateOf<List<Inspection>>(emptyList()) }; var active by remember { mutableStateOf<Inspection?>(null) }; var photos by remember { mutableStateOf<List<InspectionPhoto>>(emptyList()) }
 var plate by remember { mutableStateOf("") }; var employee by remember { mutableStateOf("") }; var vehicle by remember { mutableStateOf("") }; var screen by remember { mutableStateOf("home") }
 var selectedPhoto by remember { mutableStateOf<InspectionPhoto?>(null) }
 var deleteTarget by remember { mutableStateOf<Inspection?>(null) }
 var editingInspection by remember { mutableStateOf<Inspection?>(null) }; var pendingTask by remember { mutableStateOf<ShotTask?>(null) }; var cameraUri by remember { mutableStateOf<Uri?>(null) }; var cameraFile by remember { mutableStateOf<File?>(null) }
 fun refresh() { scope.launch { inspections=dao.getInspections(); active?.let { photos=dao.getPhotos(it.id) } } }; LaunchedEffect(Unit) { refresh() }
 val camera= rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> val task=pendingTask; val ins=active; val uri=cameraUri; if(ok && task!=null && ins!=null && uri!=null) scope.launch {
    val path=cameraFile?.absolutePath ?: ""
    val quality = PhotoQualityAnalyzer.analyze(path)
    val modelPrediction = tfliteModel?.predict(path)
    val angle = if (modelPrediction != null) {
        AngleMatch(
            taskId = tasks.firstOrNull { it.id == modelPrediction.label }?.id,
            confidence = (modelPrediction.confidence * 100).toInt(),
            reliable = modelPrediction.confidence >= 0.70f,
            message = if (modelPrediction.confidence >= 0.70f) "Определено обученной моделью" else "Низкая уверенность модели"
        )
    } else {
        ReferenceAngleClassifier.classify(context, path, tasks.map { AngleReference(it.id, it.samples) }, task.id)
    }
    dao.savePhoto(InspectionPhoto(inspectionId=ins.id, taskId=task.id, filePath=path,
        qualityPassed=quality.passed, qualityScore=quality.score, qualityMessage=quality.message,
        detectedTaskId=angle.taskId ?: "", angleConfidence=angle.confidence, angleReliable=angle.reliable))
    photos=dao.getPhotos(ins.id)
 }; pendingTask=null }
 fun takePhoto(task:ShotTask) { val ins=active ?: return; val dir=File(context.filesDir,"inspections/${ins.id}").apply { mkdirs() }; val file=File(dir,"${task.id}_${System.currentTimeMillis()}.jpg"); val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,file.name); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg") }; val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.provider",file); cameraUri=uri; cameraFile=file; pendingTask=task; camera.launch(uri) }
 MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF1677FF),secondary=Color(0xFF69B1FF),background=Color(0xFF0B1220),surface=Color(0xFF152238),onSurface=Color(0xFFF1F5FF))) {
 Scaffold(topBar={ TopAppBar(title={ Text("Фотоосмотр ТС",fontWeight=FontWeight.Bold) }, navigationIcon={ if(screen!="home") IconButton(onClick={screen="home";active=null;refresh()}) { Icon(Icons.Default.ArrowBack,"Назад") } }) }) { pad ->
 Column(Modifier.fillMaxSize().background(Color(0xFF0B1220)).padding(pad).padding(16.dp)) {
 when(screen) {
 "home" -> {
   Text("Панель осмотров",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Color.White)
   Text("Контроль состояния грузового транспорта",fontSize=13.sp,color=Color(0xFF9AAAC3))
   Spacer(Modifier.height(20.dp))
   Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF152238)),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
     Column(Modifier.padding(18.dp)) {
       Row(verticalAlignment=Alignment.CenterVertically) {
         Icon(Icons.Default.LocalShipping,null,tint=Color(0xFF69B1FF),modifier=Modifier.size(32.dp))
         Spacer(Modifier.width(12.dp))
         Column { Text("Truck Photo Inspection",fontWeight=FontWeight.Bold,color=Color.White,fontSize=18.sp); Text("Офлайн • данные на устройстве",color=Color(0xFF9AAAC3),fontSize=12.sp) }
       }
       Spacer(Modifier.height(18.dp))
       Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
         Column(Modifier.weight(1f)) { Text("${inspections.size}",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White); Text("Всего осмотров",fontSize=12.sp,color=Color(0xFF9AAAC3)) }
         Column(Modifier.weight(1f)) { Text("${inspections.count { it.completed }}",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color(0xFF52C41A)); Text("Завершено",fontSize=12.sp,color=Color(0xFF9AAAC3)) }
         Column(Modifier.weight(1f)) { Text("${inspections.count { !it.completed }}",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color(0xFFFAAD14)); Text("В работе",fontSize=12.sp,color=Color(0xFF9AAAC3)) }
       }
     }
   }
   Spacer(Modifier.height(16.dp))
   Button(onClick={ plate="";employee="";vehicle="";screen="new" },modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(12.dp)) {
     Icon(Icons.Default.Add,null); Spacer(Modifier.width(8.dp)); Text("Начать новый осмотр",fontWeight=FontWeight.SemiBold)
   }
   Spacer(Modifier.height(22.dp))
   Row(verticalAlignment=Alignment.CenterVertically) {
     Text("История осмотров",fontWeight=FontWeight.Bold,fontSize=19.sp,color=Color.White,modifier=Modifier.weight(1f))
     Text("${inspections.size} записей",fontSize=12.sp,color=Color(0xFF9AAAC3))
   }
   Spacer(Modifier.height(8.dp))
   if(inspections.isEmpty()) {
     Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF152238)),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
       Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
         Icon(Icons.Default.PhotoCamera,null,tint=Color(0xFF69B1FF),modifier=Modifier.size(36.dp))
         Spacer(Modifier.height(8.dp))
         Text("Осмотров пока нет",color=Color.White,fontWeight=FontWeight.SemiBold)
         Text("Создайте первый осмотр автомобиля",color=Color(0xFF9AAAC3),fontSize=12.sp)
       }
     }
   }
   LazyColumn { items(inspections) { item ->
     Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable { active=item; scope.launch { photos=dao.getPhotos(item.id); screen=if(item.completed) "report" else "inspection" } },colors=CardDefaults.cardColors(containerColor=Color(0xFF152238)),shape=RoundedCornerShape(14.dp)) {
       Column(Modifier.padding(14.dp)) {
         Row(verticalAlignment=Alignment.CenterVertically) {
           Icon(Icons.Default.LocalShipping,null,tint=Color(0xFF69B1FF))
           Spacer(Modifier.width(10.dp))
           Column(Modifier.weight(1f)) {
             Text(item.plate,fontWeight=FontWeight.Bold,fontSize=18.sp,color=Color.White)
             Text(item.vehicle.ifBlank { "Грузовой автомобиль" },fontSize=13.sp,color=Color(0xFF9AAAC3))
           }
           Text(if(item.completed) "Завершён" else "В работе",fontSize=12.sp,color=if(item.completed) Color(0xFF52C41A) else Color(0xFFFAAD14))
         }
         Spacer(Modifier.height(8.dp))
         Text(java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date(item.createdAt)),fontSize=12.sp,color=Color(0xFF9AAAC3))
         Row {
           TextButton(onClick={ plate=item.plate;employee=item.employee;vehicle=item.vehicle;editingInspection=item;screen="edit" }) { Icon(Icons.Default.Edit,null); Text("Изменить") }
           TextButton(onClick={ deleteTarget=item }) { Icon(Icons.Default.Delete,null); Text("Удалить",color=Color(0xFFFF7875)) }
         }
       }
     }
   } }
 }
 "new" -> { Text("Новый осмотр",fontSize=23.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(16.dp)); OutlinedTextField(plate,{plate=it.uppercase()},label={Text("Государственный номер *")},modifier=Modifier.fillMaxWidth(),singleLine=true); Spacer(Modifier.height(10.dp)); OutlinedTextField(vehicle,{vehicle=it},label={Text("Марка / модель")},modifier=Modifier.fillMaxWidth(),singleLine=true); Spacer(Modifier.height(10.dp)); OutlinedTextField(employee,{employee=it},label={Text("Сотрудник *")},modifier=Modifier.fillMaxWidth(),singleLine=true); Spacer(Modifier.height(18.dp)); Button(onClick={ if(plate.isNotBlank() && employee.isNotBlank()) scope.launch { val id=dao.insertInspection(Inspection(plate=plate,employee=employee,vehicle=vehicle)); active=dao.getInspection(id); photos=emptyList(); screen="inspection"; refresh() } },enabled=plate.isNotBlank()&&employee.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Создать осмотр") } }
 "edit" -> { Text("Редактирование осмотра",fontSize=23.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(16.dp)); OutlinedTextField(plate,{plate=it.uppercase()},label={Text("Государственный номер *")},modifier=Modifier.fillMaxWidth(),singleLine=true); Spacer(Modifier.height(10.dp)); OutlinedTextField(vehicle,{vehicle=it},label={Text("Марка / модель")},modifier=Modifier.fillMaxWidth(),singleLine=true); Spacer(Modifier.height(10.dp)); OutlinedTextField(employee,{employee=it},label={Text("Сотрудник *")},modifier=Modifier.fillMaxWidth(),singleLine=true); Spacer(Modifier.height(18.dp)); Button(onClick={ val old=editingInspection; if(old!=null && plate.isNotBlank() && employee.isNotBlank()) scope.launch { dao.updateInspection(old.copy(plate=plate,employee=employee,vehicle=vehicle)); active=dao.getInspection(old.id); refresh(); screen="home"; editingInspection=null } },enabled=plate.isNotBlank()&&employee.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Сохранить изменения") } }
 "inspection" -> { val ins=active; if(ins!=null) { Text(ins.plate,fontSize=24.sp,fontWeight=FontWeight.Bold); Text("${ins.vehicle.ifBlank { "Грузовой автомобиль" }} • ${ins.employee}",color=Color.Gray); Spacer(Modifier.height(12.dp)); val done=photos.filter{(it.qualityPassed || it.manuallyApproved) && (it.angleReliable && it.detectedTaskId == it.taskId || it.angleManuallyApproved)}.map{it.taskId}.toSet(); LinearProgressIndicator(progress={done.size.toFloat()/tasks.size},modifier=Modifier.fillMaxWidth()); Text("Принято ${done.size} из ${tasks.size} обязательных ракурсов. Снимки с замечаниями нужно переснять или подтвердить вручную.",fontSize=12.sp,color=Color.Gray); Spacer(Modifier.height(12.dp)); LazyColumn(Modifier.weight(1f)) { items(tasks) { task -> val photo=photos.find{it.taskId==task.id}; Card(Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(12.dp)) { Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) { AsyncImage(model="file:///android_asset/reference_photos/${task.samples.first()}",contentDescription=null,modifier=Modifier.size(74.dp),contentScale=ContentScale.Crop); Column(Modifier.weight(1f).padding(horizontal=10.dp)) { Text(task.title,fontWeight=FontWeight.SemiBold); Text(task.hint,fontSize=12.sp,color=Color.Gray); Text(
 if(photo==null) "Обязательное фото"
 else if(photo.qualityPassed) "Качество: ${photo.qualityScore}/100 — принято"
 else if(photo.manuallyApproved) "Принято вручную · ${photo.qualityMessage}"
 else "Проверка ${photo.qualityScore}/100: ${photo.qualityMessage}",
 fontSize=11.sp,
 color=if(photo!=null && (photo.qualityPassed || photo.manuallyApproved)) Color(0xFF2E7D32) else Color(0xFFB45309)
 )
 if(photo!=null) {
   AsyncImage(model=File(photo.filePath),contentDescription="Фото ${task.title}",modifier=Modifier.fillMaxWidth().height(150.dp).padding(top=6.dp).clickable { selectedPhoto=photo },contentScale=ContentScale.Crop)
   TextButton(onClick={ selectedPhoto=photo }) { Icon(Icons.Default.ZoomIn,null); Text("Открыть фото") }
   val angleOk = photo.angleReliable && photo.detectedTaskId == photo.taskId
   val detectedTitle = tasks.firstOrNull { it.id == photo.detectedTaskId }?.title ?: "не определён"
   Text(
     if(angleOk) "Ракурс: совпадает с заданием"
     else if(photo.angleReliable) "Возможен другой ракурс: $detectedTitle (${photo.angleConfidence}%)"
     else "Ракурс: нужна ручная проверка (${photo.angleConfidence}%)",
     fontSize=11.sp,
     color=if(angleOk || photo.angleManuallyApproved) Color(0xFF2E7D32) else Color(0xFFB45309)
   )
   if((!photo.qualityPassed && !photo.manuallyApproved) || (!angleOk && !photo.angleManuallyApproved)) {
     TextButton(onClick={ scope.launch {
       dao.updatePhoto(photo.copy(manuallyApproved=photo.manuallyApproved || !photo.qualityPassed,
         angleManuallyApproved=photo.angleManuallyApproved || !angleOk))
       photos=dao.getPhotos(ins.id)
     } }) { Text("Подтвердить вручную") }
   }
 }
 }; Column {
 FilledTonalButton(onClick={takePhoto(task)}) { Text(if(photo!=null) "Переснять" else "Снять") }
 if(photo!=null) TextButton(onClick={ scope.launch { File(photo.filePath).delete(); dao.deletePhoto(ins.id,task.id); photos=dao.getPhotos(ins.id) } }) { Text("Удалить фото",color=Color(0xFFB3261E)) }
} } } } }; Button(onClick={ if(done.size==tasks.size) scope.launch { dao.updateInspection(ins.copy(completed=true)); refresh(); screen="report" } },enabled=done.size==tasks.size,modifier=Modifier.fillMaxWidth()) { Text("Завершить осмотр") } } }
 "report" -> { val ins=active; if(ins!=null) { Text("Фотоосмотр завершён",fontSize=23.sp,fontWeight=FontWeight.Bold); Text("${ins.plate} • ${photos.size} фотографий",color=Color.Gray); Spacer(Modifier.height(12.dp)); LazyColumn { items(tasks) { t -> val p=photos.find{it.taskId==t.id}; if(p!=null) Row(Modifier.fillMaxWidth().padding(6.dp).clickable { selectedPhoto=p },verticalAlignment=Alignment.CenterVertically) { AsyncImage(model=File(p.filePath),contentDescription=t.title,modifier=Modifier.size(64.dp),contentScale=ContentScale.Crop); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(t.title,fontWeight=FontWeight.Medium); Text("Нажмите для просмотра",fontSize=11.sp,color=Color.Gray) }; Icon(Icons.Default.CheckCircle,color=Color(0xFF2E7D32),contentDescription=null) } } }; Text("Отчёт сохранён локально на устройстве.",color=Color.Gray) } }
 }
 if(selectedPhoto!=null) {
   Dialog(onDismissRequest={ selectedPhoto=null }) {
     Surface(shape=RoundedCornerShape(16.dp),color=Color.Black,modifier=Modifier.fillMaxWidth()) {
       Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
         AsyncImage(model=File(selectedPhoto!!.filePath),contentDescription="Полноэкранный просмотр",modifier=Modifier.fillMaxWidth().heightIn(max=620.dp),contentScale=ContentScale.Fit)
         TextButton(onClick={ selectedPhoto=null }) { Text("Закрыть",color=Color.White) }
       }
     }
   }
 }
 if(deleteTarget!=null) {
   AlertDialog(onDismissRequest={ deleteTarget=null },title={ Text("Удалить фотоосмотр?") },text={ Text("Осмотр, все его фотографии и локальные файлы будут удалены без возможности восстановления.") },confirmButton={ TextButton(onClick={ val target=deleteTarget; if(target!=null) scope.launch { dao.deleteInspectionPhotos(target.id); dao.deleteInspection(target.id); File(context.filesDir,"inspections/${target.id}").deleteRecursively(); inspections=dao.getInspections(); if(active?.id==target.id) { active=null; photos=emptyList() }; deleteTarget=null } }) { Text("Удалить",color=Color(0xFFB3261E)) } },dismissButton={ TextButton(onClick={deleteTarget=null}) { Text("Отмена") } })
 }
 }
 }
 }
}
