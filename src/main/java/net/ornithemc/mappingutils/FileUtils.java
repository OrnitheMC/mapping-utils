package net.ornithemc.mappingutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

	public static void requireReadable(Path path) throws IOException {
		if (!Files.exists(path) || !Files.isReadable(path)) {
			throw new IOException("cannot read file " + path);
		}
	}

	public static void requireReadable(Path... paths) throws IOException {
		for (Path path : paths) {
			requireReadable(path);
		}
	}

	public static void requireWritable(Path path) throws IOException {
		if (Files.exists(path) && !Files.isWritable(path)) {
			throw new IOException("cannot write file " + path);
		}
	}

	public static void requireWritable(Path... paths) throws IOException {
		for (Path path : paths) {
			requireWritable(path);
		}
	}
}
