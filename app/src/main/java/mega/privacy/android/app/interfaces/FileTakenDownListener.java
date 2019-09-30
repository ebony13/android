package mega.privacy.android.app.interfaces;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import mega.privacy.android.app.R;
import mega.privacy.android.app.utils.Util;

public interface FileTakenDownListener {
    /**
     * The method of negative button event
     */
    void fileTakenDownNegativeButtonProcess();

    class FileTakenDownNotificationHandler {
        private static AlertDialog alertDialogTakenDown = null;

        public static void showTakenDownDialog(Activity activity, final FileTakenDownListener listener) {

            if (activity == null) {
                return;
            }

            if (alertDialogTakenDown != null && alertDialogTakenDown.isShowing()) {
                return;
            }

            if (activity.isFinishing()) {
                return;
            }

            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
            dialogBuilder.setTitle(activity.getString(R.string.general_error_word))
                         .setMessage(activity.getString(R.string.video_takendown_error)).setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                    listener.fileTakenDownNegativeButtonProcess();
                }
            });
            alertDialogTakenDown = dialogBuilder.create();

            alertDialogTakenDown.show();
        }
    }
}
