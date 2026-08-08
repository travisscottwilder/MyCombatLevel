package com.mycombatlevel;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mycombatlevel")
public interface ExampleConfig extends Config
{
	@ConfigItem(
			keyName = "highlightPlayers",
			name = "Highlight Attackable Players",
			description = "Highlight players within your combat range"
	)
	default boolean highlightPlayers()
	{
		return true;
	}
}
