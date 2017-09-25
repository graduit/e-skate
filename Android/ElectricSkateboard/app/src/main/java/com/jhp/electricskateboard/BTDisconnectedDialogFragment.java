package com.jhp.electricskateboard;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Created by J Park on 31/01/2017.
 */

public class BTDisconnectedDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        String dialogTitle = "Bluetooth device not connected";
        builder.setTitle(dialogTitle)
                .setMessage("Please reopen the activity to try and connect")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // If the user is not connected to anything, simply close the activity,
                        // and force the user to reopen the activity to restart the connection attempt process.
                        getActivity().finish();
                    }
                });

        AlertDialog dialog = builder.create();
        return dialog;
    }
}