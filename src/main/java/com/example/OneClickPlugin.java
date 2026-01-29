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
	description = "Combines configured items in one click via Invocation",
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

	private Method invokeMenuActionMethod;

	@Provides
	OneClickConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OneClickConfig.class);
	}

	@Override
	protected void startUp()
	{
		findInvokeMethod();
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

		Map<String, String> pairs = parseConfig();
		String targetIdentifier = null;
		String clickedIdStr = String.valueOf(clickedId);

		// Logic to find if this item is in our list
		if (pairs.containsKey(clickedIdStr)) targetIdentifier = pairs.get(clickedIdStr);
		else if (pairs.containsKey(clickedName)) targetIdentifier = pairs.get(clickedName);
		else if (pairs.containsValue(clickedIdStr)) targetIdentifier = getKeyByValue(pairs, clickedIdStr);
		else if (pairs.containsValue(clickedName)) targetIdentifier = getKeyByValue(pairs, clickedName);

		if (targetIdentifier == null) return;

		int sourceSlot = clickedSlot;
		int sourceId = clickedId;
		String sourceName = clickedName; 
		
		int targetSlot = -1;
		int targetId = -1;
		String targetName = "";

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
				targetName = tname;
				break;
			}
		}

		if (targetSlot != -1)
		{
			// WE FOUND THE PAIR.
			
			// 1. Stop the original click from happening (so we don't double click)
			event.consume();

			final int fTargetId = targetId;
			if (config.debug())
			{
				clientThread.invokeLater(() -> 
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Invoking: " + sourceId + " -> " + fTargetId, null));
			}

			// 2. Invoke "Use" on Source Item (This selects it)
			// Param0=Slot, Param1=Widget, Opcode=ITEM_USE(38 or 33), ID=ItemId, Option="Use", Target=Name
			invoke(sourceSlot, event.getParam1(), MenuAction.ITEM_USE.getId(), sourceId, "Use", sourceName);

			// 3. Invoke "Use On" Target Item
			// Opcode changes based on Legacy/Modern setting
			int useOpcode = (config.clickMode() == OneClickConfig.ClickMode.LEGACY) 
				? MenuAction.ITEM_USE_ON_ITEM.getId() 
				: MenuAction.WIDGET_TARGET_ON_WIDGET.getId();

			invoke(targetSlot, event.getParam1(), useOpcode, targetId, "Use", sourceName + " -> " + targetName);
		}
	}

	private void invoke(int param0, int param1, int opcode, int id, String option, String target)
	{
		if (invokeMenuActionMethod == null) 
		{
			if (config.debug()) System.out.println("Invoke Method Not Found!");
			return;
		}

		try
		{
			// Invoke the method: (param0, param1, opcode, id, option, target, mouseX, mouseY)
			invokeMenuActionMethod.invoke(client, param0, param1, opcode, id, option, target, 0, 0);
		}
		catch (Exception e)
		{
			if (config.debug()) System.out.println("Invoke Error: " + e.getMessage());
		}
	}

	private void findInvokeMethod()
	{
		try
		{
			// 1. Try standard name first
			invokeMenuActionMethod = client.getClass().getMethod("invokeMenuAction", int.class, int.class, int.class, int.class, String.class, String.class, int.class, int.class);
			return;
		}
		catch (Exception e) {}

		// 2. Search by Signature (The "Fingerprint" method)
		// We look for ANY method that takes (int, int, int, int, String, String, int, int)
		for (Method m : client.getClass().getDeclaredMethods())
		{
			Class<?>[] params = m.getParameterTypes();
			if (params.length == 8 
				&& params[0] == int.class && params[1] == int.class 
				&& params[2] == int.class && params[3] == int.class
				&& params[4] == String.class && params[5] == String.class
				&& params[6] == int.class && params[7] == int.class)
			{
				m.setAccessible(true);
				invokeMenuActionMethod = m;
				if (config.debug()) System.out.println("Found Invoke Method via Signature: " + m.getName());
				return;
			}
		}
		
		// 3. Fallback: Some clients exclude mouse coordinates (6 args)
		for (Method m : client.getClass().getDeclaredMethods())
		{
			Class<?>[] params = m.getParameterTypes();
			if (params.length == 6
				&& params[0] == int.class && params[1] == int.class 
				&& params[2] == int.class && params[3] == int.class
				&& params[4] == String.class && params[5] == String.class)
			{
				m.setAccessible(true);
				invokeMenuActionMethod = m;
				if (config.debug()) System.out.println("Found Invoke Method (6 args): " + m.getName());
				return;
			}
		}
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
