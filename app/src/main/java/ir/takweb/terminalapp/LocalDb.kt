package ir.takweb.terminalapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import org.json.JSONArray

class LocalDb(ctx: Context) : SQLiteOpenHelper(ctx, "terminal_offline.db", null, 2) {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY,name TEXT NOT NULL)")
  db.execSQL("CREATE TABLE terminals(terminal_no TEXT PRIMARY KEY,shop_name TEXT,region TEXT,serial_no TEXT,address TEXT,mobile TEXT)")
  db.execSQL("CREATE TABLE assignments(project_id INTEGER NOT NULL,terminal_no TEXT NOT NULL,server_status TEXT NOT NULL,local_method TEXT,local_note TEXT,completed_at TEXT,highlighted INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(project_id,terminal_no))")
  db.execSQL("CREATE TABLE report_queue(id TEXT PRIMARY KEY,project_id INTEGER,terminal_no TEXT,note TEXT,completed_at TEXT,sent INTEGER NOT NULL DEFAULT 0)")
 }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
  if(oldVersion < 2) db.execSQL("ALTER TABLE assignments ADD COLUMN highlighted INTEGER NOT NULL DEFAULT 0")
 }
 fun importSnapshot(data: JSONObject) {
  val projects = data.getJSONArray("projects"); val terminals = data.getJSONArray("terminals"); val assignments = data.getJSONArray("project_terminals")
  val db = writableDatabase; db.beginTransaction()
  try {
   db.execSQL("DELETE FROM projects")
   for(i in 0 until projects.length()) { val x=projects.getJSONObject(i); db.execSQL("INSERT INTO projects(id,name) VALUES(?,?)",arrayOf(x.getInt("id"),x.getString("name"))) }
   db.execSQL("DELETE FROM terminals")
   for(i in 0 until terminals.length()) { val x=terminals.getJSONObject(i); db.execSQL("INSERT INTO terminals VALUES(?,?,?,?,?,?)",arrayOf(x.getString("terminal_no"),x.optString("shop_name"),x.optString("region"),x.optString("serial_no"),x.optString("address"),x.optString("mobile"))) }
   // Preserve local completion status for existing project/terminal pairs.
   db.execSQL("CREATE TEMP TABLE incoming(project_id INTEGER,terminal_no TEXT,server_status TEXT,PRIMARY KEY(project_id,terminal_no))")
   for(i in 0 until assignments.length()) { val x=assignments.getJSONObject(i); db.execSQL("INSERT INTO incoming VALUES(?,?,?)",arrayOf(x.getInt("project_id"),x.getString("terminal_no"),x.optString("status","pending"))) }
   db.execSQL("DELETE FROM assignments WHERE NOT EXISTS(SELECT 1 FROM incoming i WHERE i.project_id=assignments.project_id AND i.terminal_no=assignments.terminal_no) AND local_method IS NULL")
   db.execSQL("INSERT OR IGNORE INTO assignments(project_id,terminal_no,server_status) SELECT project_id,terminal_no,server_status FROM incoming")
   db.execSQL("UPDATE assignments SET server_status=(SELECT i.server_status FROM incoming i WHERE i.project_id=assignments.project_id AND i.terminal_no=assignments.terminal_no) WHERE EXISTS(SELECT 1 FROM incoming i WHERE i.project_id=assignments.project_id AND i.terminal_no=assignments.terminal_no)")
   db.execSQL("DROP TABLE incoming")
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 // The same WHERE clause is used for counting and paging, so filters apply to ALL records.
 private fun filterSql(pendingOnly:Boolean,highlightMode:Int):String =
  " FROM assignments a JOIN projects p ON p.id=a.project_id JOIN terminals t ON t.terminal_no=a.terminal_no WHERE (?=0 OR a.project_id=?) AND (?='' OR t.region=?) AND (t.terminal_no LIKE ? OR t.shop_name LIKE ?)"+
  (if(pendingOnly)" AND a.server_status='pending' AND a.local_method IS NULL" else "")+
  (when(highlightMode){1->" AND a.highlighted=1";2->" AND a.highlighted=0";else->""})
 private fun filterArgs(q:String,region:String,project:Int):Array<String> =
  arrayOf(project.toString(),project.toString(),region,region,"%$q%","%$q%")
 fun countFiltered(q:String,region:String,project:Int,pendingOnly:Boolean,highlightMode:Int):Int{
  val sql="SELECT COUNT(*)"+filterSql(pendingOnly,highlightMode)
  readableDatabase.rawQuery(sql,filterArgs(q,region,project)).use{return if(it.moveToFirst())it.getInt(0) else 0}
 }
 fun listPage(q:String,region:String,project:Int,pendingOnly:Boolean,highlightMode:Int,page:Int,pageSize:Int=100):List<TerminalItem>{
  val sql="SELECT a.project_id,p.name,t.terminal_no,t.shop_name,t.region,t.mobile,t.address,t.serial_no,a.server_status,a.local_method,a.highlighted"+
   filterSql(pendingOnly,highlightMode)+" ORDER BY t.shop_name COLLATE LOCALIZED ASC,t.terminal_no COLLATE BINARY ASC,p.name COLLATE LOCALIZED ASC LIMIT ? OFFSET ?"
  val args=filterArgs(q,region,project)+arrayOf(pageSize.coerceIn(1,100).toString(),(page.coerceAtLeast(0)*pageSize.coerceIn(1,100)).toString())
  val out=mutableListOf<TerminalItem>()
  readableDatabase.rawQuery(sql,args).use{while(it.moveToNext())out.add(TerminalItem(it.getInt(0),it.getString(1),it.getString(2),it.getString(3)?:"",it.getString(4)?:"",it.getString(5)?:"",it.getString(6)?:"",it.getString(7)?:"",it.getString(8),it.getString(9),it.getInt(10)==1))}
  return out
 }
 fun toggleHighlight(item: TerminalItem): Boolean {
  writableDatabase.execSQL("UPDATE assignments SET highlighted=CASE WHEN highlighted=1 THEN 0 ELSE 1 END WHERE project_id=? AND terminal_no=?",arrayOf(item.projectId,item.number))
  readableDatabase.rawQuery("SELECT highlighted FROM assignments WHERE project_id=? AND terminal_no=?",arrayOf(item.projectId.toString(),item.number)).use { return it.moveToFirst() && it.getInt(0)==1 }
 }
 fun clearHighlights() { writableDatabase.execSQL("UPDATE assignments SET highlighted=0 WHERE highlighted!=0") }
 fun projects(): List<Pair<Int,String>> { val out=mutableListOf<Pair<Int,String>>(); readableDatabase.rawQuery("SELECT id,name FROM projects ORDER BY id",null).use { while(it.moveToNext()) out.add(it.getInt(0) to it.getString(1)) }; return out }
 fun regions(): List<String> { val out=mutableListOf<String>(); readableDatabase.rawQuery("SELECT DISTINCT region FROM terminals WHERE region IS NOT NULL AND region!='' ORDER BY region",null).use { while(it.moveToNext()) out.add(it.getString(0)) }; return out }
 fun complete(item: TerminalItem, method: String, note: String) {
  val timestamp=java.time.Instant.now().toString(); val db=writableDatabase; db.beginTransaction()
  try { db.execSQL("UPDATE assignments SET local_method=?,local_note=?,completed_at=? WHERE project_id=? AND terminal_no=?",arrayOf(method,note,timestamp,item.projectId,item.number))
   if(method=="manual") db.execSQL("INSERT INTO report_queue(id,project_id,terminal_no,note,completed_at) VALUES(?,?,?,?,?)",arrayOf(java.util.UUID.randomUUID().toString(),item.projectId,item.number,note,timestamp))
   db.setTransactionSuccessful()
  } finally {db.endTransaction()}
 }
 fun totals(): Triple<Int,Int,Int> { val c=readableDatabase.rawQuery("SELECT COUNT(*),SUM(CASE WHEN server_status='pending' AND local_method IS NULL THEN 1 ELSE 0 END),SUM(CASE WHEN local_method IS NOT NULL OR server_status!='pending' THEN 1 ELSE 0 END) FROM assignments",null); c.use { if(it.moveToFirst()) return Triple(it.getInt(0),it.getInt(1),it.getInt(2)) }; return Triple(0,0,0) }
 fun queued(): JSONArray { val arr=JSONArray(); readableDatabase.rawQuery("SELECT id,project_id,terminal_no,note,completed_at FROM report_queue WHERE sent=0 ORDER BY completed_at LIMIT 25",null).use { while(it.moveToNext()) arr.put(JSONObject().put("client_report_id",it.getString(0)).put("project_id",it.getInt(1)).put("terminal_no",it.getString(2)).put("note",it.getString(3)).put("reported_at",it.getString(4)).put("method","manual")) }; return arr }
 fun markSent(ids: List<String>) { for(id in ids) writableDatabase.execSQL("UPDATE report_queue SET sent=1 WHERE id=?",arrayOf(id)) }
}
data class TerminalItem(val projectId:Int,val project:String,val number:String,val shop:String,val region:String,val mobile:String,val address:String,val serial:String,val serverStatus:String,val localMethod:String?,val highlighted:Boolean)
