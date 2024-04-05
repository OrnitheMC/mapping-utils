package net.ornithemc.mappingutils.io.enigma.dir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.enigma.file.EnigmaFileReader;

public class EnigmaDirReader {

	public static Mappings read(Path dir, boolean cacheById) throws IOException {
		return new EnigmaDirReader(dir, new Mappings(cacheById)).read();
	}

	private final Path dir;
	private final Mappings mappings;

	private EnigmaDirReader(Path dir, Mappings mappings) {
		this.dir = dir;
		this.mappings = mappings;
	}

	public Mappings read() throws IOException {
		Iterator<Path> it = Files.walk(dir).
			filter(p -> Files.isRegularFile(p)).
			filter(p -> p.toString().endsWith(Format.ENIGMA_FILE.mappingsExtension())).
			iterator();

		while (it.hasNext()) {
			EnigmaFileReader.read(it.next(), mappings);
		}

		mappings.sort();

		return mappings;
	}
}
