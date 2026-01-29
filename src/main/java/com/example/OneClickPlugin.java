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
import java.lang.reflect.Field;
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

		// DEBUG: Print to console
		if (config.debug())
		{
			System.out.println("Clicked: " + clickedName + " (" + clickedId + ")");
		}

		Map<String, String> pairs = parseConfig();
		String targetIdentifier = null;
		String clickedIdStr = String.valueOf(clickedId);

		// Match Logic
		if (pairs.containsKey(clickedIdStr)) targetIdentifier = pairs.get(clickedIdStr);
		else if (pairs.containsKey(clickedName)) targetIdentifier = pairs.get(clickedName);
		else if (pairs.containsValue(clickedIdStr)) targetIdentifier = getKeyByValue(pairs, clickedIdStr);
		else if (pairs.containsValue(clickedName)) targetIdentifier = getKeyByValue(pairs, clickedName);

		if (targetIdentifier == null) return;

		int sourceSlot = clickedSlot;
		int sourceId = clickedId;
		String sourceName = clickedName; // We need the name now too
		
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
			final int widgetId = event.getParam1();

			// 1. FORCE CLIENT STATE
			// We need to set ID, Slot, Widget, AND Name for the client to be happy.
			boolean success = setSelectedInventoryItem(sourceSlot, sourceId, sourceName);

			if (config.debug())
			{
				final String status = success ? "State Set!" : "Reflection Failed!";
				clientThread.invokeLater(() -> 
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "OneClick: " + status + " (" + sourceId + "->" + finalTargetId + ")", null));
			}

			// 2. MODIFY CLICK
			if (config.clickMode() == OneClickConfig.ClickMode.LEGACY)
			{
				event.getMenuEntry().setType(MenuAction.ITEM_USE_ON_ITEM);
			}
			else
			{
				event.getMenuEntry().setType(MenuAction.WIDGET_TARGET_ON_WIDGET);
			}

			event.getMenuEntry().setParam0(targetSlot);
			event.getMenuEntry().setParam1(widgetId);
			event.getMenuEntry().setIdentifier(targetId);
		}
	}

	private boolean setSelectedInventoryItem(int slot, int id, String name)
	{
		boolean success = false;
		try
		{
			// Try to find the methods (RuneLite API)
			invokeMethod(client, "setSelectedItemWidget", int.class, WidgetInfo.INVENTORY.getId());
			invokeMethod(client, "setSelectedItemSlot", int.class, slot);
			invokeMethod(client, "setSelectedItemId", int.class, id);
			
			// Some clients require the Name to be set too!
			// We try to capitalize it nicely just in case (e.g. "feather" -> "Feather")
			String niceName = Character.toUpperCase(name.charAt(0)) + name.substring(1);
			invokeMethod(client, "setSelectedItemName", String.class, niceName);
			
			success = true;
		}
		catch (Exception e)
		{
			// Reflection failed
		}

		// Try the brute force boolean as backup
		setClientItemSelected(true);

		return success;
	}

	private void setClientItemSelected(boolean selected)
	{
		try { invokeMethod(client, "setItemSelected", boolean.class, selected); } catch (Exception e) {}
		try { invokeMethod(client, "setItemSelected", int.class, selected ? 1 : 0); } catch (Exception e) {}
		try {
			Field f = client.getClass().getDeclaredField("isItemSelected");
			f.setAccessible(true);
			f.setBoolean(client, selected);
		} catch (Exception e) {}
	}

	private void invokeMethod(Object target, String methodName, Class<?> paramType, Object paramValue) throws Exception
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
