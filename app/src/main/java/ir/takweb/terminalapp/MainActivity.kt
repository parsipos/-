package ir.takweb.terminalapp

import android.app.*
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.*
import android.widget.*
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
 private lateinit var db:LocalDb
 private lateinit var body:LinearLayout
 private lateinit var search:EditText
 private lateinit var projects:Spinner
 private lateinit var regions:Spinner
 private lateinit var onlyPending:CheckBox
 private lateinit var highlightFilter:Spinner
 private lateinit var pagination:LinearLayout
 private lateinit var resultCount:TextView
 private var currentPage=0
 private var initializing=true
 private lateinit var results:LinearLayout
 private lateinit var counters:TextView
 private var ids=listOf(0)
 private val navy=Color.rgb(18,39,78)
 private val blue=Color.rgb(20,91,172)
 private val bg=Color.rgb(244,247,252)
 private val selectedBg=Color.rgb(218,237,255)
 private val api="https://parsipos.ir/wp-json/pptm/v1"
 private fun dp(x:Int)=(resources.displayMetrics.density*x).toInt()
 private fun shape(color:Int,r:Int=16):GradientDrawable = GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat()}
 private fun txt(s:String,size:Float=14f,color:Int=navy,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(color);gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;if(bold)typeface=Typeface.DEFAULT_BOLD}
 private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h))}
 private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
 private fun card():LinearLayout=column().apply{background=shape(Color.WHITE);setPadding(dp(16),dp(14),dp(16),dp(14));elevation=dp(2).toFloat()}
 private fun button(s:String,primary:Boolean=true):TextView=txt(s,14f,if(primary)Color.WHITE else blue,true).apply{gravity=Gravity.CENTER;setPadding(dp(16),dp(13),dp(16),dp(13));background=shape(if(primary)blue else Color.rgb(232,241,253),12);isClickable=true}
 private fun addCard(v:View){val lp=LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);body.addView(v,lp)}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=navy;window.navigationBarColor=Color.WHITE;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;db=LocalDb(this)
  val scroll=ScrollView(this).apply{fillViewport=true;background=shape(bg,0)};body=column().apply{setPadding(dp(16),dp(18),dp(16),dp(30))};scroll.addView(body);setContentView(scroll)
  val header=column().apply{background=shape(navy,22);setPadding(dp(20),dp(20),dp(20),dp(20))}
  val brand=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val logo=ImageView(this).apply{setImageResource(resources.getIdentifier("sadad_logo","drawable",packageName));scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="نشان پرداخت الکترونیک سداد"}
  if(resources.getIdentifier("sadad_logo","drawable",packageName)!=0)brand.addView(logo,LinearLayout.LayoutParams(dp(74),dp(55)))
  brand.addView(txt("مدیریت پروژه پایانه‌ها",20f,Color.WHITE,true),LinearLayout.LayoutParams(-1,dp(52)));header.addView(brand)
  header.addView(txt("پرداخت الکترونیک سداد  •  نسخه آفلاین",12f,Color.rgb(196,215,241)))
  val developerMenu=button("☰  منو",false).apply{textSize=12f}
  developerMenu.setOnClickListener{anchor->
   PopupMenu(this,anchor).apply{
    menu.add("درباره توسعه‌دهنده").setOnMenuItemClickListener{showDeveloperInfo();true}
    show()
   }
  }
  header.addView(space(9))
  header.addView(developerMenu,LinearLayout.LayoutParams(dp(116),-2))
  header.addView(space(15));val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
  val sync=button("↻  به‌روزرسانی");val report=button("↑  ارسال گزارش",false)
  actions.addView(sync,LinearLayout.LayoutParams(0,-2,1f));actions.addView(Space(this),LinearLayout.LayoutParams(dp(8),1));actions.addView(report,LinearLayout.LayoutParams(0,-2,1f));header.addView(actions);addCard(header)
  sync.setOnClickListener{askToken(false)};report.setOnClickListener{askToken(true)}
  val summary=card();summary.addView(txt("نمای کلی فعالیت‌ها",16f,navy,true));summary.addView(space(7));counters=txt("—",15f,blue,true);summary.addView(counters);addCard(summary)
  val filter=card();filter.addView(txt("جستجو و فیلتر",16f,navy,true));filter.addView(space(9))
  search=EditText(this).apply{hint="شماره پایانه یا نام فروشگاه";textSize=14f;setSingleLine(true);setPadding(dp(12),dp(10),dp(12),dp(10));background=shape(bg,10)};filter.addView(search);filter.addView(space(9))
  projects=Spinner(this);regions=Spinner(this);filter.addView(txt("پروژه",12f));filter.addView(projects);filter.addView(txt("منطقه",12f));filter.addView(regions)
  onlyPending=CheckBox(this).apply{text="فقط پایانه‌های در انتظار انجام";isChecked=true};filter.addView(onlyPending)
  filter.addView(txt("وضعیت هایلایت",12f));highlightFilter=Spinner(this)
  highlightFilter.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("همه پایانه‌ها","فقط هایلایت‌شده‌ها","فقط هایلایت‌نشده‌ها"))
  filter.addView(highlightFilter);addCard(filter)
  val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  head.addView(txt("فهرست پایانه‌ها",17f,navy,true),LinearLayout.LayoutParams(0,-2,1f))
  val reset=button("پاک‌کردن رنگ‌ها",false).apply{textSize=12f;setPadding(dp(10),dp(10),dp(10),dp(10))}
  reset.setOnClickListener{AlertDialog.Builder(this).setTitle("ریست رنگ‌ها").setMessage("رنگ تمام پایانه‌های علامت‌گذاری‌شده پاک شود؟ وضعیت انجام کارها تغییر نمی‌کند.").setPositiveButton("ریست"){_,_->db.clearHighlights();refresh()}.setNegativeButton("انصراف",null).show()}
  head.addView(reset);body.addView(head);body.addView(space(5));body.addView(txt("برای تغییر رنگ، روی کارت بزنید؛ برای جزئیات و ثبت انجام، کارت را نگه دارید.",12f,Color.GRAY));body.addView(space(9))
  resultCount=txt("",12f,Color.DKGRAY);body.addView(resultCount);body.addView(space(8))
  results=column();body.addView(results)
  pagination=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;layoutDirection=View.LAYOUT_DIRECTION_RTL};body.addView(pagination)
  loadFilters();initializing=false;refresh()
  search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){currentPage=0;refresh()};override fun afterTextChanged(s:Editable?) {}})
  projects.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,i:Int,id:Long){currentPage=0;refresh()}}
  regions.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,i:Int,id:Long){currentPage=0;refresh()}}
  highlightFilter.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,i:Int,id:Long){currentPage=0;refresh()}}
  onlyPending.setOnCheckedChangeListener{_,_->currentPage=0;refresh()}
 }
 private fun loadFilters(){val p=db.projects();ids=listOf(0)+p.map{it.first};projects.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("همه پروژه‌ها")+p.map{it.second});regions.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("همه مناطق")+db.regions())}
 private fun refresh(){if(initializing||!::results.isInitialized||!::highlightFilter.isInitialized)return
  val (total,pending,done)=db.totals();counters.text="کل: $total     •     باقی‌مانده: $pending     •     انجام‌شده: $done"
  val reg=if(regions.selectedItemPosition<=0)"" else regions.selectedItem.toString()
  val highlightMode=highlightFilter.selectedItemPosition
  val count=db.countFiltered(search.text.toString().trim(),reg,ids.getOrElse(projects.selectedItemPosition){0},onlyPending.isChecked,highlightMode)
  val totalPages=maxOf(1,(count+99)/100)
  if(currentPage>=totalPages)currentPage=totalPages-1
  val items=db.listPage(search.text.toString().trim(),reg,ids.getOrElse(projects.selectedItemPosition){0},onlyPending.isChecked,highlightMode,currentPage,100)
  val start=if(count==0)0 else currentPage*100+1
  val end=minOf(count,(currentPage+1)*100)
  resultCount.text="نمایش $start تا $end از $count پایانه  •  صفحه ${currentPage+1} از $totalPages  •  مرتب‌سازی: نام فروشگاه"
  results.removeAllViews();pagination.removeAllViews()
  if(items.isEmpty()){val empty=card();empty.addView(txt("موردی برای نمایش وجود ندارد",15f,navy,true));empty.addView(space(5));empty.addView(txt("فیلترها را تغییر دهید یا اطلاعات پایانه‌ها را وارد کنید.",12f,Color.GRAY));results.addView(empty)}
  for(x in items){val c=card();if(x.highlighted)c.background=shape(selectedBg,16)
   val state=if(x.localMethod!=null)"انجام‌شده در گوشی" else if(x.serverStatus=="pending")"در انتظار انجام" else "تکمیل‌شده"
   c.addView(txt(x.shop.ifBlank{"فروشگاه بدون نام"},16f,navy,true));c.addView(space(5));c.addView(txt("شماره پایانه: ${x.number}",14f,blue,true));c.addView(space(4));c.addView(txt("${x.region.ifBlank{"منطقه نامشخص"}}  •  ${x.project}",12f,Color.DKGRAY));c.addView(space(5));c.addView(txt(state,12f,if(state=="در انتظار انجام")Color.rgb(180,106,0) else Color.rgb(25,127,74),true))
   c.setOnClickListener{db.toggleHighlight(x);refresh()};c.setOnLongClickListener{details(x);true}
   results.addView(c,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
  }
  if(count>100){val previous=button("صفحه قبل",false);previous.isEnabled=currentPage>0;previous.alpha=if(currentPage>0)1f else 0.4f
   previous.setOnClickListener{if(currentPage>0){currentPage--;refresh()}}
   val next=button("صفحه بعد",false);next.isEnabled=currentPage+1<totalPages;next.alpha=if(next.isEnabled)1f else 0.4f
   next.setOnClickListener{if(currentPage+1<totalPages){currentPage++;refresh()}}
   pagination.addView(previous,LinearLayout.LayoutParams(0,-2,1f));pagination.addView(txt("${currentPage+1} / $totalPages",13f,navy,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(45),0.8f));pagination.addView(next,LinearLayout.LayoutParams(0,-2,1f))
  }
 }
 private fun details(x:TerminalItem){val options=if(x.localMethod==null&&x.serverStatus=="pending")arrayOf("تماس با فروشگاه","ثبت انجام تراکنشی","ثبت انجام دستی","بستن") else arrayOf("تماس با فروشگاه","بستن")
  AlertDialog.Builder(this).setTitle("پایانه ${x.number}").setMessage("فروشگاه: ${x.shop}\nپروژه: ${x.project}\nمنطقه: ${x.region}\nسریال: ${x.serial}\nآدرس: ${x.address}\nموبایل: ${x.mobile}").setItems(options){_,i->when(options[i]){
  "تماس با فروشگاه"->{val phone=x.mobile.filter{it.isDigit()||it=='+'};if(phone.isNotBlank())startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$phone"))) else toast("شماره تماس ثبت نشده است")}
  "ثبت انجام تراکنشی"->confirm(x,"transactional","")
  "ثبت انجام دستی"->{val note=EditText(this).apply{hint="توضیح کوتاه تا ۱۰۰ کاراکتر";filters=arrayOf(InputFilter.LengthFilter(100))};AlertDialog.Builder(this).setTitle("انجام دستی").setView(note).setPositiveButton("ثبت"){_,_->confirm(x,"manual",note.text.toString())}.setNegativeButton("انصراف",null).show()}
  }}.show()
 }
 private fun confirm(x:TerminalItem,method:String,note:String){AlertDialog.Builder(this).setMessage("انجام کار برای پایانه ${x.number} ذخیره شود؟").setPositiveButton("تأیید"){_,_->db.complete(x,method,note);refresh();toast("روی گوشی ذخیره شد")}.setNegativeButton("انصراف",null).show()}
 private fun askToken(report:Boolean){val token=EditText(this).apply{hint="توکن اتصال صادرشده از وردپرس";inputType=129};AlertDialog.Builder(this).setTitle(if(report)"ارسال گزارش‌های دستی" else "همگام‌سازی از پارسی پوز").setMessage("توکن را از پیشخوان وردپرس ← پروژه پایانه‌ها دریافت کنید. این مقدار داخل APK قرار ندارد.").setView(token).setPositiveButton("ادامه"){_,_->if(token.text.isNotBlank())network(report,token.text.toString().trim())}.setNegativeButton("انصراف",null).show()}
 private fun network(report:Boolean,key:String){toast("در حال ارتباط با سرور...");thread{try{if(report){val arr=db.queued();if(arr.length()==0){ui("گزارش ارسال‌نشده‌ای وجود ندارد");return@thread};var sent=0;for(i in 0 until arr.length()){val r=arr.getJSONObject(i);val result=request("/reports",key,r);if(!result.optBoolean("ok"))throw Exception("گزارش تأیید نشد");db.markSent(listOf(r.getString("client_report_id")));sent++};ui("$sent گزارش دستی ارسال شد")}
  else{val result=request("/sync",key,null);if(!result.optBoolean("ok")||!result.has("project_terminals"))throw Exception("پاسخ سرور نامعتبر است");db.importSnapshot(result);runOnUiThread{loadFilters();refresh();message("اطلاعات با موفقیت دریافت و آفلاین ذخیره شد")}}
 }catch(e:Exception){ui("خطای ارتباط: ${e.message}")}}}
 private fun request(path:String,key:String,data:JSONObject?):JSONObject{val c=URL(api+path).openConnection() as HttpURLConnection;try{c.requestMethod=if(data==null)"GET" else "POST";c.connectTimeout=20000;c.readTimeout=45000;c.setRequestProperty("Authorization","Bearer $key");c.setRequestProperty("Accept","application/json");if(data!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(data.toString().toByteArray(Charsets.UTF_8))}};val status=c.responseCode;val raw=(if(status in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:"";if(status !in 200..299)throw Exception("HTTP $status: "+runCatching{JSONObject(raw).optString("message")}.getOrDefault("خطای سرور"));return JSONObject(raw)}finally{c.disconnect()}}
 private fun showDeveloperInfo(){
  val info="""این برنامه توسط علی شاهری، پشتیبان مازندران، طراحی و توسعه داده شده است.

این نرم‌افزار یک ابزار مستقل و غیررسمی برای مدیریت آفلاین اطلاعات پایانه‌ها است و محصول رسمی شرکت پرداخت الکترونیک سداد محسوب نمی‌شود. استفاده از نام و نشان سداد در این برنامه به معنای تأیید، مالکیت یا پشتیبانی رسمی آن شرکت نیست.

کلیه حقوق مادی و معنوی مربوط به کد، طراحی و قابلیت‌های اختصاصی این نرم‌افزار متعلق به توسعه‌دهنده، علی شاهری، بوده و هرگونه تکثیر، انتشار، فروش یا تغییر و عرضه مجدد آن بدون اجازه وی مجاز نیست؛ حقوق قانونی صاحبان علائم تجاری و محتوای متعلق به اشخاص ثالث محفوظ است.""".trimIndent()
  AlertDialog.Builder(this).setTitle("درباره توسعه‌دهنده").setMessage(info).setPositiveButton("بستن",null).show()
 }
 private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
 private fun ui(s:String)=runOnUiThread{message(s)}
 private fun message(s:String){AlertDialog.Builder(this).setMessage(s).setPositiveButton("باشه",null).show()}
}
