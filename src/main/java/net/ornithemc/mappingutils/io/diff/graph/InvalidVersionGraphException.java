package net.ornithemc.mappingutils.io.diff.graph;

public class InvalidVersionGraphException extends RuntimeException {

	private static final long serialVersionUID = -8565290558822804201L;

	public InvalidVersionGraphException() {
		super();
	}

	public InvalidVersionGraphException(String s) {
		super(s);
	}

	public InvalidVersionGraphException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidVersionGraphException(Throwable cause) {
		super(cause);
	}
}
