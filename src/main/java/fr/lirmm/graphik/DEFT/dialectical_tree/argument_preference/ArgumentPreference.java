package fr.lirmm.graphik.DEFT.dialectical_tree.argument_preference;

import fr.lirmm.graphik.DEFT.dialectical_tree.Argument;

public interface ArgumentPreference {
	public static final int NOT_DEFEAT = 0;
	public static final int BLOCKING_DEFEAT = 1;
	public static final int PROPER_DEFEAT = 2;
	
	public int compare(Argument attacker, Argument attackee);
}
