package com.touchnight.sillytavern;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import java.net.*;
import java.io.*;
import com.touchnight.sillytavern.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    //We just want one instance of node running in the background.
    public static boolean _startedNodeAlready=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if( !_startedNodeAlready ) {
            _startedNodeAlready=true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //The path where we expect the node project to be at runtime.
                    String nodeDir=getApplicationContext().getFilesDir().getAbsolutePath()+"/sillytavern";
                    // Fix tokenizers.js and tiktoken_bg.cjs after extraction
                    fixTokenizersFile(new File(nodeDir));
                    fixTiktokenFile(new File(nodeDir));
                    if (wasAPKUpdated()) {
                        //Recursively delete any existing sillytavern.
                        File nodeDirReference=new File(nodeDir);
                        if (nodeDirReference.exists()) {
                            deleteFolderRecursively(new File(nodeDir));
                        }
                        //Copy the node project from assets into the application's data path.
                        copyAssetFolder(getApplicationContext().getAssets(), "sillytavern", nodeDir);

                        saveLastUpdateTime();
                        

                    }
                    startNodeWithArguments(new String[]{"node",
                            nodeDir+"/server.js"
                    });
                }
            }).start();
        }

        final Button buttonVersions = (Button) findViewById(R.id.btVersions);
        final TextView textViewVersions = (TextView) findViewById(R.id.tvVersions);

        buttonVersions.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("StaticFieldLeak")
            public void onClick(View v) {

                //Network operations should be done in the background.
                new AsyncTask<Void,Void,String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        String nodeResponse="";
                        try {
                            URL localNodeServer = new URL("http://localhost:8000/");
                            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(localNodeServer.openStream()));
                            String inputLine;
                            while ((inputLine = in.readLine()) != null)
                                nodeResponse=nodeResponse+inputLine;
                            in.close();
                        } catch (Exception ex) {
                            nodeResponse=ex.toString();
                        }
                        return nodeResponse;
                    }
                    @Override
                    protected void onPostExecute(String result) {
                        textViewVersions.setText(result);
                    }
                }.execute();

            }
        });
    }

    private boolean wasAPKUpdated() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        long previousLastUpdateTime = prefs.getLong("NODEJS_MOBILE_APK_LastUpdateTime", 0);
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return (lastUpdateTime != previousLastUpdateTime);
    }

    private void saveLastUpdateTime() {
        long lastUpdateTime = 1;
        try {
            PackageInfo packageInfo = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
            lastUpdateTime = packageInfo.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("NODEJS_MOBILE_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("NODEJS_MOBILE_APK_LastUpdateTime", lastUpdateTime);
        editor.commit();
    }

    private static boolean deleteFolderRecursively(File file) {
        try {
            boolean res=true;
            for (File childFile : file.listFiles()) {
                if (childFile.isDirectory()) {
                    res &= deleteFolderRecursively(childFile);
                } else {
                    res &= childFile.delete();
                }
            }
            res &= file.delete();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (files.length==0) {
                //If it's a file, it won't have any assets "inside" it.
                res &= copyAsset(assetManager,
                        fromAssetPath,
                        toPath);
            } else {
                new File(toPath).mkdirs();
                for (String file : files)
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native Integer startNodeWithArguments(String[] arguments);
    
    private static final String TAG = "MainActivity";
    
    private void fixTokenizersFile(File rootDir) {
        try {
            File tokenizersFile = new File(rootDir, "node_modules/sillytavern-transformers/src/tokenizers.js");
            if (tokenizersFile.exists()) {
                android.util.Log.i(TAG, "Fixing tokenizers.js file at: " + tokenizersFile.getAbsolutePath());
                
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(tokenizersFile))) {
                    String line;
                    int lineNum = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNum++;
                        
                        // 修复第1202行的问题正则表达式
                        if (lineNum == 1202 && line.contains("/^\\p{Cc}|\\p{Cf}|\\p{Co}|\\p{Cs}$/u")) {
                            // 替换为传统字符类正则表达式
                            line = line.replace(
                                "/^\\p{Cc}|\\p{Cf}|\\p{Co}|\\p{Cs}$/u.test(char)",
                                "/^[\\x00-\\x1F\\x7F-\\x9F]|[\\xAD\\u0600-\\u0605\\u061C\\u06DD\\u070F\\u08E2\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u206F\\uFEFF\\uFFF9-\\uFFFB]|[\\uD800-\\uDFFF]|[\\uFFF0-\\uFFFF]$/.test(char)"
                            );
                            android.util.Log.i(TAG, "Fixed regex at line " + lineNum);
                        }
                        
                        // 修复第1396行的问题正则表达式
                        if (lineNum == 1396 && line.contains("\\p{L}+")) {
                            // 替换为传统字符类正则表达式，\p{L} 表示任何字母，\p{N} 表示任何数字
                            line = line.replace(
                                "/'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+/gu",
                                "/'s|'t|'re|'ve|'m|'ll|'d| ?[A-Za-z\\u00C0-\\u00FF\\u0100-\\u017F\\u0180-\\u024F\\u0370-\\u03FF\\u0400-\\u04FF\\u0500-\\u052F\\u0530-\\u058F\\u0590-\\u05FF\\u0600-\\u06FF\\u0750-\\u077F\\u0900-\\u097F\\u0980-\\u09FF\\u0A00-\\u0A7F\\u0A80-\\u0AFF\\u0B00-\\u0B7F\\u0B80-\\u0BFF\\u0C00-\\u0C7F\\u0C80-\\u0CFF\\u0D00-\\u0D7F\\u0D80-\\u0DFF\\u0E00-\\u0E7F\\u0E80-\\u0EFF\\u0F00-\\u0FFF\\u1000-\\u109F\\u10A0-\\u10FF\\u1100-\\u11FF\\u1E00-\\u1EFF\\u1F00-\\u1FFF\\u2000-\\u206F\\u2070-\\u209F\\u20A0-\\u20CF\\u20D0-\\u20FF\\u2100-\\u214F\\u2150-\\u218F\\u2190-\\u21FF\\u2200-\\u22FF\\u2300-\\u23FF\\u2400-\\u243F\\u2440-\\u245F\\u2460-\\u24FF\\u2500-\\u257F\\u2580-\\u259F\\u25A0-\\u25FF\\u2600-\\u26FF\\u2700-\\u27BF\\u2800-\\u28FF\\u2900-\\u297F\\u2980-\\u29FF\\u2A00-\\u2AFF\\u2B00-\\u2BFF\\u2C00-\\u2C5F\\u2C60-\\u2C7F\\u2C80-\\u2CFF\\u2D00-\\u2D2F\\u2D30-\\u2D7F\\u2D80-\\u2DDF\\u2E00-\\u2E7F\\u2E80-\\u2EFF\\u2F00-\\u2FDF\\u3000-\\u303F\\u3040-\\u309F\\u30A0-\\u30FF\\u3100-\\u312F\\u3130-\\u318F\\u3190-\\u319F\\u31A0-\\u31BF\\u31F0-\\u31FF\\u3200-\\u32FF\\u3300-\\u33FF\\u3400-\\u4DBF\\u4DC0-\\u4DFF\\u4E00-\\u9FFF\\uA000-\\uA48F\\uA490-\\uA4CF\\uA700-\\uA71F\\uA720-\\uA7FF\\uA800-\\uA82F\\uA840-\\uA87F\\uA880-\\uA8DF\\uA900-\\uA92F\\uA930-\\uA95F\\uA960-\\uA97F\\uAC00-\\uD7AF\\uF900-\\uFAFF]+| ?[0-9\\u0660-\\u0669\\u06F0-\\u06F9\\u07C0-\\u07C9\\u0966-\\u096F\\u09E6-\\u09EF\\u0A66-\\u0A6F\\u0AE6-\\u0AEF\\u0B66-\\u0B6F\\u0BE6-\\u0BEF\\u0C66-\\u0C6F\\u0CE6-\\u0CEF\\u0D66-\\u0D6F\\u0E50-\\u0E59\\u0ED0-\\u0ED9\\u0F20-\\u0F29\\u1040-\\u1049\\u1090-\\u1099\\u17E0-\\u17E9\\u1810-\\u1819\\u1946-\\u194F\\u19D0-\\u19D9\\u1A80-\\u1A89\\u1A90-\\u1A99\\u1B50-\\u1B59\\u1BB0-\\u1BB9\\u1C40-\\u1C49\\u1C50-\\u1C59\\uA620-\\uA629\\uA8D0-\\uA8D9\\uA900-\\uA909\\uA9D0-\\uA9D9\\uAA50-\\uAA59\\uABF0-\\uABF9\\uFF10-\\uFF19]+|\\s+(?!\\S)|\\s+/gu"
                            );
                            android.util.Log.i(TAG, "Fixed regex at line " + lineNum);
                        }
                        
                        content.append(line).append("\n");
                    }
                }
                
                // 写回修改后的内容
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tokenizersFile))) {
                    writer.write(content.toString());
                }
                
                android.util.Log.i(TAG, "Successfully patched tokenizers.js");
            } else {
                android.util.Log.e(TAG, "Could not find tokenizers.js at: " + tokenizersFile.getAbsolutePath());
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error fixing tokenizers.js file", e);
        }
    }

    private void fixTiktokenFile(File rootDir) {
        try {
            File tiktokenFile = new File(rootDir, "node_modules/tiktoken/tiktoken_bg.cjs");
            if (tiktokenFile.exists()) {
                android.util.Log.i(TAG, "Fixing tiktoken_bg.cjs file at: " + tiktokenFile.getAbsolutePath());
                
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(tiktokenFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 修复 TextDecoder 初始化，移除 "fatal" 选项
                        if (line.contains("new lTextDecoder") && line.contains("fatal")) {
                            // 将 let cachedTextDecoder = new lTextDecoder('utf-8', { ignoreBOM: true, fatal: true }); 
                            // 替换为 let cachedTextDecoder = new lTextDecoder('utf-8', { ignoreBOM: true });
                            line = line.replace("{ ignoreBOM: true, fatal: true }", "{ ignoreBOM: true }");
                            android.util.Log.i(TAG, "Fixed lTextDecoder instantiation in tiktoken_bg.cjs: " + line);
                        }
                        
                        content.append(line).append("\n");
                    }
                }
                
                // 写回修改后的内容
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tiktokenFile))) {
                    writer.write(content.toString());
                }
                
                android.util.Log.i(TAG, "Successfully patched tiktoken_bg.cjs");
            } else {
                android.util.Log.e(TAG, "Could not find tiktoken_bg.cjs at: " + tiktokenFile.getAbsolutePath());
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error fixing tiktoken_bg.cjs file", e);
        }
    }
}