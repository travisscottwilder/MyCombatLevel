package com.mycombatlevel;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("mycombatlevel")
public interface ExampleConfig extends Config {

	enum HighlightMode {
		ENEMIES_ARE_NEAR,
		ALWAYS
	}


	@ConfigSection(
			name = "Highlighting",
			description = "Settings for when to highlight and how.",
			position = 20
	)
	String heading_highlighting = "heading_highlighting";
	@ConfigSection(
			name = "Yourself",
			description = "Settings for your character.",
			position = 30
	)
	String heading_yourself = "heading_yourself";

	@ConfigSection(
			name = "Attachable Enemies",
			description = "Settings for your enemies.",
			position = 40
	)
	String heading_enemies = "heading_enemies";

	@ConfigSection(
			name = "Minimap",
			description = "Settings for your minimap.",
			position = 50
	)
	String heading_minimap = "heading_minimap";






	@ConfigItem(
			keyName 	= "highlightInaSafeSpot",
			name 		= "Highlight When In A Safe Spot?",
			description = "When you are not in a PVP world, do you want to highlight even in a safe zone?",
			position 	= 10,
			section 	= heading_highlighting
	)
	default boolean highlightInaSafeSpot() {return true;}

	@ConfigItem(
			keyName 	= "highlightMode",
			name 		= "When",
			description = "When should highlighting be activated?",
			position 	= 20,
			section 	= heading_highlighting
	)
	default HighlightMode highlightMode() { return HighlightMode.ENEMIES_ARE_NEAR; }






	@ConfigItem(
			keyName 	= "playerSafeColor",
			name 		= "You're safe",
			description = "When attackable enemies are nearby, this is<br>the color you glow when you are in a safe zone.",
			position 	= 20,
			section 	= heading_yourself
	)
	default Color playerSafeColor() {return Color.GREEN;}
	@ConfigItem(
			keyName 	= "playerAttackableColor",
			name 		= "You're attackable",
			description = "When attackable enemies are nearby this is<br>the color you glow when you are NOT in a safe zone<br>and can be attacked.",
			position 	= 30,
			section 	= heading_yourself
	)
	default Color playerAttackableColor() {return Color.RED;}
	@ConfigItem(
			keyName 	= "yourPlayersGlowWidth",
			name 		= "Your Glow Width",
			description = "How thick do we draw your players width for the glowing?",
			position 	= 40,
			section 	= heading_yourself
	)
	default int yourPlayersGlowWidth() {return 3;}






	@ConfigItem(
			keyName 	= "enemyEasyColor",
			name 		= "Enemy Easy",
			description = "",
			position 	= 50,
			section 	= heading_enemies
	)
	default Color enemyEasyColor() {return Color.GREEN;}
	@ConfigItem(
			keyName 	= "enemyEqual",
			name 		= "Your equal",
			description = "",
			position 	= 60,
			section 	= heading_enemies
	)
	default Color enemyEqual() { return new Color(50, 218, 150);}
	@ConfigItem(
			keyName 	= "enemyChallengingColor",
			name 		= "Enemy Challenging",
			description = "",
			position 	= 70,
			section 	= heading_enemies
	)
	default Color enemyChallengingColor() {return Color.YELLOW;}
	@ConfigItem(
			keyName 	= "enemyHardColor",
			name 		= "Enemy Hard",
			description = "",
			position 	= 70,
			section 	= heading_enemies

	)
	default Color enemyHardColor() {return Color.ORANGE;}
	@ConfigItem(
			keyName 	= "enemyExtremeColor",
			name 		= "Enemy EXTREME",
			description = "",
			position 	= 80,
			section 	= heading_enemies

	)
	default Color enemyExtremeColor() {return new Color(170, 0, 0);}
	@ConfigItem(
			keyName 	= "enemiesGlowWidth",
			name 		= "Enemy Glow Width",
			description = "How thick do we draw the width of enemie's glow?",
			position 	= 80,
			section 	= heading_enemies
	)
	default int enemiesGlowWidth() {return 1;}





	@ConfigItem(
			keyName 	= "dotBackgroundColor",
			name 		= "Dot Background",
			description = "On the minimap attackable enemies have a background color, this is it.",
			position 	= 10,
			section 	= heading_minimap
	)
	default Color dotBackgroundColor() {return Color.BLACK;}
	@ConfigItem(
			keyName 	= "dotWidth",
			name 		= "Dot's width",
			description = "How thick do we make the dots on the minimap?",
			position 	= 20,
			section 	= heading_minimap
	)
	default int dotWidth() {return 8;}
	@ConfigItem(
			keyName 	= "minimapRefreshRate",
			name 		= "Refresh Rate (ms)",
			description = "How often do we recalculate the enemies location on the minimap?<Br>Allowed value: 30ms - 1000ms",
			position 	= 30,
			section 	= heading_minimap
	)
	@Range(min = 30, max = 1000)
	default int minimapRefreshRate() {return 75;}







} //System.out.println("text here");
