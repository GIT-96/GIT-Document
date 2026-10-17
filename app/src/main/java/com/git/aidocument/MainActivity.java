package com.git.aidocument;
import android.app.*; import android.widget.Toast; import android.os.*; import android.content.*; import android.net.Uri; import android.webkit.*; import java.io.*; import java.nio.charset.StandardCharsets;
public class MainActivity extends Activity {
 WebView web; String currentName="Ghi-chu.txt", pending=""; Uri currentUri=null; static final int OPEN=100,SAVE=101,FOLDER=102;
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xfff7faff);getWindow().setNavigationBarColor(0xffffffff);getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);web=new WebView(this);setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.addJavascriptInterface(new Bridge(),"Android");web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){handleIncoming(getIntent());}});web.loadUrl("file:///android_asset/index.html");if(!getPreferences(0).getBoolean("onboarded",false))new AlertDialog.Builder(this).setTitle("Thiết lập quyền truy cập").setMessage("GIT AI Document cần bạn chọn thư mục để xem tài liệu. Android chỉ cấp quyền với thư mục bạn chọn; bạn có thể bỏ qua và chọn từng tệp sau.").setPositiveButton("Chọn thư mục",(d,w)->openFolderPicker()).setNegativeButton("Để sau",(d,w)->{}).setOnDismissListener(d->getPreferences(0).edit().putBoolean("onboarded",true).apply()).show();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIncoming(intent);}
 void handleIncoming(Intent intent){if(intent==null)return;String action=intent.getAction();Uri uri=Intent.ACTION_VIEW.equals(action)?intent.getData():Intent.ACTION_SEND.equals(action)?intent.getParcelableExtra(Intent.EXTRA_STREAM):null;if(uri!=null){try{showSelected(uri);}catch(Exception e){toast("Không đọc được tài liệu đã chọn");}}}
 void showSelected(Uri uri)throws Exception{
 currentUri=uri;String n="Tài liệu";
 try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())n=c.getString(0);}
 currentName=n;String lower=n.toLowerCase(java.util.Locale.ROOT);
 if(lower.endsWith(".pdf")||"application/pdf".equals(getContentResolver().getType(uri))){showPdf(uri,n);return;}
 if(lower.endsWith(".docx")){showOffice(uri,n,false);return;}
 if(lower.endsWith(".xlsx")){showOffice(uri,n,true);return;}
 if(lower.endsWith(".txt")||lower.endsWith(".md")||lower.endsWith(".csv")){
 byte[] bytes=readLimited(uri,3000000);
 web.evaluateJavascript("loadDocument("+org.json.JSONObject.quote(n)+","+org.json.JSONObject.quote(new String(bytes,StandardCharsets.UTF_8))+")",null);return;
 }
 web.evaluateJavascript("showUnsupportedDocument("+org.json.JSONObject.quote(n)+")",null);
 }
 byte[] readLimited(Uri uri,int max)throws Exception{
 try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
 byte[] buf=new byte[8192];int k;while((k=in.read(buf))!=-1){if(out.size()+k>max)throw new IOException("Tệp quá lớn");out.write(buf,0,k);}return out.toByteArray();}
 }
 void showPdf(Uri uri,String n)throws Exception{
 android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");
 if(fd==null)throw new IOException("Không thể mở PDF");
 try(android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(fd)){
 org.json.JSONArray pages=new org.json.JSONArray();int count=Math.min(renderer.getPageCount(),30);
 for(int i=0;i<count;i++){try(android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(i)){
 int w= Math.min(1100,page.getWidth()*2);int h=Math.max(1,(int)((double)page.getHeight()*w/page.getWidth()));
 android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
 bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
 ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,75,out);bitmap.recycle();
 pages.put("data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));
 }}
 web.evaluateJavascript("showRealPdf("+org.json.JSONObject.quote(n)+","+pages.toString()+","+renderer.getPageCount()+")",null);
 }finally{fd.close();}
 }
 void showOffice(Uri uri,String n,boolean sheet)throws Exception{
 byte[] data=readLimited(uri,12000000);java.util.HashMap<String,byte[]> entries=new java.util.HashMap<>();
 try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(data))){
 java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){String path=e.getName();
 if(path.equals("word/document.xml")||path.equals("xl/sharedStrings.xml")||path.equals("xl/worksheets/sheet1.xml")){
 ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int k;
 while((k=zip.read(b))!=-1){if(out.size()+k>4000000)throw new IOException("Nội dung quá lớn");out.write(b,0,k);}
 entries.put(path,out.toByteArray());}zip.closeEntry();
 }}
 if(!sheet){
 byte[] xml=entries.get("word/document.xml");if(xml==null)throw new IOException("DOCX không hợp lệ");
 StringBuilder text=new StringBuilder();org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();
 parser.setInput(new ByteArrayInputStream(xml),"UTF-8");int event;
 while((event=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
 if(event==org.xmlpull.v1.XmlPullParser.START_TAG&&parser.getName().equals("t"))text.append(parser.nextText());
 if(event==org.xmlpull.v1.XmlPullParser.END_TAG&&parser.getName().equals("p"))text.append("\n");
 if(text.length()>350000)break;
 }
 web.evaluateJavascript("showRealText("+org.json.JSONObject.quote(n)+","+org.json.JSONObject.quote(text.toString())+")",null);
 }else{
 java.util.ArrayList<String> shared=new java.util.ArrayList<>();byte[] sx=entries.get("xl/sharedStrings.xml");
 if(sx!=null){org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();parser.setInput(new ByteArrayInputStream(sx),"UTF-8");StringBuilder item=null;int event;
 while((event=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){if(event==org.xmlpull.v1.XmlPullParser.START_TAG){if(parser.getName().equals("si"))item=new StringBuilder();if(parser.getName().equals("t")&&item!=null)item.append(parser.nextText());}else if(event==org.xmlpull.v1.XmlPullParser.END_TAG&&parser.getName().equals("si")&&item!=null){shared.add(item.toString());item=null;}}}
 byte[] sx1=entries.get("xl/worksheets/sheet1.xml");if(sx1==null)throw new IOException("Không tìm thấy sheet1");
 org.json.JSONArray rows=new org.json.JSONArray();org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();parser.setInput(new ByteArrayInputStream(sx1),"UTF-8");org.json.JSONArray row=null;String type="",cellRef="";int event;
 while((event=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT&&rows.length()<300){
 if(event==org.xmlpull.v1.XmlPullParser.START_TAG){
 String tag=parser.getName();if(tag.equals("row"))row=new org.json.JSONArray();
 if(tag.equals("c")){type=parser.getAttributeValue(null,"t");cellRef=parser.getAttributeValue(null,"r");}
 if((tag.equals("v")||tag.equals("t"))&&row!=null){String value=parser.nextText();if("s".equals(type)){try{value=shared.get(Integer.parseInt(value));}catch(Exception ignored){}}
 int col=0;for(char ch:cellRef.toCharArray()){if(ch>='A'&&ch<='Z')col=col*26+ch-'A'+1;else break;}
 if(col>0&&col<=40){while(row.length()<col-1)row.put("");if(row.length()==col-1)row.put(value);}}
 }else if(event==org.xmlpull.v1.XmlPullParser.END_TAG&&parser.getName().equals("row")&&row!=null){rows.put(row);row=null;}
 }
 web.evaluateJavascript("showRealSheet("+org.json.JSONObject.quote(n)+","+rows.toString()+")",null);
 }
 }

 void openFolderPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FOLDER);}
 class Bridge {
 @JavascriptInterface public String listFiles(){try{String saved=getPreferences(0).getString("folder","");org.json.JSONArray result=new org.json.JSONArray();if(saved.isEmpty())return result.toString();Uri tree=Uri.parse(saved);Uri children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree));try(android.database.Cursor c=getContentResolver().query(children,new String[]{android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){if(c!=null){while(c.moveToNext()&&result.length()<500){org.json.JSONObject item=new org.json.JSONObject();item.put("id",c.getString(0));item.put("name",c.getString(1));item.put("mime",c.getString(2));result.put(item);}}}return result.toString();}catch(Exception e){return "[]";}}
 @JavascriptInterface public void openFromFolder(String id){runOnUiThread(()->{try{Uri tree=Uri.parse(getPreferences(0).getString("folder",""));Uri uri=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,id);showSelected(uri);}catch(Exception e){toast("Không mở được tệp");}});}
 @JavascriptInterface public void saveCurrent(String value){runOnUiThread(()->{if(currentUri==null){pending=value;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,currentName);startActivityForResult(i,SAVE);return;}try(OutputStream out=getContentResolver().openOutputStream(currentUri,"wt")){out.write(value.getBytes(StandardCharsets.UTF_8));toast("Đã lưu vào tệp gốc");}catch(Exception e){toast("Không có quyền ghi. Hãy dùng Lưu thành tệp mới");}});}
 @JavascriptInterface public void openFolder(){runOnUiThread(()->openFolderPicker());}
 @JavascriptInterface public String folderStatus(){return getPreferences(0).getString("folder","").isEmpty()?"Chưa chọn thư mục":"Đã cấp quyền thư mục";}
 @JavascriptInterface public void open(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN);});}
 @JavascriptInterface public void save(String value){runOnUiThread(()->{pending=value;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,currentName.endsWith(".txt")?currentName:"GIT-Note.txt");startActivityForResult(i,SAVE);});}
 @JavascriptInterface public void share(String value){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,value);startActivity(Intent.createChooser(i,"Chia sẻ nội dung"));});}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;Uri uri=data.getData();if(uri==null)return;try{if(request==FOLDER){int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);getContentResolver().takePersistableUriPermission(uri,flags);getPreferences(0).edit().putString("folder",uri.toString()).apply();toast("Đã cấp quyền truy cập thư mục");web.evaluateJavascript("go(\u0027files\u0027)",null);return;}if(request==SAVE){currentUri=uri;try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(pending.getBytes(StandardCharsets.UTF_8));}toast("Đã lưu tài liệu");}else{showSelected(uri);}}catch(Exception e){toast("Không thể xử lý tệp");}}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override public void onBackPressed(){web.evaluateJavascript("goHome()",null);}
}