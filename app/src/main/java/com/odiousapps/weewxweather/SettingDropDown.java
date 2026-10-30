package com.odiousapps.weewxweather;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.ListAdapter;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

// A settings drop-down that opens with its current setting highlighted, so a TV remote
// or keyboard starts from there instead of from nothing
class SettingDropDown extends MaterialAutoCompleteTextView
{
	public SettingDropDown(Context context)
	{
		super(context);
	}

	public SettingDropDown(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public SettingDropDown(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
	}

	@Override
	public void showDropDown()
	{
		super.showDropDown();

		ListAdapter adapter = getAdapter();
		if(adapter == null)
			return;

		String current = getText().toString();
		for(int i = 0; i < adapter.getCount(); i++)
		{
			if(current.equals(String.valueOf(adapter.getItem(i))))
			{
				// Opening the popup posts a clear of the list selection, so select after it
				int position = i;
				post(() ->
				{
					if(isPopupShowing())
						setListSelection(position);
				});
				return;
			}
		}
	}

	// Moving past either end of the list clears the selection and hands the keys back to
	// the text box for typing, these boxes can't be typed in so stay on the end item
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		ListAdapter adapter = getAdapter();
		if(isPopupShowing() && adapter != null)
		{
			int position = getListSelection();
			if((keyCode == KeyEvent.KEYCODE_DPAD_DOWN && position == adapter.getCount() - 1) ||
			   (keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0))
				return true;
		}

		return super.onKeyDown(keyCode, event);
	}
}
