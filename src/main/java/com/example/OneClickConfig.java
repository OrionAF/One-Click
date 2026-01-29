package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("oneclick")
public interface OneClickConfig extends Config
{
	@ConfigItem(
		keyName = "idPairs",
		name = "Item Pairs (Names or IDs)",
		description = "Format: Source:Target. Example: 'Feather:Arrow shaft' OR '314:52'.",
		position = 1
	)
	default String idPairs()
	{
		return "Feather:Arrow shaft\n314:52";
	}

	@ConfigItem(
		keyName = "debug",
		name = "Debug Messages",
		description = "Show chat messages to help fix issues.",
		position = 2
	)
	default boolean debug()
	{
		return true;
	}
}
