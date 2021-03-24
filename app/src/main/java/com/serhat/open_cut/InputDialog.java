package com.serhat.open_cut;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;



public class InputDialog extends AlertDialog{
    private EditText mEditText = null;

    public interface InputDialogListener{
        public void onInputEntered(String input);
    }

    public InputDialog(String title, String message, Activity activity, InputDialogListener inputDialogListener){
        this(title, message, null, true, false, activity, inputDialogListener);
    }

    public InputDialog(String title, String message, String text, boolean editable, boolean password, Activity activity, InputDialogListener inputDialogListener){
        super(activity);

        mEditText = new EditText(activity);

        if(text != null)
            mEditText.setText(text);

        mEditText.setEnabled(editable);
        mEditText.setMaxHeight(250);

        if(password)
            mEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        this.setTitle(title);
        this.setMessage(message);
        this.setView(mEditText);

        final InputDialogListener listener = inputDialogListener;

        this.setButton("Ok", new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id){
                if(listener != null)
                    listener.onInputEntered(mEditText.getText() + "");
            }
        });

        this.setButton2(activity.getString(R.string.cancel_dialog), new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id){
                dialog.dismiss();
            }
        });
    }
}