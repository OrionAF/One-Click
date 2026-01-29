package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
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
	tags = {"fletching", "automation"}
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
		// 1. Filter out non-inventory clicks
		if (event.getMenuAction() != MenuAction.ITEM_USE && 
			event.getMenuAction() != MenuAction.ITEM_FIRST_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_SECOND_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_THIRD_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_FOURTH_OPTION &&
			event.getMenuAction() != MenuAction.ITEM_FIFTH_OPTION)
		{
			return;
		}

		// 2. Validate Inventory
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null) return;
		
		Item[] items = inventory.getItems();
		int clickedSlot = event.getParam0();
		if (clickedSlot < 0 || clickedSlot >= items.length) return;

		Item clickedItem = items[clickedSlot];
		int clickedId = clickedItem.getId();
		String clickedName = Text.removeTags(client.getItemDefinition(clickedId).getName()).toLowerCase();

		// 3. Check Config for Matches (ID or Name)
		Map<String, String> pairs = parseConfig();
		String targetIdentifier = null; // This will be the Name or ID of the target
		
		String clickedIdStr = String.valueOf(clickedId);

		// Check if we clicked the Source (Key) -> Find Target (Value)
		if (pairs.containsKey(clickedIdStr)) targetIdentifier = pairs.get(clickedIdStr);
		else if (pairs.containsKey(clickedName)) targetIdentifier = pairs.get(clickedName);
		
		// Check if we clicked the Target (Value) -> Find Source (Key) [Reverse check]
		else if (pairs.containsValue(clickedIdStr)) targetIdentifier = getKeyByValue(pairs, clickedIdStr);
		else if (pairs.containsValue(clickedName)) targetIdentifier = getKeyByValue(pairs, clickedName);

		if (targetIdentifier == null) return; // No match found

		// 4. Find the slots for Source and Target in inventory
		int sourceSlot = -1;
		int sourceId = -1;
		int targetSlot = -1;
		int targetId = -1;

		boolean lookingForId = isNumeric(targetIdentifier);

		for (int i = 0; i < items.length; i++)
		{
			Item item = items[i];
			int tempId = item.getId();
			String tempName = Text.removeTags(client.getItemDefinition(tempId).getName()).toLowerCase();

			// Check Source (What we are using)
			if (sourceSlot == -1)
			{
				boolean match = (tempId == clickedId); // If we clicked source
				if (!match) // If we clicked target, source is the other one
				{
					if (lookingForId && String.valueOf(tempId).equals(targetIdentifier)) match = true;
					else if (!lookingForId && tempName.equals(targetIdentifier)) match = true;
				}

				if (match)
				{
					// Wait, if we clicked 'Target', then 'targetIdentifier' is actually the Source.
					// Let's simplify: We need one slot to be the Clicked item, one to be the Other item.
				}
			}
		}

		// Re-do Search Logic simplistically
		// We have "Clicked Item" (A). We found "Other Item" (B) in config.
		// We want to trigger A -> B.
		
		// Determine which is Source and which is Target based on config order doesn't strictly matter
		// for A->B or B->A interactions usually, but let's stick to config Key -> Value.
		
		String configTarget = pairs.getOrDefault(clickedIdStr, pairs.get(clickedName));
		boolean isSource = (configTarget != null); // If true, we clicked Source.
		
		String otherItemIdentifier = isSource ? configTarget : getKeyByValue(pairs, clickedIdStr);
		if (otherItemIdentifier == null) otherItemIdentifier = getKeyByValue(pairs, clickedName);

		// Find the "Other" item
		int otherSlot = -1;
		int otherId = -1;
		
		boolean otherIsId = isNumeric(otherItemIdentifier);

		for (int i = 0; i < items.length; i++)
		{
			// Skip the item we just clicked (unless we are using item on itself?)
			if (i == clickedSlot) continue;

			int tid = items[i].getId();
			String tname = Text.removeTags(client.getItemDefinition(tid).getName()).toLowerCase();

			if (otherIsId)
			{
				if (String.valueOf(tid).equals(otherItemIdentifier))
				{
					otherSlot = i;
					otherId = tid;
					break;
				}
			}
			else
			{
				if (tname.equals(otherItemIdentifier))
				{
					otherSlot = i;
					otherId = tid;
					break;
				}
			}
		}

		if (otherSlot != -1)
		{
			// We have both items.
			// source = Clicked Item, target = Other Item
			int finalSourceSlot = clickedSlot;
			int finalSourceId = clickedId;
			int finalTargetSlot = otherSlot;
			int finalTargetId = otherId;

			debugMsg("Combining " + finalSourceId + " -> " + finalTargetId);

			// EXECUTE
			setSelectedInventoryItem(finalSourceSlot, finalSourceId);
			
			event.getMenuEntry().setType(MenuAction.WIDGET_TARGET_ON_WIDGET);
			event.getMenuEntry().setParam0(finalTargetSlot);
			event.getMenuEntry().setParam1(WidgetInfo.INVENTORY.getId());
			event.getMenuEntry().setIdentifier(finalTargetId);
		}
		else
		{
			debugMsg("Found config match but could not find the other item in inventory.");
		}
	}

	private void setSelectedInventoryItem(int slot, int id)
	{
		try
		{
			invokeMethod(client, "setSelectedItemWidget", int.class, WidgetInfo.INVENTORY.getId());
			invokeMethod(client, "setSelectedItemSlot", int.class, slot);
			invokeMethod(client, "setSelectedItemId", int.class, id);
		}
		catch (Exception e)
		{
			debugMsg("Reflection failed: " + e.getMessage());
		}
	}

	private void invokeMethod(Object target, String methodName, Class<?> paramType, int paramValue) throws Exception
	{
		Method method = target.getClass().getMethod(methodName, paramType);
		method.invoke(target, paramValue);
	}

	private String getKeyByValue(Map<String, String> map, String value)
	{
		for (Map.Entry<String, String> entry : map.entrySet())
		{
			if (entry.getValue().equals(value)) return entry.getKey();
		}
		return null;
	}

	private boolean isNumeric(String str)
	{
		if (str == null) return false;
		return str.matches("-?\\d+");
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

	private void debugMsg(String msg)
	{
		if (config.debug())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[OneClick] " + msg, null);
		}
	}
}
