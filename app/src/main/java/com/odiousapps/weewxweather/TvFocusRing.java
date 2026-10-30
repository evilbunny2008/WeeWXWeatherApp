package com.odiousapps.weewxweather;

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;

// Draws a ring over whatever has focus, the default focus highlights are too faint to
// follow from across the room. Foregrounds the views already had are put back afterwards.
final class TvFocusRing
{
	private static final Object NO_FOREGROUND = new Object();

	private TvFocusRing()
	{}

	// Each window (the activity, every dialog) has its own focus, so attach to each one
	static void attach(View root)
	{
		root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) ->
		{
			if(oldFocus != null)
				remove(oldFocus);

			if(newFocus != null)
				add(newFocus);
		});

		// Whatever already had focus before we started listening
		View focus = root.findFocus();
		if(focus != null)
			add(focus);
	}

	private static void add(View view)
	{
		if(view.getTag(R.id.tv_focus_ring) != null)
			return;

		Drawable foreground = view.getForeground();
		view.setTag(R.id.tv_focus_ring, foreground == null ? NO_FOREGROUND : foreground);
		view.setForeground(ContextCompat.getDrawable(view.getContext(), R.drawable.tv_focus_ring));
	}

	private static void remove(View view)
	{
		Object saved = view.getTag(R.id.tv_focus_ring);
		if(saved == null)
			return;

		view.setForeground(saved == NO_FOREGROUND ? null : (Drawable)saved);
		view.setTag(R.id.tv_focus_ring, null);
	}
}
