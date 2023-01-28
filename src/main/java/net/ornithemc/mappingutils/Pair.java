package net.ornithemc.mappingutils;

public class Pair<L, R> {

	public final L left;
	public final R right;

	public Pair(L left, R right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Pair)) {
			return false;
		}

		@SuppressWarnings("rawtypes")
		Pair other = (Pair)o;

		return left.equals(other.left) && right.equals(other.right);
	}

	@Override
	public int hashCode() {
		return 31 * left.hashCode() + right.hashCode();
	}
}
