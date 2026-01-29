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
		description = "List of item pairs to combine in format Name:Name (e.g. Feather:Arrow shaft). One per line or comma separated.",
		position = 1
	)
	default String idPairs()
	{
		return "Feather:Arrow shaft\nHeadless arrow:Bronze arrowtips\nKnife:Oak logs";
	}
}
