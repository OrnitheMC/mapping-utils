package net.ornithemc.mappingutils.io.matcher;

public class InputFile {

	public final String name;
	public final long size;
	public final HashAlgorithm alg;
	public final byte[] hash;

	public InputFile(String name, long size, HashAlgorithm alg, byte[] hash) {
		this.name = name;
		this.size = size;
		this.alg = alg;
		this.hash = hash;
	}

	public enum HashAlgorithm {

		SHA1("SHA-1"),
		SHA256("SHA-256");

		public final String id;

		private HashAlgorithm(String id) {
			this.id = id;
		}
	}
}
