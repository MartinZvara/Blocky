import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;

/**
 * Created by Martin Zvara on 24.4.2014.
 */
public class PreprocessTask extends AsyncTask<Object, Object, String> {

    AlertDialog.Builder alertDialogBuilder;
    ProgressDialog progDailog;
    BlockyActivity blocky;

    public PreprocessTask(BlockyActivity activity){
        blocky = activity;

    }

    protected void onPreExecute() {
        super.onPreExecute();
        alertDialogBuilder = new AlertDialog.Builder(blocky);
        progDailog = new ProgressDialog(blocky);
        progDailog.setMessage("Spracovávam ...");
        progDailog.setIndeterminate(false);
        progDailog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progDailog.setCancelable(true);
        progDailog.show();
    }

    @Override
    protected String doInBackground(Object... o) {
        return blocky.preprocess();
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        progDailog.dismiss();
        openAlert(result);
    }

    protected void openAlert(String message) {

        alertDialogBuilder.setTitle("Info");
        alertDialogBuilder.setMessage(message);
        alertDialogBuilder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        // show alert
        alertDialog.show();
    }
}
