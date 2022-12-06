package net.ornithemc.mappingutils;

public class PropagationOptions {

	/**
	 * The direction along the version tree along with the changes
	 * are propagated, with up being towards the root version and
	 * down beingn away from the root version.
	 */
	public final PropagationDirection dir;
	/**
	 * This option controls how changes to fields and methods are
	 * propagated. When enabled, changes to field and method mappings
	 * are propagated through the name of that field or method, rather
	 * than its key (name + descriptor). Note that this setting should
	 * only be enabled if you are sure field and method names are
	 * unique. If multiple targets with the specified name exist, an
	 * exception will be thrown.
	 */
	public final boolean lenient;

	private PropagationOptions(PropagationDirection dir, boolean lenient) {
		this.dir = dir;
		this.lenient = lenient;
	}

	public static class Builder {

		private PropagationDirection dir = PropagationDirection.BOTH;
		private boolean lenient = false;

		public Builder setPropagationDirection(PropagationDirection dir) {
			this.dir = dir;
			return this;
		}

		public Builder lenient() {
			this.lenient = true;
			return this;
		}

		public PropagationOptions build() {
			return new PropagationOptions(dir, lenient);
		}
	}
}
