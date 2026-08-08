package com.mycombatlevel;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.client.eventbus.Subscribe;



import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;


@PluginDescriptor(
		name = "My Combat Level"
)
public class ExamplePlugin extends Plugin
{
	@Inject
	private Client client;

	private int combatLevel;
	private int attackMin;
	private int attackMax;

	private boolean isThereSomeoneToAttack;
	private boolean isPvpWorld;
	private boolean inWilderness;
	private boolean inSafeZone;


	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ModelOutlineRenderer modelOutlineRenderer;


	private final MyOverlay overlay = new MyOverlay();

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
	}


	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{

		MenuEntry menuEntry = event.getMenuEntry();

		if (menuEntry.getType() != MenuAction.PLAYER_FIRST_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_SECOND_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_THIRD_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_FOURTH_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_FIFTH_OPTION)
		{
			return;
		}

		int identifier = menuEntry.getIdentifier();

		Player player = null;

		for (Player p : client.getPlayers())
		{
			if (p != null && p.getId() == identifier)
			{
				player = p;
				break;
			}
		}

		if (player == null)
		{
			return;
		}

		if (player == client.getLocalPlayer())
		{
			return;
		}

		int combatLevel = player.getCombatLevel();

		if (combatLevel >= attackMin && combatLevel <= attackMax)
		{

			String target = menuEntry.getTarget();

			if (menuEntry.getOption().equals("Attack")) {
				// Remove existing RuneScape color tags
				target = target.replaceAll("<col=[^>]*>", "");
				target = target.replaceAll("</col>", "");
			}

			menuEntry.setTarget(
					"<col=ff8000>! " + target + "</col>"
			);
		}
	}



	private class MyOverlay extends Overlay
	{
		MyOverlay()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_WIDGETS);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			if (client.getGameState() != GameState.LOGGED_IN) { return null; }

			Player localPlayer = client.getLocalPlayer();
			int combatLevel 	= localPlayer.getCombatLevel();
			int wildernessLevel = 0;
			inSafeZone 	= true;
			isPvpWorld 	= WorldType.isPvpWorld(client.getWorldType());
			inWilderness = client.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
			isThereSomeoneToAttack = false;


			if (isPvpWorld) {
				Widget safeZoneWidget 	= client.getWidget(WidgetInfo.PVP_WORLD_SAFE_ZONE);
				inSafeZone 				= safeZoneWidget != null && !safeZoneWidget.isHidden();
				wildernessLevel 		= 15;
			}

			if(inWilderness) {
				Widget wildernessWidget = client.getWidget(WidgetInfo.PVP_WILDERNESS_LEVEL);
				if (wildernessWidget != null) {
					String text = wildernessWidget.getText();
					text 		= text.replace("Level: ", "");
					if (text != null && !text.isEmpty()) {
						int brIndex = text.indexOf("<br>");

						if (brIndex != -1) {
							text = text.substring(0, brIndex);
						}

						wildernessLevel += Integer.parseInt(text);
						inSafeZone = false;
					}
				}
			}

			attackMin = Math.max(3, combatLevel - wildernessLevel);
			attackMax = combatLevel + wildernessLevel;


			for (Player player : client.getPlayers()) {
				if (player == localPlayer)
				{
					continue;
				}

				int playerCombatLevel = player.getCombatLevel();

				if (playerCombatLevel >= attackMin && playerCombatLevel <= attackMax)
				{
					isThereSomeoneToAttack = true;



					// Orange outline around player in the game
					modelOutlineRenderer.drawOutline(
							player,
							2,
							Color.ORANGE,
							4
					);






					LocalPoint localPoint = player.getLocalLocation();

					if (localPoint == null)
					{
						continue;
					}


					Point minimapPoint = Perspective.localToMinimap(client, localPoint);

					if (minimapPoint != null)
					{
						int x = (int) minimapPoint.getX();
						int y = (int) minimapPoint.getY();

						graphics.setColor(Color.BLACK);
						graphics.fillOval(
								x - 2,
								y - 1,
								5,
								5
						);

						graphics.setColor(Color.ORANGE);
						graphics.fillOval(
								x - 1,
								y,
								3,
								3
						);
					}


				}
			}

			return null;
		}
	}


	private class MySceneOverlay extends Overlay
	{
		MySceneOverlay()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_SCENE);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			Player localPlayer = client.getLocalPlayer();

			if(isThereSomeoneToAttack && (isPvpWorld || inWilderness)) {

				Color myColor = Color.GREEN;

				if (!inSafeZone) {
					myColor = Color.RED;
				}

				if (localPlayer != null) {
					modelOutlineRenderer.drawOutline(
							localPlayer,
							2,
							myColor,
							4
					);
				}
			}

			for (Player player : client.getPlayers())
			{
				if (player == localPlayer)
				{
					continue;
				}

				int playerCombatLevel = player.getCombatLevel();

				if (playerCombatLevel >= attackMin && playerCombatLevel <= attackMax)
				{
					modelOutlineRenderer.drawOutline(
							player,
							2,
							Color.ORANGE,
							4
					);
				}
			}

			return null;
		}
	}
}