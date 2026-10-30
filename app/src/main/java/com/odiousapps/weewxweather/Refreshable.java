package com.odiousapps.weewxweather;

// Tabs implement this so a TV remote, which can't pull to refresh,
// can trigger the same refresh as the swipe gesture
interface Refreshable
{
	void refresh();
}
