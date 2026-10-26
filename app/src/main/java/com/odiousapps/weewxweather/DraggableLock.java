package com.odiousapps.weewxweather;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Lets a floating swipe lock checkbox be dragged around its container.
 * A tap still toggles the checkbox, once the finger moves past the touch
 * slop the gesture becomes a drag and the click is cancelled.
 * The position is saved as a fraction of the container so it survives
 * rotation and different screen sizes.
 */
final class DraggableLock
{
	private DraggableLock() {}

	@SuppressLint("ClickableViewAccessibility")
	static void attach(View handle, String name)
	{
		// Move the wrapping layout, moving the checkbox inside it would get clipped
		View target = (View)handle.getParent();
		String keyX = "swipeLockPosX_" + name;
		String keyY = "swipeLockPosY_" + name;
		int slop = ViewConfiguration.get(handle.getContext()).getScaledTouchSlop();

		final float[] down = new float[4];
		final boolean[] dragging = {false};

		target.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
		{
			if(!dragging[0])
				restore(target, keyX, keyY);
		});

		handle.setOnTouchListener((v, ev) ->
		{
			switch(ev.getActionMasked())
			{
				case MotionEvent.ACTION_DOWN ->
				{
					down[0] = ev.getRawX();
					down[1] = ev.getRawY();
					down[2] = target.getTranslationX();
					down[3] = target.getTranslationY();
					dragging[0] = false;
					// Stop the ViewPager2 stealing horizontal drags
					v.getParent().requestDisallowInterceptTouchEvent(true);
					return false;
				}
				case MotionEvent.ACTION_MOVE ->
				{
					float dx = ev.getRawX() - down[0];
					float dy = ev.getRawY() - down[1];

					if(!dragging[0])
					{
						if(Math.hypot(dx, dy) < slop)
							return false;

						dragging[0] = true;

						// Clear the pressed state so the checkbox doesn't toggle on release
						MotionEvent cancel = MotionEvent.obtain(ev);
						cancel.setAction(MotionEvent.ACTION_CANCEL);
						v.onTouchEvent(cancel);
						cancel.recycle();
					}

					View parent = (View)target.getParent();
					target.setTranslationX(clamp(down[2] + dx, -target.getLeft(), parent.getWidth() - target.getRight()));
					target.setTranslationY(clamp(down[3] + dy, -target.getTop(), parent.getHeight() - target.getBottom()));
					return true;
				}
				case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
				{
					if(!dragging[0])
						return false;

					dragging[0] = false;
					save(target, keyX, keyY);
					return true;
				}
			}

			return dragging[0];
		});
	}

	private static void save(View target, String keyX, String keyY)
	{
		View parent = (View)target.getParent();
		int rangeX = parent.getWidth() - target.getWidth();
		int rangeY = parent.getHeight() - target.getHeight();

		if(rangeX > 0)
			KeyValue.putVar(keyX, clamp((target.getLeft() + target.getTranslationX()) / rangeX, 0f, 1f));

		if(rangeY > 0)
			KeyValue.putVar(keyY, clamp((target.getTop() + target.getTranslationY()) / rangeY, 0f, 1f));
	}

	private static void restore(View target, String keyX, String keyY)
	{
		View parent = (View)target.getParent();
		if(parent == null || target.getWidth() == 0 || target.getHeight() == 0)
			return;

		float fx = (float)KeyValue.readVar(keyX, -1f);
		float fy = (float)KeyValue.readVar(keyY, -1f);

		if(fx >= 0f)
			target.setTranslationX(fx * (parent.getWidth() - target.getWidth()) - target.getLeft());

		if(fy >= 0f)
			target.setTranslationY(fy * (parent.getHeight() - target.getHeight()) - target.getTop());
	}

	private static float clamp(float val, float min, float max)
	{
		return Math.clamp(max, min, val);
	}
}
