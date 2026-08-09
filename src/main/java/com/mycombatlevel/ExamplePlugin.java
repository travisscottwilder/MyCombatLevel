package com.mycombatlevel;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


@PluginDescriptor(name = "My Combat Level")
public class ExamplePlugin extends Plugin {
	@Inject
	private Client client;
	@Inject
	private ExampleConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ModelOutlineRenderer modelOutlineRenderer;





	@Provides
	ExampleConfig provideConfig(ConfigManager configManager) {return configManager.getConfig(ExampleConfig.class);}





	private int attackMin;
	private int attackMax;
	private boolean validationToDraw;

	private Player localPlayer;
	private List<Player> allEnemies = new ArrayList<>();

	private boolean inSafeZone		= true;
	private int myCombatLevel;
	private int wildernessLevel 	= 0;
	private int lastWildernessLevel = 0;
	private boolean isPvpWorld 		= false;
	private final List<EnemyMinimapMarker> enemyMinimapMarkers = new ArrayList<>();

	private final overlay_minimap 	overlay_minimap 	= new overlay_minimap();
	private final overlay_screen 	overlay_screen 		= new overlay_screen();


	@Inject
	private ClientThread clientThread;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> minimapTask;





	@Override
	protected void startUp() {
		overlayManager.add(overlay_minimap);overlayManager.add(overlay_screen);

		calculateLocalAttackRange();
		allEnemies	= calculateAllEnemies();
		isPvpWorld 	= WorldType.isPvpWorld(client.getWorldType());

		startMinimapThread();
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay_minimap);overlayManager.remove(overlay_screen);

