package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("oneclick")
public interface OneClickConfig extends Config
{
	@ConfigItem(
		keyName = "idPairs",
		name = "Item Pairs",
		description = "Format: Source:Target (e.g. Feather:Arrow shaft OR 314:52)",
		position = 1
	)
	default String idPairs()
	{
		return "Feather:Arrow shaft\n314:52";
	}

	@ConfigItem(
		keyName = "clickType",
		name = "Click Mode",
		description = "Try changing this if items highlight instead of combining.",
		position = 2
	)
	default ClickMode clickMode()
	{
		return ClickMode.MODERN;
	}

	@ConfigItem(
		keyName = "debug",
		name = "Debug Messages",
		description = "Show chat messages to help fix issues.",
		position = 3
	)
	default boolean debug()
	{
		return true;
	}

	enum ClickMode
	{
		MODERN, // WIDGET_TARGET_ON_WIDGET (Standard OSRS)
		LEGACY  // ITEM_USE_ON_ITEM (Common on RSPS)
	}
}
