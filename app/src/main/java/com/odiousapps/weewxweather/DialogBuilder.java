package com.odiousapps.weewxweather;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;

// An AlertDialog.Builder that makes dialogs usable with a TV remote: a dialog is its own
// window, so it needs its own focus ring, and the remote needs a button focused to start
// from. Builder.show() goes through create(), so both ways of showing a dialog get this.
class DialogBuilder extends AlertDialog.Builder
{
	DialogBuilder(Context context)
	{
		super(context);
	}

	@Override
	public AlertDialog create()
	{
		AlertDialog dialog = super.create();

		if(WeeWXApp.isTV())
			dialog.setOnShowListener(d -> setupForTV(dialog));

		return dialog;
	}

	private static void setupForTV(AlertDialog dialog)
	{
		for(int which : new int[]{DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE,
								  DialogInterface.BUTTON_NEUTRAL})
		{
			Button button = dialog.getButton(which);
			if(button != null && button.getVisibility() == View.VISIBLE)
			{
				button.requestFocus();
				break;
			}
		}

		if(dialog.getWindow() != null)
			TvFocusRing.attach(dialog.getWindow().getDecorView());
	}
}
