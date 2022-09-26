package net.ornithemc.mappingutils;

public enum PropagationDirection {

	NONE,
	UP,
	DOWN,
	BOTH;

	public boolean up() {
		return this == UP || this == BOTH;
	}

	public boolean down() {
		return this == DOWN || this == BOTH;
	}
}
