package com.playerfrom10000years.nativefilesave;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final String SAVE_FILE = "10000-years-character-dle-save.json";
    static final String BACKUP_FILE = "10000-years-character-dle-save.backup.json";
    static final int MAX_TRIES = 5;
    final ArrayList<CharacterData> characters = new ArrayList<>();
    final ArrayList<HistoryEntry> history = new ArrayList<>();
    final ArrayList<String> arcCatalog = new ArrayList<>();
    final HashSet<String> recent = new HashSet<>();
    CharacterData target;
    final ArrayList<CharacterData> guesses = new ArrayList<>();
    int wrong=0, hints=0, score=1000;
    String lastHint="";
    final HashSet<String> usedHintCats = new HashSet<>();
    String difficulty="Beginner";
    boolean finished=false;

    LinearLayout root, guessesBox;
    AutoCompleteTextView guessInput;
    TextView status, wrongTv, triesTv, hintTv, scoreTv, targetTv, clueTv;
    Spinner difficultySpinner;
    ArrayAdapter<String> nameAdapter;
    final String[] modes={"Beginner","Easy","Medium","Hard","Expert","God Mode"};

    static class CharacterData {
        String name,arc,location,affiliation,status,gender,race,nationality,power,description; int arcOrder;
        CharacterData(JSONObject o) throws JSONException {
            name=o.optString("name"); arc=o.optString("arc"); arcOrder=o.optInt("arcOrder",-1);
            location=o.optString("location"); affiliation=o.optString("affiliation");
            status=o.optString("status"); gender=o.optString("gender"); race=o.optString("race");
            nationality=o.optString("nationality"); power=o.optString("power"); description=o.optString("description");
        }
    }
    static class HistoryEntry {
        String character,difficulty,date; boolean win; int wrong,guesses,hints,score;
        JSONObject json() throws JSONException {
            JSONObject o=new JSONObject(); o.put("character",character); o.put("difficulty",difficulty);
            o.put("win",win); o.put("wrong",wrong); o.put("guesses",guesses); o.put("hints",hints);
            o.put("score",score); o.put("date",date); return o;
        }
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        loadCharacters();
        nameAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,getNames());
        guessInput.setAdapter(nameAdapter);
        loadSaveOrNew();
    }

    void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,18);
        root.setBackgroundColor(Color.rgb(12,14,20));
        ScrollView scroll=new ScrollView(this); LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);

        TextView title=tv("Player from 10,000 Years\nGuess Character Game",22,true); content.addView(title);
        TextView sub=tv("Native Android • no WebView • automatic physical save",13,false); sub.setTextColor(Color.LTGRAY); content.addView(sub);

        LinearLayout modeRow=row();
        difficultySpinner=new Spinner(this);
        ArrayAdapter<String> ma=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes);
        difficultySpinner.setAdapter(ma); modeRow.addView(difficultySpinner,new LinearLayout.LayoutParams(0,WRAP(),1));
        Button historyBtn=button("History"); modeRow.addView(historyBtn,new LinearLayout.LayoutParams(0,WRAP(),1));
        Button statsBtn=button("Statistics"); modeRow.addView(statsBtn,new LinearLayout.LayoutParams(0,WRAP(),1));
        Button restoreBtn=button("Restore file"); modeRow.addView(restoreBtn,new LinearLayout.LayoutParams(0,WRAP(),1));
        content.addView(modeRow);

        LinearLayout stats=row();
        wrongTv=stat("Wrong: 0"); triesTv=stat("Tries: 0/5"); hintTv=stat("Hints: 0"); scoreTv=stat("Score: 1000");
        stats.addView(wrongTv,new LinearLayout.LayoutParams(0,WRAP(),1)); stats.addView(triesTv,new LinearLayout.LayoutParams(0,WRAP(),1));
        stats.addView(hintTv,new LinearLayout.LayoutParams(0,WRAP(),1)); stats.addView(scoreTv,new LinearLayout.LayoutParams(0,WRAP(),1)); content.addView(stats);

        clueTv=tv("Every 3 wrong guesses reveals a clue.",12,false); clueTv.setTextColor(Color.rgb(221,214,254)); content.addView(clueTv);

        guessInput=new AutoCompleteTextView(this); guessInput.setHint("Type a character name…"); guessInput.setSingleLine(true);
        content.addView(guessInput,new LinearLayout.LayoutParams(-1,WRAP()));
        Button guessBtn=button("GUESS"); content.addView(guessBtn);
        Button newBtn=button("NEW ROUND"); content.addView(newBtn);

        targetTv=tv("",16,true); targetTv.setTextColor(Color.YELLOW); content.addView(targetTv);
        guessesBox=new LinearLayout(this); guessesBox.setOrientation(LinearLayout.VERTICAL); content.addView(guessesBox);
        status=tv("Waiting…",12,false); status.setTextColor(Color.LTGRAY); content.addView(status);

        guessBtn.setOnClickListener(v->submitGuess());
        guessInput.setOnEditorActionListener((v,id,e)->{submitGuess();return true;});
        newBtn.setOnClickListener(v->newRound());
        historyBtn.setOnClickListener(v->showHistory());
        statsBtn.setOnClickListener(v->showStats());
        restoreBtn.setOnClickListener(v->openRestore());
        difficultySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ if(target!=null && !finished){difficulty=modes[pos]; saveNow();} }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
    }

    int WRAP(){return LinearLayout.LayoutParams.WRAP_CONTENT;}
    TextView tv(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(4,10,4,10);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setPadding(0,6,0,6);return l;}
    TextView stat(String s){TextView t=tv(s,11,false);t.setGravity(Gravity.CENTER);return t;}

    void loadCharacters(){
        loadArcCatalog();
        try(InputStream in=getAssets().open("characters.json")){
            String s=new String(readAll(in),StandardCharsets.UTF_8); JSONArray a=new JSONArray(s);
            for(int i=0;i<a.length();i++) characters.add(new CharacterData(a.getJSONObject(i)));
            status.setText("Database loaded: "+characters.size()+" characters • arc catalog through "+(arcCatalog.isEmpty()?"159":arcCatalog.get(arcCatalog.size()-1)));
        }catch(Exception e){status.setText("Database error: "+e.getMessage());}
    }
    void loadArcCatalog(){
        try(InputStream in=getAssets().open("arcs.json")){
            String s=new String(readAll(in),StandardCharsets.UTF_8); JSONArray a=new JSONArray(s);
            arcCatalog.clear();
            for(int i=0;i<a.length();i++) arcCatalog.add(a.getJSONObject(i).optString("name"));
        }catch(Exception ignored){}
    }
    byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    ArrayList<String> getNames(){ArrayList<String> n=new ArrayList<>();for(CharacterData c:characters)n.add(c.name);return n;}

    void loadSaveOrNew(){
        try{
            String raw=readPublicSave();
            if(raw!=null){loadBundle(new JSONObject(raw)); status.setText("Loaded from Download • "+SAVE_FILE); render(); return;}
        }catch(Exception ignored){}
        newRound();
    }

    CharacterData find(String q){
        String n=norm(q); if(n.isEmpty())return null;
        for(CharacterData c:characters)if(norm(c.name).equals(n))return c;
        for(CharacterData c:characters)if(norm(c.name).contains(n)||n.contains(norm(c.name)))return c;
        return null;
    }
    String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]","").replaceAll("\\s+"," ").trim();}

    void newRound(){
        if(characters.isEmpty())return;
        ArrayList<CharacterData> pool=new ArrayList<>();
        for(CharacterData c:characters)if(!recent.contains(c.name))pool.add(c);
        if(pool.isEmpty())pool.addAll(characters);
        target=pool.get(new Random().nextInt(pool.size())); guesses.clear(); wrong=0; hints=0; score=1000; finished=false; lastHint=""; usedHintCats.clear();
        difficulty=(String)difficultySpinner.getSelectedItem(); targetTv.setText("");
        guessInput.setText(""); render(); saveNow();
    }

    void submitGuess(){
        if(target==null)return;
        if(finished){toast("Start a new round first.");return;}
        CharacterData c=find(guessInput.getText().toString());
        if(c==null){toast("Choose a character from the database.");return;}
        for(CharacterData g:guesses)if(g.name.equals(c.name)){toast("Already guessed.");return;}
        guesses.add(c);
        boolean win=norm(c.name).equals(norm(target.name));
        if(win){finish(true);return;}
        wrong++;score=Math.max(0,score-50);
        if(wrong>=MAX_TRIES){finish(false);return;}
        if(wrong%3==0)unlockHint();
        guessInput.setText(""); guessInput.dismissDropDown();
        render(); saveNow(); // physical file is updated after every guess too
    }

    void unlockHint(){
        String[] fields={"gender","nationality","race","status","location","affiliation","arc","power","description"};
        String[] labels={"Gender","Nationality","Race","Status","Location","Affiliation","Arc","Power Type","Description"};
        for(int i=0;i<fields.length;i++){
            if(usedHintCats.contains(fields[i])) continue;
            usedHintCats.add(fields[i]);
            String value="";
            switch(fields[i]){
                case "arc": value=target.arc; break;
                case "location": value=target.location; break;
                case "nationality": value=target.nationality; break;
                case "affiliation": value=target.affiliation; break;
                case "status": value=target.status; break;
                case "gender": value=target.gender; break;
                case "race": value=target.race; break;
                case "power": value=target.power; break;
            }
            if(value==null||value.isEmpty()) value="No data";
            lastHint=labels[i]+": "+value;
            hints++;
            return;
        }
    }

    void finish(boolean win){
        finished=true;
        HistoryEntry h=new HistoryEntry();h.character=target.name;h.difficulty=difficulty;h.win=win;h.wrong=wrong;h.guesses=guesses.size();h.hints=hints;h.score=score;
        h.date=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date());history.add(0,h);recent.add(target.name);
        saveNow();
        render();
        targetTv.setText(win?"✓ Correct: "+target.name:"✗ Answer: "+target.name);
        toast(win?"Round saved to Download!":"5 tries used — round saved to Download!");
    }

    void render(){
        wrongTv.setText("Wrong: "+wrong);triesTv.setText("Tries: "+guesses.size()+"/"+MAX_TRIES);
        hintTv.setText("Hints: "+hints);scoreTv.setText("Score: "+score);
        clueTv.setText(lastHint.isEmpty()?"Every 3 wrong guesses reveals a clue.":"Clue: "+lastHint);
        guessesBox.removeAllViews();
        for(int i=0;i<guesses.size();i++){
            CharacterData g=guesses.get(i);
            StringBuilder b=new StringBuilder();
            b.append("Guess ").append(i+1).append(": ").append(g.name);
            b.append("\n").append(field("Gender",g.gender,result(g.gender,target.gender)));
            b.append("\n").append(field("Nationality",g.nationality,result(g.nationality,target.nationality)));
            b.append("\n").append(field("Race",g.race,result(g.race,target.race)));
            b.append("\n").append(field("Status",g.status,result(g.status,target.status)));
            b.append("\n").append(field("Location",g.location,result(g.location,target.location)));
            b.append("\n").append(field("Affiliation",g.affiliation,result(g.affiliation,target.affiliation)));
            b.append("\n").append(field("Arc",g.arc,arcResult(g)));
            b.append("\n").append(field("Power Type",g.power,result(g.power,target.power)));
            b.append("\n").append(field("Description",g.description,result(g.description,target.description)));
            TextView card=tv(b.toString(),13,true);
            card.setBackgroundColor(Color.rgb(25,29,38)); card.setPadding(12,12,12,12);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,WRAP());p.setMargins(0,5,0,5);guessesBox.addView(card,p);
        }
        status.setText(finished?"Saved to Download automatically.":"Every guess is saved automatically.");
    }
    String field(String n,String v,String r){return n+": "+(v==null||v.isEmpty()?"No data":v)+"  "+r;}
    String result(String a,String b){if(a==null||a.isEmpty()||b==null||b.isEmpty())return "?";return norm(a).equals(norm(b))?"✓":"✗";}
    String arcResult(CharacterData g){if(norm(g.arc).equals(norm(target.arc)))return "✓";int d=target.arcOrder-g.arcOrder;return d>0?"↑":"↓";}

    JSONObject bundle()throws JSONException{
        JSONObject o=new JSONObject();o.put("version",1);o.put("game","tenk-native-file-save");o.put("savedAt",System.currentTimeMillis());
        JSONObject st=new JSONObject();st.put("target",characterJson(target));st.put("wrong",wrong);st.put("hints",hints);st.put("score",score);st.put("lastHint",lastHint);st.put("difficulty",difficulty);st.put("finished",finished);
        JSONArray gs=new JSONArray();for(CharacterData c:guesses)gs.put(characterJson(c));st.put("guesses",gs);o.put("state",st);
        JSONArray hs=new JSONArray();for(HistoryEntry h:history)hs.put(h.json());o.put("history",hs);
        JSONArray rs=new JSONArray();for(String r:recent)rs.put(r);o.put("recent",rs);return o;
    }
    JSONObject characterJson(CharacterData c)throws JSONException{JSONObject o=new JSONObject();o.put("name",c.name);o.put("arc",c.arc);o.put("arcOrder",c.arcOrder);o.put("location",c.location);o.put("affiliation",c.affiliation);o.put("status",c.status);o.put("gender",c.gender);o.put("race",c.race);o.put("nationality",c.nationality);o.put("power",c.power);o.put("description",c.description);return o;}

    void loadBundle(JSONObject o)throws JSONException{
        JSONObject st=o.getJSONObject("state");target=new CharacterData(st.getJSONObject("target"));wrong=st.optInt("wrong");hints=st.optInt("hints");score=st.optInt("score",1000);lastHint=st.optString("lastHint","");difficulty=st.optString("difficulty","Beginner");finished=st.optBoolean("finished",false);
        guesses.clear();JSONArray gs=st.optJSONArray("guesses");if(gs!=null)for(int i=0;i<gs.length();i++)guesses.add(new CharacterData(gs.getJSONObject(i)));
        history.clear();JSONArray hs=o.optJSONArray("history");if(hs!=null)for(int i=0;i<hs.length();i++){JSONObject x=hs.getJSONObject(i);HistoryEntry h=new HistoryEntry();h.character=x.optString("character");h.difficulty=x.optString("difficulty","Beginner");h.win=x.optBoolean("win");h.wrong=x.optInt("wrong");h.guesses=x.optInt("guesses");h.hints=x.optInt("hints");h.score=x.optInt("score");h.date=x.optString("date");history.add(h);}
        recent.clear();JSONArray rs=o.optJSONArray("recent");if(rs!=null)for(int i=0;i<rs.length();i++)recent.add(rs.optString(i));
        difficultySpinner.setSelection(Math.max(0,Arrays.asList(modes).indexOf(difficulty)));
    }

    String readPublicSave(){
        Uri u=findDownloadFile(SAVE_FILE);if(u==null)return null;
        try(InputStream in=getContentResolver().openInputStream(u)){return new String(readAll(in),StandardCharsets.UTF_8);}catch(Exception e){return null;}
    }
    Uri findDownloadFile(String name){
        String[] p={MediaStore.Downloads._ID};
        try(Cursor c=getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,p,MediaStore.Downloads.DISPLAY_NAME+"=?",new String[]{name},null)){
            if(c!=null&&c.moveToFirst())return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,String.valueOf(c.getLong(0)));
        }catch(Exception ignored){}
        return null;
    }
    boolean writeDownload(String name,String raw){
        try{
            Uri u=findDownloadFile(name);
            if(u==null){
                ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,"application/json");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);
                u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            }
            if(u==null)return false;
            try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){if(out==null)return false;out.write(raw.getBytes(StandardCharsets.UTF_8));out.flush();}
            return true;
        }catch(Exception e){return false;}
    }
    void saveNow(){
        try{
            String raw=bundle().toString(2);
            // The backup is written first, then the current save. This keeps a previous copy if the second write fails.
            String old=readPublicSave();if(old!=null)writeDownload(BACKUP_FILE,old);
            boolean ok=writeDownload(SAVE_FILE,raw);
            status.setText(ok?"Saved automatically to Download/"+SAVE_FILE:"Save failed — check storage access");
        }catch(Exception e){status.setText("Save error: "+e.getMessage());}
    }

    void openRestore(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,44);
    }
    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);if(r!=44||c!=RESULT_OK||d==null)return;
        try(InputStream in=getContentResolver().openInputStream(d.getData())){
            loadBundle(new JSONObject(new String(readAll(in),StandardCharsets.UTF_8)));render();saveNow();toast("Restored and saved to Download.");
        }catch(Exception e){toast("Restore failed: invalid save file.");}
    }

    void showStats(){
        StringBuilder s=new StringBuilder();
        s.append("Difficulty statistics\n\n");
        for(String mode:modes){
            int games=0,wins=0,totalWrong=0,totalHints=0,totalScore=0;
            for(HistoryEntry h:history) if(mode.equals(h.difficulty)){
                games++; if(h.win)wins++; totalWrong+=h.wrong; totalHints+=h.hints; totalScore+=h.score;
            }
            s.append(mode).append(": ").append(games).append(" games");
            if(games>0){
                s.append(" • win ").append((wins*100)/games).append("%");
                s.append(" • avg wrong ").append(String.format(Locale.US,"%.1f",(double)totalWrong/games));
                s.append(" • avg hints ").append(String.format(Locale.US,"%.1f",(double)totalHints/games));
                s.append(" • avg score ").append(String.format(Locale.US,"%.1f",(double)totalScore/games));
            }
            s.append("\n");
        }
        new android.app.AlertDialog.Builder(this).setTitle("Statistics").setMessage(s.toString()).setPositiveButton("Close",null).show();
    }

    void showHistory(){
        StringBuilder s=new StringBuilder("Completed rounds: "+history.size()+"\n\n");
        int n=0;for(HistoryEntry h:history){if(n++>=100)break;s.append(h.date).append("  ").append(h.win?"✓":"✗").append("  ").append(h.character).append("  ").append(h.difficulty).append("  score ").append(h.score).append("\n");}
        new android.app.AlertDialog.Builder(this).setTitle("History").setMessage(s.length()>0?s.toString():"No completed rounds yet.").setPositiveButton("Close",null).show();
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
