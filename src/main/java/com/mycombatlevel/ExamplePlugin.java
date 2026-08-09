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
	@Inject
	private ClientThread clientThread;





	@Provides
	ExampleConfig provideConfig(ConfigManager configManager) {return configManager.getConfig(ExampleConfig.class);}





	private int attackMin;
	private int attackMax;
	private boolean validationToDraw;

	private Player localPlayer;
	private List<Player> allEnemies = new ArrayList<>();

	private int myCombatLevel;
	private boolean inSafeZone		= true;
	private int wildernessLevel 	= 0;
	private int lastWildernessLevel = 0;
	private boolean isPvpWorld 		= false;
	private final List<EnemyMinimapMarker> enemyMinimapMarkers = new ArrayList<>();

	private final overlay_minimap 	overlay_minimap 	= new overlay_minimap();
	private final overlay_screen 	overlay_screen 		= new overlay_screen();

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> minimapTask;





	@Override
	protected void startUp() {
		overlayManager.add(overlay_minimap);
		overlayManager.add(overlay_screen);
		recalcCachedConfigs();

		calculateLocalAttackRange();
		allEnemies	= calculateAllEnemies();
		isPvpWorld 	= WorldType.isPvpWorld(client.getWorldType());
		startMinimapThread();
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay_minimap);
		overlayManager.remove(overlay_screen);

		if (minimapTask != null) {
			minimapTask.cancel(true);
			minimapTask = null;
		}
	}





	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals("mycombatlevel")) { return; }
		recalcCachedConfigs();
		calculateMinimMapCoords();
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
		calculateMinimMapCoords();
	}

	@Subscribe
	private void onPlayerDespawned(PlayerDespawned event) { allEnemies.remove(event.getPlayer());calculateLocalAttackRange();calculateMinimMapCoords(); }

	@Subscribe
	private void onStatChanged(StatChanged event) { calculateLocalAttackRange();calculateAllEnemies(); }

	@Subscribe
	public void onWorldChanged(WorldChanged event) { isPvpWorld = WorldType.isPvpWorld(client.getWorldType());calculateAllEnemies(); }





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

		if (minimapTask == null || minimapTask.isCancelled() || minimapTask.isDone()) {
			System.out.println("NO THREAD STARTING IT DUDE");
			calculateAllEnemies();
			startMinimapThread();
		}

        boolean inWilderness 	= client.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
		inSafeZone				= !inWilderness; //if we are not in the wilderness then we assume we are safe
		validationToDraw 		= (inWilderness || cache_highlightInaSafeSpot);

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
		if(wildernessLevel == 0){ if(cache_highlightInaSafeSpot){ wildernessLevel = 1; } }

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
					graphics.setColor(cache_dotBackgroundColor);
					graphics.fillOval(marker.x - 1, marker.y, cache_dotWidth, cache_dotWidth);

					graphics.setColor(marker.color);
					graphics.fillOval(marker.x, marker.y, cache_dotWidth - 2, cache_dotWidth - 2);
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
							player, cache_enemiesGlowWidth,
							getEnemyColor(playerCombatLevel), cache_enemiesGlowWidth * 3);
					}
				}
			}



            if(validationToDraw && (cache_highlightMode == ExampleConfig.HighlightMode.ALWAYS || !allEnemies.isEmpty())) {
				if (localPlayer != null) {
					Color myColor;
					if (!inSafeZone) {	myColor = cache_playerAttackableColor;}
					else{ 				myColor = cache_playerSafeColor; }

					modelOutlineRenderer.drawOutline(
						localPlayer, cache_yourPlayersGlowWidth,
						myColor, cache_yourPlayersGlowWidth*3
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
			colorChosen = cache_enemyEqual;
		}else if(difference >= 3){
			colorChosen = cache_enemyEasyColor;
		}else{
			if(difference < -6){
				colorChosen = cache_enemyExtremeColor;
			}else if(difference < -3){
				colorChosen = cache_enemyHardColor;
			}
			else  {
				colorChosen = cache_enemyChallengingColor;
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

		try {
			for (Player player : allEnemies) {
				LocalPoint localPoint = player.getLocalLocation();

				if (localPoint == null) { 	continue; }
				if(client == null) { 		continue; }

				Point minimapPoint = Perspective.localToMinimap(client, localPoint);
				if (minimapPoint != null) {
					int x = minimapPoint.getX();
					int y = minimapPoint.getY();

					if (cache_dotWidth > 10) {
						x -= 4;
						y -= 3;
					} else if (cache_dotWidth > 8) {
						x -= 3;
						y -= 2;
					} else if (cache_dotWidth > 6) {
						x -= 2;
						y -= 1;
					} else if (cache_dotWidth > 3) {
						y -= 1;
						x -= 1;
					}

					enemyMinimapMarkers.add(new EnemyMinimapMarker(x, y, getEnemyColor(player.getCombatLevel())));
				}
			}

		}catch (Throwable e) { System.out.println("THREAD ERROR CAUGHT"); }
	}



	private void startMinimapThread(){
		if (minimapTask != null) {
			minimapTask.cancel(true);
			minimapTask = null;
		}

		minimapTask = executor.scheduleAtFixedRate(() -> {
			clientThread.invokeLater(() -> {
				calculateMinimMapCoords();
			});

		}, 0, cache_minimapRefreshRate, TimeUnit.MILLISECONDS);
	}




	private static class EnemyMinimapMarker {
		int x;
		int y;
		Color color;

		EnemyMinimapMarker(int x, int y, Color color) {
			this.x 		= x;
			this.y 		= y;
			this.color 	= color;
		}
	}


	private boolean cache_highlightInaSafeSpot;
	private ExampleConfig.HighlightMode cache_highlightMode;
	private Color cache_playerSafeColor;
	private Color cache_playerAttackableColor;
	private int cache_yourPlayersGlowWidth;
	private Color cache_enemyEasyColor;
	private Color cache_enemyEqual;
	private Color cache_enemyChallengingColor;
	private Color cache_enemyHardColor;
	private Color cache_enemyExtremeColor;
	private int cache_enemiesGlowWidth;
	private Color cache_dotBackgroundColor;
	private int cache_dotWidth;
	private int cache_minimapRefreshRate;
	private void recalcCachedConfigs(){
		cache_highlightInaSafeSpot	= config.highlightInaSafeSpot();
		cache_highlightMode			= config.highlightMode();
		cache_playerSafeColor		= config.playerSafeColor();
		cache_playerAttackableColor	= config.playerAttackableColor();
		cache_yourPlayersGlowWidth	= config.yourPlayersGlowWidth();
		cache_enemyEasyColor		= config.enemyEasyColor();
		cache_enemyEqual			= config.enemyEqual();
		cache_enemyChallengingColor	= config.enemyChallengingColor();
		cache_enemyHardColor		= config.enemyHardColor();
		cache_enemyExtremeColor 	= config.enemyExtremeColor();
		cache_enemiesGlowWidth 		= config.enemiesGlowWidth();
		cache_dotBackgroundColor 	= config.dotBackgroundColor();
		cache_dotWidth 				= config.dotWidth();
		cache_minimapRefreshRate 	= config.minimapRefreshRate();
	}

}//end of main class ||  System.out.println("text here");



