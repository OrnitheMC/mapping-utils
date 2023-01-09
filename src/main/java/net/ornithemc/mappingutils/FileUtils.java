package net.ornithemc.mappingutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class FileUtils {

	public static void requireReadable(Path path) throws IOException {
		if (!Files.exists(path) || !Files.isReadable(path)) {
			throw new IOException("cannot read file " + path);
		}
	}

	public static void requireReadable(List<Path> paths) throws IOException {
		for (Path path : paths) {
			requireReadable(path);
		}
	}

	public static void requireReadable(Path... paths) throws IOException {
		requireReadable(Arrays.asList(paths));
	}

	public static void requireWritable(Path path) throws IOException {
		if (Files.exists(path) && !Files.isWritable(path)) {
			throw new IOException("cannot write file " + path);
		}
	}

	public static void requireWritable(List<Path> paths) throws IOException {
		for (Path path : paths) {
			requireWritable(path);
		}
	}

	public static void requireWritable(Path... paths) throws IOException {
		requireWritable(Arrays.asList(paths));
	}

	public static void iterate(Path path, Consumer<File> operation) throws IOException {
		File dir = path.toFile();

		if (!dir.isDirectory()) {
			throw new IllegalStateException("given path is not a directory!");
		}

		for (File file : dir.listFiles()) {
			if (!file.isFile()) {
				continue;
			}

			requireReadable(file.toPath());

			operation.accept(file);
		}
	}

	public static void delete(File f) throws IOException {
		if (f.exists()) {
			if (f.isDirectory()) {
				for (File ff : f.listFiles()) {
					delete(ff);
				}
			}

			f.delete();
		}
	}
}