		if (minimapTask != null) {
			minimapTask.cancel(true);
			minimapTask = null;
		}
	}




	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals("mycombatlevel")) { return; }
		startMinimapThread();
	}





	@Subscribe
	private void onPlayerSpawned(PlayerSpawned event) {
		Player player = event.getPlayer();
		if(player == null){ 		return; }
		if(player == localPlayer) { return;}

		calculateLocalAttackRange();
		int playerCombatLevel = player.getCombatLevel();
		if (playerCombatLevel >= attackMin && playerCombatLevel <= attackMax) { allEnemies.add(player); }
	}

	@Subscribe
	private void onPlayerDespawned(PlayerDespawned event) { allEnemies.remove(event.getPlayer());calculateLocalAttackRange(); }

	@Subscribe
	private void onStatChanged(StatChanged event) { calculateLocalAttackRange();calculateAllEnemies(); }

	@Subscribe
	public void onWorldChanged(WorldChanged event) {isPvpWorld = WorldType.isPvpWorld(client.getWorldType());calculateAllEnemies(); }





	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if(!validationToDraw){ return; }

		MenuEntry menuEntry = event.getMenuEntry();
		if (menuEntry.getType() != MenuAction.PLAYER_FIRST_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_SECOND_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_THIRD_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_FOURTH_OPTION
				&& menuEntry.getType() != MenuAction.PLAYER_FIFTH_OPTION) { return; }
		int identifier 	= menuEntry.getIdentifier();
		Player player 	= null;

		if(!allEnemies.isEmpty()) {
			for (Player p : allEnemies) {
				if (p != null && p.getId() == identifier) {
					player = p;
					break;
				}
			}
		}

		if (player == null) {			return; }
		if (player == localPlayer) {	return; }

		int playersCombatLevel = player.getCombatLevel();
		if (playersCombatLevel >= attackMin && playersCombatLevel <= attackMax) {

			String target = menuEntry.getTarget();
			if (menuEntry.getOption().equals("Attack")) {
				// Remove existing RuneScape color tags on the attack field to highlight the whole field
				target = target.replaceAll("<col=[^>]*>", "");
				target = target.replace("</col>", "");
			}

			Color playersColor 	= getEnemyColor(playersCombatLevel);
			String hex 			= String.format("%02X%02X%02X", playersColor.getRed(), playersColor.getGreen(), playersColor.getBlue());

			menuEntry.setTarget(
					"<col=" + hex + ">!" + target + "</col>"
			);
		}
	}



	@Subscribe
	private void onGameTick(GameTick event) {
        boolean inWilderness 	= client.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
		inSafeZone				= !inWilderness; //if we are not in the wilderness then we assume we are safe
		validationToDraw 		= (inWilderness || config.highlightInaSafeSpot());

		if (isPvpWorld) {
			Widget safeZoneWidget	= client.getWidget(InterfaceID.PvpIcons.PVPW_SAFE);
			inSafeZone 				= safeZoneWidget != null && !safeZoneWidget.isHidden();
			wildernessLevel 		= 15;

			if(!inSafeZone) { validationToDraw = true; }

		}else{ wildernessLevel 		= 1; }

		if(inWilderness) {
			inSafeZone 				= false;
			Widget wildernessWidget = client.getWidget(InterfaceID.PvpIcons.WILDERNESSLEVEL);

			if (wildernessWidget != null) {
				String text = wildernessWidget.getText();
				if (!text.isEmpty()) {
					text	= text.replace("Level: ", "");
					if (!text.isEmpty()) {
						int brIndex = text.indexOf("<br>");
						if (brIndex != -1) { text = text.substring(0, brIndex);}

						wildernessLevel += Integer.parseInt(text);
					}
				}
			}
		}

		//do our safe zone check
		if(wildernessLevel == 0){if(config.highlightInaSafeSpot()){ wildernessLevel = 1; }}

		//if our wilderness level changes then calc our wilderness specific stuff
		if(wildernessLevel != lastWildernessLevel) {
			calculateLocalAttackRange();
			allEnemies = calculateAllEnemies();
		}lastWildernessLevel = wildernessLevel;

		calculateMinimMapCoords();
	}




	private class overlay_minimap extends Overlay {
		overlay_minimap() { setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS); }

		@Override
		public Dimension render(Graphics2D graphics) {
			if (client.getGameState() != GameState.LOGGED_IN) { return null; }

			if(validationToDraw && !enemyMinimapMarkers.isEmpty()) {

				for (EnemyMinimapMarker marker : enemyMinimapMarkers) {

					graphics.setColor(config.dotBackgroundColor());
					graphics.fillOval(
							marker.x - 1,
							marker.y,
							config.dotWidth(),
							config.dotWidth()
					);

					graphics.setColor(marker.color);
					graphics.fillOval(
							marker.x,
							marker.y,
							config.dotWidth() - 2,
							config.dotWidth() - 2
					);
				}

			}

			return null;
		}
	}

	private class overlay_screen extends Overlay {
		overlay_screen() {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}

		@Override
		public Dimension render(Graphics2D graphics) {
			if (client.getGameState() != GameState.LOGGED_IN) { return null; }

			if(validationToDraw) {
				if (!allEnemies.isEmpty()) {
					for (Player player : allEnemies) {
						int playerCombatLevel = player.getCombatLevel();
						modelOutlineRenderer.drawOutline(
							player,
							config.enemiesGlowWidth(),
							getEnemyColor(playerCombatLevel),
							config.enemiesGlowWidth() * 3
						);
					}
				}
			}



            if(validationToDraw && (config.highlightMode() == ExampleConfig.HighlightMode.ALWAYS || !allEnemies.isEmpty())) {
				if (localPlayer != null) {
					Color myColor;
					if (!inSafeZone) {	myColor = config.playerAttackableColor();}
					else{ 				myColor = config.playerSafeColor(); }

					modelOutlineRenderer.drawOutline(
						localPlayer,
						config.yourPlayersGlowWidth(),
						myColor,
						config.yourPlayersGlowWidth()*3
					);
				}
			}

			return null;
		}
	}




	private Color getEnemyColor(int enemyLevel){
		int difference = (myCombatLevel - enemyLevel);
		Color colorChosen;

		//if our difference is positive then we are higher combat then them
		if(difference == 0 || difference == 1 || difference == 2){
			colorChosen = config.enemyEqual();
		}else if(difference >= 3){
			colorChosen = config.enemyEasyColor();
		}else{
			if(difference < -6){
				colorChosen = config.enemyExtremeColor();
			}else if(difference < -3){
				colorChosen = config.enemyHardColor();
			}
			else  {
				colorChosen = config.enemyChallengingColor();
			}
		}

		return colorChosen;
	}


	private List<Player> calculateAllEnemies(){
		var allPlayers 				= client.getTopLevelWorldView().players();
		allEnemies.clear();

		if (allPlayers != null && allPlayers.iterator().hasNext()) {
			for (Player player : allPlayers) {
				if (player == localPlayer) {continue;}

				if(player != null) {
					int playerCombatLevel = player.getCombatLevel();
					if (playerCombatLevel >= attackMin && playerCombatLevel <= attackMax) {
						allEnemies.add(player);
					}
				}
            }
		}

		calculateMinimMapCoords();
		return allEnemies;
	}


	private void calculateLocalAttackRange(){
		localPlayer		= client.getLocalPlayer();
		if(localPlayer == null){ return; }

		myCombatLevel	= localPlayer.getCombatLevel();
		attackMin 		= Math.max(3, myCombatLevel - wildernessLevel);
		attackMax 		= myCombatLevel + wildernessLevel;
		if(attackMin == attackMax){ attackMin = 0;attackMax = 0; }
	}


	private void calculateMinimMapCoords(){
		enemyMinimapMarkers.clear();

		for (Player player : allEnemies) {
			LocalPoint localPoint = player.getLocalLocation();

			if (localPoint == null) { continue; }

			Point minimapPoint = Perspective.localToMinimap(client, localPoint);
			if (minimapPoint != null) {
				int x = minimapPoint.getX();
				int y = minimapPoint.getY();

				if(config.dotWidth() > 10){
					x -= 4;
					y -= 3;
				}else if(config.dotWidth() > 8){
					x -= 3;
					y -= 2;
				}else if(config.dotWidth() > 6) {
					x -= 2;
					y -= 1;
				}else if(config.dotWidth() > 3){
					y -= 1;
					x -= 1;
				}

				enemyMinimapMarkers.add(new EnemyMinimapMarker(x, y, getEnemyColor(player.getCombatLevel())));
			}
		}
	}



	private void startMinimapThread(){
		if (minimapTask != null) {
			minimapTask.cancel(true);
		}

		minimapTask = executor.scheduleAtFixedRate(() -> {
			clientThread.invokeLater(() -> {
				calculateMinimMapCoords();
			});

		}, 0, config.minimapRefreshRate(), TimeUnit.MILLISECONDS);
	}




	private static class EnemyMinimapMarker {
		int x;
		int y;
		Color color;

		EnemyMinimapMarker(int x, int y, Color color) {
			this.x = x;
			this.y = y;
			this.color = color;
		}
	}


}//end of main class



