"""Host regression tests for the actual scanner with a small fake Android provider.
The fakes test enumeration and selection, not device SAF permission behavior.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/net/Uri.java': '''package android.net; public class Uri {
 final String value; private Uri(String s){value=s;} public static Uri parse(String s){return new Uri(s);}
 public String toString(){return value;}}''',
'android/database/Cursor.java': '''package android.database; public interface Cursor extends AutoCloseable {
 boolean moveToNext(); String getString(int i); long getLong(int i); boolean isNull(int i); void close();}''',
'android/content/ContentResolver.java': '''package android.content; import android.net.Uri; import android.database.Cursor;
 public abstract class ContentResolver {public abstract Cursor query(Uri u,String[] p,String s,String[] a,String o);}''',
'android/content/Context.java': '''package android.content; public class Context {public ContentResolver resolver;
 public ContentResolver getContentResolver(){return resolver;}}''',
'android/provider/DocumentsContract.java': '''package android.provider; import android.net.Uri;
 public class DocumentsContract {public static String getDocumentId(Uri u){return u.toString();}
 public static Uri buildChildDocumentsUriUsingTree(Uri t,String id){return Uri.parse(id);}
 public static Uri buildDocumentUriUsingTree(Uri t,String id){return Uri.parse(id);}
 public static class Document { public static final String COLUMN_DOCUMENT_ID="id", COLUMN_DISPLAY_NAME="name",
 COLUMN_MIME_TYPE="mime", COLUMN_LAST_MODIFIED="modified", MIME_TYPE_DIR="dir"; }}''',
'androidx/documentfile/provider/DocumentFile.java': '''package androidx.documentfile.provider;
 import android.content.Context; import android.net.Uri; public class DocumentFile {
 public static boolean readable=true; Uri uri; public static DocumentFile fromTreeUri(Context c,Uri u){
 DocumentFile f=new DocumentFile();f.uri=u;return f;} public boolean exists(){return true;}
 public boolean canRead(){return readable;} public Uri getUri(){return uri;}}''',
'android/media/MediaMetadataRetriever.java': '''package android.media; import android.content.Context;import android.net.Uri;
 public class MediaMetadataRetriever {public static int opened=0;public static final int METADATA_KEY_DURATION=1;
 public void setDataSource(Context c,Uri u){opened++;}public String extractMetadata(int k){return "5000";}
 public void release(){}}''',
'com/parsban/callai/AppPrefs.java': '''package com.parsban.callai; import android.content.Context;
 public class AppPrefs {public static String recordingTree(Context c){return "root";}}''',
'com/parsban/callai/ScannerRegression.java': r'''package com.parsban.callai;
import android.content.*;import android.database.Cursor;import android.net.Uri;
import android.media.MediaMetadataRetriever;import androidx.documentfile.provider.DocumentFile;
import java.util.*;
public class ScannerRegression {
 static class Provider extends ContentResolver {
  Map<String,List<Object[]>> rows=new HashMap<>(); int queries; boolean denied;
  public Cursor query(Uri u,String[] p,String s,String[] a,String order){
   queries++; if(denied)throw new SecurityException("denied");
   List<Object[]> data=rows.getOrDefault(u.toString(),Collections.emptyList());
   return new Cursor(){int n=-1;public boolean moveToNext(){return ++n<data.size();}
    public String getString(int i){return (String)data.get(n)[i];}
    public long getLong(int i){return ((Number)data.get(n)[i]).longValue();}
    public boolean isNull(int i){return data.get(n)[i]==null;}public void close(){}};
  }
 }
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"));
  Context c=new Context();Provider p=new Provider();c.resolver=p;
  List<Object[]> files=new ArrayList<>();
  for(int n=0;n<3000;n++)files.add(new Object[]{"old"+n,"Call old_260101_010000.m4a","audio/mp4",1L});
  files.add(new Object[]{"latest","Call 021118_261020_063103.m4a","audio/mpeg",1L});
  files.add(new Object[]{"persian","Call همکار 🌹_261019_210755.m4a","application/octet-stream",Long.MAX_VALUE});
  p.rows.put("root",files);
  check("latest".equals(RecordingScanner.findNewest(c).toString()),"newest after 3000 old entries");
  check(p.queries==1,"single metadata query");
  check(MediaMetadataRetriever.opened==0,"latest lookup must not open audio");
  Collections.reverse(files);
  check("latest".equals(RecordingScanner.findNewest(c).toString()),"provider order independent");
  CallSession session=new CallSession();session.phone="021118";session.contactName="021118";
  session.createdAt=RecordingScanner.samsungTime("Call 021118_261020_063103.m4a");
  session.connectedAt=session.createdAt;session.endedAt=session.createdAt+5000;
  check("latest".equals(RecordingScanner.findBest(c,session).toString()),"short phone matching");
  check(RecordingScanner.samsungTime("Call همکار 🌹_261019_210755.m4a")>0,"Persian and emoji filename");
  check(RecordingScanner.samsungTime("Call bad_261332_250000.m4a")==0,"invalid date rejected");
  files.clear();check(RecordingScanner.findNewest(c)==null,"empty folder");
  files.add(new Object[]{"sub","nested","dir",0L});
  p.rows.put("sub",Collections.singletonList(new Object[]{"nested-audio","Call 021118_261020_063103.m4a","audio/mp4",1L}));
  check("nested-audio".equals(RecordingScanner.findNewest(c).toString()),"nested folder");
  p.denied=true;boolean threw=false;try{RecordingScanner.findNewest(c);}catch(IllegalStateException e){threw=true;}
  check(threw,"permission failures visible");p.denied=false;DocumentFile.readable=false;threw=false;
  try{RecordingScanner.findNewest(c);}catch(IllegalStateException e){threw=true;}check(threw,"unreadable root visible");
  System.out.println("PASS: scanner ordering, >2500 entries, no audio reads for latest, short number, Persian/emoji, invalid date, empty/nested folder, permission failures");
 }
}'''
}
with tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp)
    for name,content in STUBS.items():
        path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
    for name in ['RecordingScanner.java','PhoneUtil.java','CallSession.java']:
        (root/'com/parsban/callai'/name).write_text((ROOT/'app/src/main/java/com/parsban/callai'/name).read_text())
    subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(root/'out'),*[str(x) for x in root.rglob('*.java')]],check=True)
    subprocess.run(['java','-cp',str(root/'out'),'com.parsban.callai.ScannerRegression'],check=True)
