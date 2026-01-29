package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@PluginDescriptor(
	name = "One Click",
	description = "Combines configured items in one click",
	tags = {"fletching", "herblore", "macro", "automation"}
)
public class OneClickPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OneClickConfig config;

	@Provides
	OneClickConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OneClickConfig.class);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Only run if we are clicking an item in the inventory
		if (event.getMenuAction() != MenuAction.ITEM_USE && 
			event.getMenuAction() != MenuAction.ITEM_FIRST_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_SECOND_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_THIRD_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_FOURTH_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_FIFTH_OPTION)
		{
			return;
		}

		Widget widget = client.getWidget(event.getParam1());
		if (widget == null)
		{
			return;
		}
		
		int clickedItemId = event.getItemId();
		if (clickedItemId == -1) return;

		Map<String, String> pairs = parseConfig();
		
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null) return;
		
		Item[] items = inventory.getItems();
		String clickedName = null;
		int clickedSlot = event.getParam0();
		
		if (clickedSlot >= 0 && clickedSlot < items.length)
		{
			clickedName = Text.removeTags(client.getItemDefinition(items[clickedSlot].getId()).getName());
		}

		if (clickedName == null) return;

		String targetName = null;
		
		// Check Source -> Target
		if (pairs.containsKey(clickedName.toLowerCase()))
		{
			targetName = pairs.get(clickedName.toLowerCase());
		}
		// Check Target -> Source (Reverse)
		else if (pairs.containsValue(clickedName.toLowerCase()))
		{
			for (Map.Entry<String, String> entry : pairs.entrySet())
			{
				if (entry.getValue().equals(clickedName.toLowerCase()))
				{
					targetName = entry.getValue();
					clickedName = entry.getKey();
					break;
				}
			}
		}

		if (targetName == null) return;

		int sourceSlot = -1;
		int sourceId = -1;
		int targetSlot = -1;
		int targetId = -1;

		for (int i = 0; i < items.length; i++)
		{
			String itemName = Text.removeTags(client.getItemDefinition(items[i].getId()).getName()).toLowerCase();
			
			if (sourceSlot == -1 && itemName.equals(clickedName.toLowerCase()))
			{
				sourceSlot = i;
				sourceId = items[i].getId();
			}
			else if (targetSlot == -1 && itemName.equals(targetName.toLowerCase()))
			{
				targetSlot = i;
				targetId = items[i].getId();
			}

			if (sourceSlot != -1 && targetSlot != -1) break;
		}

		if (sourceSlot != -1 && targetSlot != -1)
		{
			// 1. Set the Selected Item using Reflection (bypasses missing API methods)
			setSelectedInventoryItem(sourceSlot, sourceId);

			// 2. Modify the event using getMenuEntry() (Fixes the setters error)
			event.getMenuEntry().setType(MenuAction.WIDGET_TARGET_ON_WIDGET);
			event.getMenuEntry().setParam0(targetSlot);
			event.getMenuEntry().setParam1(WidgetInfo.INVENTORY.getId());
			event.getMenuEntry().setIdentifier(targetId);
		}
	}

	private void setSelectedInventoryItem(int slot, int id)
	{
		try
		{
			// Use reflection to call the hidden methods on the Client
			invokeMethod(client, "setSelectedItemWidget", int.class, WidgetInfo.INVENTORY.getId());
			invokeMethod(client, "setSelectedItemSlot", int.class, slot);
			invokeMethod(client, "setSelectedItemId", int.class, id);
		}
		catch (Exception e)
		{
			// If reflection fails, we just ignore it. 
			// The plugin might still work depending on server-side packet validation.
		}
	}

	private void invokeMethod(Object target, String methodName, Class<?> paramType, int paramValue)
	{
		try
		{
			Method method = target.getClass().getMethod(methodName, paramType);
			method.invoke(target, paramValue);
		}
		catch (Exception ignored)
		{
			// Method not found or accessible
		}
	}

	private Map<String, String> parseConfig()
	{
		Map<String, String> pairs = new HashMap<>();
		String configStr = config.idPairs();
		if (configStr == null) return pairs;

		for (String line : configStr.split("[\n,]"))
		{
			String[] parts = line.split(":");
			if (parts.length == 2)
			{
				pairs.put(parts[0].trim().toLowerCase(), parts[1].trim().toLowerCase());
			}
		}
		return pairs;
	}
}
