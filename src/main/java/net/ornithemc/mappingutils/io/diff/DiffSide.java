package net.ornithemc.mappingutils.io.diff;

public enum DiffSide {

	A, B;

	public DiffSide opposite() {
		return this == A ? B : A;
	}
}
