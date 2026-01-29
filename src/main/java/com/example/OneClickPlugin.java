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
import net.runelite.client.callback.ClientThread;
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
	private ClientThread clientThread;

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
		// 1. Filter Check
		Widget widget = client.getWidget(event.getParam1());
		if (widget == null) return;
		
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null) return;
		
		int clickedSlot = event.getParam0();
		Item[] items = inventory.getItems();

		if (clickedSlot < 0 || clickedSlot >= items.length) return;

		Item clickedItem = items[clickedSlot];
		int clickedId = clickedItem.getId();
		
		if (clickedId <= -1 || clickedId == 6512) return;

		String clickedName = Text.removeTags(client.getItemDefinition(clickedId).getName()).toLowerCase();

		// 2. Debug Print (Optional)
		if (config.debug())
		{
			// Safe debug without lambda errors
			System.out.println("Clicked: " + clickedName + " (" + clickedId + ") Action: " + event.getMenuAction());
		}

		// 3. Find Match
		Map<String, String> pairs = parseConfig();
		String targetIdentifier = null;
		String clickedIdStr = String.valueOf(clickedId);

		if (pairs.containsKey(clickedIdStr)) targetIdentifier = pairs.get(clickedIdStr);
		else if (pairs.containsKey(clickedName)) targetIdentifier = pairs.get(clickedName);
		else if (pairs.containsValue(clickedIdStr)) targetIdentifier = getKeyByValue(pairs, clickedIdStr);
		else if (pairs.containsValue(clickedName)) targetIdentifier = getKeyByValue(pairs, clickedName);

		if (targetIdentifier == null) return;

		// 4. Find Other Item Slot
		int sourceSlot = clickedSlot;
		int sourceId = clickedId;
		int targetSlot = -1;
		int targetId = -1;

		boolean lookingForId = isNumeric(targetIdentifier);

		for (int i = 0; i < items.length; i++)
		{
			if (i == clickedSlot) continue;

			int tid = items[i].getId();
			String tname = Text.removeTags(client.getItemDefinition(tid).getName()).toLowerCase();

			boolean match = false;
			if (lookingForId && String.valueOf(tid).equals(targetIdentifier)) match = true;
			else if (!lookingForId && tname.equals(targetIdentifier)) match = true;

			if (match)
			{
				targetSlot = i;
				targetId = tid;
				break;
			}
		}

		if (targetSlot != -1)
		{
			final int finalTargetId = targetId;
			final int finalTargetSlot = targetSlot;
			final int widgetId = event.getParam1(); // Use the actual widget from the event

			if (config.debug())
			{
				clientThread.invokeLater(() -> 
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Combining -> " + finalTargetId + " (Slot " + finalTargetSlot + ")", null));
			}

			// 5. EXECUTE SWAP
			
			// A. Force Select the Source Item
			setSelectedInventoryItem(sourceSlot, sourceId);

			// B. Change the Click Event to target the Other Item
			if (config.clickMode() == OneClickConfig.ClickMode.LEGACY)
			{
				event.getMenuEntry().setType(MenuAction.ITEM_USE_ON_ITEM);
			}
			else
			{
				event.getMenuEntry().setType(MenuAction.WIDGET_TARGET_ON_WIDGET);
			}

			event.getMenuEntry().setParam0(targetSlot);
			event.getMenuEntry().setParam1(widgetId); // Use exact widget ID from the click
			event.getMenuEntry().setIdentifier(targetId);
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
			// Reflection fail
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
		return str != null && str.matches("-?\\d+");
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
