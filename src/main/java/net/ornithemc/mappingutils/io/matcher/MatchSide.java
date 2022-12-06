package net.ornithemc.mappingutils.io.matcher;

public enum MatchSide {
	A, B;
	public MatchSide opposite() {
		return this == A ? B : A;
	}
}
