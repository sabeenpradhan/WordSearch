package sabeen.wordsearch;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Async Task class for getting data from the API
 * Created by sabeen on 7/23/16.
 */
public class GetChallengeAsyncTask extends AsyncTask<String, Void, Void> {
    private SharedPreferences.Editor editor;
    private SharedPreferences preferences;
    private Context context;
    private ProgressDialog progressDialog;

    public GetChallengeAsyncTask(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("Challenge", context.MODE_PRIVATE);
        editor = preferences.edit();
    }
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Loading...");
        progressDialog.show();
    }

    @Override
    protected Void doInBackground(String... params) {
        editor.putBoolean("firstTime", false);
        URL url = null;
        try {
            url = new URL(params[0]);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String str;
            Integer count = 0;
            while ((str = in.readLine()) != null) {
                count++;
//              saving each line of obtained data to SharedPreference which is one JSONObject
                editor.putString("JSONObject" + count, str);
            }
//            saving number of JSONObject that is number of puzzles
            editor.putInt("maxPuzzle",count);
            editor.commit();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if(progressDialog.isShowing()){
            progressDialog.dismiss();
        }
    }
}
